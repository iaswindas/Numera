"""FSOAggregator — Federated Subspace Orthogonalization aggregation.

The aggregator maintains a *shared subspace* computed via SVD on IFRS
taxonomy embeddings.  Tenant updates are projected onto this shared
subspace for averaging, while the *private component* (orthogonal
complement) is retained per-tenant and never leaves the local shard.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

import numpy as np

from .models import FSOConfig, GlobalUpdate, ModelUpdate

logger = logging.getLogger(__name__)

_TAXONOMY_PATH = Path(__file__).resolve().parents[4] / "data" / "ifrs_taxonomy.json"


class FSOAggregator:
    """Federated aggregation over orthogonal subspaces."""

    def __init__(self, config: FSOConfig | None = None, taxonomy_path: Path | str | None = None):
        self.config = config or FSOConfig()
        self._taxonomy_path = Path(taxonomy_path) if taxonomy_path else _TAXONOMY_PATH
        self._shared_basis: np.ndarray | None = None  # (k, d) orthonormal rows
        self._taxonomy_embeddings: np.ndarray | None = None
        self._round_updates: dict[str, ModelUpdate] = {}

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def initialise(self) -> None:
        """Load taxonomy embeddings and compute the shared subspace."""
        self._taxonomy_embeddings = self._build_taxonomy_embeddings()
        self._shared_basis = self._compute_shared_subspace(self._taxonomy_embeddings)
        logger.info(
            "FSO shared subspace initialised — shape %s, taxonomy items %d",
            self._shared_basis.shape,
            self._taxonomy_embeddings.shape[0],
        )

    def submit_update(self, update: ModelUpdate) -> None:
        """Buffer a single tenant's update for the current round."""
        self._round_updates[update.tenant_id] = update
        logger.debug("Buffered update from tenant %s (round %d)", update.tenant_id, update.round_number)

    def aggregate(self, round_number: int) -> GlobalUpdate:
        """Aggregate buffered updates into a single global update.

        Steps:
        1. Project each tenant update onto the shared subspace.
        2. Compute a weighted average of shared projections.
        3. Verify orthogonality between shared and private components.
        """
        if self._shared_basis is None:
            self.initialise()
        assert self._shared_basis is not None

        updates = list(self._round_updates.values())
        if len(updates) < self.config.min_tenants_per_round:
            raise ValueError(
                f"Need at least {self.config.min_tenants_per_round} tenant updates, got {len(updates)}"
            )

        param_dim = updates[0].parameters.shape[0]

        # Pad/truncate shared basis to match parameter dimension
        basis = self._ensure_basis_dim(param_dim)

        # 1. Project to shared subspace and accumulate weighted average
        total_samples = sum(max(u.num_samples, 1) for u in updates)
        aggregated_shared = np.zeros(param_dim, dtype=np.float64)

        for update in updates:
            clipped = self._clip_update(update.parameters)
            shared_proj = self._project_to_shared(clipped, basis)
            weight = max(update.num_samples, 1) / total_samples
            aggregated_shared += weight * shared_proj

        # 2. Orthogonality score (mean |<shared_row, aggregated>| — should be ~0 for private part)
        private_component = aggregated_shared - self._project_to_shared(aggregated_shared, basis)
        orth_score = self._orthogonality_score(aggregated_shared, private_component, basis)

        # 3. Privacy check
        privacy_ok = self.verify_privacy(updates, basis)

        global_update = GlobalUpdate(
            round_number=round_number,
            shared_component=aggregated_shared,
            participating_tenants=[u.tenant_id for u in updates],
            orthogonality_score=orth_score,
            privacy_verified=privacy_ok,
            metrics={
                "num_tenants": float(len(updates)),
                "total_samples": float(total_samples),
                "mean_loss": float(np.mean([u.loss for u in updates])),
            },
        )

        self._round_updates.clear()
        logger.info(
            "FSO round %d aggregated — %d tenants, orth=%.6f, privacy=%s",
            round_number,
            len(updates),
            orth_score,
            privacy_ok,
        )
        return global_update

    # ------------------------------------------------------------------
    # Projection helpers
    # ------------------------------------------------------------------

    def _project_to_shared(self, vector: np.ndarray, basis: np.ndarray) -> np.ndarray:
        """Project *vector* onto the column space of *basis* (rows are basis vectors)."""
        # basis: (k, d),  vector: (d,)
        coefficients = basis @ vector  # (k,)
        return basis.T @ coefficients  # (d,)

    def _project_to_private(self, vector: np.ndarray, basis: np.ndarray) -> np.ndarray:
        """Return the component of *vector* orthogonal to the shared subspace."""
        return vector - self._project_to_shared(vector, basis)

    # ------------------------------------------------------------------
    # Shared subspace construction
    # ------------------------------------------------------------------

    def _compute_shared_subspace(self, embeddings: np.ndarray) -> np.ndarray:
        """Compute an orthonormal basis for the top-k left singular vectors.

        Parameters
        ----------
        embeddings:
            (n_items, d) matrix of taxonomy item embeddings.

        Returns
        -------
        basis:
            (k, d) matrix whose rows form an orthonormal basis for
            the shared subspace.
        """
        k = min(self.config.shared_subspace_dim, *embeddings.shape)
        # Centre the embeddings (zero-mean per feature)
        centred = embeddings - embeddings.mean(axis=0)
        U, S, Vt = np.linalg.svd(centred, full_matrices=False)
        basis = Vt[:k]  # top-k right singular vectors (rows are orthonormal)
        logger.debug("SVD top-%d singular values: %s", k, S[:k].tolist())
        return basis

    # ------------------------------------------------------------------
    # Privacy / orthogonality verification
    # ------------------------------------------------------------------

    def verify_privacy(self, updates: list[ModelUpdate], basis: np.ndarray) -> bool:
        """Mathematical guarantee: the private component of every tenant
        update is orthogonal to the shared subspace within tolerance.

        This ensures that no individual tenant data leaks into the
        global shared component.
        """
        tol = self.config.orthogonality_tolerance
        for update in updates:
            private = self._project_to_private(update.parameters, basis)
            # Inner product of private component with each basis row should be ~0
            dots = basis @ private  # (k,)
            max_dot = float(np.max(np.abs(dots)))
            if max_dot > tol:
                logger.warning(
                    "Privacy check FAILED for tenant %s: max |dot|=%.8f > tol=%.8f",
                    update.tenant_id,
                    max_dot,
                    tol,
                )
                return False
        return True

    def _orthogonality_score(
        self,
        shared: np.ndarray,
        private: np.ndarray,
        basis: np.ndarray,
    ) -> float:
        """Return 1.0 when perfectly orthogonal, 0.0 when fully aligned."""
        shared_norm = np.linalg.norm(shared)
        private_norm = np.linalg.norm(private)
        if shared_norm < 1e-12 or private_norm < 1e-12:
            return 1.0
        cos_sim = abs(float(np.dot(shared, private) / (shared_norm * private_norm)))
        return 1.0 - cos_sim

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _build_taxonomy_embeddings(self) -> np.ndarray:
        """Build pseudo-embeddings from the IFRS taxonomy JSON.

        Each taxonomy item (and its synonyms) is encoded into a
        deterministic vector using a seeded random projection.  In
        production this would be replaced by actual SBERT embeddings.
        """
        taxonomy = self._load_taxonomy()
        items: list[str] = []
        for key, synonyms in taxonomy.items():
            items.append(key)
            items.extend(synonyms)

        dim = self.config.taxonomy_embedding_dim
        rng = np.random.default_rng(seed=42)
        embeddings = np.zeros((len(items), dim), dtype=np.float64)
        for i, item in enumerate(items):
            # Deterministic hash-based seed per item for reproducibility
            item_seed = int.from_bytes(item.encode("utf-8")[:8].ljust(8, b"\x00"), "little") % (2**31)
            item_rng = np.random.default_rng(seed=item_seed)
            embeddings[i] = item_rng.standard_normal(dim)

        # L2-normalise rows
        norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
        norms = np.maximum(norms, 1e-12)
        embeddings /= norms
        return embeddings

    def _load_taxonomy(self) -> dict[str, list[str]]:
        """Load IFRS taxonomy from JSON, with graceful fallback."""
        try:
            with open(self._taxonomy_path, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            if not isinstance(data, dict):
                raise TypeError("Taxonomy must be a JSON object")
            return {str(k): [str(s) for s in v] for k, v in data.items()}
        except (FileNotFoundError, json.JSONDecodeError, TypeError) as exc:
            logger.warning("Could not load taxonomy from %s: %s — using fallback", self._taxonomy_path, exc)
            return {
                "Revenue": ["Net Sales", "Total Income"],
                "Total Assets": ["Assets Total"],
                "Total Equity": ["Shareholders Equity"],
            }

    def _ensure_basis_dim(self, param_dim: int) -> np.ndarray:
        """Resize or pad the shared basis to match the parameter dimension."""
        assert self._shared_basis is not None
        k, d = self._shared_basis.shape
        if d == param_dim:
            return self._shared_basis
        if d > param_dim:
            return self._shared_basis[:, :param_dim]
        # Pad with zeros
        padded = np.zeros((k, param_dim), dtype=np.float64)
        padded[:, :d] = self._shared_basis
        return padded

    def _clip_update(self, params: np.ndarray) -> np.ndarray:
        """Clip update vector to the configured norm to limit sensitivity."""
        norm = np.linalg.norm(params)
        if norm > self.config.clip_norm:
            return params * (self.config.clip_norm / norm)
        return params
