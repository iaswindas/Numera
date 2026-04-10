"""OCR Service API router — mounts all sub-routers."""

from fastapi import APIRouter

from .health import router as health_router
from .ocr import router as ocr_router
from .tables import router as tables_router

router = APIRouter()

router.include_router(health_router, prefix="/ocr", tags=["health"])
router.include_router(ocr_router, prefix="/ocr", tags=["ocr"])
router.include_router(tables_router, prefix="/ocr/tables", tags=["tables"])
