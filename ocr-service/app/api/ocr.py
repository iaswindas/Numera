"""POST /api/ocr/extract — Smart routing: Native PDF → direct extract, Scanned → VLM/OCR."""

import logging
import time

from fastapi import APIRouter, Request
from pydantic import BaseModel

from app.api.errors import ocr_failure, storage_error, pdf_corrupt, pdf_password_protected, MLError

router = APIRouter()
logger = logging.getLogger("ocr-service.api.ocr")


# --- Request / Response models ---

class OcrRequest(BaseModel):
    document_id: str
    storage_path: str
    language: str = "en"
    pages: list[int] | None = None
    password: str | None = None


class OcrTextBlock(BaseModel):
    text: str
    confidence: float
    bbox: list[float]
    page: int


class OcrResponse(BaseModel):
    document_id: str
    total_pages: int
    pages_processed: int
    pages_failed: int
    text_blocks: list[OcrTextBlock]
    full_text: str
    processing_time_ms: int
    backend: str = "native"  # "native", "qwen3vl", "paddleocr"
    pdf_type: str = "native"  # "native", "scanned", "mixed"
    errors: list[MLError] = []


# --- Endpoint ---

@router.post("/extract", response_model=OcrResponse)
async def extract_text(request: OcrRequest, http_request: Request):
    """Extract text from a PDF document.

    Smart routing:
    1. Check if PDF has embedded text (native/digital)
    2. If native → extract text directly (PyMuPDF, no ML cost)
    3. If scanned → use VLM/OCR for full processing
    4. If mixed → per-page routing
    """
    start = time.time()
    vlm = getattr(http_request.app.state, "vlm_processor", None)
    errors: list[MLError] = []

    # Download document
    storage = http_request.app.state.storage
    try:
        pdf_bytes = storage.download(request.storage_path)
    except FileNotFoundError:
        return OcrResponse(
            document_id=request.document_id,
            total_pages=0, pages_processed=0, pages_failed=0,
            text_blocks=[], full_text="",
            processing_time_ms=int((time.time() - start) * 1000),
            errors=[storage_error(f"File not found: {request.storage_path}")],
        )
    except Exception as exc:
        return OcrResponse(
            document_id=request.document_id,
            total_pages=0, pages_processed=0, pages_failed=0,
            text_blocks=[], full_text="",
            processing_time_ms=int((time.time() - start) * 1000),
            errors=[storage_error(str(exc))],
        )

    # ─── Smart routing: detect PDF type ───
    from app.utils.pdf_utils import has_embedded_text, get_page_types, extract_native_text, pdf_to_images, PdfPasswordError

    try:
        is_native = has_embedded_text(pdf_bytes, password=request.password)
        page_types = get_page_types(pdf_bytes, password=request.password) if not is_native else None
    except PdfPasswordError as exc:
        return OcrResponse(
            document_id=request.document_id,
            total_pages=0, pages_processed=0, pages_failed=0,
            text_blocks=[], full_text="",
            processing_time_ms=int((time.time() - start) * 1000),
            errors=[pdf_password_protected()],
        )
    pdf_type = "native" if is_native else "scanned"

    all_blocks: list[OcrTextBlock] = []
    pages_processed = 0
    pages_failed = 0
    backend = "native"

    if is_native:
        # ─── NATIVE PDF: extract directly (no ML cost!) ───
        try:
            page_texts = extract_native_text(pdf_bytes, pages=request.pages, password=request.password)
            total_pages = len(page_texts)

            for page_num, text in page_texts:
                if text.strip():
                    all_blocks.append(OcrTextBlock(
                        text=text.strip(),
                        confidence=1.0,  # Native text is 100% accurate
                        bbox=[0.0, 0.0, 1.0, 1.0],
                        page=page_num,
                    ))
                    pages_processed += 1
                else:
                    pages_processed += 1  # Empty page is still processed

            backend = "native"
            logger.info("Native PDF: extracted %d pages directly", total_pages)
        except Exception as exc:
            errors.append(pdf_corrupt(f"Native extraction failed: {exc}"))
            # Fall through to VLM/OCR
            is_native = False

    if not is_native:
        # ─── SCANNED/MIXED PDF: use VLM or PaddleOCR ───
        try:
            page_images = pdf_to_images(pdf_bytes, password=request.password)
        except PdfPasswordError:
            return OcrResponse(
                document_id=request.document_id,
                total_pages=0, pages_processed=0, pages_failed=0,
                text_blocks=[], full_text="",
                processing_time_ms=int((time.time() - start) * 1000),
                errors=[pdf_password_protected()],
            )
        except Exception as exc:
            return OcrResponse(
                document_id=request.document_id,
                total_pages=0, pages_processed=0, pages_failed=0,
                text_blocks=[], full_text="",
                processing_time_ms=int((time.time() - start) * 1000),
                errors=[pdf_corrupt(f"Failed to convert PDF: {exc}")],
            )

        if request.pages:
            page_images = [(n, img) for n, img in page_images if n in request.pages]

        total_pages = len(page_images)

        # For mixed PDFs, check which pages need OCR
        native_pages = set()
        if page_types:
            for pt in page_types:
                if pt["type"] == "native":
                    native_pages.add(pt["page"])
        if native_pages:
            pdf_type = "mixed"

        if vlm and vlm.is_loaded:
            backend = "qwen3vl"
            for page_num, img in page_images:
                # Skip pages that have native text (in mixed PDFs)
                if page_num in native_pages:
                    native_text = extract_native_text(pdf_bytes, pages=[page_num], password=request.password)
                    if native_text and native_text[0][1].strip():
                        all_blocks.append(OcrTextBlock(
                            text=native_text[0][1].strip(),
                            confidence=1.0,
                            bbox=[0.0, 0.0, 1.0, 1.0],
                            page=page_num,
                        ))
                        pages_processed += 1
                        continue

                try:
                    from PIL import Image
                    if not isinstance(img, Image.Image):
                        img = Image.fromarray(img)

                    text = vlm.extract_text(img)
                    if text:
                        all_blocks.append(OcrTextBlock(
                            text=text,
                            confidence=0.95,
                            bbox=[0.0, 0.0, 1.0, 1.0],
                            page=page_num,
                        ))
                    pages_processed += 1
                except Exception as exc:
                    pages_failed += 1
                    errors.append(ocr_failure(f"Page {page_num}: {exc}", page=page_num))
        else:
            # PaddleOCR fallback
            backend = "paddleocr"
            engine = getattr(http_request.app.state, "ocr_engine", None)
            if engine:
                for page_num, img in page_images:
                    try:
                        blocks = engine.extract_page(img, page_num, request.language)
                        for block in blocks:
                            all_blocks.append(OcrTextBlock(
                                text=block["text"],
                                confidence=block.get("confidence", 0.0),
                                bbox=block.get("bbox", [0, 0, 1, 1]),
                                page=page_num,
                            ))
                        pages_processed += 1
                    except Exception as exc:
                        pages_failed += 1
                        errors.append(ocr_failure(f"Page {page_num}: {exc}", page=page_num))

    full_text = "\n".join(b.text for b in all_blocks)
    processing_ms = int((time.time() - start) * 1000)

    if not is_native:
        total_pages = len(page_images) if not is_native else len(page_texts)

    logger.info(
        "OCR: doc=%s type=%s backend=%s pages=%d/%d blocks=%d time=%dms",
        request.document_id, pdf_type, backend, pages_processed, total_pages,
        len(all_blocks), processing_ms,
    )

    return OcrResponse(
        document_id=request.document_id,
        total_pages=total_pages,
        pages_processed=pages_processed,
        pages_failed=pages_failed,
        text_blocks=all_blocks,
        full_text=full_text,
        processing_time_ms=processing_ms,
        backend=backend,
        pdf_type=pdf_type,
        errors=errors,
    )
