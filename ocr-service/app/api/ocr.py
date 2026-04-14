"""POST /api/ocr/extract — Smart routing: Native PDF → direct extract, Scanned → VLM/OCR."""

import logging
import time
from typing import Iterable

from fastapi import APIRouter, Request
from fastapi import HTTPException
from pydantic import BaseModel, Field

from app.api.errors import ocr_failure, storage_error, pdf_corrupt, pdf_password_protected, MLError
from app.config import settings
from app.services.image_preprocessor import PreprocessOptions, preprocess_image

router = APIRouter()
logger = logging.getLogger("ocr-service.api.ocr")


# --- Request / Response models ---

class OcrPreprocessOverrides(BaseModel):
    enable_despeckle_low: bool | None = None
    enable_despeckle_high: bool | None = None
    enable_deskew: bool | None = None
    enable_watermark_removal: bool | None = None


class OcrRequest(BaseModel):
    document_id: str
    storage_path: str
    language: str = "en"
    pages: list[int] | None = None
    password: str | None = None
    preprocess: OcrPreprocessOverrides | None = None


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
    errors: list[MLError] = Field(default_factory=list)


class OcrLanguagesResponse(BaseModel):
    supported: list[str]
    default: str
    auto_enabled: bool


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
    preprocess_options = _build_preprocess_options(request.preprocess)

    # Download document
    storage = getattr(http_request.app.state, "storage", None)
    if storage is None:
        raise HTTPException(status_code=502, detail="Storage subsystem unavailable")
    try:
        pdf_bytes = await storage.download(request.storage_path)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail=f"File not found: {request.storage_path}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Storage error: {exc}")

    # ─── Check for Word/Excel documents ───
    from app.utils.document_converter import is_docx, is_xlsx, extract_docx_text, extract_xlsx_text

    if is_docx(pdf_bytes) or is_xlsx(pdf_bytes):
        try:
            doc_pages = extract_docx_text(pdf_bytes) if is_docx(pdf_bytes) else extract_xlsx_text(pdf_bytes)
            doc_blocks = [
                OcrTextBlock(text=p.text, confidence=1.0, bbox=[0.0, 0.0, 1.0, 1.0], page=p.page)
                for p in doc_pages if p.text.strip()
            ]
            full_text = "\n".join(b.text for b in doc_blocks)
            processing_ms = int((time.time() - start) * 1000)
            doc_type = "docx" if is_docx(pdf_bytes) else "xlsx"
            logger.info("Document: doc=%s type=%s pages=%d time=%dms", request.document_id, doc_type, len(doc_pages), processing_ms)
            return OcrResponse(
                document_id=request.document_id,
                total_pages=len(doc_pages),
                pages_processed=len(doc_pages),
                pages_failed=0,
                text_blocks=doc_blocks,
                full_text=full_text,
                processing_time_ms=processing_ms,
                backend="native",
                pdf_type=doc_type,
            )
        except Exception as exc:
            return OcrResponse(
                document_id=request.document_id,
                total_pages=0, pages_processed=0, pages_failed=0,
                text_blocks=[], full_text="",
                processing_time_ms=int((time.time() - start) * 1000),
                errors=[ocr_failure(f"Document conversion failed: {exc}")],
            )

    # ─── Smart routing: detect PDF type ───
    from app.utils.pdf_utils import has_embedded_text, get_page_types, extract_native_text, pdf_to_images, PdfPasswordError

    selected_language = _resolve_language(
        requested_language=request.language,
        pdf_bytes=pdf_bytes,
        request_pages=request.pages,
        password=request.password,
        http_request=http_request,
    )

    try:
        is_native = has_embedded_text(pdf_bytes, password=request.password)
        page_types = get_page_types(pdf_bytes, password=request.password) if not is_native else None
    except PdfPasswordError as exc:
        return OcrResponse(
            document_id=request.document_id,
            total_pages=0, pages_processed=0, pages_failed=0,
            text_blocks=[], full_text="",
            processing_time_ms=int((time.time() - start) * 1000),
            errors=[pdf_password_protected(detail=str(exc))],
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
        except PdfPasswordError as exc:
            return OcrResponse(
                document_id=request.document_id,
                total_pages=0, pages_processed=0, pages_failed=0,
                text_blocks=[], full_text="",
                processing_time_ms=int((time.time() - start) * 1000),
                errors=[pdf_password_protected(detail=str(exc))],
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
                img = preprocess_image(img, preprocess_options)
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
            from app.ml.paddle_ocr import PaddleOCREngine

            engine = PaddleOCREngine.get_instance(lang=selected_language, use_gpu=settings.use_gpu)
            if engine:
                for page_num, img in page_images:
                    try:
                        img = preprocess_image(img, preprocess_options)
                        page_result = engine.extract_page(img, page_num)
                        blocks = page_result.text_blocks
                        for block in blocks:
                            all_blocks.append(OcrTextBlock(
                                text=block.text,
                                confidence=block.confidence,
                                bbox=[
                                    block.bbox.x,
                                    block.bbox.y,
                                    block.bbox.width,
                                    block.bbox.height,
                                ],
                                page=block.page,
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
        "OCR: doc=%s type=%s backend=%s lang=%s pages=%d/%d blocks=%d time=%dms",
        request.document_id, pdf_type, backend, selected_language, pages_processed, total_pages,
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


@router.get("/languages", response_model=OcrLanguagesResponse)
def supported_languages() -> OcrLanguagesResponse:
    supported = sorted(set(settings.supported_language_list))
    return OcrLanguagesResponse(
        supported=supported,
        default=settings.lang,
        auto_enabled=settings.enable_language_auto_detect,
    )


def _build_preprocess_options(overrides: OcrPreprocessOverrides | None) -> PreprocessOptions:
    return PreprocessOptions(
        enable_despeckle_low=(
            overrides.enable_despeckle_low
            if overrides and overrides.enable_despeckle_low is not None
            else settings.enable_despeckle_low
        ),
        enable_despeckle_high=(
            overrides.enable_despeckle_high
            if overrides and overrides.enable_despeckle_high is not None
            else settings.enable_despeckle_high
        ),
        enable_deskew=(
            overrides.enable_deskew
            if overrides and overrides.enable_deskew is not None
            else settings.enable_deskew
        ),
        enable_watermark_removal=(
            overrides.enable_watermark_removal
            if overrides and overrides.enable_watermark_removal is not None
            else settings.enable_watermark_removal
        ),
    )


def _resolve_language(
    requested_language: str,
    pdf_bytes: bytes,
    request_pages: list[int] | None,
    password: str | None,
    http_request: Request,
) -> str:
    supported = set(settings.supported_language_list)
    requested = (requested_language or settings.lang).lower().strip()

    if requested != "auto":
        if requested in supported:
            return requested
        logger.warning("Unsupported language '%s', falling back to %s", requested, settings.lang)
        return settings.lang

    if not settings.enable_language_auto_detect:
        return settings.lang

    from app.utils.pdf_utils import extract_native_text, pdf_to_images, PdfPasswordError

    try:
        sampled_pages = request_pages[:1] if request_pages else [0]
        native = extract_native_text(pdf_bytes, pages=sampled_pages, password=password)
        sample_text = "\n".join(text for _, text in native if text.strip())
        guessed = _detect_language_from_text(sample_text, supported)
        if guessed:
            return guessed

        if not settings.use_vlm:
            images = pdf_to_images(pdf_bytes, pages=sampled_pages, password=password)
            if images:
                guessed = _detect_language_with_paddle(images[0][1], supported)
                if guessed:
                    return guessed
    except PdfPasswordError:
        return settings.lang
    except Exception as exc:
        logger.debug("Auto language detection failed, using default: %s", exc)

    return settings.lang


def _detect_language_from_text(text: str, supported: set[str]) -> str | None:
    if not text.strip():
        return None

    arabic_chars = sum(1 for ch in text if "\u0600" <= ch <= "\u06FF")
    cjk_chars = sum(1 for ch in text if "\u4E00" <= ch <= "\u9FFF")
    french_accents = sum(1 for ch in text if ch in "éèêëàâîïôùûçÉÈÊËÀÂÎÏÔÙÛÇ")
    latin_chars = sum(1 for ch in text if ch.isascii() and ch.isalpha())

    if arabic_chars > 5 and "ar" in supported:
        return "ar"
    if cjk_chars > 5 and "zh" in supported:
        return "zh"
    if french_accents > 3 and "fr" in supported:
        return "fr"
    if latin_chars > 10 and "en" in supported:
        return "en"
    return None


def _detect_language_with_paddle(image, supported: Iterable[str]) -> str | None:
    from app.ml.paddle_ocr import PaddleOCREngine

    best_lang: str | None = None
    best_score = -1.0

    for lang in supported:
        try:
            engine = PaddleOCREngine.get_instance(lang=lang, use_gpu=settings.use_gpu)
            result = engine.extract_page(image, page_num=0)
            if not result.text_blocks:
                continue
            avg_conf = sum(block.confidence for block in result.text_blocks) / len(result.text_blocks)
            if avg_conf > best_score:
                best_score = avg_conf
                best_lang = lang
        except Exception:
            continue

    return best_lang
