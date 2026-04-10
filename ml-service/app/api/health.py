"""ML Service health check — reports all model states and infrastructure."""

import time
from fastapi import APIRouter, Request

from app.services.database import health_check as pg_health_check

router = APIRouter()

_start_time = time.time()


@router.get("/health")
async def health_check(request: Request):
    """Return service health with actual model load states and infra status."""
    app = request.app

    pg_status = await pg_health_check()

    return {
        "status": "healthy",
        "service": "ml-service",
        "version": "1.0.0",
        "models": {
            "layoutlm": {
                "production": {
                    "loaded": getattr(app.state, "layoutlm_loaded", False),
                },
                "staging": {
                    "loaded": getattr(app.state, "layoutlm_staging", False),
                },
            },
            "sentence_bert": {
                "production": {
                    "loaded": getattr(app.state, "sbert_loaded", False),
                },
                "staging": {
                    "loaded": getattr(app.state, "sbert_staging", False),
                },
            },
        },
        "features": {
            "ab_testing": getattr(request.app.state, "_ab_enabled", False)
            if hasattr(request.app.state, "_ab_enabled")
            else False,
            "client_models": getattr(app.state, "client_model_resolver", None) is not None,
            "feedback_storage": "postgresql" if getattr(app.state, "pg_available", False) else "memory",
        },
        "infrastructure": {
            "postgresql": pg_status,
            "device": getattr(
                getattr(app.state, "model_manager", None), "device", "unknown"
            ),
        },
        "uptime_seconds": int(time.time() - _start_time),
    }
