"""Structured OCR error models and pre-defined error codes.

Used across the OCR service for consistent, structured error responses
that support per-page graceful degradation.
"""

from pydantic import BaseModel
from typing import Optional


class MLError(BaseModel):
    """Structured OCR/ML error for graceful degradation."""
    error_code: str
    message: str
    page: int | None = None
    recoverable: bool = True
    detail: str | None = None


# --- Pre-defined error factories ---

def ocr_failure(message: str, page: int | None = None) -> MLError:
    return MLError(
        error_code="OCR_FAILURE",
        message=message,
        page=page,
        recoverable=True,
        detail="Page skipped, processing continues",
    )


def table_detection_failure(message: str, page: int | None = None) -> MLError:
    return MLError(
        error_code="TABLE_DETECTION_FAILURE",
        message=message,
        page=page,
        recoverable=True,
    )


def pdf_corrupt(message: str) -> MLError:
    return MLError(
        error_code="PDF_CORRUPT",
        message=message,
        recoverable=False,
    )


def pdf_password_protected(detail: str | None = None) -> MLError:
    return MLError(
        error_code="PDF_PASSWORD_PROTECTED",
        message="PDF is password-protected",
        recoverable=False,
        detail=detail or "Provide password or upload an unprotected version",
    )


def storage_error(message: str) -> MLError:
    return MLError(
        error_code="STORAGE_ERROR",
        message=message,
        recoverable=False,
    )
