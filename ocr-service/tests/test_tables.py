"""Tests for table detection."""

import pytest


class TestTableDetection:
    def test_detect_tables_missing_file_returns_error(self, client):
        response = client.post("/api/ocr/tables/detect", json={
            "document_id": "test-123",
            "storage_path": "nonexistent/file.pdf",
        })
        assert response.status_code in (404, 502)
