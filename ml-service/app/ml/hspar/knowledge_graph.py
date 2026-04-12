"""Query and validation services for H-SPAR knowledge graphs."""

from __future__ import annotations

import logging
from collections import defaultdict
from datetime import datetime, timezone

from .models import EdgeType, KGEdge, KGNode, KnowledgeGraph, TraversalResult

logger = logging.getLogger("ml-service.ml.hspar.knowledge_graph")


class KnowledgeGraphService:
    """Traversal, query, validation, and merge operations on a KnowledgeGraph."""

    def query(self, graph: KnowledgeGraph, entity_label: str) -> list[TraversalResult]:
        """Return all upstream paths that contribute to *entity_label*."""
        node = self._find_node_by_label(graph, entity_label)
        if node is None:
            return []
        return self._trace_upstream(graph, node.id)

    def get_contributors(self, graph: KnowledgeGraph, node_id: str) -> list[KGNode]:
        """Return nodes with edges pointing *into* ``node_id``."""
        incoming_ids = {
            e.source_id for e in graph.edges if e.target_id == node_id
        }
        return [n for n in graph.nodes if n.id in incoming_ids]

    def get_dependents(self, graph: KnowledgeGraph, node_id: str) -> list[KGNode]:
        """Return nodes that depend on ``node_id`` (edges going *out*)."""
        outgoing_ids = {
            e.target_id for e in graph.edges if e.source_id == node_id
        }
        return [n for n in graph.nodes if n.id in outgoing_ids]

    def validate_consistency(self, graph: KnowledgeGraph) -> list[dict]:
        """Check that SUM_OF edges are arithmetically consistent.

        Returns a list of inconsistency dicts with ``node_id``, ``expected``,
        ``actual``, and ``difference``.
        """
        node_map = {n.id: n for n in graph.nodes}
        # Group SUM_OF edges by target (the sum node).
        sum_groups: dict[str, list[str]] = defaultdict(list)
        for edge in graph.edges:
            if edge.edge_type == EdgeType.SUM_OF:
                sum_groups[edge.target_id].append(edge.source_id)

        issues: list[dict] = []
        for target_id, source_ids in sum_groups.items():
            target = node_map.get(target_id)
            if target is None or target.value is None:
                continue
            addend_values = [
                node_map[sid].value
                for sid in source_ids
                if sid in node_map and node_map[sid].value is not None
            ]
            if not addend_values:
                continue
            expected = sum(addend_values)
            diff = abs(expected - target.value)
            # Tolerance: 0.5% of the target or 1.0 absolute
            threshold = max(abs(target.value) * 0.005, 1.0)
            if diff > threshold:
                issues.append(
                    {
                        "node_id": target_id,
                        "label": target.label,
                        "expected": round(expected, 4),
                        "actual": round(target.value, 4),
                        "difference": round(diff, 4),
                    }
                )
        return issues

    def merge_graphs(self, graphs: list[KnowledgeGraph]) -> KnowledgeGraph:
        """Merge multiple knowledge graphs (e.g. multi-period) into one."""
        all_nodes: list[KGNode] = []
        all_edges: list[KGEdge] = []
        seen_node_ids: set[str] = set()

        for g in graphs:
            for node in g.nodes:
                if node.id not in seen_node_ids:
                    all_nodes.append(node)
                    seen_node_ids.add(node.id)
            all_edges.extend(g.edges)

        entity_id = graphs[0].entity_id if graphs else ""
        document_id = graphs[0].document_id if graphs else ""
        return KnowledgeGraph(
            nodes=all_nodes,
            edges=all_edges,
            entity_id=entity_id,
            document_id=document_id,
            created_at=datetime.now(timezone.utc),
        )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _find_node_by_label(graph: KnowledgeGraph, label: str) -> KGNode | None:
        label_lower = label.lower()
        for n in graph.nodes:
            if n.label.lower() == label_lower:
                return n
        return None

    def _trace_upstream(
        self, graph: KnowledgeGraph, target_id: str
    ) -> list[TraversalResult]:
        """BFS upstream: follow edges where ``target_id`` is the edge target."""
        node_map = {n.id: n for n in graph.nodes}
        # adjacency: target → [(source, edge)]
        adj: dict[str, list[tuple[str, KGEdge]]] = defaultdict(list)
        for e in graph.edges:
            adj[e.target_id].append((e.source_id, e))

        results: list[TraversalResult] = []
        visited: set[str] = set()
        queue: list[tuple[str, list[str], list[str]]] = [
            (target_id, [target_id], [])
        ]

        while queue:
            current, path, rels = queue.pop(0)
            for source_id, edge in adj.get(current, []):
                if source_id in visited:
                    continue
                visited.add(source_id)
                new_path = path + [source_id]
                new_rels = rels + [edge.edge_type.value]
                source_node = node_map.get(source_id)
                results.append(
                    TraversalResult(
                        path=new_path,
                        relationships=new_rels,
                        total_value=source_node.value if source_node else None,
                    )
                )
                queue.append((source_id, new_path, new_rels))

        return results
