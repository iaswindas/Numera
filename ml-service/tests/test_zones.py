"""Tests for zone classification endpoint."""

import pytest
from app.ml.zone_classifier import classify_by_keywords
from app.api.models import ZoneType


class TestKeywordClassifier:
    def test_balance_sheet_strong_keywords(self):
        text = "Total Assets 5,000,000 Total Liabilities 3,000,000 Total Equity 2,000,000"
        zone, confidence = classify_by_keywords(text)
        assert zone == ZoneType.BALANCE_SHEET
        assert confidence >= 0.8

    def test_income_statement_strong_keywords(self):
        text = "Revenue 10,000 Cost of Sales 6,000 Gross Profit 4,000 Net Income 1,500"
        zone, confidence = classify_by_keywords(text)
        assert zone == ZoneType.INCOME_STATEMENT
        assert confidence >= 0.8

    def test_cash_flow_strong_keywords(self):
        text = "Cash from Operations 2,000 Cash from Investing (500) Cash from Financing (300)"
        zone, confidence = classify_by_keywords(text)
        assert zone == ZoneType.CASH_FLOW
        assert confidence >= 0.7

    def test_unknown_text_returns_other(self):
        text = "Lorem ipsum dolor sit amet"
        zone, confidence = classify_by_keywords(text)
        assert zone == ZoneType.OTHER
        assert confidence < 0.5


class TestZoneHealth:
    def test_health_check(self, client):
        response = client.get("/api/ml/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "ml-service"
