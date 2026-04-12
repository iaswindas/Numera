"""Tests for the H-SPAR knowledge graph module."""

from __future__ import annotations

import pytest

from app.ml.hspar.graph_builder import KnowledgeGraphBuilder
from app.ml.hspar.knowledge_graph import KnowledgeGraphService
from app.ml.hspar.models import EdgeType, KnowledgeGraph, NodeType


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

SAMPLE_ROWS = [
    {"label": "Cash and Equivalents", "value": 50000, "indent_level": 2, "row_index": 0},
    {"label": "Accounts Receivable", "value": 30000, "indent_level": 2, "row_index": 1},
    {"label": "Inventory", "value": 20000, "indent_level": 2, "row_index": 2},
    {"label": "Total Current Assets", "value": 100000, "indent_level": 1, "is_bold": True, "row_index": 3},
    {"label": "Property Plant Equipment", "value": 200000, "indent_level": 2, "row_index": 4},
    {"label": "Total Non-Current Assets", "value": 200000, "indent_level": 1, "is_bold": True, "row_index": 5},
    {"label": "Total Assets", "value": 300000, "indent_level": 0, "is_bold": True, "row_index": 6},
]

SAMPLE_EXPRESSIONS = [
    {
        "sum_label": "Total Current Assets",
        "addend_labels": ["Cash and Equivalents", "Accounts Receivable", "Inventory"],
        "confidence": 0.99,
    },
    {
        "sum_label": "Total Assets",
        "addend_labels": ["Total Current Assets", "Total Non-Current Assets"],
        "confidence": 0.98,
    },
]


@pytest.fixture
def builder() -> KnowledgeGraphBuilder:
    return KnowledgeGraphBuilder()


@pytest.fixture
def service() -> KnowledgeGraphService:
    return KnowledgeGraphService()


@pytest.fixture
def sample_graph(builder: KnowledgeGraphBuilder) -> KnowledgeGraph:
    return builder.build(
        extracted_rows=SAMPLE_ROWS,
        expressions=SAMPLE_EXPRESSIONS,
        zone_type="BALANCE_SHEET",
        entity_id="test-entity",
        document_id="test-doc",
    )


# ---------------------------------------------------------------------------
# Graph building
# ---------------------------------------------------------------------------

