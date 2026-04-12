"""Pydantic models for the RS-BSN covenant predictor."""

from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class RegimeState(str, Enum):
    """Market regime detected by the HMM."""
    NORMAL = "NORMAL"
    STRESSED = "STRESSED"
    CRISIS = "CRISIS"


class RegimeDetection(BaseModel):
    """Result of regime classification on a value trajectory."""
    regime: RegimeState
    probability: float = Field(..., ge=0.0, le=1.0)
    transition_matrix: list[list[float]] = Field(
        ...,
        description="3×3 row-stochastic transition matrix [NORMAL, STRESSED, CRISIS].",
    )


class BayesianForecast(BaseModel):
    """Single-step Bayesian forecast under a particular regime."""
    period: int = Field(..., description="Forecast step (1-based).")
    mean: float
    std: float = Field(..., ge=0.0)
    ci_lower: float
    ci_upper: float
    regime: RegimeState


class RSBSNPrediction(BaseModel):
    """Full prediction envelope returned by the RS-BSN predictor."""
    breach_probability: float = Field(..., ge=0.0, le=1.0)
    confidence_interval: dict[str, float] = Field(
        ...,
        description="Keys: lower, upper — 90 % credible interval on breach probability.",
    )
    forecasts: list[BayesianForecast]
    regime_history: list[RegimeState]
    factors: list[dict[str, Any]]
