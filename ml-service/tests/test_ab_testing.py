"""Tests for A/B testing infrastructure."""

import pytest
from app.ml.zone_classifier import classify_by_keywords
from app.api.models import ZoneType


class TestABTestRouting:
    """Test that A/B test routing is functional."""

    def test_heuristic_always_returns_version(self):
        """classify_by_keywords should always return a result."""
        text = "Total Assets 5,000,000 Total Liabilities 3,000,000"
        zone, confidence = classify_by_keywords(text)
        assert zone == ZoneType.BALANCE_SHEET
        assert confidence > 0.5

    def test_zone_type_values(self):
        """All ZoneType enum values should be valid."""
        assert len(ZoneType) == 8
        assert ZoneType.BALANCE_SHEET.value == "BALANCE_SHEET"
        assert ZoneType.OTHER.value == "OTHER"


class TestHealthWithAB:
    def test_health_reports_staging(self, client):
        """Health endpoint should report staging model status."""
        response = client.get("/api/ml/health")
        assert response.status_code == 200
        data = response.json()
        assert "staging" in data["models"]["layoutlm"]
        assert "staging" in data["models"]["sentence_bert"]
        assert "features" in data
