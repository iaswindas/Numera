"""Feedback collection endpoints — PostgreSQL-backed with in-memory fallback.

Collects analyst corrections to ML mapping suggestions for future model
retraining. Provides batch export API for Colab notebook consumption.
"""

import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query, Request

from app.api.models import (
    FeedbackExportResponse,
    FeedbackItem,
    FeedbackRequest,
    FeedbackResponse,
    FeedbackStatsResponse,
)
from app.services.feedback_store import FeedbackStore

router = APIRouter()
logger = logging.getLogger("ml-service.api.feedback")


async def _get_feedback_store(http_request: Request) -> FeedbackStore:
    store = getattr(http_request.app.state, "feedback_store", None)
    if store is None:
        store = FeedbackStore()
        await store.init(None)
        http_request.app.state.feedback_store = store
    return store


@router.post("/feedback", response_model=FeedbackResponse)
async def submit_feedback(
    request: FeedbackRequest,
    http_request: Request,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID"),
):
    """Accept analyst corrections to ML mapping suggestions.

    Corrections are stored in PostgreSQL (or in-memory if PG unavailable)
    and used for periodic model retraining via Colab notebooks.
    """
    # --- Tenant isolation: reject mismatched tenant IDs ---
    for item in request.corrections:
        if x_tenant_id and item.tenant_id and item.tenant_id != x_tenant_id:
            raise HTTPException(
                status_code=403,
                detail="Tenant ID mismatch: correction tenant_id does not match X-Tenant-ID header",
            )

    store = await _get_feedback_store(http_request)

    corrections = [
        {
            "document_id": item.document_id,
            "tenant_id": item.tenant_id,
            "analyst_id": item.analyst_id,
            "source_text": item.source_text,
            "source_zone_type": item.source_zone_type,
            "suggested_item_id": item.suggested_item_id,
            "suggested_item_label": item.suggested_item_label,
            "suggested_confidence": item.suggested_confidence,
            "corrected_item_id": item.corrected_item_id,
            "corrected_item_label": item.corrected_item_label,
            "correction_type": item.correction_type.value,
            "model_version": item.model_version,
        }
        for item in request.corrections
    ]

    accepted = await store.save_corrections(corrections)
    stats = await store.get_stats()

    logger.info(
        "Feedback: accepted=%d total=%d storage=%s",
        accepted, stats["total_corrections"], stats["storage"],
    )

    return FeedbackResponse(
        accepted=accepted,
        total_stored=stats["total_corrections"],
        message=f"Accepted {accepted} corrections.",
        storage=stats["storage"],
    )


@router.get("/feedback/export", response_model=FeedbackExportResponse)
async def export_feedback(
    http_request: Request,
    since: Optional[str] = Query(None, description="ISO datetime — export records after this"),
    tenant_id: Optional[str] = Query(None, description="Filter to specific tenant"),
    limit: int = Query(10000, ge=1, le=100000),
):
    """Export feedback records for Colab retraining notebooks.

    This endpoint is called by `20_feedback_retraining.ipynb` to download
    accumulated corrections for model fine-tuning.
    """
    store = await _get_feedback_store(http_request)

    since_dt = None
    if since:
        try:
            since_dt = datetime.fromisoformat(since)
        except ValueError:
            since_dt = None

    records = await store.export_since(since=since_dt, tenant_id=tenant_id, limit=limit)

    # Serialise datetime objects for JSON
    serialised = []
    for r in records:
        row = {}
        for k, v in r.items():
            if isinstance(v, datetime):
                row[k] = v.isoformat()
            else:
                row[k] = v
            # Exclude internal 'id' column
            if k == "id":
                continue
        serialised.append(row)

    return FeedbackExportResponse(
        records=serialised,
        total=len(serialised),
        since=since,
        tenant_id=tenant_id,
    )


@router.get("/feedback/stats", response_model=FeedbackStatsResponse)
async def feedback_stats(
    http_request: Request,
    tenant_id: Optional[str] = Query(None),
):
    """Return feedback statistics."""
    store = await _get_feedback_store(http_request)
    stats = await store.get_stats(tenant_id=tenant_id)
    return FeedbackStatsResponse(**stats)
