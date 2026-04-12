"""Build a financial knowledge graph from extracted rows and expressions."""

from __future__ import annotations

import logging
import re
import uuid
from datetime import datetime, timezone

from .models import EdgeType, KGEdge, KGNode, KnowledgeGraph, NodeType

logger = logging.getLogger("ml-service.ml.hspar.graph_builder")

_TOTAL_PATTERN = re.compile(
    r"\b(total|net|closing balance|gross)\b", re.IGNORECASE
)
_SUBTOTAL_PATTERN = re.compile(
    r"\b(subtotal|sub-total|sub total)\b", re.IGNORECASE
)
_RATIO_PATTERN = re.compile(
    r"\b(ratio|margin|percentage|percent|%|turnover|coverage)\b", re.IGNORECASE
)

_DEFAULT_TOLERANCE = 0.005


class KnowledgeGraphBuilder:
    """Constructs a KnowledgeGraph from extracted financial data."""

    def __init__(self, tolerance: float = _DEFAULT_TOLERANCE):
        self.tolerance = tolerance

    def build(
        self,
        extracted_rows: list[dict],
        expressions: list[dict],
        zone_type: str = "",
        entity_id: str = "",
        document_id: str = "",
    ) -> KnowledgeGraph:
        """Build a knowledge graph from extraction results and discovered expressions.

        Args:
            extracted_rows: List of dicts with keys ``label``, ``value`` (float|None),
                ``indent_level`` (int, optional), ``is_bold`` (bool, optional),
                ``row_index`` (int, optional).
            expressions: List of dicts with keys ``sum_label``, ``addend_labels`` (list[str]),
                ``residual`` (float, optional), ``confidence`` (float, optional).
            zone_type: Financial-statement zone (e.g. ``BALANCE_SHEET``).
            entity_id: Owning entity identifier.
            document_id: Source document identifier.

        Returns:
            A populated ``KnowledgeGraph``.
        """
        nodes = self._create_nodes(extracted_rows, zone_type)
        label_to_id = {n.label: n.id for n in nodes}

        edges: list[KGEdge] = []
        edges.extend(self._hierarchy_edges(extracted_rows, nodes, label_to_id))
        edges.extend(self._expression_edges(expressions, label_to_id))
        edges.extend(self._ratio_edges(nodes, label_to_id))

        return KnowledgeGraph(
            nodes=nodes,
            edges=edges,
            entity_id=entity_id,
            document_id=document_id,
            created_at=datetime.now(timezone.utc),
        )

    # ------------------------------------------------------------------
    # Node creation
    # ------------------------------------------------------------------

    def _create_nodes(
        self, rows: list[dict], zone_type: str
    ) -> list[KGNode]:
        nodes: list[KGNode] = []
        for idx, row in enumerate(rows):
            label = str(row.get("label", "")).strip()
            if not label:
                continue
            value = row.get("value")
            node_type = self._classify_node(label, row)
            nodes.append(
                KGNode(
                    id=f"node-{idx}-{uuid.uuid4().hex[:8]}",
                    label=label,
                    node_type=node_type,
                    value=float(value) if value is not None else None,
                    zone_type=zone_type,
                    metadata={
                        "row_index": row.get("row_index", idx),
                        "indent_level": row.get("indent_level", 0),
                        "is_bold": row.get("is_bold", False),
                    },
                )
            )
        return nodes

    @staticmethod
    def _classify_node(label: str, row: dict) -> NodeType:
        if _RATIO_PATTERN.search(label):
            return NodeType.RATIO
        if _SUBTOTAL_PATTERN.search(label):
            return NodeType.SUBTOTAL
        if _TOTAL_PATTERN.search(label):
            return NodeType.TOTAL
        if row.get("is_bold"):
            return NodeType.CATEGORY
        return NodeType.LINE_ITEM

    # ------------------------------------------------------------------
    # Hierarchy edges (indentation / naming)
    # ------------------------------------------------------------------

    def _hierarchy_edges(
        self,
        rows: list[dict],
        nodes: list[KGNode],
        label_to_id: dict[str, str],
    ) -> list[KGEdge]:
        edges: list[KGEdge] = []
        if not nodes:
            return edges

        # Group consecutive line-items under the nearest following total/subtotal
        # at a shallower indent level.
        for i, node in enumerate(nodes):
            if node.node_type not in (NodeType.TOTAL, NodeType.SUBTOTAL):
                continue
            node_indent = node.metadata.get("indent_level", 0)
            # Walk backwards to collect children at deeper indent.
            for j in range(i - 1, -1, -1):
                candidate = nodes[j]
                cand_indent = candidate.metadata.get("indent_level", 0)
                if cand_indent <= node_indent:
                    break  # reached same or shallower level — stop
                edges.append(
                    KGEdge(
                        source_id=candidate.id,
                        target_id=node.id,
                        edge_type=EdgeType.COMPONENT_OF,
                    )
                )

        # Also link "Total X" to children whose labels contain the tail.
        self._link_totals_by_name(nodes, label_to_id, edges)
        return edges

    @staticmethod
    def _link_totals_by_name(
        nodes: list[KGNode],
        label_to_id: dict[str, str],
        edges: list[KGEdge],
    ) -> None:
        existing_pairs = {(e.source_id, e.target_id) for e in edges}
        for node in nodes:
            if node.node_type not in (NodeType.TOTAL, NodeType.SUBTOTAL):
                continue
            # Extract tail: "Total Current Assets" → "Current Assets"
            tail = re.sub(r"(?i)^(total|subtotal|net|gross)\s+", "", node.label).strip()
            if not tail:
                continue
            for candidate in nodes:
                if candidate.id == node.id:
                    continue
                if candidate.node_type in (NodeType.TOTAL, NodeType.SUBTOTAL):
                    continue
                if tail.lower() in candidate.label.lower() or candidate.label.lower() in tail.lower():
                    pair = (candidate.id, node.id)
                    if pair not in existing_pairs:
                        edges.append(
                            KGEdge(
                                source_id=candidate.id,
                                target_id=node.id,
                                edge_type=EdgeType.COMPONENT_OF,
                            )
                        )
                        existing_pairs.add(pair)

    # ------------------------------------------------------------------
    # Expression-based (SUM_OF) edges
    # ------------------------------------------------------------------

    def _expression_edges(
        self,
        expressions: list[dict],
        label_to_id: dict[str, str],
    ) -> list[KGEdge]:
        edges: list[KGEdge] = []
        for expr in expressions:
            sum_label = expr.get("sum_label", "")
            sum_id = label_to_id.get(sum_label)
            if sum_id is None:
                continue
            addend_labels: list[str] = expr.get("addend_labels", [])
            confidence = float(expr.get("confidence", 1.0))
            expr_text_parts: list[str] = []
            for addend_label in addend_labels:
                addend_id = label_to_id.get(addend_label)
                if addend_id is None:
                    continue
                expr_text_parts.append(addend_label)
                edges.append(
                    KGEdge(
                        source_id=addend_id,
                        target_id=sum_id,
                        edge_type=EdgeType.SUM_OF,
                        weight=confidence,
                        expression=f"{addend_label} → {sum_label}",
                    )
                )
        return edges

    # ------------------------------------------------------------------
    # Ratio edges
    # ------------------------------------------------------------------

    def _ratio_edges(
        self,
        nodes: list[KGNode],
        label_to_id: dict[str, str],
    ) -> list[KGEdge]:
        edges: list[KGEdge] = []
        ratio_nodes = [n for n in nodes if n.node_type == NodeType.RATIO]
        non_ratio_nodes = [n for n in nodes if n.node_type != NodeType.RATIO]

        for rn in ratio_nodes:
            # Heuristic: look for two non-ratio nodes whose division ≈ ratio value
            if rn.value is None or rn.value == 0:
                continue
            for i, a in enumerate(non_ratio_nodes):
                if a.value is None or a.value == 0:
                    continue
                for b in non_ratio_nodes[i + 1 :]:
                    if b.value is None or b.value == 0:
                        continue
                    # Try a/b and b/a
                    for num, den in ((a, b), (b, a)):
                        ratio = num.value / den.value
                        if abs(ratio - rn.value) <= self.tolerance * max(abs(rn.value), 1):
                            edges.append(
                                KGEdge(
                                    source_id=num.id,
                                    target_id=rn.id,
                                    edge_type=EdgeType.RATIO_TO,
                                    expression=f"{num.label} / {den.label}",
                                )
                            )
                            edges.append(
                                KGEdge(
                                    source_id=den.id,
                                    target_id=rn.id,
                                    edge_type=EdgeType.RATIO_TO,
                                    expression=f"{num.label} / {den.label}",
                                )
                            )
                            break
                    else:
                        continue
                    break
        return edges
