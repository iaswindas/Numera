"""POST /api/ocr/tables/detect — Smart routing: Native → PyMuPDF, Scanned → VLM."""

import logging
import time
import uuid

from fastapi import APIRouter, Request
from fastapi import HTTPException
from pydantic import BaseModel

from app.api.errors import storage_error, table_detection_failure, pdf_corrupt, pdf_password_protected, MLError
from app.api.models import DetectedTable, BoundingBox, TableCell

router = APIRouter()
logger = logging.getLogger("ocr-service.api.tables")


class TableDetectRequest(BaseModel):
    document_id: str
    storage_path: str
    pages: list[int] | None = None
    min_rows: int = 2
    min_cols: int = 2
    password: str | None = None


class TableDetectResponse(BaseModel):
    document_id: str
    total_pages: int
    tables_detected: int
    tables_filtered: int
    tables: list[DetectedTable]
    processing_time_ms: int
    backend: str = "native"
    pdf_type: str = "native"
    errors: list[MLError] = []


@router.post("/detect", response_model=TableDetectResponse)
async def detect_tables(request: TableDetectRequest, http_request: Request):
    """Detect and extract financial tables from a PDF.

    Smart routing:
    1. Native PDF → PyMuPDF find_tables() (fast, no ML cost)
    2. Scanned PDF → VLM full extraction (one call does OCR + tables + zones)
    3. Fallback → PP-Structure
    """
    start = time.time()
    vlm = getattr(http_request.app.state, "vlm_processor", None)
    errors: list[MLError] = []

    # Download
    storage = getattr(http_request.app.state, "storage", None)
    if storage is None:
        raise HTTPException(status_code=502, detail="Storage subsystem unavailable")
    try:
        pdf_bytes = storage.download(request.storage_path)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail=f"File not found: {request.storage_path}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Storage error: {exc}")

    # ─── Smart routing ───
    from app.utils.pdf_utils import has_embedded_text, extract_native_tables, pdf_to_images, PdfPasswordError

    try:
        is_native = has_embedded_text(pdf_bytes, password=request.password)
    except PdfPasswordError as exc:
        return TableDetectResponse(
            document_id=request.document_id,
            total_pages=0, tables_detected=0, tables_filtered=0,
            tables=[], processing_time_ms=int((time.time() - start) * 1000),
            errors=[pdf_password_protected(detail=str(exc))],
        )
    all_tables: list[DetectedTable] = []
    tables_filtered = 0
    total_pages = 0
    backend = "native"
    pdf_type = "native" if is_native else "scanned"

    if is_native:
        # ─── NATIVE PDF: PyMuPDF table extraction (no ML cost!) ───
        try:
            native_tables = extract_native_tables(pdf_bytes, pages=request.pages, password=request.password)
            total_pages = len(set(t.page_number for t in native_tables)) or 1

            for nt in native_tables:
                if len(nt.rows) < request.min_rows or len(nt.headers) < request.min_cols:
                    tables_filtered += 1
                    continue

                # Convert NativeTable → DetectedTable
                table = _native_to_detected(nt, request.document_id)
                all_tables.append(table)

            backend = "native"
            logger.info("Native tables: found %d, filtered %d", len(all_tables), tables_filtered)
        except Exception as exc:
            logger.warning("Native table extraction failed: %s", exc)
            is_native = False  # Fall through to VLM

    if not is_native:
        # ─── SCANNED PDF: VLM or PP-Structure ───
        try:
            page_images = pdf_to_images(pdf_bytes, password=request.password)
        except PdfPasswordError as exc:
            return TableDetectResponse(
                document_id=request.document_id,
                total_pages=0, tables_detected=0, tables_filtered=0,
                tables=[], processing_time_ms=int((time.time() - start) * 1000),
                errors=[pdf_password_protected(detail=str(exc))],
            )
        except Exception as exc:
            return TableDetectResponse(
                document_id=request.document_id,
                total_pages=0, tables_detected=0, tables_filtered=0,
                tables=[], processing_time_ms=int((time.time() - start) * 1000),
                errors=[pdf_corrupt(f"Failed to convert PDF: {exc}")],
            )

        if request.pages:
            page_images = [(n, img) for n, img in page_images if n in request.pages]
        total_pages = len(page_images)

        if vlm and vlm.is_loaded:
            backend = "qwen3vl"
            for page_num, img in page_images:
                try:
                    from PIL import Image
                    if not isinstance(img, Image.Image):
                        img = Image.fromarray(img)

                    vlm_result = vlm.extract_page(img, page_num)
                    page_tables = vlm.build_detected_tables(vlm_result, page_num)

                    for table in page_tables:
                        if table.rows < request.min_rows or table.cols < request.min_cols:
                            tables_filtered += 1
                            continue
                        all_tables.append(table)
                except Exception as exc:
                    errors.append(table_detection_failure(
                        f"Page {page_num}: {exc}", page=page_num
                    ))
        else:
            # PP-Structure fallback
            backend = "paddleocr"
            table_detector = getattr(http_request.app.state, "table_detector", None)
            if table_detector:
                for page_num, img in page_images:
                    try:
                        raw_tables = table_detector.detect_tables(img, page_num)
                        for raw in raw_tables:
                            if raw.rows < request.min_rows or raw.cols < request.min_cols:
                                tables_filtered += 1
                                continue
                            all_tables.append(raw)
                    except Exception as exc:
                        errors.append(table_detection_failure(
                            f"Page {page_num}: {exc}", page=page_num
                        ))

    processing_ms = int((time.time() - start) * 1000)

    logger.info(
        "Tables: doc=%s type=%s backend=%s pages=%d detected=%d filtered=%d time=%dms",
        request.document_id, pdf_type, backend, total_pages,
        len(all_tables), tables_filtered, processing_ms,
    )

    return TableDetectResponse(
        document_id=request.document_id,
        total_pages=total_pages,
        tables_detected=len(all_tables),
        tables_filtered=tables_filtered,
        tables=all_tables,
        processing_time_ms=processing_ms,
        backend=backend,
        pdf_type=pdf_type,
        errors=errors,
    )


