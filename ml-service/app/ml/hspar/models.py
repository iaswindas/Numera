"""Data models for the H-SPAR knowledge graph."""

from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum

from pydantic import BaseModel, Field


class NodeType(str, Enum):
    LINE_ITEM = "LINE_ITEM"
    CATEGORY = "CATEGORY"
    TOTAL = "TOTAL"
    SUBTOTAL = "SUBTOTAL"
    RATIO = "RATIO"
    DERIVED = "DERIVED"


class EdgeType(str, Enum):
    SUM_OF = "SUM_OF"
    COMPONENT_OF = "COMPONENT_OF"
    RATIO_TO = "RATIO_TO"
    DERIVED_FROM = "DERIVED_FROM"
    EQUALS = "EQUALS"
    PERCENTAGE_OF = "PERCENTAGE_OF"


class KGNode(BaseModel):
    id: str
    label: str
    node_type: NodeType
    value: float | None = None
    zone_type: str = ""
    metadata: dict = Field(default_factory=dict)


class KGEdge(BaseModel):
    source_id: str
    target_id: str
    edge_type: EdgeType
    weight: float = 1.0
    expression: str | None = None


class KnowledgeGraph(BaseModel):
    nodes: list[KGNode] = Field(default_factory=list)
    edges: list[KGEdge] = Field(default_factory=list)
    entity_id: str = ""
    document_id: str = ""
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class TraversalResult(BaseModel):
    path: list[str] = Field(default_factory=list)
    relationships: list[str] = Field(default_factory=list)
    total_value: float | None = None
