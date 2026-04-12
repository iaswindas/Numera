"""Numera OCR Service — Qwen3-VL or PaddleOCR backend.

Loads either the Qwen3-VL-8B VLM (SOTA, single-model pipeline) or the
legacy PaddleOCR + PP-Structure pipeline based on config.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.router import router
from app.config import settings

logger = logging.getLogger("ocr-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage startup lifecycle — load VLM or PaddleOCR based on config."""
    logger.info("OCR Service starting up (backend=%s) ...", settings.processor_backend)

    if settings.use_vlm:
        # --- Qwen3-VL-8B VLM ---
        from app.ml.vlm_processor import Qwen3VLProcessor

        try:
            vlm = Qwen3VLProcessor(
                model_id=settings.vlm_model_id,
                device=settings.vlm_device,
                quantize=settings.vlm_quantize,
                model_path=settings.vlm_model_path or None,
                max_new_tokens=settings.vlm_max_new_tokens,
            )
            app.state.vlm_processor = vlm
            app.state.vlm_loaded = vlm.is_loaded
            app.state.ocr_engine = None
            app.state.ocr_loaded = False
            app.state.table_detector = None
            app.state.table_loaded = False
            logger.info(
                "Qwen3-VL loaded ✅ (model=%s, quantized=%s, device=%s)",
                settings.vlm_model_id, settings.vlm_quantize, settings.vlm_device,
            )
        except Exception:
            logger.exception("Failed to load Qwen3-VL — falling back to PaddleOCR")
            app.state.vlm_processor = None
            app.state.vlm_loaded = False
            # Fall through to PaddleOCR below
            await _load_paddleocr(app)
    else:
        # --- Legacy PaddleOCR + PP-Structure ---
        app.state.vlm_processor = None
        app.state.vlm_loaded = False
        await _load_paddleocr(app)

    # --- Period parser (shared by both backends) ---
    from app.services.period_parser import PeriodParser
    app.state.period_parser = PeriodParser()

    # --- STGH Fingerprinter ---
    if settings.enable_stgh:
        from app.ml.stgh import STGHConfig, STGHFingerprinter

        app.state.stgh_fingerprinter = STGHFingerprinter(
            STGHConfig(
                hash_bits=settings.stgh_hash_bits,
                k_neighbors=settings.stgh_k_neighbors,
                gcn_hidden=settings.stgh_gcn_hidden,
                gcn_output=settings.stgh_gcn_output,
                semantic_dim=settings.stgh_semantic_dim,
                similarity_threshold=settings.stgh_similarity_threshold,
                use_semantic_model=settings.stgh_use_semantic_model,
            )
        )
        app.state.stgh_loaded = True
    else:
        app.state.stgh_fingerprinter = None
        app.state.stgh_loaded = False

    # --- Storage client ---
    from app.services.storage_client import StorageClient
    app.state.storage = StorageClient(settings)
    logger.info("Storage client initialised (endpoint=%s)", settings.minio_endpoint)

    logger.info("OCR Service ready ✅ (backend=%s)", settings.processor_backend)
    yield
    logger.info("OCR Service shutting down …")


async def _load_paddleocr(app: FastAPI):
    """Load PaddleOCR + PP-Structure (legacy pipeline)."""
    from app.ml.paddle_ocr import PaddleOCREngine
    try:
        engine = PaddleOCREngine.get_instance(
            lang=settings.lang, use_gpu=settings.use_gpu
        )
        app.state.ocr_engine = engine
        app.state.ocr_loaded = True
        logger.info("PaddleOCR engine loaded (lang=%s, gpu=%s)", settings.lang, settings.use_gpu)
    except Exception:
        logger.exception("Failed to load PaddleOCR engine")
        app.state.ocr_engine = None
        app.state.ocr_loaded = False

    from app.ml.table_detector import TableDetector
    try:
        detector = TableDetector(use_gpu=settings.use_gpu)
        app.state.table_detector = detector
        app.state.table_loaded = detector.is_loaded
        logger.info("PP-Structure table detector loaded (available=%s)", detector.is_loaded)
    except Exception:
        logger.exception("Failed to load PP-Structure")
        app.state.table_detector = None
        app.state.table_loaded = False


app = FastAPI(
    title="Numera OCR Service",
    description="Qwen3-VL-8B / PaddleOCR based document processing",
    version="1.0.0",
    lifespan=lifespan,
)

# --- CORS ---
_allow_credentials = "*" not in settings.cors_origin_list
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=_allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- API Key Authentication (V-05) ---
from app.middleware.api_key_auth import ApiKeyMiddleware  # noqa: E402
app.add_middleware(ApiKeyMiddleware, api_key=settings.api_key)


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
