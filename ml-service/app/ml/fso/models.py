"""Data models for Federated Subspace Orthogonalization (FSO)."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import numpy as np
from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Core dataclasses (used internally by aggregator / trainer)
# ---------------------------------------------------------------------------


@dataclass
class FSOConfig:
    """Configuration for an FSO federated learning round."""

    shared_subspace_dim: int = 64
    private_subspace_dim: int = 32
    orthogonality_tolerance: float = 1e-6
    min_tenants_per_round: int = 2
    max_rounds: int = 100
    learning_rate: float = 1e-3
    regularization_lambda: float = 0.01
    taxonomy_embedding_dim: int = 128
    privacy_epsilon: float = 1.0
    clip_norm: float = 1.0


@dataclass
class ModelUpdate:
    """A single tenant's model-parameter update."""

    tenant_id: str
    round_number: int
    parameters: np.ndarray  # flattened parameter vector
    loss: float = 0.0
    num_samples: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class GlobalUpdate:
    """Result of aggregating tenant updates into a global model delta."""

    round_number: int
    shared_component: np.ndarray  # projected onto shared subspace
    participating_tenants: list[str] = field(default_factory=list)
    orthogonality_score: float = 0.0
    privacy_verified: bool = False
    metrics: dict[str, float] = field(default_factory=dict)


# ---------------------------------------------------------------------------
# API-facing Pydantic models
# ---------------------------------------------------------------------------


class FSOStatus(str, Enum):
    IDLE = "IDLE"
    TRAINING = "TRAINING"
    AGGREGATING = "AGGREGATING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class TenantUpdateRequest(BaseModel):
    """Payload from a tenant contributing its local update."""

    tenant_id: str = Field(..., min_length=1)
    round_number: int = Field(..., ge=0)
    parameters: list[float] = Field(..., min_length=1)
    loss: float = Field(default=0.0)
    num_samples: int = Field(default=0, ge=0)


class TrainRoundRequest(BaseModel):
    """Request to kick off a new federated training round."""

    round_number: int = Field(default=0, ge=0)
    tenant_ids: list[str] = Field(default_factory=list)
    config_overrides: dict[str, Any] = Field(default_factory=dict)


class TrainRoundResponse(BaseModel):
    """Response after completing (or scheduling) a training round."""

    round_number: int
    status: FSOStatus
    participating_tenants: list[str]
    orthogonality_score: float
    privacy_verified: bool
    metrics: dict[str, float] = Field(default_factory=dict)


class FSOStatusResponse(BaseModel):
    """Current status of the FSO subsystem."""

    status: FSOStatus
    current_round: int
    total_rounds_completed: int
    participating_tenants: list[str]
    last_orthogonality_score: float
    last_privacy_verified: bool
