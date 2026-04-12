"""FSO — Federated Subspace Orthogonalization.

Privacy-preserving federated learning where tenant model updates
are decomposed into a globally-shared subspace (learned from IFRS
taxonomy embeddings via SVD) and a private complement that never
leaves the tenant shard.
"""

from .aggregator import FSOAggregator
from .models import (
    FSOConfig,
    FSOStatus,
    FSOStatusResponse,
    GlobalUpdate,
    ModelUpdate,
    TenantUpdateRequest,
    TrainRoundRequest,
    TrainRoundResponse,
)
from .trainer import FSOLocalTrainer

__all__ = [
    "FSOAggregator",
    "FSOConfig",
    "FSOLocalTrainer",
    "FSOStatus",
    "FSOStatusResponse",
    "GlobalUpdate",
    "ModelUpdate",
    "TenantUpdateRequest",
    "TrainRoundRequest",
    "TrainRoundResponse",
]