class TestGraphBuilder:
    def test_creates_nodes_for_all_rows(self, sample_graph: KnowledgeGraph):
        assert len(sample_graph.nodes) == len(SAMPLE_ROWS)

    def test_node_types_classified(self, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        assert by_label["Cash and Equivalents"].node_type == NodeType.LINE_ITEM
        assert by_label["Total Current Assets"].node_type == NodeType.TOTAL
        assert by_label["Total Assets"].node_type == NodeType.TOTAL

    def test_node_values_preserved(self, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        assert by_label["Cash and Equivalents"].value == 50000
        assert by_label["Total Assets"].value == 300000

    def test_zone_type_set(self, sample_graph: KnowledgeGraph):
        for node in sample_graph.nodes:
            assert node.zone_type == "BALANCE_SHEET"

    def test_entity_and_document_ids(self, sample_graph: KnowledgeGraph):
        assert sample_graph.entity_id == "test-entity"
        assert sample_graph.document_id == "test-doc"

    def test_sum_of_edges_created(self, sample_graph: KnowledgeGraph):
        sum_edges = [e for e in sample_graph.edges if e.edge_type == EdgeType.SUM_OF]
        # 3 addends → Total Current Assets + 2 addends → Total Assets = 5
        assert len(sum_edges) == 5

    def test_component_of_edges_created(self, sample_graph: KnowledgeGraph):
        comp_edges = [e for e in sample_graph.edges if e.edge_type == EdgeType.COMPONENT_OF]
        assert len(comp_edges) > 0

    def test_empty_rows_produce_empty_graph(self, builder: KnowledgeGraphBuilder):
        graph = builder.build([], [], zone_type="BALANCE_SHEET")
        assert graph.nodes == []
        assert graph.edges == []

    def test_rows_without_values(self, builder: KnowledgeGraphBuilder):
        rows = [{"label": "Some Item"}, {"label": "Another Item"}]
        graph = builder.build(rows, [])
        assert len(graph.nodes) == 2
        assert all(n.value is None for n in graph.nodes)


# ---------------------------------------------------------------------------
# Hierarchy detection
# ---------------------------------------------------------------------------

class TestHierarchyDetection:
    def test_indented_items_have_component_edges(self, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        comp_edges = [e for e in sample_graph.edges if e.edge_type == EdgeType.COMPONENT_OF]
        source_ids = {e.source_id for e in comp_edges}
        # Cash, AR, Inventory should be sources of COMPONENT_OF
        for label in ("Cash and Equivalents", "Accounts Receivable", "Inventory"):
            assert by_label[label].id in source_ids

    def test_total_name_linking(self, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        total_ca = by_label["Total Current Assets"]
        comp_edges = [
            e for e in sample_graph.edges
            if e.edge_type == EdgeType.COMPONENT_OF and e.target_id == total_ca.id
        ]
        assert len(comp_edges) > 0


# ---------------------------------------------------------------------------
# Traversal queries
# ---------------------------------------------------------------------------

class TestTraversalQueries:
    def test_query_returns_paths(self, service: KnowledgeGraphService, sample_graph: KnowledgeGraph):
        results = service.query(sample_graph, "Total Current Assets")
        assert len(results) > 0

    def test_query_nonexistent_label(self, service: KnowledgeGraphService, sample_graph: KnowledgeGraph):
        results = service.query(sample_graph, "Nonexistent Item")
        assert results == []

    def test_get_contributors(self, service: KnowledgeGraphService, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        total_ca = by_label["Total Current Assets"]
        contributors = service.get_contributors(sample_graph, total_ca.id)
        contributor_labels = {c.label for c in contributors}
        assert "Cash and Equivalents" in contributor_labels
        assert "Accounts Receivable" in contributor_labels

    def test_get_dependents(self, service: KnowledgeGraphService, sample_graph: KnowledgeGraph):
        by_label = {n.label: n for n in sample_graph.nodes}
        cash = by_label["Cash and Equivalents"]
        dependents = service.get_dependents(sample_graph, cash.id)
        dependent_labels = {d.label for d in dependents}
        assert "Total Current Assets" in dependent_labels


# ---------------------------------------------------------------------------
# Consistency validation
# ---------------------------------------------------------------------------

class TestConsistencyValidation:
    def test_consistent_graph_has_no_issues(self, service: KnowledgeGraphService, sample_graph: KnowledgeGraph):
        issues = service.validate_consistency(sample_graph)
        assert issues == []

    def test_inconsistent_graph_reports_issues(self, service: KnowledgeGraphService, builder: KnowledgeGraphBuilder):
        rows = [
            {"label": "Item A", "value": 100, "indent_level": 1, "row_index": 0},
            {"label": "Item B", "value": 200, "indent_level": 1, "row_index": 1},
            {"label": "Total X", "value": 999, "indent_level": 0, "row_index": 2},  # Should be 300
        ]
        expressions = [
            {"sum_label": "Total X", "addend_labels": ["Item A", "Item B"]},
        ]
        graph = builder.build(rows, expressions)
        issues = service.validate_consistency(graph)
        assert len(issues) == 1
        assert issues[0]["label"] == "Total X"
        assert issues[0]["expected"] == 300.0


# ---------------------------------------------------------------------------
# Graph merge
# ---------------------------------------------------------------------------

class TestGraphMerge:
    def test_merge_combines_nodes_and_edges(self, service: KnowledgeGraphService, builder: KnowledgeGraphBuilder):
        g1 = builder.build(
            [{"label": "A", "value": 10}], [], entity_id="e1", document_id="d1"
        )
        g2 = builder.build(
            [{"label": "B", "value": 20}], [], entity_id="e1", document_id="d2"
        )
        merged = service.merge_graphs([g1, g2])
        assert len(merged.nodes) == 2
        labels = {n.label for n in merged.nodes}
        assert labels == {"A", "B"}


# ---------------------------------------------------------------------------
# Ratio detection
# ---------------------------------------------------------------------------

class TestRatioDetection:
    def test_ratio_edge_detected(self, builder: KnowledgeGraphBuilder):
        rows = [
            {"label": "Current Assets", "value": 100000, "indent_level": 0, "row_index": 0},
            {"label": "Current Liabilities", "value": 50000, "indent_level": 0, "row_index": 1},
            {"label": "Current Ratio", "value": 2.0, "indent_level": 0, "row_index": 2},
        ]
        graph = builder.build(rows, [])
        ratio_edges = [e for e in graph.edges if e.edge_type == EdgeType.RATIO_TO]
        assert len(ratio_edges) == 2  # numerator + denominator both linked


# ---------------------------------------------------------------------------
# API integration (via TestClient)
# ---------------------------------------------------------------------------

class TestKnowledgeGraphAPI:
    def test_build_endpoint(self, client):
        payload = {
            "entity_id": "api-test",
            "document_id": "doc-1",
            "zone_type": "BALANCE_SHEET",
            "extracted_rows": [
                {"label": "Cash", "value": 100, "indent_level": 1, "row_index": 0},
                {"label": "Total", "value": 100, "indent_level": 0, "is_bold": True, "row_index": 1},
            ],
            "expressions": [
                {"sum_label": "Total", "addend_labels": ["Cash"]},
            ],
        }
        resp = client.post("/api/ml/knowledge-graph/build", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert "graph" in body
        assert len(body["graph"]["nodes"]) == 2

    def test_get_endpoint(self, client):
        # Build first
        client.post("/api/ml/knowledge-graph/build", json={
            "entity_id": "get-test",
            "document_id": "doc-1",
            "extracted_rows": [{"label": "X", "value": 1}],
        })
        resp = client.get("/api/ml/knowledge-graph/get-test")
        assert resp.status_code == 200

    def test_get_not_found(self, client):
        resp = client.get("/api/ml/knowledge-graph/nonexistent")
        assert resp.status_code == 404

    def test_query_endpoint(self, client):
        client.post("/api/ml/knowledge-graph/build", json={
            "entity_id": "query-test",
            "document_id": "doc-1",
            "extracted_rows": [
                {"label": "A", "value": 50, "indent_level": 1},
                {"label": "Total A", "value": 50, "indent_level": 0, "is_bold": True},
            ],
            "expressions": [{"sum_label": "Total A", "addend_labels": ["A"]}],
        })
        resp = client.post(
            "/api/ml/knowledge-graph/query-test/query",
            json={"entity_label": "Total A"},
        )
        assert resp.status_code == 200

    def test_validate_endpoint(self, client):
        # Build a consistent graph via the build endpoint first, then validate it
        build_resp = client.post("/api/ml/knowledge-graph/build", json={
            "entity_id": "val-test",
            "document_id": "doc-1",
            "extracted_rows": [
                {"label": "X", "value": 10, "indent_level": 1},
                {"label": "Total", "value": 10, "indent_level": 0, "is_bold": True},
            ],
            "expressions": [{"sum_label": "Total", "addend_labels": ["X"]}],
        })
        graph_data = build_resp.json()["graph"]
        resp = client.post("/api/ml/knowledge-graph/validate", json=graph_data)
        assert resp.status_code == 200
        body = resp.json()
        assert body["is_consistent"] is True
