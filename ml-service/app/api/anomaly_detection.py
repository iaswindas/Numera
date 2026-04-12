"""POST /api/ml/anomaly — OW-PGGR anomaly detection endpoints."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.ml.owpggr.detector import AnomalyDetector
from app.ml.owpggr.models import AnomalyReport

logger = logging.getLogger(__name__)
router = APIRouter()

_detector = AnomalyDetector()


# --- Request / Response models ---

class SpreadValueItem(BaseModel):
    line_item_id: str
    label: str
    value: float | str | None = None
    zone_type: str | None = None


class AnomalyDetectionRequest(BaseModel):
    spread_values: list[SpreadValueItem]
    historical_values: list[list[SpreadValueItem]] | None = None
    template_validations: list[dict] | None = None


class AnomalyDetectionResponse(BaseModel):
    report: AnomalyReport
    processing_time_ms: int


class BatchAnomalyDetectionRequest(BaseModel):
    items: list[AnomalyDetectionRequest] = Field(..., min_length=1, max_length=100)


class BatchAnomalyDetectionResponse(BaseModel):
    reports: list[AnomalyDetectionResponse]
    total_processing_time_ms: int


# --- Endpoints ---

@router.post("/anomaly/detect", response_model=AnomalyDetectionResponse)
async def detect_anomalies(request: AnomalyDetectionRequest) -> AnomalyDetectionResponse:
    """Run OW-PGGR anomaly detection on a single spread."""
    start = time.perf_counter_ns()
    try:
        spread_dicts = [sv.model_dump() for sv in request.spread_values]
        hist_dicts = (
            [[sv.model_dump() for sv in period] for period in request.historical_values]
            if request.historical_values
            else None
        )
        report = _detector.detect(
            spread_values=spread_dicts,
            historical_values=hist_dicts,
            template_validations=request.template_validations,
        )
    except Exception:
        logger.exception("Anomaly detection failed")
        raise HTTPException(status_code=500, detail="Anomaly detection failed")
    elapsed_ms = int((time.perf_counter_ns() - start) / 1_000_000)
    return AnomalyDetectionResponse(report=report, processing_time_ms=elapsed_ms)


@router.post("/anomaly/detect/batch", response_model=BatchAnomalyDetectionResponse)
async def detect_anomalies_batch(request: BatchAnomalyDetectionRequest) -> BatchAnomalyDetectionResponse:
    """Run OW-PGGR anomaly detection on multiple spreads."""
    total_start = time.perf_counter_ns()
    reports: list[AnomalyDetectionResponse] = []
    for item in request.items:
        start = time.perf_counter_ns()
        try:
            spread_dicts = [sv.model_dump() for sv in item.spread_values]
            hist_dicts = (
                [[sv.model_dump() for sv in period] for period in item.historical_values]
                if item.historical_values
                else None
            )
            report = _detector.detect(
                spread_values=spread_dicts,
                historical_values=hist_dicts,
                template_validations=item.template_validations,
            )
        except Exception:
            logger.exception("Anomaly detection failed for batch item")
            raise HTTPException(status_code=500, detail="Anomaly detection failed for batch item")
        elapsed_ms = int((time.perf_counter_ns() - start) / 1_000_000)
        reports.append(AnomalyDetectionResponse(report=report, processing_time_ms=elapsed_ms))

    total_ms = int((time.perf_counter_ns() - total_start) / 1_000_000)
    return BatchAnomalyDetectionResponse(reports=reports, total_processing_time_ms=total_ms)
