"""POST/GET /api/ml/knowledge-graph — H-SPAR knowledge-graph endpoints."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.ml.hspar.graph_builder import KnowledgeGraphBuilder
from app.ml.hspar.knowledge_graph import KnowledgeGraphService
from app.ml.hspar.models import KnowledgeGraph

logger = logging.getLogger(__name__)
router = APIRouter()

_builder = KnowledgeGraphBuilder()
_service = KnowledgeGraphService()

# In-memory store keyed by entity_id — production would persist to DB.
_store: dict[str, KnowledgeGraph] = {}


# --- Request / Response models ---

class ExtractedRowItem(BaseModel):
    label: str
    value: float | None = None
    indent_level: int = 0
    is_bold: bool = False
    row_index: int = 0


class ExpressionItem(BaseModel):
    sum_label: str
    addend_labels: list[str]
    residual: float = 0.0
    confidence: float = 1.0


class BuildKGRequest(BaseModel):
    entity_id: str
    document_id: str
    zone_type: str = ""
    extracted_rows: list[ExtractedRowItem]
    expressions: list[ExpressionItem] = Field(default_factory=list)


class BuildKGResponse(BaseModel):
    graph: KnowledgeGraph
    processing_time_ms: int


class QueryRequest(BaseModel):
    entity_label: str


class QueryResponse(BaseModel):
    paths: list[dict]
    processing_time_ms: int


class ValidateResponse(BaseModel):
    inconsistencies: list[dict]
    is_consistent: bool
    processing_time_ms: int


# --- Endpoints ---

@router.post("/knowledge-graph/build", response_model=BuildKGResponse)
async def build_knowledge_graph(request: BuildKGRequest) -> BuildKGResponse:
    """Build a knowledge graph from extraction data."""
    start = time.perf_counter_ns()
    rows = [r.model_dump() for r in request.extracted_rows]
    exprs = [e.model_dump() for e in request.expressions]

    graph = _builder.build(
        extracted_rows=rows,
        expressions=exprs,
        zone_type=request.zone_type,
        entity_id=request.entity_id,
        document_id=request.document_id,
    )
    _store[request.entity_id] = graph
    elapsed = (time.perf_counter_ns() - start) // 1_000_000
    return BuildKGResponse(graph=graph, processing_time_ms=elapsed)


@router.get("/knowledge-graph/{entity_id}", response_model=KnowledgeGraph)
async def get_knowledge_graph(entity_id: str) -> KnowledgeGraph:
    """Retrieve a previously built knowledge graph."""
    graph = _store.get(entity_id)
    if graph is None:
        raise HTTPException(status_code=404, detail=f"No graph found for entity {entity_id}")
    return graph


@router.post("/knowledge-graph/{entity_id}/query", response_model=QueryResponse)
async def query_knowledge_graph(entity_id: str, request: QueryRequest) -> QueryResponse:
    """Query a knowledge graph for upstream contributors to a labelled entity."""
    graph = _store.get(entity_id)
    if graph is None:
        raise HTTPException(status_code=404, detail=f"No graph found for entity {entity_id}")
    start = time.perf_counter_ns()
    results = _service.query(graph, request.entity_label)
    elapsed = (time.perf_counter_ns() - start) // 1_000_000
    return QueryResponse(
        paths=[r.model_dump() for r in results],
        processing_time_ms=elapsed,
    )


@router.post("/knowledge-graph/validate", response_model=ValidateResponse)
async def validate_knowledge_graph(graph: KnowledgeGraph) -> ValidateResponse:
    """Validate arithmetic consistency of a knowledge graph."""
    start = time.perf_counter_ns()
    issues = _service.validate_consistency(graph)
    elapsed = (time.perf_counter_ns() - start) // 1_000_000
    return ValidateResponse(
        inconsistencies=issues,
        is_consistent=len(issues) == 0,
        processing_time_ms=elapsed,
    )
