"""Numera ML Service — Phase 1 with feedback, A/B testing, and pipeline.

FastAPI application with lifespan management for pre-loading models,
PostgreSQL connection pool, feedback store, and client model resolver.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.router import router
from app.config import settings

logger = logging.getLogger("ml-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown lifecycle.

    Loads all ML models, initialises PostgreSQL pool, feedback store,
    and client model resolver as singletons on app.state.
    """
    logger.info("ML Service starting up …")

    # --- PostgreSQL connection pool ---
    from app.services.database import init_pool, close_pool

    pg_ok = await init_pool(
        dsn=settings.pg_dsn,
        min_size=settings.pg_min_pool,
        max_size=settings.pg_max_pool,
    )
    app.state.pg_available = pg_ok

    # --- Feedback Store ---
    from app.services.feedback_store import FeedbackStore
    from app.services.database import get_pool

    feedback_store = FeedbackStore()
    await feedback_store.init(get_pool())
    app.state.feedback_store = feedback_store

    # --- Model Manager ---
    from app.services.model_manager import ModelManager

    model_manager = ModelManager(
        mlflow_uri=settings.mlflow_uri,
        local_cache_dir=settings.model_cache_dir,
    )
    app.state.model_manager = model_manager

    # --- LayoutLM Zone Classifier ---
    from app.ml.zone_classifier import LayoutLMZoneClassifier

    zone_classifier = LayoutLMZoneClassifier(model_manager, settings)
    app.state.zone_classifier = zone_classifier
    app.state.layoutlm_loaded = zone_classifier.is_loaded
    app.state.layoutlm_staging = zone_classifier.staging_loaded
    logger.info(
        "LayoutLM: prod=%s staging=%s",
        zone_classifier.is_loaded, zone_classifier.staging_loaded,
    )

    # --- Sentence-BERT Semantic Matcher ---
    from app.ml.semantic_matcher import SemanticMatcher

    semantic_matcher = SemanticMatcher(model_manager, settings)
    app.state.semantic_matcher = semantic_matcher
    app.state.sbert_loaded = semantic_matcher.is_loaded
    app.state.sbert_staging = semantic_matcher.staging_loaded
    logger.info(
        "SBERT: prod=%s staging=%s",
        semantic_matcher.is_loaded, semantic_matcher.staging_loaded,
    )

    # --- Client Model Resolver (Phase 1) ---
    if settings.enable_client_models:
        from app.services.client_model_resolver import ClientModelResolver

        resolver = ClientModelResolver(model_manager, settings)
        app.state.client_model_resolver = resolver
        logger.info("Client model resolver enabled (min_corrections=%d)", settings.client_model_min_corrections)
    else:
        app.state.client_model_resolver = None

    logger.info("ML Service ready ✅ (A/B=%s, client_models=%s, pg=%s)",
                settings.ab_test_enabled, settings.enable_client_models, pg_ok)
    yield

    # --- Shutdown ---
    logger.info("ML Service shutting down …")
    await close_pool()
    logger.info("ML Service stopped")


app = FastAPI(
    title="Numera ML Service",
    description="Phase 1 ML service: LayoutLM + SBERT + A/B testing + feedback pipeline",
    version="1.0.0",
    lifespan=lifespan,
)

# --- CORS ---
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Global error handler ---
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "error": "internal_server_error",
            "detail": str(exc) if settings.debug else "An internal error occurred.",
        },
    )


# --- Routes ---
app.include_router(router, prefix="/api")


if __name__ == "__main__":
    import uvicorn

    logging.basicConfig(
        level=logging.DEBUG if settings.debug else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )
