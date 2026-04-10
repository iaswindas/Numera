"""PDF utilities: image conversion + native text/table extraction.

Supports two paths:
1. Native PDFs (digital, embedded text) → extract text directly, skip VLM/OCR
2. Scanned PDFs (images only) → convert to images for VLM processing

Cost savings: ~80% of financial reports are native PDFs.
"""

import logging
import re
from dataclasses import dataclass, field

import fitz  # PyMuPDF
import numpy as np

logger = logging.getLogger("ocr-service.utils.pdf_utils")


# ─── Password-Protected PDF Support ────────────────────────────


class PdfPasswordError(Exception):
    """Raised when a PDF requires a password that was not provided."""


def open_pdf(pdf_bytes: bytes, password: str | None = None) -> fitz.Document:
    """Open a PDF with optional password decryption.

    Args:
        pdf_bytes: Raw PDF file bytes.
        password: Optional password for encrypted PDFs.

    Returns:
        An open fitz.Document.

    Raises:
        PdfPasswordError: If the PDF is encrypted and no valid password was given.
    """
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")

    if doc.is_encrypted:
        if password:
            if not doc.authenticate(password):
                doc.close()
                raise PdfPasswordError("Invalid password for encrypted PDF")
        else:
            doc.close()
            raise PdfPasswordError("PDF is password-protected; supply a password")

    return doc


# ─── PDF Type Detection ────────────────────────────────────────


def has_embedded_text(pdf_bytes: bytes, sample_pages: int = 3, min_chars: int = 100, password: str | None = None) -> bool:
    """Check if a PDF has embedded (selectable) text.

    Samples the first N pages. If they average 100+ characters, it's a
    native/digital PDF and OCR can be skipped.

    Args:
        pdf_bytes: Raw PDF file bytes.
        sample_pages: Number of pages to sample.
        min_chars: Minimum average chars per page to consider "native".
        password: Optional password for encrypted PDFs.

    Returns:
        True if PDF has embedded text (native), False if scanned.
    """
    doc = open_pdf(pdf_bytes, password)
    total_chars = 0
    pages_checked = min(sample_pages, len(doc))

    for i in range(pages_checked):
        text = doc[i].get_text("text")
        total_chars += len(text.strip())

    doc.close()

    avg_chars = total_chars / max(pages_checked, 1)
    is_native = avg_chars >= min_chars
    logger.info(
        "PDF type detection: %s (avg %d chars/page, sampled %d pages)",
        "NATIVE" if is_native else "SCANNED", avg_chars, pages_checked,
    )
    return is_native


def get_page_types(pdf_bytes: bytes, min_chars: int = 50, password: str | None = None) -> list[dict]:
    """Classify each page as native or scanned.

    For mixed PDFs (some pages native, some scanned), this enables
    per-page routing to save VLM costs.

    Returns:
        List of {"page": int, "type": "native"|"scanned", "char_count": int}
    """
    doc = open_pdf(pdf_bytes, password)
    results = []

    for i in range(len(doc)):
        text = doc[i].get_text("text").strip()
        page_type = "native" if len(text) >= min_chars else "scanned"
        results.append({"page": i, "type": page_type, "char_count": len(text)})

    doc.close()
    return results


# ─── Native Text Extraction ────────────────────────────────────


def extract_native_text(
    pdf_bytes: bytes,
    pages: list[int] | None = None,
    password: str | None = None,
) -> list[tuple[int, str]]:
    """Extract embedded text from native PDF pages.

    Much faster and more accurate than VLM/OCR for digital PDFs.

    Args:
        pdf_bytes: Raw PDF file bytes.
        pages: Specific pages (0-indexed) or None for all.
        password: Optional password for encrypted PDFs.

    Returns:
        List of (page_number, text) tuples.
    """
    doc = open_pdf(pdf_bytes, password)
    results = []

    page_range = pages if pages is not None else range(len(doc))
    for page_num in page_range:
        if page_num >= len(doc):
            continue
        text = doc[page_num].get_text("text")
        results.append((page_num, text))

    doc.close()
    return results


# ─── Native Table Extraction ──────────────────────────────────


@dataclass
class NativeTableRow:
    """A row extracted from a native PDF table."""
    label: str = ""
    values: list[float | None] = field(default_factory=list)
    is_total: bool = False
    is_header: bool = False
    indent_level: int = 0


@dataclass
class NativeTable:
    """A table extracted from a native PDF without OCR."""
    page_number: int = 0
    headers: list[str] = field(default_factory=list)
    rows: list[NativeTableRow] = field(default_factory=list)
    raw_text: str = ""
    bbox: tuple[float, float, float, float] = (0, 0, 1, 1)


def extract_native_tables(
    pdf_bytes: bytes,
    pages: list[int] | None = None,
    password: str | None = None,
) -> list[NativeTable]:
    """Extract tables from native PDFs using PyMuPDF's built-in table finder.

    PyMuPDF 1.23+ has a table extraction API that works on PDFs with
    embedded text. This is 100x faster and more accurate than VLM for
    digital documents.

    Args:
        pdf_bytes: Raw PDF file bytes.
        pages: Specific pages (0-indexed) or None for all.
        password: Optional password for encrypted PDFs.

    Returns:
        List of NativeTable objects.
    """
    doc = open_pdf(pdf_bytes, password)
    all_tables = []

    page_range = pages if pages is not None else range(len(doc))
    for page_num in page_range:
        if page_num >= len(doc):
            continue
        page = doc[page_num]

        try:
            # PyMuPDF table finder (fitz >= 1.23)
            tabs = page.find_tables()
            for tab in tabs:
                raw_data = tab.extract()
                if not raw_data or len(raw_data) < 2:
                    continue  # Skip tables with only headers

                # First row is typically headers
                headers = [str(cell or "").strip() for cell in raw_data[0]]

                rows = []
                for row_data in raw_data[1:]:
                    cells = [str(cell or "").strip() for cell in row_data]
                    if not any(cells):
                        continue  # Skip empty rows

                    label = cells[0] if cells else ""
                    values = []
                    for cell_text in cells[1:]:
                        values.append(_parse_number(cell_text))

                    # Detect totals
                    is_total = bool(re.search(
                        r'\b(total|sub-?total|net|closing|opening)\b',
                        label, re.IGNORECASE,
                    ))

                    # Detect indentation (leading spaces in the original)
                    indent = 0
                    if label != label.lstrip():
                        indent = min((len(label) - len(label.lstrip())) // 2, 3)
                    label = label.strip()

                    rows.append(NativeTableRow(
                        label=label,
                        values=values,
                        is_total=is_total,
                        is_header=not any(v is not None for v in values),
                        indent_level=indent,
                    ))

                # Normalize bbox to 0-1 range
                page_rect = page.rect
                tb = tab.bbox
                bbox = (
                    tb[0] / page_rect.width,
                    tb[1] / page_rect.height,
                    tb[2] / page_rect.width,
                    tb[3] / page_rect.height,
                )

                all_tables.append(NativeTable(
                    page_number=page_num,
                    headers=headers,
                    rows=rows,
                    raw_text="\n".join(
                        f"{r.label}\t{r.values}" for r in rows
                    ),
                    bbox=bbox,
                ))

        except AttributeError:
            # PyMuPDF version doesn't have find_tables() — fall back
            logger.warning("PyMuPDF find_tables() not available (upgrade to >= 1.23)")
        except Exception as exc:
            logger.warning("Table extraction failed on page %d: %s", page_num, exc)

    doc.close()
    logger.info("Native table extraction: found %d tables", len(all_tables))
    return all_tables


def _parse_number(text: str) -> float | None:
    """Parse a financial number from text.

    Handles: 1,234.56  (1,234.56)  -1234  1,234  --  nil  n/a  blank
    """
    if not text or not text.strip():
        return None

    text = text.strip()

    # Skip non-numeric
    if text.lower() in ("", "-", "--", "—", "nil", "n/a", "na", "*"):
        return None

    # Detect negatives: parentheses
    negative = False
    if text.startswith("(") and text.endswith(")"):
        negative = True
        text = text[1:-1]
    elif text.startswith("-"):
        negative = True
        text = text[1:]

    # Remove currency symbols and whitespace
    text = re.sub(r'[£$€¥₹\s]', '', text)

    # Remove commas
    text = text.replace(",", "")

    # Handle percentage
    text = text.rstrip("%")

    try:
        value = float(text)
        return -value if negative else value
    except ValueError:
        return None


# ─── Image Conversion (for scanned PDFs) ──────────────────────


def pdf_to_images(
    pdf_bytes: bytes,
    dpi: int = 300,
    pages: list[int] | None = None,
    password: str | None = None,
) -> list[tuple[int, np.ndarray]]:
    """Convert PDF pages to numpy images.

    Args:
        pdf_bytes: Raw PDF file bytes.
        dpi: Resolution for rendering (default 300 for OCR quality).
        pages: Optional list of 0-indexed page numbers. None = all pages.
        password: Optional password for encrypted PDFs.

    Returns:
        List of (page_number, image_array) tuples. Images are RGB uint8.
    """
    doc = open_pdf(pdf_bytes, password)
    results = []

    page_range = pages if pages is not None else range(len(doc))
    for page_num in page_range:
        if page_num >= len(doc):
            logger.warning("Page %d out of range (document has %d pages)", page_num, len(doc))
            continue
        page = doc[page_num]
        zoom = dpi / 72  # 72 is default PDF DPI
        mat = fitz.Matrix(zoom, zoom)
        pix = page.get_pixmap(matrix=mat)

        # Convert pixmap to numpy array
        img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(
            pix.height, pix.width, pix.n
        )
        # Convert RGBA → RGB if needed
        if pix.n == 4:
            img = img[:, :, :3]

        results.append((page_num, img.copy()))  # .copy() so buffer survives doc.close()

    doc.close()
    logger.debug("Converted %d/%d pages at %d DPI", len(results), len(doc), dpi)
    return results
