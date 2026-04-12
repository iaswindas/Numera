"""Hidden Markov Model for regime detection (NORMAL / STRESSED / CRISIS).

Uses a minimal Gaussian-emission HMM trained via the EM algorithm.  When
``hmmlearn`` is available it delegates to :class:`hmmlearn.hmm.GaussianHMM`;
otherwise a pure-NumPy fallback is used so the service can start without the
optional dependency.
"""

from __future__ import annotations

import logging
from typing import List, Tuple

import numpy as np

from .models import RegimeDetection, RegimeState

logger = logging.getLogger(__name__)

_N_REGIMES = 3
_REGIME_ORDER = [RegimeState.NORMAL, RegimeState.STRESSED, RegimeState.CRISIS]

# Default priors — will be overwritten by EM when data is sufficient.
_DEFAULT_MEANS = np.array([0.0, -0.5, -2.0])
_DEFAULT_VARS = np.array([0.1, 0.5, 2.0])
_DEFAULT_TRANSITION = np.array([
    [0.90, 0.08, 0.02],
    [0.15, 0.70, 0.15],
    [0.05, 0.15, 0.80],
])
_DEFAULT_START = np.array([0.70, 0.20, 0.10])


def _try_import_hmmlearn():  # pragma: no cover
    """Attempt to import hmmlearn; return None on failure."""
    try:
        from hmmlearn.hmm import GaussianHMM  # type: ignore[import-untyped]
        return GaussianHMM
    except ImportError:
        return None


