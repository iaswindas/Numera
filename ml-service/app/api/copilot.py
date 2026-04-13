"""Copilot API endpoints.

POST /api/ml/copilot/query          – Ask a question (RAG pipeline)
POST /api/ml/copilot/query/parse    – Parse NL query to structured filters
POST /api/ml/copilot/index/spread   – Index spread values
POST /api/ml/copilot/index/document – Index document OCR chunks
GET  /api/ml/copilot/status         – Service health & vector store stats
"""

from __future__ import annotations

import logging
import traceback
from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.ml.copilot.query_parser import QueryParser
from app.ml.copilot.rag_engine import RagEngine
from app.ml.copilot.vector_store import VectorStore

logger = logging.getLogger(__name__)

router = APIRouter()

# ---------------------------------------------------------------------------
# Module singletons (lazy init)
# ---------------------------------------------------------------------------

_vector_store: VectorStore | None = None
_rag_engine: RagEngine | None = None
_query_parser: QueryParser | None = None


def _get_store() -> VectorStore:
    global _vector_store
    if _vector_store is None:
        _vector_store = VectorStore()
        _vector_store.initialise()
    return _vector_store


def _get_rag() -> RagEngine:
    global _rag_engine
    if _rag_engine is None:
        _rag_engine = RagEngine(vector_store=_get_store())
    return _rag_engine


def _get_parser() -> QueryParser:
    global _query_parser
    if _query_parser is None:
        _query_parser = QueryParser()
    return _query_parser


# ---------------------------------------------------------------------------
# Request / response models
# ---------------------------------------------------------------------------


class CopilotQueryRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000)
    collections: list[str] | None = None
    top_k: int = Field(default=5, ge=1, le=20)
    customer_id: str | None = None


class CitationDto(BaseModel):
    source_id: str
    text: str
    collection: str
    score: float
    metadata: dict[str, Any] = {}


class CopilotQueryResponse(BaseModel):
    answer: str
    citations: list[CitationDto]
    model: str
    provider: str
    latency_ms: int
    context_tokens: int = 0


class NlQueryRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=1000)


class NlQueryResponse(BaseModel):
    intent: str
    filters: dict[str, Any]
    sort_by: str | None = None
    sort_order: str = "desc"
    limit: int = 20
    confidence: float = 0.0
    raw_query: str = ""


class IndexSpreadRequest(BaseModel):
    spread_id: str
    customer_id: str
    items: list[dict[str, Any]]


class IndexDocumentRequest(BaseModel):
    document_id: str
    customer_id: str
    chunks: list[dict[str, Any]]


class IndexResponse(BaseModel):
    indexed_count: int
    collection: str


class CopilotStatusResponse(BaseModel):
    status: str
    provider: str
    collections: dict[str, int]


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.post("/copilot/query", response_model=CopilotQueryResponse)
async def copilot_query(req: CopilotQueryRequest) -> CopilotQueryResponse:
    """Ask the copilot a question using the RAG pipeline."""
    try:
        engine = _get_rag()
        result = engine.query(
            question=req.question,
            collections=req.collections,
            top_k=req.top_k,
            customer_id=req.customer_id,
        )
        return CopilotQueryResponse(
            answer=result.answer,
            citations=[
                CitationDto(
                    source_id=c.source_id,
                    text=c.text,
                    collection=c.collection,
                    score=c.score,
                    metadata=c.metadata,
                )
                for c in result.citations
            ],
            model=result.model,
            provider=result.provider,
            latency_ms=result.latency_ms,
            context_tokens=result.context_tokens,
        )
    except Exception as exc:
        logger.error("Copilot query failed: %s\n%s", exc, traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"Copilot query failed: {exc}") from exc


@router.post("/copilot/query/parse", response_model=NlQueryResponse)
async def copilot_parse_query(req: NlQueryRequest) -> NlQueryResponse:
    """Parse a natural-language query into structured dashboard filters."""
    try:
        parser = _get_parser()
        parsed = parser.parse(req.query)
        return NlQueryResponse(
            intent=parsed.intent,
            filters=parsed.filters,
            sort_by=parsed.sort_by,
            sort_order=parsed.sort_order,
            limit=parsed.limit,
            confidence=parsed.confidence,
            raw_query=parsed.raw_query,
        )
    except Exception as exc:
        logger.error("NL query parse failed: %s\n%s", exc, traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"Query parse failed: {exc}") from exc


@router.post("/copilot/index/spread", response_model=IndexResponse)
async def index_spread(req: IndexSpreadRequest) -> IndexResponse:
    """Index spread values for RAG retrieval."""
    try:
        store = _get_store()
        count = store.index_spread(
            spread_id=req.spread_id,
            customer_id=req.customer_id,
            items=req.items,
        )
        return IndexResponse(indexed_count=count, collection="spreads")
    except Exception as exc:
        logger.error("Spread indexing failed: %s\n%s", exc, traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"Spread indexing failed: {exc}") from exc


@router.post("/copilot/index/document", response_model=IndexResponse)
async def index_document(req: IndexDocumentRequest) -> IndexResponse:
    """Index document OCR chunks for RAG retrieval."""
    try:
        store = _get_store()
        count = store.index_document(
            document_id=req.document_id,
            customer_id=req.customer_id,
            chunks=req.chunks,
        )
        return IndexResponse(indexed_count=count, collection="documents")
    except Exception as exc:
        logger.error("Document indexing failed: %s\n%s", exc, traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"Document indexing failed: {exc}") from exc


@router.get("/copilot/status", response_model=CopilotStatusResponse)
async def copilot_status() -> CopilotStatusResponse:
    """Return copilot health and vector-store statistics."""
    try:
        store = _get_store()
        engine = _get_rag()
        return CopilotStatusResponse(
            status="ready",
            provider=engine._provider.value,
            collections=store.stats(),
        )
    except Exception as exc:
        logger.warning("Copilot status check failed: %s", exc)
        return CopilotStatusResponse(
            status="degraded",
            provider="unknown",
            collections={},
        )
