"""POST /api/ml/pipeline/process — Full document processing pipeline.

Orchestrates the end-to-end ML pipeline:
  1. OCR extraction (calls ocr-service)
  2. Table detection (calls ocr-service)
  3. Zone classification (local)
  4. Mapping suggestion (local)

Supports graceful degradation: if any step fails, returns partial results.
"""

import logging
import time
from typing import Optional

import httpx
from fastapi import APIRouter, Header, Request

from app.api.errors import pipeline_step_failure, pipeline_timeout
from app.api.models import (
    ClassifiedZone,
    DetectedTable,
    MappedRow,
    MappingSummary,
    MLError,
    SourceRow,
    TargetLineItem,
    ZoneType,
)
from app.api.pipeline_models import (
    PipelineRequest,
    PipelineResponse,
    PipelineStepResult,
    ProcessingStatus,
)
from app.config import settings

router = APIRouter()
logger = logging.getLogger("ml-service.api.pipeline")


@router.post("/process", response_model=PipelineResponse)
async def process_document(
    request: PipelineRequest,
    http_request: Request,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID"),
    authorization: Optional[str] = Header(None, alias="Authorization"),
):
    """Process a document through the full ML pipeline.

    Calls each step sequentially, collecting results. If a step fails,
    subsequent steps may be skipped but partial results are still returned.
    """
    start_time = time.time()
    tenant_id = x_tenant_id or request.tenant_id

    steps: list[PipelineStepResult] = []
    all_errors: list[MLError] = []
    status = ProcessingStatus.UPLOADED

    tables: list[DetectedTable] = []
    zones: list[ClassifiedZone] = []
    mappings: list[MappedRow] = []
    mapping_summary: MappingSummary | None = None
    pages_processed = 0
    model_version = "production"

    ocr_service_url = settings.ocr_service_url
    timeout = settings.pipeline_timeout_seconds

    async with httpx.AsyncClient(timeout=timeout) as client:

        # ─── Step 1: OCR Extraction ───
        if not request.skip_ocr:
            step_start = time.time()
            try:
                resp = await client.post(
                    f"{ocr_service_url}/api/ocr/extract",
                    json={
                        "document_id": request.document_id,
                        "storage_path": request.storage_path,
                        "language": request.language,
                    },
                )
                resp.raise_for_status()
                ocr_data = resp.json()
                pages_processed = ocr_data.get("total_pages", 0)
                status = ProcessingStatus.OCR_COMPLETE
                steps.append(PipelineStepResult(
                    step="ocr_extract",
                    status="success",
                    duration_ms=int((time.time() - step_start) * 1000),
                    data={"pages": pages_processed},
                ))
            except httpx.TimeoutException:
                err = pipeline_timeout("ocr_extract", timeout)
                all_errors.append(err)
                steps.append(PipelineStepResult(
                    step="ocr_extract", status="failed",
                    duration_ms=int((time.time() - step_start) * 1000),
                    errors=[err],
                ))
            except Exception as exc:
                err = pipeline_step_failure("ocr_extract", str(exc))
                all_errors.append(err)
                steps.append(PipelineStepResult(
                    step="ocr_extract", status="failed",
                    duration_ms=int((time.time() - step_start) * 1000),
                    errors=[err],
                ))
        else:
            steps.append(PipelineStepResult(
                step="ocr_extract", status="skipped", duration_ms=0,
            ))

        # ─── Step 2: Table Detection ───
        if not request.skip_tables and status >= ProcessingStatus.OCR_COMPLETE:
            step_start = time.time()
            try:
                resp = await client.post(
                    f"{ocr_service_url}/api/ocr/tables/detect",
                    json={
                        "document_id": request.document_id,
                        "storage_path": request.storage_path,
                    },
                )
                resp.raise_for_status()
                table_data = resp.json()
                # Parse tables from response
                raw_tables = table_data.get("tables", [])
                for t in raw_tables:
                    try:
                        tables.append(DetectedTable(**t))
                    except Exception:
                        logger.warning("Failed to parse table: %s", t.get("table_id", "?"))

                status = ProcessingStatus.TABLES_DETECTED
                steps.append(PipelineStepResult(
                    step="table_detect",
                    status="success",
                    duration_ms=int((time.time() - step_start) * 1000),
                    data={"tables_found": len(tables)},
                ))
            except Exception as exc:
                err = pipeline_step_failure("table_detect", str(exc))
                all_errors.append(err)
                steps.append(PipelineStepResult(
                    step="table_detect", status="failed",
                    duration_ms=int((time.time() - step_start) * 1000),
                    errors=[err],
                ))
        else:
            steps.append(PipelineStepResult(
                step="table_detect", status="skipped", duration_ms=0,
            ))

        # ─── Step 3: Zone Classification (local) ───
        if not request.skip_zones and tables:
            step_start = time.time()
            try:
                zone_classifier = http_request.app.state.zone_classifier
                from app.ml.zone_classifier import classify_by_keywords

                for table in tables:
                    raw_text = " ".join(cell.text for cell in table.cells)
                    bboxes = [
                        [
                            int(cell.bbox.x * 1000),
                            int(cell.bbox.y * 1000),
                            int((cell.bbox.x + cell.bbox.width) * 1000),
                            int((cell.bbox.y + cell.bbox.height) * 1000),
                        ]
                        for cell in table.cells
                    ]

                    heur_type, heur_conf = classify_by_keywords(raw_text)
                    final_type, final_conf, zone_version = heur_type, heur_conf, "heuristic"
                    meth = "HEURISTIC"

                    if heur_conf < 0.80 and zone_classifier.is_loaded:
                        ml_type, ml_conf, ml_ver = zone_classifier.classify(raw_text, bboxes)
                        zone_version = ml_ver
                        if heur_type == ml_type:
                            meth = "COMBINED"
                            final_conf = max(heur_conf, ml_conf)
                        elif ml_conf > heur_conf:
                            meth = "ML"
                            final_type = ml_type
                            final_conf = ml_conf

                    zones.append(ClassifiedZone(
                        table_id=table.table_id,
                        zone_type=final_type,
                        zone_label=final_type.value.replace("_", " ").title(),
                        confidence=round(final_conf, 4),
                        classification_method=meth,
                        detected_periods=table.detected_periods,
                        detected_currency=table.detected_currency,
                        detected_unit=table.detected_unit,
                        model_version=zone_version,
                    ))

                status = ProcessingStatus.ZONES_CLASSIFIED
                steps.append(PipelineStepResult(
                    step="zone_classify",
                    status="success",
                    duration_ms=int((time.time() - step_start) * 1000),
                    data={"zones": len(zones)},
                ))
            except Exception as exc:
                err = pipeline_step_failure("zone_classify", str(exc))
                all_errors.append(err)
                steps.append(PipelineStepResult(
                    step="zone_classify", status="failed",
                    duration_ms=int((time.time() - step_start) * 1000),
                    errors=[err],
                ))
        else:
            steps.append(PipelineStepResult(
                step="zone_classify", status="skipped", duration_ms=0,
            ))

        # ─── Step 4: Mapping Suggestion (local) ───
        if not request.skip_mapping and zones:
            step_start = time.time()
            try:
                matcher = http_request.app.state.semantic_matcher

                # Resolve client model if available
                resolver = getattr(http_request.app.state, "client_model_resolver", None)
                if tenant_id and resolver:
                    client_matcher = await resolver.resolve(tenant_id, http_request.app.state)
                    if client_matcher:
                        matcher = client_matcher

                if request.taxonomy_path:
                    matcher.load_taxonomy(request.taxonomy_path)

                # Build source rows from tables + zones
                source_rows = _build_source_rows(tables, zones)

                target_items: list[TargetLineItem] = []
                auth_token = request.auth_token or authorization

                if request.template_id:
                    target_items = await _load_target_items(
                        client=client,
                        template_id=request.template_id,
                        backend_url=settings.backend_url,
                        auth_token=auth_token,
                    )

                if source_rows and target_items:
                    mappings, model_version = matcher.match(source_rows, target_items)

                    high = sum(1 for m in mappings if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "HIGH")
                    medium = sum(1 for m in mappings if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "MEDIUM")
                    low = sum(1 for m in mappings if m.suggested_mappings and m.suggested_mappings[0].confidence_level.value == "LOW")
                    unmapped = sum(1 for m in mappings if not m.suggested_mappings)

                    mapping_summary = MappingSummary(
                        total_source_rows=len(mappings),
                        high_confidence=high, medium_confidence=medium,
                        low_confidence=low, unmapped=unmapped,
                    )

                status = ProcessingStatus.MAPPED
                steps.append(PipelineStepResult(
                    step="mapping_suggest",
                    status="success",
                    duration_ms=int((time.time() - step_start) * 1000),
                    data={"rows_mapped": len(mappings)},
                ))
            except Exception as exc:
                err = pipeline_step_failure("mapping_suggest", str(exc))
                all_errors.append(err)
                steps.append(PipelineStepResult(
                    step="mapping_suggest", status="failed",
                    duration_ms=int((time.time() - step_start) * 1000),
                    errors=[err],
                ))
        else:
            steps.append(PipelineStepResult(
                step="mapping_suggest", status="skipped", duration_ms=0,
            ))

    if all_errors and status == ProcessingStatus.UPLOADED:
        status = ProcessingStatus.ERROR

    total_ms = int((time.time() - start_time) * 1000)
    logger.info(
        "Pipeline: doc=%s status=%s pages=%d tables=%d zones=%d mappings=%d time=%dms",
        request.document_id, status.value, pages_processed,
        len(tables), len(zones), len(mappings), total_ms,
    )

    return PipelineResponse(
        document_id=request.document_id,
        status=status,
        steps=steps,
        total_duration_ms=total_ms,
        pages_processed=pages_processed,
        tables_detected=len(tables),
        zones_classified=len(zones),
        rows_mapped=len(mappings),
        tables=tables,
        zones=zones,
        mappings=mappings,
        mapping_summary=mapping_summary,
        model_version=model_version,
        errors=all_errors,
    )


