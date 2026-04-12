"""Pydantic models for OW-PGGR anomaly detection."""

from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum

from pydantic import BaseModel, Field


class AnomalyType(str, Enum):
    STATISTICAL_OUTLIER = "STATISTICAL_OUTLIER"
    BALANCE_VIOLATION = "BALANCE_VIOLATION"
    TREND_BREAK = "TREND_BREAK"
    RATIO_ANOMALY = "RATIO_ANOMALY"
    MATERIALITY_FLAG = "MATERIALITY_FLAG"


class Anomaly(BaseModel):
    line_item_id: str
    line_item_label: str
    anomaly_type: AnomalyType
    severity: float = Field(..., ge=0.0, le=1.0)
    description: str
    value: float
    expected_range: tuple[float, float] | None = None
    materiality_score: float = Field(0.0, ge=0.0, le=1.0)


class AnomalyReport(BaseModel):
    anomalies: list[Anomaly]
    overall_risk_score: float = Field(..., ge=0.0, le=1.0)
    summary: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    total_items_checked: int
    flagged_count: int
