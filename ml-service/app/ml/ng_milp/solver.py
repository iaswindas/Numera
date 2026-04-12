"""Exact arithmetic relationship discovery for financial statements."""

from __future__ import annotations

import itertools
import logging
from decimal import Decimal

from .gnn_pruner import GNNPruner
from .models import CandidateEdge, CandidateGraph, CellValue, Expression, NGMILPConfig

logger = logging.getLogger("ml-service.ml.ng_milp.solver")

try:
    from ortools.sat.python import cp_model
except Exception:  # pragma: no cover - optional dependency path
    cp_model = None


class NGMILPSolver:
    """Neural-guided exact solver for arithmetic relationship discovery."""

    TOTAL_KEYWORDS = ("total", "subtotal", "net", "closing", "opening")

    def __init__(self, config: NGMILPConfig):
        self.config = config
        self.gnn_pruner = GNNPruner(config)
        self.tolerance = Decimal(str(config.tolerance))

    def solve(self, cells: list[CellValue]) -> list[Expression]:
        """Discover arithmetic expressions for candidate total rows."""
        usable_cells = [cell for cell in cells if cell.value is not None]
        if len(usable_cells) < 3:
            return []

        graph = self._build_candidate_graph(usable_cells)
        if not graph.edges:
            return []

        pruned_graph, scores = self.gnn_pruner.prune(graph)
        expressions: list[Expression] = []

        for sum_index in self._candidate_sum_indices(pruned_graph.cells):
            sum_edges = pruned_graph.edges_for_sum(sum_index)
            if len(sum_edges) < 2:
                continue

            expression = self._solve_sum(sum_index, sum_edges, pruned_graph.cells, scores)
            if expression is not None:
                expressions.append(expression)

        expressions.sort(key=lambda expression: expression.sum_cell.row)
        return expressions

    def _build_candidate_graph(self, cells: list[CellValue]) -> CandidateGraph:
        edges: list[CandidateEdge] = []

        for sum_index, sum_cell in enumerate(cells):
            if not self._is_sum_candidate(sum_index, cells):
                continue

            window_start = max(0, sum_index - self.config.candidate_row_window)
            for addend_index in range(window_start, sum_index):
                addend = cells[addend_index]
                if addend.page != sum_cell.page:
                    continue
                if addend.row >= sum_cell.row:
                    continue
                if addend.label == sum_cell.label:
                    continue

                row_gap = sum_cell.row - addend.row
                indent_gap = addend.indent_level - sum_cell.indent_level
                if sum_cell.is_total and indent_gap < 0:
                    continue

                sum_abs = abs(sum_cell.value)
                addend_abs = abs(addend.value)
                value_ratio = float(addend_abs / sum_abs) if sum_abs > 0 else 1.0
                spatial_distance = float(row_gap + max(0, sum_cell.indent_level - addend.indent_level))

                edges.append(
                    CandidateEdge(
                        sum_index=sum_index,
                        addend_index=addend_index,
                        spatial_distance=spatial_distance,
                        hierarchy_gap=indent_gap,
                        value_ratio=value_ratio,
                    )
                )

        return CandidateGraph(cells=cells, edges=edges)

    def _candidate_sum_indices(self, cells: list[CellValue]) -> list[int]:
        return [
            index
            for index in range(len(cells))
            if self._is_sum_candidate(index, cells)
        ]

    def _is_sum_candidate(self, index: int, cells: list[CellValue]) -> bool:
        cell = cells[index]
        if index < 2:
            return False

        label = cell.label.lower().strip()
        explicit_total = cell.is_total or any(keyword in label for keyword in self.TOTAL_KEYWORDS)
        preceding_rows = cells[max(0, index - 4):index]
        has_block_before = len(preceding_rows) >= 2 and any(
            row.indent_level >= cell.indent_level for row in preceding_rows
        )
        return explicit_total or has_block_before

    def _solve_sum(
        self,
        sum_index: int,
        sum_edges: list[CandidateEdge],
        cells: list[CellValue],
        scores: dict[tuple[int, int], float],
    ) -> Expression | None:
        if self.config.use_ortools and cp_model is not None:
            expression = self._solve_with_cp_sat(sum_index, sum_edges, cells, scores)
            if expression is not None:
                return expression

        return self._solve_with_combinations(sum_index, sum_edges, cells, scores)

    def _solve_with_cp_sat(
        self,
        sum_index: int,
        sum_edges: list[CandidateEdge],
        cells: list[CellValue],
        scores: dict[tuple[int, int], float],
    ) -> Expression | None:
        sum_cell = cells[sum_index]
        model = cp_model.CpModel()
        scale = self.config.numeric_scale

        addends = [cells[edge.addend_index] for edge in sum_edges]
        scaled_values = [self._to_int(cell.value, scale) for cell in addends]
        target_value = self._to_int(sum_cell.value, scale)
        bound = max(abs(target_value), sum(abs(value) for value in scaled_values), scale)

        selectors = [model.NewBoolVar(f"x_{sum_index}_{idx}") for idx in range(len(addends))]
        total_selected = model.NewIntVar(0, len(addends), f"count_{sum_index}")
        expression_value = model.NewIntVar(-bound, bound, f"sum_{sum_index}")
        difference = model.NewIntVar(-bound * 2, bound * 2, f"diff_{sum_index}")
        abs_difference = model.NewIntVar(0, bound * 2, f"abs_diff_{sum_index}")

        model.Add(total_selected == sum(selectors))
        model.Add(total_selected >= 2)
        model.Add(total_selected <= min(self.config.max_addends, len(addends)))
        model.Add(expression_value == sum(selector * value for selector, value in zip(selectors, scaled_values, strict=False)))
        model.Add(difference == expression_value - target_value)
        model.AddAbsEquality(abs_difference, difference)
        model.Minimize(abs_difference * 1000 + total_selected)

        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = max(self.config.solver_timeout_ms / 1000.0, 0.05)
        solver.parameters.num_search_workers = 8

        status = solver.Solve(model)
        if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
            return None

        selected_addends = [
            addend
            for selector, addend in zip(selectors, addends, strict=False)
            if solver.Value(selector)
        ]
        if len(selected_addends) < 2:
            return None

        residual = abs(sum((cell.value for cell in selected_addends), Decimal("0")) - sum_cell.value)
        if residual > self._allowed_residual(sum_cell.value):
            return None

        confidence = self._average_confidence(sum_index, selected_addends, cells, scores)
        return Expression(
            sum_cell=sum_cell,
            addends=selected_addends,
            residual=residual,
            confidence=confidence,
        )

    def _solve_with_combinations(
        self,
        sum_index: int,
        sum_edges: list[CandidateEdge],
        cells: list[CellValue],
        scores: dict[tuple[int, int], float],
    ) -> Expression | None:
        sum_cell = cells[sum_index]
        candidate_cells = [cells[edge.addend_index] for edge in sum_edges]
        best_solution: list[CellValue] | None = None
        best_residual: Decimal | None = None

        max_size = min(self.config.max_addends, len(candidate_cells))
        for size in range(2, max_size + 1):
            for combination in itertools.combinations(candidate_cells, size):
                computed = sum((cell.value for cell in combination), Decimal("0"))
                residual = abs(computed - sum_cell.value)
                if best_residual is None or residual < best_residual:
                    best_solution = list(combination)
                    best_residual = residual

                if residual <= self._allowed_residual(sum_cell.value):
                    confidence = self._average_confidence(sum_index, list(combination), cells, scores)
                    return Expression(
                        sum_cell=sum_cell,
                        addends=list(combination),
                        residual=residual,
                        confidence=confidence,
                    )

        if best_solution is None or best_residual is None:
            return None
        if best_residual > self._allowed_residual(sum_cell.value):
            return None

        confidence = self._average_confidence(sum_index, best_solution, cells, scores)
        return Expression(
            sum_cell=sum_cell,
            addends=best_solution,
            residual=best_residual,
            confidence=confidence,
        )

    def _average_confidence(
        self,
        sum_index: int,
        addends: list[CellValue],
        cells: list[CellValue],
        scores: dict[tuple[int, int], float],
    ) -> float:
        row_to_index = {cell.row: index for index, cell in enumerate(cells)}
        selected_scores = [
            scores.get((sum_index, row_to_index[cell.row]), 0.5)
            for cell in addends
            if cell.row in row_to_index
        ]
        if not selected_scores:
            return 0.5
        return round(sum(selected_scores) / len(selected_scores), 4)

    def _allowed_residual(self, target: Decimal) -> Decimal:
        target_abs = abs(target)
        if target_abs == 0:
            return Decimal("1")
        return max(Decimal("0.01"), target_abs * self.tolerance)

    @staticmethod
    def _to_int(value: Decimal, scale: int) -> int:
        return int((value * scale).quantize(Decimal("1")))