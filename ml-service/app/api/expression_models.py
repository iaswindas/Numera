"""Pydantic models for the expression/mapping API."""

from pydantic import BaseModel
from typing import Optional


class SourceRefModel(BaseModel):
    """A reference to a value extracted from the PDF."""
    row_index: int
    label: str
    value: Optional[float] = None
    page: int = 0
    confidence: float = 0.0


class MappingExpressionModel(BaseModel):
    """A complete mapping from PDF source(s) to a model template cell."""
    target_item_id: str
    target_label: str
    expression_type: str = "DIRECT"  # DIRECT, SUM, NEGATE, ABSOLUTE, SCALE, MANUAL
    sources: list[SourceRefModel] = []
    scale_factor: float = 1.0
    computed_value: Optional[float] = None
    confidence: float = 0.0
    explanation: str = ""


class ExpressionBuildRequest(BaseModel):
    """Request to build mapping expressions."""
    document_id: str
    tenant_id: str = "default"
    customer_id: str = ""
    template_id: str = "ifrs-corporate-v1"
    zone_type: str = "INCOME_STATEMENT"
    period_index: int = 0
    extracted_rows: list[dict]    # From VLM/OCR extraction
    semantic_matches: list[dict]  # From /api/ml/mapping/suggest
    use_autofill: bool = True     # Apply patterns from previous spreads


class ExpressionBuildResponse(BaseModel):
    """Response with built mapping expressions."""
    document_id: str
    template_id: str
    zone_type: str
    expressions: list[MappingExpressionModel]
    total_mapped: int
    total_items: int
    coverage_pct: float
    unit_scale: float = 1.0
    autofilled: int = 0  # How many came from expression memory
    validation_results: list[dict] = []
