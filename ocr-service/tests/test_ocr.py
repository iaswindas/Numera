"""Tests for OCR extraction endpoint."""

import pytest


class TestOcrHealth:
    def test_health_check(self, client):
        response = client.get("/api/ocr/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "ocr-service"
        assert "paddleocr" in data["models"]
        assert "pp_structure" in data["models"]


class TestOcrExtract:
    def test_extract_missing_file_returns_error(self, client):
        response = client.post("/api/ocr/extract", json={
            "document_id": "test-123",
            "storage_path": "nonexistent/file.pdf",
        })
        # Should return 404 (not found) or 502 (storage error)
        assert response.status_code in (404, 502)
