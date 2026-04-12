"""POST /api/ml/covenants/predict — RS-BSN covenant breach prediction.

Provides advanced regime-switching Bayesian state-space prediction with
graceful fallback to the baseline :class:`CovenantPredictor` when RS-BSN
fails or is disabled.
"""

from __future__ import annotations

import logging
import time
from enum import Enum
from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field, field_validator

from app.ml.covenant_predictor import CovenantPredictor
from app.ml.rsbsn import RSBSNPredictor, RegimeState

logger = logging.getLogger(__name__)

router = APIRouter()


# ------------------------------------------------------------------
# Request / Response models
# ------------------------------------------------------------------

class RSBSNDirection(str, Enum):
    ABOVE = "ABOVE"
    BELOW = "BELOW"


class RSBSNHistoryPoint(BaseModel):
    period: str = Field(..., min_length=1)
    value: float


class RSBSNPredictionRequest(BaseModel):
    covenantId: str = Field(..., min_length=1)
    threshold: float
    direction: RSBSNDirection = RSBSNDirection.BELOW
    history: list[RSBSNHistoryPoint]
    periodsAhead: int = Field(default=4, ge=1, le=12)

    @field_validator("history")
    @classmethod
    def validate_history_min(cls, v: list[RSBSNHistoryPoint]) -> list[RSBSNHistoryPoint]:
        if len(v) < 1:
            raise ValueError("history must contain at least 1 data point")
        return v


class RSBSNBatchRequest(BaseModel):
    requests: list[RSBSNPredictionRequest]

    @field_validator("requests")
    @classmethod
    def validate_batch_size(cls, v: list[RSBSNPredictionRequest]) -> list[RSBSNPredictionRequest]:
        if len(v) > 50:
            raise ValueError("batch size must not exceed 50")
        return v


class RSBSNForecastPoint(BaseModel):
    period: int
    mean: float
    std: float
    ci_lower: float
    ci_upper: float
    regime: str


class RSBSNRegimeDetection(BaseModel):
    current_regime: str
    regime_history: list[str]


class RSBSNConfidenceInterval(BaseModel):
    lower: float
    upper: float


class RSBSNFactorImpact(BaseModel):
    name: str
    impact: float


class RSBSNPredictionResponse(BaseModel):
    covenantId: str
    breach_probability: float
    confidence_interval: RSBSNConfidenceInterval
    forecasts: list[RSBSNForecastPoint]
    regime_detection: RSBSNRegimeDetection
    factors: list[RSBSNFactorImpact]
    model: str = "RS-BSN"
    processing_time_ms: int = 0


class RSBSNBatchResponse(BaseModel):
    results: list[RSBSNPredictionResponse]
    total_processing_time_ms: int = 0


# ------------------------------------------------------------------
# Endpoints
# ------------------------------------------------------------------

@router.post("/covenants/predict", response_model=RSBSNPredictionResponse)
async def predict_rsbsn(request: RSBSNPredictionRequest) -> RSBSNPredictionResponse:
    """RS-BSN covenant breach prediction with automatic fallback."""
    start = time.monotonic()
    try:
        response = _run_rsbsn(request)
    except Exception as exc:
        logger.error("RS-BSN prediction failed for %s: %s", request.covenantId, exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={"error_code": "PREDICTION_FAILED", "message": str(exc)},
        ) from exc
    response.processing_time_ms = int((time.monotonic() - start) * 1000)
    return response


@router.post("/covenants/predict/batch", response_model=RSBSNBatchResponse)
async def predict_rsbsn_batch(request: RSBSNBatchRequest) -> RSBSNBatchResponse:
    """Batch RS-BSN predictions."""
    start = time.monotonic()
    results: list[RSBSNPredictionResponse] = []
    for item in request.requests:
        try:
            results.append(_run_rsbsn(item))
        except Exception:
            logger.warning("Batch item %s failed; using fallback.", item.covenantId, exc_info=True)
            results.append(_run_fallback(item))
    elapsed = int((time.monotonic() - start) * 1000)
    return RSBSNBatchResponse(results=results, total_processing_time_ms=elapsed)


