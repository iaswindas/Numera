"""Tests for the Phase 1 NG-MILP implementation."""

from decimal import Decimal


def test_solver_discovers_exact_sum():
    from app.ml.ng_milp import CellValue, NGMILPConfig, NGMILPSolver

    solver = NGMILPSolver(
        NGMILPConfig(
            use_ortools=False,
            candidate_row_window=5,
            max_candidates_per_cell=5,
        )
    )
    cells = [
        CellValue(row=0, col=0, value=Decimal("100"), label="Cash", indent_level=1),
        CellValue(row=1, col=0, value=Decimal("50"), label="Trade Receivables", indent_level=1),
        CellValue(row=2, col=0, value=Decimal("150"), label="Total Current Assets", indent_level=0, is_total=True),
    ]

    expressions = solver.solve(cells)

    assert len(expressions) == 1
    assert expressions[0].sum_cell.label == "Total Current Assets"
    assert {cell.label for cell in expressions[0].addends} == {"Cash", "Trade Receivables"}
    assert expressions[0].residual == Decimal("0")


def test_gnn_pruner_uses_uniform_prior_without_weights():
    from app.ml.ng_milp.gnn_pruner import GNNPruner
    from app.ml.ng_milp.models import CandidateEdge, CandidateGraph, CellValue, NGMILPConfig

    pruner = GNNPruner(NGMILPConfig(gnn_weights_path=""))
    graph = CandidateGraph(
        cells=[
            CellValue(row=0, col=0, value=Decimal("100"), label="Cash"),
            CellValue(row=1, col=0, value=Decimal("150"), label="Total Assets", is_total=True),
        ],
        edges=[
            CandidateEdge(sum_index=1, addend_index=0, spatial_distance=1.0, hierarchy_gap=1, value_ratio=0.66),
        ],
    )

    scores = pruner.score_edges(graph)

    assert scores[(1, 0)] == 0.5


def test_expression_engine_delegates_to_ng_milp(monkeypatch):
    from app.config import settings
    from app.ml.expression_engine import ExpressionEngine, ExpressionType, ExtractedRow

    monkeypatch.setattr(settings, "enable_ng_milp", True)
    monkeypatch.setattr(settings, "ng_milp_use_ortools", False)
    monkeypatch.setattr(settings, "ng_milp_timeout_ms", 100)
    monkeypatch.setattr(settings, "ng_milp_max_candidates_per_cell", 6)
    monkeypatch.setattr(settings, "ng_milp_candidate_row_window", 5)
    monkeypatch.setattr(settings, "ng_milp_max_addends", 4)

    engine = ExpressionEngine()
    extracted_rows = [
        ExtractedRow(index=0, label="Cash", values=[100.0], indent_level=0, page=0),
        ExtractedRow(index=1, label="Trade Receivables", values=[50.0], indent_level=0, page=0),
        ExtractedRow(index=2, label="Total Current Assets", values=[150.0], indent_level=0, page=0),
    ]
    semantic_matches = [{"row_index": 2, "target_item_id": "BS010", "confidence": 0.94}]
    model_items = [{"id": "BS010", "label": "Total Current Assets", "type": "INPUT"}]

    expressions = engine.build_expressions(
        extracted_rows=extracted_rows,
        semantic_matches=semantic_matches,
        model_items=model_items,
    )

    assert len(expressions) == 1
    assert expressions[0].expression_type == ExpressionType.SUM
    assert len(expressions[0].sources) == 2
    assert expressions[0].computed_value == 150.0