def _build_source_rows(
    tables: list[DetectedTable], zones: list[ClassifiedZone]
) -> list[SourceRow]:
    """Build SourceRow list from tables + zone classifications."""
    zone_map = {z.table_id: z for z in zones}
    rows: list[SourceRow] = []

    for table in tables:
        zone_info = zone_map.get(table.table_id)
        zone_type = zone_info.zone_type if zone_info else ZoneType.OTHER

        # Find account column
        acct_col = table.account_column or 0

        for cell in table.cells:
            if cell.col_index == acct_col and not cell.is_header and cell.text.strip():
                rows.append(SourceRow(
                    row_id=f"{table.table_id}_r{cell.row_index}",
                    text=cell.text.strip(),
                    value=None,
                    page=table.page_number,
                    coordinates=cell.bbox,
                    zone_type=zone_type,
                ))

    return rows


async def _load_target_items(
    client: httpx.AsyncClient,
    template_id: str,
    backend_url: str,
    auth_token: str | None,
) -> list[TargetLineItem]:
    headers: dict[str, str] = {}
    if auth_token:
        headers["Authorization"] = auth_token if auth_token.lower().startswith("bearer ") else f"Bearer {auth_token}"

    response = await client.get(
        f"{backend_url.rstrip('/')}/api/model-templates/{template_id}",
        headers=headers,
    )
    response.raise_for_status()

    payload = response.json()
    line_items = payload.get("lineItems", [])
    target_items: list[TargetLineItem] = []

    for item in line_items:
        target_items.append(
            TargetLineItem(
                line_item_id=str(item.get("itemCode") or item.get("id") or ""),
                label=str(item.get("label") or ""),
                parent_label=item.get("category"),
                zone_type=_parse_zone_type(item.get("zone")),
                item_type=_parse_item_type(item.get("itemType")),
            )
        )

    return target_items