# ------------------------------------------------------------------
# Internal helpers
# ------------------------------------------------------------------

def _run_rsbsn(request: RSBSNPredictionRequest) -> RSBSNPredictionResponse:
    """Execute RS-BSN prediction, falling back to baseline on error."""
    values = [pt.value for pt in request.history]

    try:
        predictor = RSBSNPredictor()
        result = predictor.predict(
            history=values,
            threshold=request.threshold,
            direction=request.direction.value,
            periods_ahead=request.periodsAhead,
        )
    except Exception:
        logger.warning("RS-BSN engine error for %s; using baseline fallback.", request.covenantId, exc_info=True)
        return _run_fallback(request)

    return RSBSNPredictionResponse(
        covenantId=request.covenantId,
        breach_probability=result.breach_probability,
        confidence_interval=RSBSNConfidenceInterval(
            lower=result.confidence_interval["lower"],
            upper=result.confidence_interval["upper"],
        ),
        forecasts=[
            RSBSNForecastPoint(
                period=f.period,
                mean=f.mean,
                std=f.std,
                ci_lower=f.ci_lower,
                ci_upper=f.ci_upper,
                regime=f.regime.value,
            )
            for f in result.forecasts
        ],
        regime_detection=RSBSNRegimeDetection(
            current_regime=result.regime_history[-1].value if result.regime_history else "NORMAL",
            regime_history=[r.value for r in result.regime_history],
        ),
        factors=[RSBSNFactorImpact(name=f["name"], impact=f["impact"]) for f in result.factors],
        model="RS-BSN",
    )


def _run_fallback(request: RSBSNPredictionRequest) -> RSBSNPredictionResponse:
    """Use the baseline CovenantPredictor as a graceful fallback."""
    direction_map = {"ABOVE": "MAX", "BELOW": "MIN"}
    fallback_dir = direction_map.get(request.direction.value, "MIN")

    predictor = CovenantPredictor(
        threshold=request.threshold,
        direction=fallback_dir,
    )
    history_dicts = [{"period": pt.period, "value": pt.value} for pt in request.history]

    try:
        result = predictor.predict_breach_probability(history_dicts, periods_ahead=request.periodsAhead)
    except ValueError:
        # Absolute last resort — return a neutral prediction.
        return _neutral_response(request.covenantId)

    return RSBSNPredictionResponse(
        covenantId=request.covenantId,
        breach_probability=result["breach_probability"],
        confidence_interval=RSBSNConfidenceInterval(
            lower=result["confidence_interval"]["lower"],
            upper=result["confidence_interval"]["upper"],
        ),
        forecasts=[
            RSBSNForecastPoint(
                period=i + 1,
                mean=fp["expected_value"],
                std=0.0,
                ci_lower=fp["expected_value"],
                ci_upper=fp["expected_value"],
                regime="NORMAL",
            )
            for i, fp in enumerate(result["forecast"])
        ],
        regime_detection=RSBSNRegimeDetection(
            current_regime="NORMAL",
            regime_history=["NORMAL"] * len(request.history),
        ),
        factors=[RSBSNFactorImpact(name=f["name"], impact=f["impact"]) for f in result["factors"]],
        model="BASELINE_FALLBACK",
    )


def _neutral_response(covenant_id: str) -> RSBSNPredictionResponse:
    """Absolute last-resort neutral response."""
    return RSBSNPredictionResponse(
        covenantId=covenant_id,
        breach_probability=0.5,
        confidence_interval=RSBSNConfidenceInterval(lower=0.0, upper=1.0),
        forecasts=[],
        regime_detection=RSBSNRegimeDetection(current_regime="NORMAL", regime_history=[]),
        factors=[RSBSNFactorImpact(name="no_data", impact=1.0)],
        model="NEUTRAL_FALLBACK",
    )
