"""Data models for the NG-MILP expression solver."""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal


@dataclass(frozen=True)
class CellValue:
    """A numeric cell that can participate in an arithmetic expression."""

    row: int
    col: int
    value: Decimal
    label: str
    indent_level: int = 0
    is_bold: bool = False
    is_total: bool = False
    page: int = 0


@dataclass(frozen=True)
class CandidateEdge:
    """A possible sum relationship between a total and an addend."""

    sum_index: int
    addend_index: int
    spatial_distance: float
    hierarchy_gap: int
    value_ratio: float


@dataclass
class CandidateGraph:
    """Pruned candidate graph used by the solver."""

    cells: list[CellValue]
    edges: list[CandidateEdge]
    edge_index: dict[tuple[int, int], CandidateEdge] = field(init=False)

    def __post_init__(self):
        self.edge_index = {
            (edge.sum_index, edge.addend_index): edge
            for edge in self.edges
        }

    def edges_for_sum(self, sum_index: int) -> list[CandidateEdge]:
        return [edge for edge in self.edges if edge.sum_index == sum_index]


@dataclass
class Expression:
    """A discovered arithmetic relationship."""

    sum_cell: CellValue
    addends: list[CellValue]
    residual: Decimal
    confidence: float
    expression_type: str = "SUM"

    @property
    def computed_value(self) -> Decimal:
        return sum((cell.value for cell in self.addends), Decimal("0"))


@dataclass
class NGMILPConfig:
    """Runtime configuration for the NG-MILP solver."""

    tolerance: float = 0.005
    max_candidates_per_cell: int = 15
    solver_timeout_ms: int = 5000
    gnn_weights_path: str = ""
    use_ortools: bool = True
    candidate_row_window: int = 16
    max_addends: int = 6
    numeric_scale: int = 100