def _parse_item_type(raw: object) -> str:
    value = str(raw or "INPUT").upper()
    if value in {"INPUT", "FORMULA", "VALIDATION", "CATEGORY"}:
        return value
    return "INPUT"


def _parse_zone_type(raw: object) -> ZoneType:
    value = str(raw or "OTHER").upper()

    if value == ZoneType.BALANCE_SHEET.value:
        return ZoneType.BALANCE_SHEET
    if value == ZoneType.INCOME_STATEMENT.value:
        return ZoneType.INCOME_STATEMENT
    if value == ZoneType.CASH_FLOW.value:
        return ZoneType.CASH_FLOW
    if value == ZoneType.NOTES_FIXED_ASSETS.value:
        return ZoneType.NOTES_FIXED_ASSETS
    if value == ZoneType.NOTES_RECEIVABLES.value:
        return ZoneType.NOTES_RECEIVABLES
    if value == ZoneType.NOTES_DEBT.value:
        return ZoneType.NOTES_DEBT
    if value in {ZoneType.NOTES_OTHER.value, "NOTES", "NOTE"}:
        return ZoneType.NOTES_OTHER

    if "BALANCE" in value:
        return ZoneType.BALANCE_SHEET
    if "INCOME" in value or value == "IS":
        return ZoneType.INCOME_STATEMENT
    if "CASH" in value or value == "CF":
        return ZoneType.CASH_FLOW
    if "FIXED" in value and "NOTE" in value:
        return ZoneType.NOTES_FIXED_ASSETS
    if "RECEIVABLE" in value and "NOTE" in value:
        return ZoneType.NOTES_RECEIVABLES
    if "DEBT" in value and "NOTE" in value:
        return ZoneType.NOTES_DEBT
    if "NOTE" in value:
        return ZoneType.NOTES_OTHER
    return ZoneType.OTHER
