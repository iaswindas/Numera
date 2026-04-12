"""Covenant breach prediction using a robust baseline forecaster.

This module avoids heavyweight dependencies and provides deterministic,
production-friendly behavior when advanced models are unavailable.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
import math
import re
from typing import Any


@dataclass(slots=True)
class PredictorContext:
    threshold: float
    direction: str  # MIN: breach when value < threshold, MAX: breach when value > threshold


class CovenantPredictor:
    """Predict covenant breach risk from historical observations.

    The model combines:
    - weighted linear trend regression (recent samples weighted more),
    - quarter-based seasonality adjustment when period labels are available,
    - residual-based uncertainty estimation.
    """

    def __init__(self, threshold: float, direction: str = "MIN") -> None:
        self.ctx = PredictorContext(
            threshold=float(threshold),
            direction=direction.upper().strip() if direction else "MIN",
        )

    def predict_breach_probability(self, history: list[dict], periods_ahead: int = 4) -> dict:
        """Predict breach probability and forward values.

        Args:
            history: List of dicts with at least a numeric value and optional period.
            periods_ahead: Number of future periods to forecast.

        Returns:
            A prediction payload with probability, confidence interval, and factors.
        """
        cleaned = self._normalize_history(history)
        if len(cleaned) < 3:
            raise ValueError("At least 3 history points are required")

        periods_ahead = max(1, min(int(periods_ahead), 12))

        values = [point["value"] for point in cleaned]
        periods = [point["period"] for point in cleaned]

        slope, intercept = self._weighted_linear_regression(values)
        residuals = [values[i] - (slope * i + intercept) for i in range(len(values))]

        seasonality = self._seasonality_by_quarter(periods, residuals)
        residual_std = self._stddev(residuals)
        base_sigma = max(1e-6, residual_std)

        forecasts: list[dict[str, Any]] = []
        risks: list[float] = []
        last_period = periods[-1] if periods else None

        for step in range(1, periods_ahead + 1):
            idx = len(values) - 1 + step
            trend_value = slope * idx + intercept
            period_label = self._next_period_label(last_period, step)
            seasonal_delta = self._seasonal_delta_for_period(period_label, seasonality)
            expected_value = trend_value + seasonal_delta

            horizon_sigma = base_sigma * math.sqrt(step)
            breach_risk = self._breach_probability(expected_value, horizon_sigma)
            risks.append(breach_risk)

            forecasts.append(
                {
                    "period": period_label,
                    "expected_value": round(expected_value, 4),
                    "breach_risk": round(breach_risk, 4),
                }
            )

        aggregate = self._aggregate_risk(risks)
        lower = max(0.0, aggregate - 1.28 * (base_sigma / max(abs(self.ctx.threshold), 1.0)) * 0.1)
        upper = min(1.0, aggregate + 1.28 * (base_sigma / max(abs(self.ctx.threshold), 1.0)) * 0.1)

        return {
            "breach_probability": round(aggregate, 4),
            "confidence_interval": {
                "lower": round(min(lower, aggregate), 4),
                "upper": round(max(upper, aggregate), 4),
            },
            "forecast": forecasts,
            "factors": self._build_factors(slope, base_sigma, values[-1], seasonality),
        }

    def _normalize_history(self, history: list[dict]) -> list[dict[str, Any]]:
        normalized: list[dict[str, Any]] = []
        for idx, item in enumerate(history):
            if not isinstance(item, dict):
                continue
            value = self._extract_numeric(item)
            if value is None:
                continue
            period = self._extract_period(item, idx)
            normalized.append({"period": period, "value": value})
        return normalized

    @staticmethod
    def _extract_numeric(item: dict[str, Any]) -> float | None:
        candidates = (
            "value",
            "calculated_value",
            "manual_value",
            "metric_value",
            "amount",
        )
        for key in candidates:
            raw = item.get(key)
            if raw is None:
                continue
            try:
                return float(raw)
            except (TypeError, ValueError):
                continue
        return None

    @staticmethod
    def _extract_period(item: dict[str, Any], index: int) -> str:
        for key in ("period", "period_end", "date", "as_of"):
            raw = item.get(key)
            if isinstance(raw, str) and raw.strip():
                return raw.strip()
        return f"P{index + 1}"

    @staticmethod
    def _weighted_linear_regression(values: list[float]) -> tuple[float, float]:
        n = len(values)
        xs = list(range(n))
        # Recent points carry stronger signal for covenant drift.
        ws = [1.0 + (i / max(n - 1, 1)) for i in xs]

        sum_w = sum(ws)
        mean_x = sum(w * x for w, x in zip(ws, xs)) / sum_w
        mean_y = sum(w * y for w, y in zip(ws, values)) / sum_w

        num = sum(w * (x - mean_x) * (y - mean_y) for w, x, y in zip(ws, xs, values))
        den = sum(w * (x - mean_x) ** 2 for w, x in zip(ws, xs))

        slope = num / den if den > 1e-12 else 0.0
        intercept = mean_y - slope * mean_x
        return slope, intercept

    @staticmethod
    def _stddev(values: list[float]) -> float:
        if len(values) < 2:
            return 0.01
        mean = sum(values) / len(values)
        variance = sum((v - mean) ** 2 for v in values) / (len(values) - 1)
        return math.sqrt(max(variance, 1e-8))

    @staticmethod
    def _quarter_key(period: str) -> str | None:
        match = re.search(r"(\d{4})[-_/ ]?Q([1-4])", period, re.IGNORECASE)
        if match:
            return f"Q{match.group(2)}"
        try:
            d = date.fromisoformat(period[:10])
            quarter = ((d.month - 1) // 3) + 1
            return f"Q{quarter}"
        except ValueError:
            return None

    def _seasonality_by_quarter(self, periods: list[str], residuals: list[float]) -> dict[str, float]:
        bucket: dict[str, list[float]] = {"Q1": [], "Q2": [], "Q3": [], "Q4": []}
        for p, r in zip(periods, residuals):
            q = self._quarter_key(p)
            if q in bucket:
                bucket[q].append(r)

        seasonal = {}
        for q, vals in bucket.items():
            seasonal[q] = sum(vals) / len(vals) if vals else 0.0
        return seasonal

    def _seasonal_delta_for_period(self, period: str, seasonality: dict[str, float]) -> float:
        q = self._quarter_key(period)
        if q is None:
            return 0.0
        return seasonality.get(q, 0.0)

    @staticmethod
    def _next_period_label(last_period: str | None, step: int) -> str:
        if not last_period:
            return f"P+{step}"

        q_match = re.search(r"(\d{4})[-_/ ]?Q([1-4])", last_period, re.IGNORECASE)
        if q_match:
            year = int(q_match.group(1))
            quarter = int(q_match.group(2))
            quarter += step
            year += (quarter - 1) // 4
            quarter = ((quarter - 1) % 4) + 1
            return f"{year}-Q{quarter}"

        try:
            d = date.fromisoformat(last_period[:10])
            months = d.month - 1 + (step * 3)
            y = d.year + months // 12
            m = (months % 12) + 1
            q = ((m - 1) // 3) + 1
            return f"{y}-Q{q}"
        except ValueError:
            return f"P+{step}"

    def _breach_probability(self, expected_value: float, sigma: float) -> float:
        sigma = max(sigma, 1e-4)
        z = (self.ctx.threshold - expected_value) / sigma
        # Logistic approximation of CDF to avoid extra dependencies.
        cdf = 1.0 / (1.0 + math.exp(-1.702 * z))

        if self.ctx.direction == "MAX":
            return float(min(max(1.0 - cdf, 0.0), 1.0))
        return float(min(max(cdf, 0.0), 1.0))

    @staticmethod
    def _aggregate_risk(risks: list[float]) -> float:
        # Probability of any breach across forecast horizon.
        survival = 1.0
        for r in risks:
            survival *= (1.0 - min(max(r, 0.0), 1.0))
        return min(max(1.0 - survival, 0.0), 1.0)

    def _build_factors(
        self,
        slope: float,
        sigma: float,
        latest_value: float,
        seasonality: dict[str, float],
    ) -> list[dict[str, Any]]:
        threshold = max(abs(self.ctx.threshold), 1e-6)
        trend_ratio = min(abs(slope) / threshold, 1.0)
        vol_ratio = min(sigma / threshold, 1.0)
        proximity = 1.0 - min(abs(latest_value - self.ctx.threshold) / threshold, 1.0)
        seasonality_pressure = min(max(abs(sum(seasonality.values())) / max(threshold, 1.0), 0.0), 1.0)

        direction_factor = "downward_trend" if self.ctx.direction == "MIN" and slope < 0 else (
            "upward_trend" if self.ctx.direction == "MAX" and slope > 0 else "trend_stable"
        )

        return [
            {"name": direction_factor, "impact": round(trend_ratio, 4)},
            {"name": "volatility", "impact": round(vol_ratio, 4)},
            {"name": "threshold_proximity", "impact": round(max(proximity, 0.0), 4)},
            {"name": "seasonality", "impact": round(seasonality_pressure, 4)},
        ]
