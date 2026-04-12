"""Bayesian state-space model with regime-dependent parameters.

Implements a *local linear trend* model::

    state  = [level, trend]
    level_{t} = level_{t-1} + trend_{t-1} + η_level
    trend_{t} = trend_{t-1} + η_trend
    obs_{t}   = level_{t} + ε

where the noise variances (``η_level``, ``η_trend``, ``ε``) depend on the
active regime.  State estimation uses the Kalman filter; forecasting samples
from the posterior predictive distribution using analytical conjugate-prior
updates.
"""

from __future__ import annotations

import logging
from typing import Dict, List

import numpy as np

from .models import BayesianForecast, RegimeState

logger = logging.getLogger(__name__)

# Regime-dependent noise multipliers (relative to base variance).
_REGIME_PARAMS: Dict[RegimeState, Dict[str, float]] = {
    RegimeState.NORMAL: {
        "obs_noise_mult": 1.0,
        "level_noise_mult": 1.0,
        "trend_drift_mult": 0.5,
    },
    RegimeState.STRESSED: {
        "obs_noise_mult": 2.0,
        "level_noise_mult": 2.0,
        "trend_drift_mult": 1.5,
    },
    RegimeState.CRISIS: {
        "obs_noise_mult": 5.0,
        "level_noise_mult": 4.0,
        "trend_drift_mult": 3.0,
    },
}

_CI_Z = 1.645  # 90 % credible interval


class BayesianStateSpace:
    """Local linear trend Kalman filter with regime-dependent noise.

    Parameters
    ----------
    base_obs_var : float | None
        Observation noise variance.  Estimated from data if *None*.
    base_level_var : float | None
        Level innovation variance.  Estimated from data if *None*.
    base_trend_var : float | None
        Trend innovation variance.  Estimated from data if *None*.
    """

    def __init__(
        self,
        base_obs_var: float | None = None,
        base_level_var: float | None = None,
        base_trend_var: float | None = None,
    ) -> None:
        self.base_obs_var = base_obs_var
        self.base_level_var = base_level_var
        self.base_trend_var = base_trend_var

        # State [level, trend] and covariance — set after fit.
        self.state: np.ndarray = np.zeros(2)
        self.P: np.ndarray = np.eye(2) * 1e4  # diffuse prior

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def fit(self, values: List[float]) -> None:
        """Run the Kalman filter over *values* to estimate the hidden state.

        After fitting, ``self.state`` and ``self.P`` hold the filtered
        estimates at the last time step.
        """
        y = np.asarray(values, dtype=np.float64)
        n = len(y)

        if n == 0:
            return

        # Heuristic base-variance estimation when not provided.
        if self.base_obs_var is None:
            residual_var = float(np.var(np.diff(y))) if n > 1 else 1.0
            self.base_obs_var = max(residual_var * 0.5, 1e-6)
        if self.base_level_var is None:
            self.base_level_var = max(self.base_obs_var * 0.1, 1e-6)
        if self.base_trend_var is None:
            self.base_trend_var = max(self.base_obs_var * 0.01, 1e-6)

        # Initialise state from first two observations.
        self.state = np.array([y[0], (y[1] - y[0]) if n > 1 else 0.0])
        self.P = np.diag([self.base_obs_var * 10, self.base_trend_var * 10])

        F = np.array([[1.0, 1.0], [0.0, 1.0]])  # state transition
        H = np.array([[1.0, 0.0]])                # observation matrix

        for t in range(n):
            R = self.base_obs_var
            Q = np.diag([self.base_level_var, self.base_trend_var])

            # Predict
            x_pred = F @ self.state
            P_pred = F @ self.P @ F.T + Q

            # Update
            innov = y[t] - (H @ x_pred)[0]
            S = (H @ P_pred @ H.T)[0, 0] + R
            K = (P_pred @ H.T) / S  # (2, 1)
            self.state = x_pred + (K * innov).ravel()
            self.P = (np.eye(2) - K @ H) @ P_pred

    def forecast(
        self,
        steps: int,
        regime: RegimeState,
    ) -> List[BayesianForecast]:
        """Generate *steps* ahead forecasts under the given *regime*.

        Returns a list of :class:`BayesianForecast` with posterior mean, std,
        and 90 % credible intervals.
        """
        params = _REGIME_PARAMS[regime]
        obs_var = self.base_obs_var * params["obs_noise_mult"]
        level_var = self.base_level_var * params["level_noise_mult"]
        trend_var = self.base_trend_var * params["trend_drift_mult"]

        F = np.array([[1.0, 1.0], [0.0, 1.0]])
        H = np.array([[1.0, 0.0]])
        Q = np.diag([level_var, trend_var])

        state = self.state.copy()
        P = self.P.copy()

        forecasts: List[BayesianForecast] = []
        for step in range(1, steps + 1):
            state = F @ state
            P = F @ P @ F.T + Q
            mean = float((H @ state)[0])
            variance = float((H @ P @ H.T)[0, 0] + obs_var)
            std = float(np.sqrt(max(variance, 1e-12)))

            forecasts.append(
                BayesianForecast(
                    period=step,
                    mean=round(mean, 6),
                    std=round(std, 6),
                    ci_lower=round(mean - _CI_Z * std, 6),
                    ci_upper=round(mean + _CI_Z * std, 6),
                    regime=regime,
                )
            )
        return forecasts

    def sample_paths(
        self,
        steps: int,
        regime: RegimeState,
        n_paths: int = 1000,
        rng: np.random.Generator | None = None,
    ) -> np.ndarray:
        """Draw *n_paths* Monte Carlo trajectories of length *steps*.

        Returns an ``(n_paths, steps)`` array of simulated observed values.
        """
        if rng is None:
            rng = np.random.default_rng(seed=42)

        params = _REGIME_PARAMS[regime]
        obs_std = np.sqrt(self.base_obs_var * params["obs_noise_mult"])
        level_std = np.sqrt(self.base_level_var * params["level_noise_mult"])
        trend_std = np.sqrt(self.base_trend_var * params["trend_drift_mult"])

        paths = np.empty((n_paths, steps))
        for i in range(n_paths):
            level, trend = self.state
            for t in range(steps):
                level = level + trend + rng.normal(0, level_std)
                trend = trend + rng.normal(0, trend_std)
                paths[i, t] = level + rng.normal(0, obs_std)

        return paths
