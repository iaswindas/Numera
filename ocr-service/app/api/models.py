"""Pydantic models for the OCR Service API."""

from pydantic import BaseModel
from typing import Literal


# --- Common Models ---

class BoundingBox(BaseModel):
    """Normalized bounding box (0.0–1.0 relative to page dimensions)."""
    x: float
    y: float
    width: float
    height: float


# --- OCR Models ---

class OcrTextBlock(BaseModel):
    text: str
    confidence: float
    bbox: BoundingBox
    page: int


class OcrPageResult(BaseModel):
    page_number: int
    width: int
    height: int
    text_blocks: list[OcrTextBlock]
    full_text: str


class OcrRequest(BaseModel):
    document_id: str
    storage_path: str
    language: str = "en"
    dpi: int = 300
    pages: list[int] | None = None


class OcrResponse(BaseModel):
    document_id: str
    total_pages: int
    pages: list[OcrPageResult]
    processing_time_ms: int
    language: str


# --- Table Detection Models ---

class TableCell(BaseModel):
    text: str
    bbox: BoundingBox
    row_index: int
    col_index: int
    row_span: int = 1
    col_span: int = 1
    is_header: bool = False
    cell_type: Literal["TEXT", "NUMERIC", "EMPTY", "MIXED"]


class DetectedTable(BaseModel):
    table_id: str
    page_number: int
    bbox: BoundingBox
    confidence: float
    rows: int
    cols: int
    cells: list[TableCell]
    header_rows: list[int]
    account_column: int | None
    value_columns: list[int]
    detected_periods: list[str] = []
    detected_currency: str | None = None
    detected_unit: str | None = None
    # VLM pre-classification (populated when backend is Qwen3-VL)
    vlm_zone_type: str | None = None
    vlm_zone_label: str | None = None
    vlm_zone_confidence: float | None = None


class TableDetectionRequest(BaseModel):
    document_id: str
    storage_path: str
    ocr_results_path: str | None = None
    pages: list[int] | None = None


class TableDetectionResponse(BaseModel):
    document_id: str
    tables: list[DetectedTable]
    total_tables: int
    processing_time_ms: int
