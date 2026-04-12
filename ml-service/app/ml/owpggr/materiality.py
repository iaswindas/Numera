"""Materiality scoring for OW-PGGR anomaly detection."""

from __future__ import annotations


class MaterialityCalculator:
    """Computes materiality weight for a line-item value.

    Uses standard audit materiality thresholds relative to total assets
    and revenue, with configurable boundaries.
    """

    def __init__(
        self,
        low_threshold: float = 0.005,
        high_threshold: float = 0.02,
    ) -> None:
        self.low_threshold = low_threshold
        self.high_threshold = high_threshold

    def compute_materiality(
        self,
        value: float,
        total_assets: float,
        revenue: float,
    ) -> float:
        """Return a materiality score in [0, 1].

        The score is the maximum of the asset-relative and revenue-relative
        materiality ratios, mapped to a 0-1 scale:
          * < low_threshold  → linear 0.0–0.3
          * low–high         → linear 0.3–0.7
          * > high_threshold → linear 0.7–1.0 (capped at 1.0)
        """
        abs_value = abs(value)
        ratios: list[float] = []
        if total_assets > 0:
            ratios.append(abs_value / total_assets)
        if revenue > 0:
            ratios.append(abs_value / revenue)
        if not ratios:
            return 0.0
        ratio = max(ratios)
        return self._map_ratio(ratio)

    def _map_ratio(self, ratio: float) -> float:
        if ratio < self.low_threshold:
            return 0.3 * (ratio / self.low_threshold) if self.low_threshold > 0 else 0.0
        if ratio < self.high_threshold:
            span = self.high_threshold - self.low_threshold
            return 0.3 + 0.4 * ((ratio - self.low_threshold) / span) if span > 0 else 0.5
        # Above high threshold — scale 0.7 → 1.0 over the next high_threshold
        overshoot = ratio - self.high_threshold
        extra = min(overshoot / self.high_threshold, 1.0) if self.high_threshold > 0 else 1.0
        return 0.7 + 0.3 * extra
