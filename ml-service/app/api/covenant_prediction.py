"""POST /api/ml/covenant/predict — covenant breach probability prediction."""

from __future__ import annotations

from enum import Enum

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field, field_validator

from app.ml.covenant_predictor import CovenantPredictor

router = APIRouter()


class PredictionDirection(str, Enum):
    MIN = "MIN"
    MAX = "MAX"


class CovenantHistoryPoint(BaseModel):
    period: str = Field(..., min_length=1)
    value: float


class CovenantPredictionRequest(BaseModel):
    covenantId: str = Field(..., min_length=1)
    threshold: float
    direction: PredictionDirection = PredictionDirection.MIN
    history: list[CovenantHistoryPoint]
    periodsAhead: int = Field(default=4, ge=1, le=12)

    @field_validator("history")
    @classmethod
    def validate_history_size(cls, history: list[CovenantHistoryPoint]) -> list[CovenantHistoryPoint]:
        if len(history) < 3:
            raise ValueError("history must contain at least 3 data points")
        return history


class ConfidenceInterval(BaseModel):
    lower: float
    upper: float


class ForecastPoint(BaseModel):
    period: str
    expected_value: float
    breach_risk: float


class FactorImpact(BaseModel):
    name: str
    impact: float


class CovenantPredictionResponse(BaseModel):
    breach_probability: float
    confidence_interval: ConfidenceInterval
    forecast: list[ForecastPoint]
    factors: list[FactorImpact]


@router.post("/covenant/predict", response_model=CovenantPredictionResponse)
async def predict_covenant_breach(request: CovenantPredictionRequest) -> CovenantPredictionResponse:
    predictor = CovenantPredictor(
        threshold=request.threshold,
        direction=request.direction.value,
    )
    try:
        result = predictor.predict_breach_probability(
            history=[point.model_dump() for point in request.history],
            periods_ahead=request.periodsAhead,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail={"error_code": "INVALID_HISTORY", "message": str(exc)}) from exc

    return CovenantPredictionResponse.model_validate(result)
