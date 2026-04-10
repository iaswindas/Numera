"""Pipeline orchestration models."""

from enum import Enum
from typing import Optional

from pydantic import BaseModel

from app.api.models import (
    BoundingBox,
    ClassifiedZone,
    DetectedTable,
    MappedRow,
    MappingSummary,
    MLError,
)


class ProcessingStatus(str, Enum):
    UPLOADED = "UPLOADED"
    OCR_COMPLETE = "OCR_COMPLETE"
    TABLES_DETECTED = "TABLES_DETECTED"
    ZONES_CLASSIFIED = "ZONES_CLASSIFIED"
    MAPPED = "MAPPED"
    ERROR = "ERROR"


class PipelineRequest(BaseModel):
    """Request to process a document through the full ML pipeline."""
    document_id: str
    storage_path: str
    language: str = "en"
    tenant_id: str | None = None
    taxonomy_path: str | None = None
    # Skip steps (useful for partial reruns)
    skip_ocr: bool = False
    skip_tables: bool = False
    skip_zones: bool = False
    skip_mapping: bool = False


class OcrPageResult(BaseModel):
    page: int
    text_blocks: int
    characters: int


class PipelineStepResult(BaseModel):
    """Result of a single pipeline step."""
    step: str
    status: str  # "success", "partial", "failed", "skipped"
    duration_ms: int
    data: dict = {}
    errors: list[MLError] = []


class PipelineResponse(BaseModel):
    """Full pipeline processing result."""
    document_id: str
    status: ProcessingStatus
    steps: list[PipelineStepResult]
    total_duration_ms: int
    # Aggregated results
    pages_processed: int = 0
    tables_detected: int = 0
    zones_classified: int = 0
    rows_mapped: int = 0
    # Detailed data (included if steps succeed)
    tables: list[DetectedTable] = []
    zones: list[ClassifiedZone] = []
    mappings: list[MappedRow] = []
    mapping_summary: MappingSummary | None = None
    model_version: str = "production"
    errors: list[MLError] = []
