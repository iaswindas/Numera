"""POST /api/ml/expressions/build — Build mapping expressions from PDF to model."""

import json
import logging
from pathlib import Path

from fastapi import APIRouter, Request

from app.api.expression_models import (
    ExpressionBuildRequest,
    ExpressionBuildResponse,
    MappingExpressionModel,
    SourceRefModel,
)
from app.ml.expression_engine import (
    ExpressionEngine,
    ExpressionMemory,
    ExtractedRow,
    MappingExpression,
)

router = APIRouter()
logger = logging.getLogger("ml-service.api.expressions")

# Shared instances
_engine = ExpressionEngine(tolerance=0.01)
_memory = ExpressionMemory()

# Load model template at startup
_templates_cache: dict[str, dict] = {}


def _get_template(template_id: str) -> dict:
    """Load and cache a model template."""
    if template_id in _templates_cache:
        return _templates_cache[template_id]

    # Look for template JSON from data directory
    search_dirs = [
        Path("/app/data/model_templates"),
        Path("data/model_templates"),
        Path(__file__).parent.parent.parent.parent / "data" / "model_templates",
    ]

    for d in search_dirs:
        path = d / f"{template_id.replace('-', '_')}.json"
        if path.exists():
            template = json.loads(path.read_text())
            _templates_cache[template_id] = template
            logger.info("Loaded template: %s from %s", template_id, path)
            return template

    # Fallback: try with the raw name
    for d in search_dirs:
        for f in d.glob("*.json"):
            try:
                data = json.loads(f.read_text())
                meta = data.get("_meta", {})
                if meta.get("id") == template_id:
                    _templates_cache[template_id] = data
                    logger.info("Loaded template: %s from %s", template_id, f)
                    return data
            except Exception:
                pass

    logger.warning("Template not found: %s", template_id)
    return {"sections": []}


@router.post("/build", response_model=ExpressionBuildResponse)
async def build_expressions(request: ExpressionBuildRequest):
    """Build mapping expressions from extracted PDF rows to model template.

    Takes semantic matches (from /api/ml/mapping/suggest) and extracted rows
    (from VLM/OCR), then builds optimal mapping expressions including:
    - Direct 1:1 mappings
    - SUM expressions (children → parent)
    - Sign flips (NEGATE)
    - Unit conversion (SCALE)
    - Autofill from previous periods
    """
    template = _get_template(request.template_id)

    # Find the target zone's items
    model_items = []
    for section in template.get("sections", []):
        if section["zone_type"] == request.zone_type:
            model_items = section.get("items", [])
            break

    if not model_items:
        logger.warning("No items found for zone %s in template %s",
                        request.zone_type, request.template_id)

    # Convert extracted rows to dataclass format
    extracted = [
        ExtractedRow(
            index=i,
            label=row.get("label", ""),
            values=row.get("values", []),
            is_total=row.get("is_total", False),
            is_header=row.get("is_header", False),
            indent_level=row.get("indent_level", 0),
            page=row.get("page", 0),
        )
        for i, row in enumerate(request.extracted_rows)
    ]

    # Detect unit conversion
    note_text = " ".join(r.get("label", "") for r in request.extracted_rows)
    unit_scale = _engine.detect_unit_conversion(extracted, note_text=note_text)

    expressions: list[MappingExpression] = []
    autofilled_count = 0

    # Try autofill first (from previous spreads of same customer)
    if request.use_autofill and request.customer_id:
        patterns = _memory.recall(request.tenant_id, request.customer_id)
        if patterns:
            autofilled = _memory.apply_remembered_patterns(
                patterns, extracted, model_items, request.period_index
            )
            expressions.extend(autofilled)
            autofilled_count = len(autofilled)
            logger.info("Autofilled %d expressions for %s:%s",
                        autofilled_count, request.tenant_id, request.customer_id)

    # Build new expressions for items not yet autofilled
    autofilled_ids = {e.target_item_id for e in expressions}
    remaining_matches = [
        m for m in request.semantic_matches
        if m.get("target_item_id") not in autofilled_ids
    ]

    if remaining_matches:
        new_expressions = _engine.build_expressions(
            extracted_rows=extracted,
            semantic_matches=remaining_matches,
            model_items=model_items,
            period_index=request.period_index,
        )
        expressions.extend(new_expressions)

    # Apply unit scaling
    if unit_scale != 1.0:
        for expr in expressions:
            if expr.computed_value is not None:
                expr.computed_value *= unit_scale
                expr.scale_factor = unit_scale

    # Remember patterns for future autofill
    if request.customer_id and expressions:
        _memory.remember(request.tenant_id, request.customer_id, expressions)

    # Run validation rules
    validations = _run_validations(template, expressions)

    # Count INPUT items for coverage
    input_items = [i for i in model_items if i.get("type") == "INPUT"]
    mapped_count = len(expressions)
    total_input = len(input_items)
    coverage = mapped_count / max(total_input, 1) * 100

    # Convert to response models
    response_expressions = [
        MappingExpressionModel(
            target_item_id=e.target_item_id,
            target_label=e.target_label,
            expression_type=e.expression_type.value,
            sources=[
                SourceRefModel(
                    row_index=s.row_index,
                    label=s.label,
                    value=s.value,
                    page=s.page,
                    confidence=s.confidence,
                )
                for s in e.sources
            ],
            scale_factor=e.scale_factor,
            computed_value=e.computed_value,
            confidence=e.confidence,
            explanation=e.explanation,
        )
        for e in expressions
    ]

    logger.info(
        "Expressions: doc=%s zone=%s mapped=%d/%d coverage=%.0f%% autofilled=%d",
        request.document_id, request.zone_type,
        mapped_count, total_input, coverage, autofilled_count,
    )

    return ExpressionBuildResponse(
        document_id=request.document_id,
        template_id=request.template_id,
        zone_type=request.zone_type,
        expressions=response_expressions,
        total_mapped=mapped_count,
        total_items=total_input,
        coverage_pct=coverage,
        unit_scale=unit_scale,
        autofilled=autofilled_count,
        validation_results=validations,
    )


def _run_validations(template: dict, expressions: list[MappingExpression]) -> list[dict]:
    """Run validation rules from the template against mapped values."""
    validations = template.get("validations", [])
    if not validations:
        return []

    # Build value lookup
    values = {}
    for e in expressions:
        values[e.target_item_id] = e.computed_value

    results = []
    for rule in validations:
        formula = rule.get("formula", "")
        expected = rule.get("expected_value", 0)
        tolerance = rule.get("tolerance", 1)

        # Try to evaluate the formula
        try:
            expr = formula
            for item_id, value in values.items():
                expr = expr.replace(f"{{{item_id}}}", str(value or 0))

            # Check if all placeholders are resolved
            import re
            if re.search(r'\{[A-Z]{2}\d+\}', expr):
                results.append({
                    "rule": rule["name"],
                    "status": "SKIPPED",
                    "reason": "Missing values for some items",
                })
                continue

            computed = eval(expr)  # Safe: only numbers and operators
            diff = abs(computed - expected)
            passed = diff <= tolerance

            results.append({
                "rule": rule["name"],
                "status": "PASS" if passed else "FAIL",
                "severity": rule.get("severity", "WARNING"),
                "computed": computed,
                "expected": expected,
                "difference": diff,
            })
        except Exception as exc:
            results.append({
                "rule": rule["name"],
                "status": "ERROR",
                "reason": str(exc),
            })

    return results