def _native_to_detected(nt, document_id: str) -> DetectedTable:
    """Convert a NativeTable (from PyMuPDF) to a DetectedTable API model."""
    num_cols = max(len(nt.headers), max((len(r.values) for r in nt.rows), default=0) + 1)
    cells: list[TableCell] = []

    # Header cells
    for col_idx, header_text in enumerate(nt.headers):
        cells.append(TableCell(
            text=str(header_text),
            bbox=BoundingBox(x=col_idx/num_cols, y=0, width=1/num_cols, height=0.05),
            row_index=0,
            col_index=col_idx,
            is_header=True,
            cell_type="TEXT",
        ))

    # Data rows
    for row_idx, row in enumerate(nt.rows, start=1):
        cells.append(TableCell(
            text=row.label,
            bbox=BoundingBox(x=0, y=row_idx*0.05, width=1/num_cols, height=0.05),
            row_index=row_idx,
            col_index=0,
            is_header=row.is_header,
            cell_type="TEXT",
        ))
        for val_idx, val in enumerate(row.values, start=1):
            val_str = str(val) if val is not None else ""
            cells.append(TableCell(
                text=val_str,
                bbox=BoundingBox(
                    x=val_idx/num_cols, y=row_idx*0.05,
                    width=1/num_cols, height=0.05,
                ),
                row_index=row_idx,
                col_index=val_idx,
                is_header=False,
                cell_type="NUMERIC" if val is not None else "EMPTY",
            ))

    bbox = BoundingBox(
        x=nt.bbox[0], y=nt.bbox[1],
        width=nt.bbox[2] - nt.bbox[0],
        height=nt.bbox[3] - nt.bbox[1],
    )

    return DetectedTable(
        table_id=f"p{nt.page_number}_t{uuid.uuid4().hex[:6]}",
        page_number=nt.page_number,
        bbox=bbox,
        confidence=1.0,  # Native extraction is deterministic
        rows=len(nt.rows) + 1,
        cols=num_cols,
        cells=cells,
        header_rows=[0],
        account_column=0,
        value_columns=list(range(1, num_cols)),
        detected_periods=[],
        detected_currency=None,
        detected_unit=None,
    )
