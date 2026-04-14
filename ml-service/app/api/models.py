"""Pydantic models for the ML Service API."""

from datetime import datetime
from enum import Enum
from typing import Literal, Optional

from pydantic import BaseModel, Field


# --- Common Models ---

class BoundingBox(BaseModel):
    """Normalised bounding box (0.0–1.0 relative to page dimensions)."""
    x: float = Field(..., ge=0.0, le=1.0)
    y: float = Field(..., ge=0.0, le=1.0)
    width: float = Field(..., ge=0.0, le=1.0)
    height: float = Field(..., ge=0.0, le=1.0)


class TableCell(BaseModel):
    text: str = Field(..., max_length=10000)
    bbox: BoundingBox
    row_index: int = Field(..., ge=0)
    col_index: int = Field(..., ge=0)
    row_span: int = Field(1, ge=1)
    col_span: int = Field(1, ge=1)
    is_header: bool = False
    cell_type: Literal["TEXT", "NUMERIC", "EMPTY", "MIXED"]


class DetectedTable(BaseModel):
    """Table detected by the OCR service, passed to ML service for classification."""
    table_id: str = Field(..., max_length=200)
    page_number: int = Field(..., ge=0)
    bbox: BoundingBox
    confidence: float = Field(..., ge=0.0, le=1.0)
    rows: int = Field(..., ge=0)
    cols: int = Field(..., ge=0)
    cells: list[TableCell] = Field(..., max_length=50000)
    header_rows: list[int] = Field(default_factory=list, max_length=100)
    account_column: int | None = None
    value_columns: list[int] = Field(default_factory=list, max_length=500)
    detected_periods: list[str] = Field(default_factory=list, max_length=100)
    detected_currency: str | None = Field(None, max_length=10)
    detected_unit: str | None = Field(None, max_length=50)
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
    table_id: str = Field(..., max_length=200)
    zone_type: ZoneType
    zone_label: str = Field(..., max_length=200)
    confidence: float = Field(..., ge=0.0, le=1.0)
    classification_method: Literal["HEURISTIC", "ML", "COMBINED", "VLM"]
    detected_periods: list[str] = Field(default_factory=list, max_length=100)
    detected_currency: str | None = Field(None, max_length=10)
    detected_unit: str | None = Field(None, max_length=50)
    model_version: str = Field("production", max_length=100)


class ZoneClassificationRequest(BaseModel):
    document_id: str = Field(..., max_length=100)
    tables: list[DetectedTable] = Field(..., max_length=500)


class ZoneClassificationResponse(BaseModel):
    document_id: str = Field(..., max_length=100)
    zones: list[ClassifiedZone]
    processing_time_ms: int = Field(..., ge=0)


# --- Mapping Models ---

class SourceRow(BaseModel):
    row_id: str = Field(..., max_length=200)
    text: str = Field(..., max_length=10000)
    value: str | None = Field(None, max_length=1000)
    page: int = Field(..., ge=0)
    coordinates: BoundingBox
    zone_type: ZoneType


class TargetLineItem(BaseModel):
    line_item_id: str = Field(..., max_length=200)
    label: str = Field(..., max_length=500)
    parent_label: str | None = Field(None, max_length=500)
    zone_type: ZoneType
    item_type: Literal["INPUT", "FORMULA", "VALIDATION", "CATEGORY"]


class MappingSuggestionRequest(BaseModel):
    document_id: str = Field(..., max_length=100)
    source_rows: list[SourceRow] = Field(..., max_length=5000)
    target_items: list[TargetLineItem] = Field(..., max_length=5000)
    taxonomy_path: str | None = Field(None, max_length=500)
    tenant_id: str | None = Field(None, max_length=100)


class ConfidenceLevel(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class SuggestedMapping(BaseModel):
    target_line_item_id: str = Field(..., max_length=200)
    target_label: str = Field(..., max_length=500)
    confidence: float = Field(..., ge=0.0, le=1.0)
    confidence_level: ConfidenceLevel
    expression: str = Field(..., max_length=2000)
    adjustments: dict


class MappedRow(BaseModel):
    source_row_id: str = Field(..., max_length=200)
    source_text: str = Field(..., max_length=10000)
    source_value: str | None = Field(None, max_length=1000)
    source_page: int = Field(..., ge=0)
    source_coordinates: BoundingBox
    suggested_mappings: list[SuggestedMapping] = Field(default_factory=list, max_length=100)
    model_version: str = Field("production", max_length=100)


class MappingSummary(BaseModel):
    total_source_rows: int
    high_confidence: int
    medium_confidence: int
    low_confidence: int
    unmapped: int


class MappingSuggestionResponse(BaseModel):
    document_id: str = Field(..., max_length=100)
    mappings: list[MappedRow]
    summary: MappingSummary
    processing_time_ms: int = Field(..., ge=0)
    model_version: str = Field("production", max_length=100)


# --- Phase 1: Feedback Models ---

class CorrectionType(str, Enum):
    REMAPPED = "REMAPPED"    # Analyst chose a different target item
    REJECTED = "REJECTED"    # Analyst rejected the suggestion entirely
    ACCEPTED = "ACCEPTED"    # Analyst confirmed the suggestion (implicit positive)


class FeedbackItem(BaseModel):
    """A single correction from an analyst."""
    source_text: str = Field(..., max_length=10000)
    source_zone_type: str = Field(..., max_length=50)
    suggested_item_id: str = Field(..., max_length=200)
    suggested_item_label: str | None = Field(None, max_length=500)
    suggested_confidence: float | None = Field(None, ge=0.0, le=1.0)
    corrected_item_id: str = Field(..., max_length=200)
    corrected_item_label: str | None = Field(None, max_length=500)
    correction_type: CorrectionType = CorrectionType.REMAPPED
    document_id: str = Field(..., max_length=100)
    customer_id: str | None = Field(None, max_length=100)
    tenant_id: str | None = Field(None, max_length=100)
    analyst_id: str | None = Field(None, max_length=100)
    model_version: str | None = Field(None, max_length=100)


class FeedbackRequest(BaseModel):
    """Batch of feedback corrections."""
    corrections: list[FeedbackItem] = Field(..., max_length=1000)


class FeedbackResponse(BaseModel):
    accepted: int = Field(..., ge=0)
    total_stored: int = Field(..., ge=0)
    message: str = Field(..., max_length=500)
    storage: str = Field(..., max_length=50)


class FeedbackExportResponse(BaseModel):
    """Response from feedback export for Colab retraining."""
    records: list[dict] = Field(default_factory=list, max_length=100000)
    total: int = Field(..., ge=0)
    since: str | None = Field(None, max_length=50)
    tenant_id: str | None = Field(None, max_length=100)


class FeedbackStatsResponse(BaseModel):
    total_corrections: int = Field(..., ge=0)
    unique_documents: int = Field(..., ge=0)
    unique_tenants: int = Field(..., ge=0)
    by_correction_type: dict
    storage: str = Field(..., max_length=50)


# --- Phase 1: Structured Errors ---

class MLError(BaseModel):
    """Structured ML error for graceful degradation."""
    error_code: str = Field(..., max_length=100)
    message: str = Field(..., max_length=2000)
    page: int | None = Field(None, ge=0)
    recoverable: bool = True
    detail: str | None = Field(None, max_length=5000)
