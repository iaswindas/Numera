"""POST /api/ml/mapping/suggest — Mapping with A/B testing and tenant support."""

import logging
import time

from fastapi import APIRouter, Header, Request
from typing import Optional

from app.api.models import (
    MappingSuggestionRequest,
    MappingSuggestionResponse,
    MappingSummary,
)
from app.ml.semantic_matcher import SemanticMatcher

router = APIRouter()
logger = logging.getLogger("ml-service.api.mapping")


@router.post("/suggest", response_model=MappingSuggestionResponse)
async def suggest_mapping(
    request: MappingSuggestionRequest,
    http_request: Request,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID"),
):
    """Suggest line-item mappings using Sentence-BERT semantic matching.

    Supports:
    - A/B testing: Routes a portion of requests to Staging model
    - Per-client models: Resolves tenant-specific model if available
    - Taxonomy expansion: Loads IFRS synonyms if taxonomy_path provided
    """
    start_time = time.time()

    tenant_id = x_tenant_id or request.tenant_id

    # --- Resolve matcher: client-specific or global ---
    matcher: SemanticMatcher = http_request.app.state.semantic_matcher
    client_resolver = getattr(http_request.app.state, "client_model_resolver", None)

    if tenant_id and client_resolver:
        client_matcher = await client_resolver.resolve(tenant_id, http_request.app.state)
        if client_matcher is not None:
            matcher = client_matcher
            logger.info("Using client-specific model for tenant=%s", tenant_id)

    if request.taxonomy_path:
        matcher.load_taxonomy(request.taxonomy_path)

    mappings, model_version = matcher.match(request.source_rows, request.target_items)

    # Generate summary stats
    high = sum(
        1 for m in mappings
        if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "HIGH"
    )
    medium = sum(
        1 for m in mappings
        if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "MEDIUM"
    )
    low = sum(
        1 for m in mappings
        if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "LOW"
    )
    unmapped = sum(1 for m in mappings if not m.suggested_mappings)

    summary = MappingSummary(
        total_source_rows=len(mappings),
        high_confidence=high,
        medium_confidence=medium,
        low_confidence=low,
        unmapped=unmapped,
    )

    processing_time_ms = int((time.time() - start_time) * 1000)
    logger.info(
        "Mapping: doc=%s tenant=%s model=%s rows=%d high=%d med=%d low=%d time=%dms",
        request.document_id, tenant_id, model_version,
        len(mappings), high, medium, low, processing_time_ms,
    )

    return MappingSuggestionResponse(
        document_id=request.document_id,
        mappings=mappings,
        summary=summary,
        processing_time_ms=processing_time_ms,
        model_version=model_version,
    )
