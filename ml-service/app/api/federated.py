"""FSO federated-learning API endpoints.

POST /api/ml/federated/train-round
GET  /api/ml/federated/status
POST /api/ml/federated/tenant-update
"""

from __future__ import annotations

import logging
import traceback

import numpy as np
from fastapi import APIRouter, HTTPException

from app.ml.fso import (
    FSOAggregator,
    FSOConfig,
    FSOLocalTrainer,
    FSOStatus,
    FSOStatusResponse,
    TenantUpdateRequest,
    TrainRoundRequest,
    TrainRoundResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter()

# ---------------------------------------------------------------------------
# Module-level singleton (created lazily via _get_aggregator)
# ---------------------------------------------------------------------------

_aggregator: FSOAggregator | None = None
_trainers: dict[str, FSOLocalTrainer] = {}
_status = FSOStatus.IDLE
_current_round = 0
_total_rounds_completed = 0
_last_orth_score = 0.0
_last_privacy_ok = False
_participating_tenants: list[str] = []


def _get_aggregator() -> FSOAggregator:
    global _aggregator
    if _aggregator is None:
        _aggregator = FSOAggregator()
        _aggregator.initialise()
    return _aggregator


def _get_trainer(tenant_id: str) -> FSOLocalTrainer:
    if tenant_id not in _trainers:
        trainer = FSOLocalTrainer()
        agg = _get_aggregator()
        # Initialise with a zero vector matching shared subspace embedding dim
        dim = agg.config.taxonomy_embedding_dim
        trainer.initialise(np.zeros(dim, dtype=np.float64))
        _trainers[tenant_id] = trainer
    return _trainers[tenant_id]


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.post("/federated/train-round", response_model=TrainRoundResponse)
async def train_round(request: TrainRoundRequest) -> TrainRoundResponse:
    """Kick off a federated training round and return aggregation results."""
    global _status, _current_round, _total_rounds_completed
    global _last_orth_score, _last_privacy_ok, _participating_tenants

    try:
        _status = FSOStatus.TRAINING
        aggregator = _get_aggregator()

        # Apply optional config overrides
        if request.config_overrides:
            for key, value in request.config_overrides.items():
                if hasattr(aggregator.config, key):
                    setattr(aggregator.config, key, value)

        round_num = request.round_number or _current_round
        _current_round = round_num

        _status = FSOStatus.AGGREGATING
        result = aggregator.aggregate(round_num)

        # Apply global update to each participating trainer
        for tid in result.participating_tenants:
            if tid in _trainers:
                _trainers[tid].apply_global_update(result.shared_component)

        _status = FSOStatus.COMPLETED
        _total_rounds_completed += 1
        _last_orth_score = result.orthogonality_score
        _last_privacy_ok = result.privacy_verified
        _participating_tenants = result.participating_tenants

        return TrainRoundResponse(
            round_number=result.round_number,
            status=_status,
            participating_tenants=result.participating_tenants,
            orthogonality_score=result.orthogonality_score,
            privacy_verified=result.privacy_verified,
            metrics=result.metrics,
        )
    except ValueError as exc:
        _status = FSOStatus.FAILED
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        _status = FSOStatus.FAILED
        logger.error("FSO train-round failed: %s\n%s", exc, traceback.format_exc())
        raise HTTPException(status_code=500, detail="FSO training round failed") from exc


@router.get("/federated/status", response_model=FSOStatusResponse)
async def get_status() -> FSOStatusResponse:
    """Return the current status of the FSO subsystem."""
    return FSOStatusResponse(
        status=_status,
        current_round=_current_round,
        total_rounds_completed=_total_rounds_completed,
        participating_tenants=_participating_tenants,
        last_orthogonality_score=_last_orth_score,
        last_privacy_verified=_last_privacy_ok,
    )


@router.post("/federated/tenant-update", status_code=202)
async def submit_tenant_update(request: TenantUpdateRequest) -> dict:
    """Accept a tenant's local model update for the current round."""
    try:
        aggregator = _get_aggregator()
        params = np.asarray(request.parameters, dtype=np.float64)

        from app.ml.fso.models import ModelUpdate

        update = ModelUpdate(
            tenant_id=request.tenant_id,
            round_number=request.round_number,
            parameters=params,
            loss=request.loss,
            num_samples=request.num_samples,
        )
        aggregator.submit_update(update)

        logger.info(
            "Accepted update from tenant %s for round %d (%d params)",
            request.tenant_id,
            request.round_number,
            len(request.parameters),
        )
        return {
            "status": "accepted",
            "tenant_id": request.tenant_id,
            "round_number": request.round_number,
        }
    except Exception as exc:
        logger.error("Failed to accept tenant update: %s", exc)
        raise HTTPException(status_code=500, detail="Failed to process tenant update") from exc
