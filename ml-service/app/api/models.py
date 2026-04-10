"""Pydantic models for the ML Service API."""

from datetime import datetime
from enum import Enum
from typing import Literal, Optional

from pydantic import BaseModel


# --- Common Models ---

class BoundingBox(BaseModel):
    """Normalised bounding box (0.0–1.0 relative to page dimensions)."""
    x: float
    y: float
    width: float
    height: float


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
    """Table detected by the OCR service, passed to ML service for classification."""
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
    # VLM pre-classification (when OCR backend is VLM, zones come for free)
    vlm_zone_type: str | None = None        # e.g. "BALANCE_SHEET"
    vlm_zone_label: str | None = None        # e.g. "Consolidated Balance Sheet"
    vlm_zone_confidence: float | None = None  # 0.0-1.0


# --- Zone Models ---

class ZoneType(str, Enum):
    BALANCE_SHEET = "BALANCE_SHEET"
    INCOME_STATEMENT = "INCOME_STATEMENT"
    CASH_FLOW = "CASH_FLOW"
    NOTES_FIXED_ASSETS = "NOTES_FIXED_ASSETS"
    NOTES_RECEIVABLES = "NOTES_RECEIVABLES"
    NOTES_DEBT = "NOTES_DEBT"
    NOTES_OTHER = "NOTES_OTHER"
    OTHER = "OTHER"


class ClassifiedZone(BaseModel):
    table_id: str
    zone_type: ZoneType
    zone_label: str
    confidence: float
    classification_method: Literal["HEURISTIC", "ML", "COMBINED", "VLM"]
    detected_periods: list[str]
    detected_currency: str | None
    detected_unit: str | None
    model_version: str = "production"  # Phase 1: A/B tracking


class ZoneClassificationRequest(BaseModel):
    document_id: str
    tables: list[DetectedTable]


class ZoneClassificationResponse(BaseModel):
    document_id: str
    zones: list[ClassifiedZone]
    processing_time_ms: int


# --- Mapping Models ---

class SourceRow(BaseModel):
    row_id: str
    text: str
    value: str | None
    page: int
    coordinates: BoundingBox
    zone_type: ZoneType


class TargetLineItem(BaseModel):
    line_item_id: str
    label: str
    parent_label: str | None
    zone_type: ZoneType
    item_type: Literal["INPUT", "FORMULA", "VALIDATION", "CATEGORY"]


class MappingSuggestionRequest(BaseModel):
    document_id: str
    source_rows: list[SourceRow]
    target_items: list[TargetLineItem]
    taxonomy_path: str | None = None
    tenant_id: str | None = None  # Phase 1: client model resolution


class ConfidenceLevel(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class SuggestedMapping(BaseModel):
    target_line_item_id: str
    target_label: str
    confidence: float
    confidence_level: ConfidenceLevel
    expression: str
    adjustments: dict


class MappedRow(BaseModel):
    source_row_id: str
    source_text: str
    source_value: str | None
    source_page: int
    source_coordinates: BoundingBox
    suggested_mappings: list[SuggestedMapping]
    model_version: str = "production"  # Phase 1: A/B tracking


class MappingSummary(BaseModel):
    total_source_rows: int
    high_confidence: int
    medium_confidence: int
    low_confidence: int
    unmapped: int


class MappingSuggestionResponse(BaseModel):
    document_id: str
    mappings: list[MappedRow]
    summary: MappingSummary
    processing_time_ms: int
    model_version: str = "production"  # Phase 1: A/B tracking


# --- Phase 1: Feedback Models ---

class CorrectionType(str, Enum):
    REMAPPED = "REMAPPED"    # Analyst chose a different target item
    REJECTED = "REJECTED"    # Analyst rejected the suggestion entirely
    ACCEPTED = "ACCEPTED"    # Analyst confirmed the suggestion (implicit positive)


class FeedbackItem(BaseModel):
    """A single correction from an analyst."""
    source_text: str
    source_zone_type: str
    suggested_item_id: str
    suggested_item_label: str | None = None
    suggested_confidence: float | None = None
    corrected_item_id: str
    corrected_item_label: str | None = None
    correction_type: CorrectionType = CorrectionType.REMAPPED
    document_id: str
    customer_id: str | None = None
    tenant_id: str | None = None
    analyst_id: str | None = None
    model_version: str | None = None


class FeedbackRequest(BaseModel):
    """Batch of feedback corrections."""
    corrections: list[FeedbackItem]


class FeedbackResponse(BaseModel):
    accepted: int
    total_stored: int
    message: str
    storage: str  # "postgresql" or "memory"


class FeedbackExportResponse(BaseModel):
    """Response from feedback export for Colab retraining."""
    records: list[dict]
    total: int
    since: str | None
    tenant_id: str | None


class FeedbackStatsResponse(BaseModel):
    total_corrections: int
    unique_documents: int
    unique_tenants: int
    by_correction_type: dict
    storage: str


# --- Phase 1: Structured Errors ---

class MLError(BaseModel):
    """Structured ML error for graceful degradation."""
    error_code: str
    message: str
    page: int | None = None
    recoverable: bool = True
    detail: str | None = None
