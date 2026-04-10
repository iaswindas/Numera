"""Tests for OCR error handling."""

import pytest
from app.api.errors import (
    ocr_failure,
    table_detection_failure,
    pdf_corrupt,
    pdf_password_protected,
    storage_error,
)


class TestOcrErrors:
    def test_ocr_failure_is_recoverable(self):
        err = ocr_failure("OCR failed on page 3", page=3)
        assert err.error_code == "OCR_FAILURE"
        assert err.page == 3
        assert err.recoverable is True

    def test_pdf_corrupt_is_not_recoverable(self):
        err = pdf_corrupt("Invalid PDF header")
        assert err.error_code == "PDF_CORRUPT"
        assert err.recoverable is False

    def test_pdf_password_protected(self):
        err = pdf_password_protected()
        assert err.error_code == "PDF_PASSWORD_PROTECTED"
        assert err.recoverable is False

    def test_table_detection_failure(self):
        err = table_detection_failure("No tables found", page=5)
        assert err.error_code == "TABLE_DETECTION_FAILURE"
        assert err.page == 5

    def test_storage_error(self):
        err = storage_error("MinIO connection refused")
        assert err.error_code == "STORAGE_ERROR"
        assert err.recoverable is False
