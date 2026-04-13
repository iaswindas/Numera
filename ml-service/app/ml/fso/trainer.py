"""FSOLocalTrainer — per-tenant local fine-tuning for FSO rounds.

Each tenant trains on its own feedback data (corrections, confirmations)
and produces a :class:`ModelUpdate` that can be submitted to the
:class:`FSOAggregator`.
"""

from __future__ import annotations

import logging
import time
from typing import Any

import numpy as np

from .models import FSOConfig, ModelUpdate

logger = logging.getLogger(__name__)


class FSOLocalTrainer:
    """Local fine-tuning loop executed within a single tenant's shard."""

    def __init__(self, config: FSOConfig | None = None):
        self.config = config or FSOConfig()
        self._current_params: np.ndarray | None = None

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def initialise(self, initial_params: np.ndarray) -> None:
        """Set the starting model parameters for this tenant."""
        self._current_params = initial_params.astype(np.float64).copy()
        logger.info("Local trainer initialised — param dim %d", self._current_params.shape[0])

    def train_round(
        self,
        tenant_id: str,
        round_number: int,
        feedback_data: list[dict[str, Any]],
    ) -> ModelUpdate:
        """Run a single local training round on the tenant's feedback.

        Parameters
        ----------
        tenant_id:
            Identifier for the tenant.
        round_number:
            Current global round number.
        feedback_data:
            List of feedback records, each containing at minimum
            ``{"input_embedding": list[float], "correction": float}``.

        Returns
        -------
        ModelUpdate ready for submission to the aggregator.
        """
        if self._current_params is None:
            raise RuntimeError("Trainer not initialised — call initialise() first")

        if not feedback_data:
            logger.warning("No feedback data for tenant %s in round %d; returning zero update", tenant_id, round_number)
            return ModelUpdate(
                tenant_id=tenant_id,
                round_number=round_number,
                parameters=np.zeros_like(self._current_params),
                loss=0.0,
                num_samples=0,
            )

        t_start = time.perf_counter()
        param_dim = self._current_params.shape[0]
        accumulated_grad = np.zeros(param_dim, dtype=np.float64)
        total_loss = 0.0
        valid_samples = 0

        for record in feedback_data:
            embedding = self._extract_embedding(record, param_dim)
            if embedding is None:
                continue
            correction = float(record.get("correction", 0.0))

            # Simple gradient: cosine-similarity based loss between
            # current params and the corrected direction.
            pred = float(np.dot(self._current_params, embedding))
            error = pred - correction
            grad = error * embedding + self.config.regularization_lambda * self._current_params
            accumulated_grad += grad
            total_loss += 0.5 * error ** 2
            valid_samples += 1

        if valid_samples > 0:
            accumulated_grad /= valid_samples
            total_loss /= valid_samples

        # Apply gradient to get the update delta (not the new params)
        update_delta = -self.config.learning_rate * accumulated_grad

        # Clip update norm
        norm = np.linalg.norm(update_delta)
        if norm > self.config.clip_norm:
            update_delta = update_delta * (self.config.clip_norm / norm)

        elapsed = time.perf_counter() - t_start
        logger.info(
            "Tenant %s round %d — %d samples, loss=%.6f, update_norm=%.6f, %.3fs",
            tenant_id,
            round_number,
            valid_samples,
            total_loss,
            np.linalg.norm(update_delta),
            elapsed,
        )

        return ModelUpdate(
            tenant_id=tenant_id,
            round_number=round_number,
            parameters=update_delta,
            loss=total_loss,
            num_samples=valid_samples,
            metadata={"elapsed_seconds": elapsed},
        )

    def apply_global_update(self, global_shared: np.ndarray) -> None:
        """Apply the aggregated global update to the local parameters."""
        if self._current_params is None:
            raise RuntimeError("Trainer not initialised — call initialise() first")
        if global_shared.shape[0] != self._current_params.shape[0]:
            raise ValueError(
                f"Dimension mismatch: params={self._current_params.shape[0]}, "
                f"update={global_shared.shape[0]}"
            )
        self._current_params += global_shared
        logger.debug("Applied global update — new param norm %.4f", np.linalg.norm(self._current_params))

    @property
    def current_params(self) -> np.ndarray | None:
        return self._current_params

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _extract_embedding(record: dict[str, Any], param_dim: int) -> np.ndarray | None:
        """Safely extract and resize an embedding vector from a feedback record."""
        raw = record.get("input_embedding")
        if raw is None:
            return None
        try:
            vec = np.asarray(raw, dtype=np.float64).flatten()
        except (ValueError, TypeError):
            return None

        if vec.shape[0] == 0:
            return None

        # Resize to match parameter dimension
        if vec.shape[0] < param_dim:
            padded = np.zeros(param_dim, dtype=np.float64)
            padded[: vec.shape[0]] = vec
            return padded
        return vec[:param_dim]
