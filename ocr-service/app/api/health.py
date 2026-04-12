"""OCR Service health check endpoint."""

import time
from fastapi import APIRouter, Request

router = APIRouter()

_start_time = time.time()


@router.get("/health")
def health_check(request: Request):
    """Return service health with actual model load states."""
    app = request.app
    return {
        "status": "healthy",
        "service": "ocr-service",
        "models": {
            "paddleocr": {
                "loaded": getattr(app.state, "ocr_loaded", False),
                "version": "2.9",
                "lang": getattr(
                    getattr(app.state, "ocr_engine", None), "lang", "unknown"
                ),
            },
            "pp_structure": {
                "loaded": getattr(app.state, "table_loaded", False),
                "version": "3.0",
            },
            "stgh": {
                "loaded": getattr(app.state, "stgh_loaded", False),
                "version": "1.0",
            },
        },
        "device": "gpu" if getattr(app.state, "ocr_engine", None)
                  and getattr(app.state.ocr_engine, "use_gpu", False) else "cpu",
        "uptime_seconds": int(time.time() - _start_time),
    }
