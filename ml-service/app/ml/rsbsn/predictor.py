"""RS-BSN predictor — main entry point.

Orchestrates regime detection, Bayesian state-space forecasting, and
Monte Carlo breach-probability estimation.
"""

from __future__ import annotations

import logging
import math
from typing import Any, Dict, List

import numpy as np

from .models import BayesianForecast, RegimeState, RSBSNPrediction
from .regime_hmm import RegimeHMM
from .state_space import BayesianStateSpace

logger = logging.getLogger(__name__)

_MC_PATHS = 1000
_CI_LEVEL = 0.90  # 90 % Monte Carlo CI on breach probability


class RSBSNPredictor:
    """Regime-Switching Bayesian State-space Network predictor.

    Parameters
    ----------
    mc_paths : int
        Number of Monte Carlo simulation paths for breach estimation.
    """

    def __init__(self, mc_paths: int = _MC_PATHS) -> None:
        self.mc_paths = mc_paths

    def predict(
        self,
        history: List[float],
        threshold: float,
        direction: str = "BELOW",
        periods_ahead: int = 4,
    ) -> RSBSNPrediction:
        """Produce an RS-BSN covenant-breach prediction.

        Parameters
        ----------
        history : list[float]
            Chronologically ordered covenant metric values.
        threshold : float
            Covenant threshold value.
        direction : str
            ``"BELOW"`` — breach when value < threshold (e.g. min coverage).
            ``"ABOVE"`` — breach when value > threshold (e.g. max leverage).
        periods_ahead : int
            Forecast horizon (capped at 12).

        Returns
        -------
        RSBSNPrediction
        """
        direction = direction.upper().strip()
        periods_ahead = max(1, min(periods_ahead, 12))

        if len(history) < 3:
            return self._fallback_simple(history, threshold, direction, periods_ahead)

        try:
            return self._full_predict(history, threshold, direction, periods_ahead)
        except Exception:
            logger.warning("RS-BSN full prediction failed; using fallback.", exc_info=True)
            return self._fallback_simple(history, threshold, direction, periods_ahead)

    # ------------------------------------------------------------------
    # Full RS-BSN pipeline
    # ------------------------------------------------------------------

    def _full_predict(
        self,
        history: List[float],
        threshold: float,
        direction: str,
        periods_ahead: int,
    ) -> RSBSNPrediction:
        # 1. Regime detection
        hmm = RegimeHMM()
        hmm.fit(history)
        detection = hmm.predict_regime(history)
        regime_history = hmm.decode(history)

        # 2. Fit state-space model
        ss = BayesianStateSpace()
        ss.fit(history)

        # 3. Weighted forecasts across regimes
        transition_row = detection.transition_matrix[
            [RegimeState.NORMAL, RegimeState.STRESSED, RegimeState.CRISIS].index(detection.regime)
        ]
        regimes = [RegimeState.NORMAL, RegimeState.STRESSED, RegimeState.CRISIS]
        weights = np.asarray(transition_row, dtype=np.float64)
        weights /= weights.sum() + 1e-12

        # Blended forecasts
        blended_forecasts = self._blend_forecasts(ss, regimes, weights, periods_ahead)

        # 4. Monte Carlo breach probability
        breach_prob, ci_lower, ci_upper = self._monte_carlo_breach(
            ss, detection, regimes, weights, threshold, direction, periods_ahead,
        )

        # 5. Contributing factors
        factors = self._compute_factors(history, threshold, direction, detection, breach_prob)

        return RSBSNPrediction(
            breach_probability=round(float(np.clip(breach_prob, 0.0, 1.0)), 4),
            confidence_interval={
                "lower": round(float(np.clip(ci_lower, 0.0, 1.0)), 4),
                "upper": round(float(np.clip(ci_upper, 0.0, 1.0)), 4),
            },
            forecasts=blended_forecasts,
            regime_history=regime_history,
            factors=factors,
        )

    # ------------------------------------------------------------------
    # Blended multi-regime forecasting
    # ------------------------------------------------------------------

    def _blend_forecasts(
        self,
        ss: BayesianStateSpace,
        regimes: List[RegimeState],
        weights: np.ndarray,
        periods_ahead: int,
    ) -> List[BayesianForecast]:
        """Produce regime-probability-weighted forecasts."""
        per_regime: Dict[RegimeState, List[BayesianForecast]] = {}
        for regime in regimes:
            per_regime[regime] = ss.forecast(periods_ahead, regime)

        blended: List[BayesianForecast] = []
        dominant_regime = regimes[int(np.argmax(weights))]

        for step_idx in range(periods_ahead):
            mean = sum(
                w * per_regime[r][step_idx].mean
                for r, w in zip(regimes, weights)
            )
            # Variance = weighted mixture variance (law of total variance).
            var_within = sum(
                w * per_regime[r][step_idx].std ** 2
                for r, w in zip(regimes, weights)
            )
            var_between = sum(
                w * (per_regime[r][step_idx].mean - mean) ** 2
                for r, w in zip(regimes, weights)
            )
            total_std = math.sqrt(max(var_within + var_between, 1e-12))

            blended.append(
                BayesianForecast(
                    period=step_idx + 1,
                    mean=round(mean, 6),
                    std=round(total_std, 6),
                    ci_lower=round(mean - 1.645 * total_std, 6),
                    ci_upper=round(mean + 1.645 * total_std, 6),
                    regime=dominant_regime,
                )
            )
        return blended

    # ------------------------------------------------------------------
    # Monte Carlo breach estimation
    # ------------------------------------------------------------------

    def _monte_carlo_breach(
        self,
        ss: BayesianStateSpace,
        detection,
        regimes: List[RegimeState],
        weights: np.ndarray,
        threshold: float,
        direction: str,
        periods_ahead: int,
    ) -> tuple[float, float, float]:
        """Simulate paths under each regime, count breaches.

        Returns (breach_probability, ci_lower, ci_upper).
        """
        rng = np.random.default_rng(seed=42)
        paths_per_regime = max(1, self.mc_paths // len(regimes))
        all_breached = np.zeros(0)

        for regime, w in zip(regimes, weights):
            n_this = max(1, int(round(self.mc_paths * w)))
            paths = ss.sample_paths(periods_ahead, regime, n_paths=n_this, rng=rng)

            # Breach = any step in the path crosses the threshold.
            if direction == "BELOW":
                breached = np.any(paths < threshold, axis=1).astype(float)
            else:  # ABOVE
                breached = np.any(paths > threshold, axis=1).astype(float)

            all_breached = np.concatenate([all_breached, breached])

        if len(all_breached) == 0:
            return 0.5, 0.0, 1.0

        prob = float(np.mean(all_breached))
        # Wilson score interval for binomial proportion.
        n = len(all_breached)
        z = 1.645  # 90 % CI
        denominator = 1 + z ** 2 / n
        centre = (prob + z ** 2 / (2 * n)) / denominator
        half_width = z * math.sqrt((prob * (1 - prob) + z ** 2 / (4 * n)) / n) / denominator
        ci_lower = max(centre - half_width, 0.0)
        ci_upper = min(centre + half_width, 1.0)

        return prob, ci_lower, ci_upper

    # ------------------------------------------------------------------
    # Contributing factors
    # ------------------------------------------------------------------

    @staticmethod
    def _compute_factors(
        history: List[float],
        threshold: float,
        direction: str,
        detection,
        breach_prob: float,
    ) -> List[Dict[str, Any]]:
        arr = np.asarray(history, dtype=np.float64)
        latest = float(arr[-1])
        threshold_abs = max(abs(threshold), 1e-6)

        # Trend
        if len(arr) >= 2:
            trend = float(arr[-1] - arr[0]) / max(len(arr) - 1, 1)
            trend_impact = min(abs(trend) / threshold_abs, 1.0)
        else:
            trend = 0.0
            trend_impact = 0.0

        # Volatility
        vol = float(np.std(arr)) if len(arr) > 1 else 0.0
        vol_impact = min(vol / threshold_abs, 1.0)

        # Proximity
        proximity = 1.0 - min(abs(latest - threshold) / threshold_abs, 1.0)

        # Regime risk
        regime_risk = {
            RegimeState.NORMAL: 0.1,
            RegimeState.STRESSED: 0.5,
            RegimeState.CRISIS: 0.9,
        }.get(detection.regime, 0.3)

        trend_dir = "downward_trend" if trend < 0 else "upward_trend"
        if direction == "ABOVE" and trend > 0:
            trend_dir = "upward_trend"
        elif direction == "BELOW" and trend < 0:
            trend_dir = "downward_trend"

        return [
            {"name": trend_dir, "impact": round(trend_impact, 4)},
            {"name": "volatility", "impact": round(vol_impact, 4)},
            {"name": "threshold_proximity", "impact": round(max(proximity, 0.0), 4)},
            {"name": "regime_risk", "impact": round(regime_risk, 4)},
        ]

    # ------------------------------------------------------------------
    # Fallback for very short histories
    # ------------------------------------------------------------------

    @staticmethod
    def _fallback_simple(
        history: List[float],
        threshold: float,
        direction: str,
        periods_ahead: int,
    ) -> RSBSNPrediction:
        """Gaussian heuristic when data is too short for full RS-BSN."""
        arr = np.asarray(history, dtype=np.float64) if history else np.array([threshold])
        mean = float(np.mean(arr))
        std = float(np.std(arr)) if len(arr) > 1 else abs(mean) * 0.1 + 1e-6

        # Simple normal CDF approximation for breach.
        z = (threshold - mean) / max(std, 1e-6)
        cdf = 1.0 / (1.0 + math.exp(-1.702 * z))
        if direction == "ABOVE":
            breach_prob = 1.0 - cdf
        else:
            breach_prob = cdf

        breach_prob = float(np.clip(breach_prob, 0.0, 1.0))

        forecasts: List[BayesianForecast] = []
        for step in range(1, periods_ahead + 1):
            horizon_std = std * math.sqrt(step)
            forecasts.append(
                BayesianForecast(
                    period=step,
                    mean=round(mean, 6),
                    std=round(horizon_std, 6),
                    ci_lower=round(mean - 1.645 * horizon_std, 6),
                    ci_upper=round(mean + 1.645 * horizon_std, 6),
                    regime=RegimeState.NORMAL,
                )
            )

        return RSBSNPrediction(
            breach_probability=round(breach_prob, 4),
            confidence_interval={
                "lower": round(max(breach_prob - 0.1, 0.0), 4),
                "upper": round(min(breach_prob + 0.1, 1.0), 4),
            },
            forecasts=forecasts,
            regime_history=[RegimeState.NORMAL] * max(len(history), 1),
            factors=[
                {"name": "insufficient_data", "impact": 0.8},
                {"name": "threshold_proximity", "impact": round(
                    1.0 - min(abs(mean - threshold) / max(abs(threshold), 1e-6), 1.0), 4
                )},
            ],
        )
