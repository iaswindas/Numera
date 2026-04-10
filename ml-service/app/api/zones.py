"""POST /api/ml/zones/classify — Zone classification with VLM pre-labels + A/B testing."""

import logging
import time
from typing import Literal

from fastapi import APIRouter, Request

from app.api.models import (
    ClassifiedZone,
    ZoneClassificationRequest,
    ZoneClassificationResponse,
    ZoneType,
)
from app.ml.zone_classifier import LayoutLMZoneClassifier, classify_by_keywords

router = APIRouter()
logger = logging.getLogger("ml-service.api.zones")

# Valid VLM zone types that map directly to our ZoneType enum
_VLM_ZONE_MAP = {zt.value: zt for zt in ZoneType}


@router.post("/classify", response_model=ZoneClassificationResponse)
async def classify_zones(request: ZoneClassificationRequest, http_request: Request):
    """Classify detected tables into financial statement zones.

    Priority order:
    1. VLM pre-classification (if table came from Qwen3-VL, zone is already known)
    2. Keyword heuristic (fast, high precision)
    3. LayoutLM ML classifier (fallback for ambiguous cases)

    When OCR backend is VLM, zone classification comes for FREE —
    no separate ML call needed. This endpoint still validates and
    can override low-confidence VLM zones.
    """
    start_time = time.time()

    zone_classifier: LayoutLMZoneClassifier = http_request.app.state.zone_classifier

    zones: list[ClassifiedZone] = []

    for table in request.tables:
        raw_text = " ".join(cell.text for cell in table.cells)

        # ─── 1. Check for VLM pre-classification ───
        vlm_zone = getattr(table, "vlm_zone_type", None)
        vlm_conf = getattr(table, "vlm_zone_confidence", None) or 0.0
        vlm_label = getattr(table, "vlm_zone_label", None)

        if vlm_zone and vlm_zone in _VLM_ZONE_MAP and vlm_conf >= 0.7:
            # VLM already classified this table — trust it
            # Cross-validate with heuristic for extra confidence
            heur_type, heur_conf = classify_by_keywords(raw_text)
            vlm_type = _VLM_ZONE_MAP[vlm_zone]

            if heur_type == vlm_type:
                # VLM and heuristic agree → high confidence
                final_conf = max(vlm_conf, heur_conf, 0.92)
                method = "VLM"
            elif vlm_conf >= 0.85:
                # VLM is very confident, heuristic disagrees → trust VLM
                final_conf = vlm_conf
                method = "VLM"
            else:
                # VLM moderate confidence, heuristic disagrees → use heuristic
                vlm_type = heur_type
                final_conf = heur_conf
                method = "HEURISTIC"

            zones.append(ClassifiedZone(
                table_id=table.table_id,
                zone_type=vlm_type,
                zone_label=vlm_label or vlm_type.value.replace("_", " ").title(),
                confidence=round(final_conf, 4),
                classification_method=method,
                detected_periods=table.detected_periods or [],
                detected_currency=table.detected_currency,
                detected_unit=table.detected_unit,
                model_version="qwen3vl",
            ))
            continue

        # ─── 2. Heuristic classification ───
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

        meth: Literal["HEURISTIC", "ML", "COMBINED", "VLM"] = "HEURISTIC"
        final_type = heur_type
        final_conf = heur_conf
        model_version = "heuristic"

        # ─── 3. LayoutLM (if heuristic is low confidence) ───
        if heur_conf < 0.80 and zone_classifier.is_loaded:
            ml_type, ml_conf, ml_version = zone_classifier.classify(raw_text, bboxes)
            model_version = ml_version
            if heur_type == ml_type:
                meth = "COMBINED"
                final_conf = max(heur_conf, ml_conf)
            elif ml_conf > heur_conf:
                meth = "ML"
                final_type = ml_type
                final_conf = ml_conf

        zones.append(
            ClassifiedZone(
                table_id=table.table_id,
                zone_type=final_type,
                zone_label=final_type.value.replace("_", " ").title(),
                confidence=round(final_conf, 4),
                classification_method=meth,
                detected_periods=getattr(table, "detected_periods", []) or [],
                detected_currency=getattr(table, "detected_currency", None),
                detected_unit=getattr(table, "detected_unit", None),
                model_version=model_version,
            )
        )

    processing_time_ms = int((time.time() - start_time) * 1000)
    logger.info(
        "Zone classification: doc=%s zones=%d time=%dms",
        request.document_id, len(zones), processing_time_ms,
    )

    return ZoneClassificationResponse(
        document_id=request.document_id,
        zones=zones,
        processing_time_ms=processing_time_ms,
    )
