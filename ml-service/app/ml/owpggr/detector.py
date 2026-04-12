"""OW-PGGR Anomaly Detector.

Performs statistical outlier detection, graph-based consistency checking,
cross-period trend analysis, and materiality-weighted scoring on financial
spread data.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

import numpy as np

from app.ml.owpggr.materiality import MaterialityCalculator
from app.ml.owpggr.models import Anomaly, AnomalyReport, AnomalyType

logger = logging.getLogger(__name__)

# Type aliases for clarity
SpreadValue = dict[str, Any]  # {line_item_id, label, value, zone_type}


class AnomalyDetector:
    """Outlier-Weighted Probabilistic Graph-Guided Reasoner."""

    # Configurable thresholds
    Z_SCORE_THRESHOLD: float = 2.5
    IQR_MULTIPLIER: float = 1.5
    MODIFIED_Z_THRESHOLD: float = 3.5
    BALANCE_TOLERANCE: float = 0.005  # 0.5 %
    TREND_STD_MULTIPLIER: float = 2.0

    # Standard ratio norms: (lower, upper)
    RATIO_NORMS: dict[str, tuple[float, float]] = {
        "current_ratio": (1.0, 3.0),
        "debt_to_equity": (0.0, 3.0),
    }

    def __init__(self, materiality: MaterialityCalculator | None = None) -> None:
        self.materiality = materiality or MaterialityCalculator()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def detect(
        self,
        spread_values: list[SpreadValue],
        historical_values: list[list[SpreadValue]] | None = None,
        template_validations: list[dict[str, Any]] | None = None,
    ) -> AnomalyReport:
        """Run all anomaly checks and return a consolidated report."""
        anomalies: list[Anomaly] = []
        historical_values = historical_values or []
        template_validations = template_validations or []

        total_assets = self._find_value(spread_values, "total_assets")
        revenue = self._find_value(spread_values, "revenue") or self._find_value(spread_values, "total_revenue")

        # 1. Statistical outlier detection (requires history)
        if historical_values:
            anomalies.extend(
                self._statistical_checks(spread_values, historical_values, total_assets, revenue)
            )

        # 2. Graph-based consistency (balance sheet equation)
        anomalies.extend(self._balance_checks(spread_values, total_assets, revenue))

        # 3. Ratio anomaly checks
        anomalies.extend(self._ratio_checks(spread_values, total_assets, revenue))

        # 4. Trend analysis
        if historical_values:
            anomalies.extend(
                self._trend_checks(spread_values, historical_values, total_assets, revenue)
            )

        # 5. Materiality flags for large-value items without other flags
        anomalies.extend(self._materiality_flags(spread_values, anomalies, total_assets, revenue))

        # Deduplicate by (line_item_id, anomaly_type)
        seen: set[tuple[str, str]] = set()
        unique: list[Anomaly] = []
        for a in anomalies:
            key = (a.line_item_id, a.anomaly_type.value)
            if key not in seen:
                seen.add(key)
                unique.append(a)
        anomalies = unique

        overall = self._overall_risk(anomalies)
        summary = self._build_summary(anomalies, len(spread_values))

        return AnomalyReport(
            anomalies=anomalies,
            overall_risk_score=overall,
            summary=summary,
            total_items_checked=len(spread_values),
            flagged_count=len(anomalies),
        )

    # ------------------------------------------------------------------
    # Statistical checks
    # ------------------------------------------------------------------

    def _statistical_checks(
        self,
        current: list[SpreadValue],
        history: list[list[SpreadValue]],
        total_assets: float,
        revenue: float,
    ) -> list[Anomaly]:
        anomalies: list[Anomaly] = []
        hist_map = self._build_history_map(history)

        for item in current:
            item_id = item.get("line_item_id", "")
            label = item.get("label", item_id)
            value = self._safe_float(item.get("value"))
            if value is None:
                continue

            hist_values = hist_map.get(item_id)
            if not hist_values or len(hist_values) < 2:
                continue

            arr = np.array(hist_values, dtype=np.float64)
            mean, std = float(np.mean(arr)), float(np.std(arr, ddof=1))

            severity: float | None = None
            desc_parts: list[str] = []

            # Z-score
            if std > 0:
                z = abs(value - mean) / std
                if z > self.Z_SCORE_THRESHOLD:
                    severity = min(z / (self.Z_SCORE_THRESHOLD * 2), 1.0)
                    desc_parts.append(f"Z-score {z:.2f} exceeds threshold {self.Z_SCORE_THRESHOLD}")

            # IQR
            q1, q3 = float(np.percentile(arr, 25)), float(np.percentile(arr, 75))
            iqr = q3 - q1
            lower_bound = q1 - self.IQR_MULTIPLIER * iqr
            upper_bound = q3 + self.IQR_MULTIPLIER * iqr
            if value < lower_bound or value > upper_bound:
                iqr_sev = min(abs(value - mean) / (iqr + 1e-9) / 4, 1.0)
                severity = max(severity or 0.0, iqr_sev)
                desc_parts.append(f"IQR outlier: value {value:.2f} outside [{lower_bound:.2f}, {upper_bound:.2f}]")

            # Modified Z-score (MAD-based)
            median = float(np.median(arr))
            mad = float(np.median(np.abs(arr - median)))
            if mad > 0:
                mz = 0.6745 * abs(value - median) / mad
                if mz > self.MODIFIED_Z_THRESHOLD:
                    mz_sev = min(mz / (self.MODIFIED_Z_THRESHOLD * 2), 1.0)
                    severity = max(severity or 0.0, mz_sev)
                    desc_parts.append(f"Modified Z-score {mz:.2f} exceeds {self.MODIFIED_Z_THRESHOLD}")

            if severity is not None and desc_parts:
                mat = self.materiality.compute_materiality(value, total_assets, revenue)
                severity = min(severity * (0.5 + 0.5 * mat), 1.0) if mat > 0 else severity
                anomalies.append(Anomaly(
                    line_item_id=item_id,
                    line_item_label=label,
                    anomaly_type=AnomalyType.STATISTICAL_OUTLIER,
                    severity=round(severity, 4),
                    description="; ".join(desc_parts),
                    value=value,
                    expected_range=(round(lower_bound, 2), round(upper_bound, 2)),
                    materiality_score=round(mat, 4),
                ))

        return anomalies

    # ------------------------------------------------------------------
    # Graph-based balance checks
    # ------------------------------------------------------------------

    def _balance_checks(
        self,
        spread: list[SpreadValue],
        total_assets: float,
        revenue: float,
    ) -> list[Anomaly]:
        anomalies: list[Anomaly] = []
        assets = self._find_value(spread, "total_assets")
        liabilities = self._find_value(spread, "total_liabilities")
        equity = self._find_value(spread, "total_equity") or self._find_value(spread, "total_shareholders_equity")

        if assets and liabilities is not None and equity is not None:
            expected = liabilities + equity
            diff = abs(assets - expected)
            denom = max(abs(assets), 1.0)
            pct_diff = diff / denom
            if pct_diff > self.BALANCE_TOLERANCE:
                severity = min(pct_diff / (self.BALANCE_TOLERANCE * 10), 1.0)
                anomalies.append(Anomaly(
                    line_item_id="balance_equation",
                    line_item_label="Assets = Liabilities + Equity",
                    anomaly_type=AnomalyType.BALANCE_VIOLATION,
                    severity=round(severity, 4),
                    description=(
                        f"Balance sheet imbalance: Assets={assets:,.2f}, "
                        f"Liabilities+Equity={expected:,.2f}, diff={diff:,.2f} ({pct_diff:.2%})"
                    ),
                    value=assets,
                    expected_range=(round(expected * (1 - self.BALANCE_TOLERANCE), 2),
                                    round(expected * (1 + self.BALANCE_TOLERANCE), 2)),
                    materiality_score=1.0,
                ))

        return anomalies

    # ------------------------------------------------------------------
    # Ratio anomaly checks
    # ------------------------------------------------------------------

    def _ratio_checks(
        self,
        spread: list[SpreadValue],
        total_assets: float,
        revenue: float,
    ) -> list[Anomaly]:
        anomalies: list[Anomaly] = []

        current_assets = self._find_value(spread, "current_assets")
        current_liabilities = self._find_value(spread, "current_liabilities")
        total_liabilities = self._find_value(spread, "total_liabilities")
        equity = self._find_value(spread, "total_equity") or self._find_value(spread, "total_shareholders_equity")

        # Current ratio
        if current_assets is not None and current_liabilities and current_liabilities != 0:
            ratio = current_assets / current_liabilities
            lo, hi = self.RATIO_NORMS["current_ratio"]
            if ratio < lo or ratio > hi:
                dist = max(lo - ratio, ratio - hi, 0) / hi
                severity = min(dist, 1.0)
                mat = self.materiality.compute_materiality(current_assets, total_assets, revenue)
                anomalies.append(Anomaly(
                    line_item_id="current_ratio",
                    line_item_label="Current Ratio",
                    anomaly_type=AnomalyType.RATIO_ANOMALY,
                    severity=round(severity, 4),
                    description=f"Current ratio {ratio:.2f} outside norm [{lo}, {hi}]",
                    value=round(ratio, 4),
                    expected_range=(lo, hi),
                    materiality_score=round(mat, 4),
                ))

        # Debt-to-equity
        if total_liabilities is not None and equity and equity != 0:
            ratio = total_liabilities / equity
            lo, hi = self.RATIO_NORMS["debt_to_equity"]
            if ratio < lo or ratio > hi:
                dist = max(lo - ratio, ratio - hi, 0) / max(hi, 1)
                severity = min(dist, 1.0)
                mat = self.materiality.compute_materiality(total_liabilities, total_assets, revenue)
                anomalies.append(Anomaly(
                    line_item_id="debt_to_equity",
                    line_item_label="Debt-to-Equity Ratio",
                    anomaly_type=AnomalyType.RATIO_ANOMALY,
                    severity=round(severity, 4),
                    description=f"Debt-to-equity {ratio:.2f} outside norm [{lo}, {hi}]",
                    value=round(ratio, 4),
                    expected_range=(lo, hi),
                    materiality_score=round(mat, 4),
                ))

        return anomalies

    # ------------------------------------------------------------------
    # Trend analysis
    # ------------------------------------------------------------------

    def _trend_checks(
        self,
        current: list[SpreadValue],
        history: list[list[SpreadValue]],
        total_assets: float,
        revenue: float,
    ) -> list[Anomaly]:
        anomalies: list[Anomaly] = []
        hist_map = self._build_history_map(history)

        for item in current:
            item_id = item.get("line_item_id", "")
            label = item.get("label", item_id)
            value = self._safe_float(item.get("value"))
            if value is None:
                continue

            hist_values = hist_map.get(item_id)
            if not hist_values or len(hist_values) < 2:
                continue

            # Compute period-over-period changes in history
            changes = [
                hist_values[i] - hist_values[i - 1]
                for i in range(1, len(hist_values))
            ]
            changes_arr = np.array(changes, dtype=np.float64)
            change_mean = float(np.mean(changes_arr))
            change_std = float(np.std(changes_arr, ddof=1)) if len(changes) > 1 else 0.0

            current_change = value - hist_values[-1]

            if change_std > 0:
                z = abs(current_change - change_mean) / change_std
                if z > self.TREND_STD_MULTIPLIER:
                    severity = min(z / (self.TREND_STD_MULTIPLIER * 2), 1.0)
                    mat = self.materiality.compute_materiality(value, total_assets, revenue)
                    severity = min(severity * (0.5 + 0.5 * mat), 1.0) if mat > 0 else severity
                    expected_lo = round(hist_values[-1] + change_mean - self.TREND_STD_MULTIPLIER * change_std, 2)
                    expected_hi = round(hist_values[-1] + change_mean + self.TREND_STD_MULTIPLIER * change_std, 2)
                    anomalies.append(Anomaly(
                        line_item_id=item_id,
                        line_item_label=label,
                        anomaly_type=AnomalyType.TREND_BREAK,
                        severity=round(severity, 4),
                        description=(
                            f"Sudden change of {current_change:,.2f} vs "
                            f"typical change {change_mean:,.2f} ± {change_std:,.2f}"
                        ),
                        value=value,
                        expected_range=(expected_lo, expected_hi),
                        materiality_score=round(mat, 4),
                    ))

        return anomalies

    # ------------------------------------------------------------------
    # Materiality-only flags
    # ------------------------------------------------------------------

    def _materiality_flags(
        self,
        spread: list[SpreadValue],
        existing: list[Anomaly],
        total_assets: float,
        revenue: float,
    ) -> list[Anomaly]:
        """Flag high-materiality items that don't already have another anomaly."""
        flagged_ids = {a.line_item_id for a in existing}
        anomalies: list[Anomaly] = []

        for item in spread:
            item_id = item.get("line_item_id", "")
            if item_id in flagged_ids:
                continue
            value = self._safe_float(item.get("value"))
            if value is None:
                continue

            mat = self.materiality.compute_materiality(value, total_assets, revenue)
            if mat >= 0.7:
                anomalies.append(Anomaly(
                    line_item_id=item_id,
                    line_item_label=item.get("label", item_id),
                    anomaly_type=AnomalyType.MATERIALITY_FLAG,
                    severity=round(mat * 0.3, 4),  # lower base severity — informational
                    description=f"High materiality item ({mat:.0%} of base) warrants review",
                    value=value,
                    materiality_score=round(mat, 4),
                ))

        return anomalies

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _build_history_map(history: list[list[SpreadValue]]) -> dict[str, list[float]]:
        """Build {line_item_id → [float values across periods]} from history."""
        result: dict[str, list[float]] = {}
        for period in history:
            for item in period:
                item_id = item.get("line_item_id", "")
                val = AnomalyDetector._safe_float(item.get("value"))
                if val is not None:
                    result.setdefault(item_id, []).append(val)
        return result

    @staticmethod
    def _find_value(spread: list[SpreadValue], *keys: str) -> float:
        """Find a value by matching line_item_id against multiple possible keys."""
        for item in spread:
            item_id = str(item.get("line_item_id", "")).lower().replace(" ", "_")
            for key in keys:
                if item_id == key:
                    v = AnomalyDetector._safe_float(item.get("value"))
                    if v is not None:
                        return v
        return 0.0

    @staticmethod
    def _safe_float(v: Any) -> float | None:
        if v is None:
            return None
        try:
            return float(v)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _overall_risk(anomalies: list[Anomaly]) -> float:
        if not anomalies:
            return 0.0
        weighted = sum(a.severity * (0.5 + 0.5 * a.materiality_score) for a in anomalies)
        # Normalise: cap at 1.0, scale by count with diminishing returns
        count_factor = 1 - 1 / (1 + len(anomalies) * 0.3)
        raw = (weighted / len(anomalies)) * 0.6 + count_factor * 0.4
        return round(min(raw, 1.0), 4)

    @staticmethod
    def _build_summary(anomalies: list[Anomaly], total: int) -> str:
        if not anomalies:
            return f"No anomalies detected across {total} line items."
        type_counts: dict[str, int] = {}
        for a in anomalies:
            type_counts[a.anomaly_type.value] = type_counts.get(a.anomaly_type.value, 0) + 1
        parts = [f"{v} {k.lower().replace('_', ' ')}" for k, v in sorted(type_counts.items())]
        return (
            f"{len(anomalies)} anomalies flagged across {total} items: "
            + ", ".join(parts) + "."
        )