class RegimeHMM:
    """Gaussian-emission HMM with 3 regimes.

    Parameters
    ----------
    n_regimes : int
        Number of hidden states (default 3).
    em_iterations : int
        Maximum EM iterations for fitting.
    min_sequence_length : int
        Sequences shorter than this trigger the fast fallback.
    """

    def __init__(
        self,
        n_regimes: int = _N_REGIMES,
        em_iterations: int = 50,
        min_sequence_length: int = 5,
    ) -> None:
        self.n_regimes = n_regimes
        self.em_iterations = em_iterations
        self.min_sequence_length = min_sequence_length

        # Parameters (set after fit or initialised to defaults).
        self.means: np.ndarray = _DEFAULT_MEANS.copy()
        self.variances: np.ndarray = _DEFAULT_VARS.copy()
        self.transition: np.ndarray = _DEFAULT_TRANSITION.copy()
        self.start_prob: np.ndarray = _DEFAULT_START.copy()
        self._fitted = False

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def fit(self, values: List[float]) -> None:
        """Fit the HMM to *percentage-change* representation of *values*.

        Short sequences (< ``min_sequence_length``) are handled gracefully:
        the model stays at default priors and the current regime is estimated
        from simple volatility heuristics.
        """
        if len(values) < self.min_sequence_length:
            logger.debug("Sequence too short (%d); using default priors.", len(values))
            return

        obs = self._to_returns(values)
        if len(obs) < 2:
            return

        GaussianHMM = _try_import_hmmlearn()
        if GaussianHMM is not None:
            self._fit_hmmlearn(obs, GaussianHMM)
        else:
            self._fit_em(obs)

        self._fitted = True

    def predict_regime(self, values: List[float]) -> RegimeDetection:
        """Return the current regime and transition probabilities."""
        if len(values) < 2:
            return self._fallback_regime(values)

        obs = self._to_returns(values)
        if len(obs) < 1:
            return self._fallback_regime(values)

        # Viterbi → most likely state sequence
        states = self._viterbi(obs)
        current_idx = int(states[-1])
        prob = self._state_posterior(obs, current_idx)

        return RegimeDetection(
            regime=_REGIME_ORDER[current_idx],
            probability=float(np.clip(prob, 0.0, 1.0)),
            transition_matrix=self.transition.tolist(),
        )

    def decode(self, values: List[float]) -> List[RegimeState]:
        """Return regime labels for every observation."""
        if len(values) < 2:
            return [self._fallback_regime(values).regime] * max(len(values), 1)
        obs = self._to_returns(values)
        states = self._viterbi(obs)
        # Prepend state for t=0 (no return computed for first value).
        return [_REGIME_ORDER[int(states[0])]] + [
            _REGIME_ORDER[int(s)] for s in states
        ]

    # ------------------------------------------------------------------
    # Internals — hmmlearn path
    # ------------------------------------------------------------------

    def _fit_hmmlearn(self, obs: np.ndarray, GaussianHMM) -> None:  # type: ignore[no-untyped-def]
        model = GaussianHMM(
            n_components=self.n_regimes,
            covariance_type="diag",
            n_iter=self.em_iterations,
            random_state=42,
        )
        X = obs.reshape(-1, 1)
        try:
            model.fit(X)
        except Exception:
            logger.warning("hmmlearn fit failed; falling back to manual EM.", exc_info=True)
            self._fit_em(obs)
            return

        # Sort components by mean so that lowest-mean ≈ CRISIS.
        order = np.argsort(model.means_.ravel())[::-1]
        self.means = model.means_.ravel()[order]
        self.variances = model.covars_.ravel()[order]
        self.transition = model.transmat_[order][:, order]
        self.start_prob = model.startprob_[order]

    # ------------------------------------------------------------------
    # Internals — pure-NumPy EM fallback
    # ------------------------------------------------------------------

    def _fit_em(self, obs: np.ndarray) -> None:
        """Baum-Welch EM on 1-D Gaussian emissions."""
        T = len(obs)
        K = self.n_regimes

        # Initialise from data quantiles.
        sorted_obs = np.sort(obs)
        q = np.linspace(0, 1, K + 2)[1:-1]
        self.means = np.quantile(sorted_obs, q)[::-1]  # descending
        self.variances = np.full(K, np.var(obs) + 1e-6)
        # Keep default transition / start as warm-start.

        for _ in range(self.em_iterations):
            log_alpha, log_beta, log_gamma, log_xi = self._e_step(obs, T, K)

            gamma = np.exp(log_gamma)
            xi = np.exp(log_xi)

            # M-step
            gamma_sum = gamma.sum(axis=0) + 1e-12
            self.start_prob = gamma[0] / (gamma[0].sum() + 1e-12)
            for k in range(K):
                self.means[k] = np.dot(gamma[:, k], obs) / gamma_sum[k]
                diff = obs - self.means[k]
                self.variances[k] = np.dot(gamma[:, k], diff ** 2) / gamma_sum[k] + 1e-6

            xi_sum = xi.sum(axis=0) + 1e-12
            self.transition = xi_sum / xi_sum.sum(axis=1, keepdims=True)

    def _e_step(
        self,
        obs: np.ndarray,
        T: int,
        K: int,
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        log_emit = self._log_emission(obs)  # (T, K)

        # Forward
        log_alpha = np.full((T, K), -np.inf)
        log_alpha[0] = np.log(self.start_prob + 1e-300) + log_emit[0]
        log_trans = np.log(self.transition + 1e-300)
        for t in range(1, T):
            for j in range(K):
                log_alpha[t, j] = _logsumexp(log_alpha[t - 1] + log_trans[:, j]) + log_emit[t, j]

        # Backward
        log_beta = np.zeros((T, K))
        for t in range(T - 2, -1, -1):
            for i in range(K):
                log_beta[t, i] = _logsumexp(
                    log_trans[i] + log_emit[t + 1] + log_beta[t + 1]
                )

        # Gamma
        log_gamma = log_alpha + log_beta
        log_gamma -= _logsumexp_2d(log_gamma)

        # Xi
        log_xi = np.full((T - 1, K, K), -np.inf)
        for t in range(T - 1):
            for i in range(K):
                for j in range(K):
                    log_xi[t, i, j] = (
                        log_alpha[t, i]
                        + log_trans[i, j]
                        + log_emit[t + 1, j]
                        + log_beta[t + 1, j]
                    )
            log_xi[t] -= _logsumexp(log_xi[t].ravel())

        return log_alpha, log_beta, log_gamma, log_xi

    # ------------------------------------------------------------------
    # Viterbi decoding
    # ------------------------------------------------------------------

    def _viterbi(self, obs: np.ndarray) -> np.ndarray:
        T = len(obs)
        K = self.n_regimes
        log_emit = self._log_emission(obs)
        log_trans = np.log(self.transition + 1e-300)

        viterbi = np.full((T, K), -np.inf)
        backptr = np.zeros((T, K), dtype=int)

        viterbi[0] = np.log(self.start_prob + 1e-300) + log_emit[0]

        for t in range(1, T):
            for j in range(K):
                scores = viterbi[t - 1] + log_trans[:, j]
                backptr[t, j] = int(np.argmax(scores))
                viterbi[t, j] = scores[backptr[t, j]] + log_emit[t, j]

        states = np.zeros(T, dtype=int)
        states[-1] = int(np.argmax(viterbi[-1]))
        for t in range(T - 2, -1, -1):
            states[t] = backptr[t + 1, states[t + 1]]
        return states

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _log_emission(self, obs: np.ndarray) -> np.ndarray:
        """Log-probability of observations under each Gaussian component."""
        T = len(obs)
        K = self.n_regimes
        log_p = np.empty((T, K))
        for k in range(K):
            var = self.variances[k]
            log_p[:, k] = -0.5 * np.log(2 * np.pi * var) - 0.5 * ((obs - self.means[k]) ** 2) / var
        return log_p

    def _state_posterior(self, obs: np.ndarray, state_idx: int) -> float:
        """Approximate posterior probability of the last observation being *state_idx*."""
        log_emit_last = self._log_emission(obs[-1:])  # (1, K)
        log_prior = np.log(self.start_prob + 1e-300)
        log_joint = log_emit_last[0] + log_prior
        log_joint -= _logsumexp(log_joint)
        return float(np.exp(log_joint[state_idx]))

    @staticmethod
    def _to_returns(values: List[float]) -> np.ndarray:
        """Convert absolute values to log-returns (percentage changes)."""
        arr = np.asarray(values, dtype=np.float64)
        arr = np.where(arr == 0, 1e-10, arr)
        returns = np.diff(np.log(np.abs(arr)))
        return returns

    def _fallback_regime(self, values: List[float]) -> RegimeDetection:
        """Simple heuristic fallback when data is insufficient."""
        if not values:
            return RegimeDetection(
                regime=RegimeState.NORMAL,
                probability=0.5,
                transition_matrix=self.transition.tolist(),
            )

        arr = np.asarray(values, dtype=np.float64)
        vol = float(np.std(arr)) if len(arr) > 1 else 0.0
        mean_abs = float(np.mean(np.abs(arr))) + 1e-10
        cv = vol / mean_abs

        if cv > 0.5:
            regime = RegimeState.CRISIS
        elif cv > 0.15:
            regime = RegimeState.STRESSED
        else:
            regime = RegimeState.NORMAL

        return RegimeDetection(
            regime=regime,
            probability=0.6,
            transition_matrix=self.transition.tolist(),
        )


# ------------------------------------------------------------------
# Numerically stable log-space utilities
# ------------------------------------------------------------------

def _logsumexp(x: np.ndarray) -> float:
    """Log-sum-exp with numerical stability."""
    x = np.asarray(x, dtype=np.float64)
    c = x.max()
    if np.isinf(c):
        return float(c)
    return float(c + np.log(np.sum(np.exp(x - c))))


def _logsumexp_2d(x: np.ndarray) -> np.ndarray:
    """Row-wise log-sum-exp for a 2-D array, broadcast-safe."""
    c = x.max(axis=1, keepdims=True)
    return c + np.log(np.sum(np.exp(x - c), axis=1, keepdims=True))
