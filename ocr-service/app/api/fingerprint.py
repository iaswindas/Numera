"""POST /api/ocr/fingerprint/generate — STGH document fingerprint generation."""

import time

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.api.models import DetectedTable

router = APIRouter()


class GeneratedFingerprint(BaseModel):
    hash: str
    embedding: list[float]
    page_idx: int
    node_count: int
    created_at: str
    table_ids: list[str] = []


class FingerprintGenerateRequest(BaseModel):
    document_id: str
    tables: list[DetectedTable]


class FingerprintGenerateResponse(BaseModel):
    document_id: str
    fingerprints: list[GeneratedFingerprint]
    total_pages: int
    processing_time_ms: int
    algorithm: str = "stgh"


@router.post("/generate", response_model=FingerprintGenerateResponse)
async def generate_fingerprints(request: FingerprintGenerateRequest, http_request: Request):
    start = time.time()
    fingerprinter = getattr(http_request.app.state, "stgh_fingerprinter", None)
    if fingerprinter is None:
        raise HTTPException(status_code=503, detail="STGH fingerprinting is disabled")

    fingerprints = fingerprinter.fingerprint_document(request.tables)
    payload = [
        GeneratedFingerprint(
            hash=fingerprint.hash,
            embedding=fingerprint.embedding.tolist(),
            page_idx=fingerprint.page_idx,
            node_count=fingerprint.node_count,
            created_at=fingerprint.created_at,
            table_ids=fingerprint.table_ids,
        )
        for fingerprint in fingerprints
    ]

    return FingerprintGenerateResponse(
        document_id=request.document_id,
        fingerprints=payload,
        total_pages=len(payload),
        processing_time_ms=int((time.time() - start) * 1000),
    )