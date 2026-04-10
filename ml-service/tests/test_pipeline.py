"""Tests for pipeline orchestration endpoint."""

import pytest


class TestPipelineModels:
    def test_processing_status_values(self):
        from app.api.pipeline_models import ProcessingStatus
        assert ProcessingStatus.UPLOADED.value == "UPLOADED"
        assert ProcessingStatus.MAPPED.value == "MAPPED"
        assert ProcessingStatus.ERROR.value == "ERROR"

    def test_pipeline_request_defaults(self):
        from app.api.pipeline_models import PipelineRequest
        req = PipelineRequest(
            document_id="doc-1",
            storage_path="test/path.pdf",
        )
        assert req.language == "en"
        assert req.skip_ocr is False
        assert req.skip_mapping is False
        assert req.tenant_id is None


class TestErrorModels:
    def test_ml_error_creation(self):
        from app.api.errors import pipeline_step_failure, pipeline_timeout
        err1 = pipeline_step_failure("ocr_extract", "Connection refused")
        assert err1.error_code == "PIPELINE_OCR_EXTRACT_FAILURE"
        assert err1.recoverable is True

        err2 = pipeline_timeout("table_detect", 120)
        assert err2.error_code == "PIPELINE_TIMEOUT"
        assert err2.recoverable is False
