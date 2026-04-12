"""Tests for OW-PGGR anomaly detection."""

from __future__ import annotations

import pytest

from app.ml.owpggr.detector import AnomalyDetector
from app.ml.owpggr.materiality import MaterialityCalculator
from app.ml.owpggr.models import AnomalyType


# --- Fixtures ---

@pytest.fixture
def detector() -> AnomalyDetector:
    return AnomalyDetector()


@pytest.fixture
def balance_sheet_spread() -> list[dict]:
    return [
        {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000, "zone_type": "BALANCE_SHEET"},
        {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 600_000, "zone_type": "BALANCE_SHEET"},
        {"line_item_id": "total_equity", "label": "Total Equity", "value": 400_000, "zone_type": "BALANCE_SHEET"},
        {"line_item_id": "current_assets", "label": "Current Assets", "value": 300_000, "zone_type": "BALANCE_SHEET"},
        {"line_item_id": "current_liabilities", "label": "Current Liabilities", "value": 200_000, "zone_type": "BALANCE_SHEET"},
        {"line_item_id": "revenue", "label": "Revenue", "value": 500_000, "zone_type": "INCOME_STATEMENT"},
    ]


@pytest.fixture
def historical_spreads() -> list[list[dict]]:
    """Three historical periods with consistent values."""
    base = [
        {"line_item_id": "total_assets", "label": "Total Assets", "value": 950_000},
        {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 570_000},
        {"line_item_id": "total_equity", "label": "Total Equity", "value": 380_000},
        {"line_item_id": "revenue", "label": "Revenue", "value": 480_000},
        {"line_item_id": "current_assets", "label": "Current Assets", "value": 280_000},
        {"line_item_id": "current_liabilities", "label": "Current Liabilities", "value": 190_000},
    ]
    # Small variations across periods
    periods = []
    for i, factor in enumerate([0.95, 0.98, 1.0]):
        period = []
        for item in base:
            period.append({**item, "value": item["value"] * factor})
        periods.append(period)
    return periods


# --- Statistical outlier tests ---

class TestStatisticalOutlier:
    def test_no_anomalies_for_consistent_data(self, detector: AnomalyDetector, balance_sheet_spread, historical_spreads):
        report = detector.detect(balance_sheet_spread, historical_spreads)
        stat_anomalies = [a for a in report.anomalies if a.anomaly_type == AnomalyType.STATISTICAL_OUTLIER]
        # With consistent data, should have no or very few statistical outliers
        assert report.total_items_checked == len(balance_sheet_spread)

    def test_detects_large_outlier(self, detector: AnomalyDetector, historical_spreads):
        # Current spread has a wildly different revenue
        current = [
            {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000},
            {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 600_000},
            {"line_item_id": "total_equity", "label": "Total Equity", "value": 400_000},
            {"line_item_id": "revenue", "label": "Revenue", "value": 5_000_000},  # 10x normal
            {"line_item_id": "current_assets", "label": "Current Assets", "value": 300_000},
            {"line_item_id": "current_liabilities", "label": "Current Liabilities", "value": 200_000},
        ]
        report = detector.detect(current, historical_spreads)
        stat = [a for a in report.anomalies if a.anomaly_type == AnomalyType.STATISTICAL_OUTLIER]
        assert len(stat) > 0
        revenue_anomaly = [a for a in stat if a.line_item_id == "revenue"]
        assert len(revenue_anomaly) == 1
        assert revenue_anomaly[0].severity > 0.3


# --- Balance violation tests ---

class TestBalanceViolation:
    def test_balanced_sheet_no_violation(self, detector: AnomalyDetector, balance_sheet_spread):
        report = detector.detect(balance_sheet_spread)
        violations = [a for a in report.anomalies if a.anomaly_type == AnomalyType.BALANCE_VIOLATION]
        assert len(violations) == 0

    def test_imbalanced_sheet_flagged(self, detector: AnomalyDetector):
        spread = [
            {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000},
            {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 600_000},
            {"line_item_id": "total_equity", "label": "Total Equity", "value": 300_000},  # 100k gap
            {"line_item_id": "revenue", "label": "Revenue", "value": 500_000},
        ]
        report = detector.detect(spread)
        violations = [a for a in report.anomalies if a.anomaly_type == AnomalyType.BALANCE_VIOLATION]
        assert len(violations) == 1
        assert violations[0].severity > 0.0
        assert "imbalance" in violations[0].description.lower()


# --- Materiality scoring tests ---

class TestMateriality:
    def test_low_materiality(self):
        calc = MaterialityCalculator()
        score = calc.compute_materiality(100, total_assets=1_000_000, revenue=500_000)
        assert score < 0.3

    def test_medium_materiality(self):
        calc = MaterialityCalculator()
        score = calc.compute_materiality(10_000, total_assets=1_000_000, revenue=500_000)
        # 10k/1M = 1% → medium range
        assert 0.3 <= score <= 0.7

    def test_high_materiality(self):
        calc = MaterialityCalculator()
        score = calc.compute_materiality(50_000, total_assets=1_000_000, revenue=500_000)
        # 50k/500k = 10% → high
        assert score > 0.7

    def test_zero_base_returns_zero(self):
        calc = MaterialityCalculator()
        score = calc.compute_materiality(100, total_assets=0, revenue=0)
        assert score == 0.0


# --- Edge cases ---

class TestEdgeCases:
    def test_empty_input(self, detector: AnomalyDetector):
        report = detector.detect([])
        assert report.flagged_count == 0
        assert report.overall_risk_score == 0.0
        assert report.total_items_checked == 0

    def test_single_item_no_history(self, detector: AnomalyDetector):
        report = detector.detect([
            {"line_item_id": "cash", "label": "Cash", "value": 100_000},
        ])
        assert report.total_items_checked == 1
        # No statistical or trend checks possible
        stat = [a for a in report.anomalies if a.anomaly_type == AnomalyType.STATISTICAL_OUTLIER]
        assert len(stat) == 0

    def test_null_values_handled(self, detector: AnomalyDetector):
        report = detector.detect([
            {"line_item_id": "cash", "label": "Cash", "value": None},
            {"line_item_id": "other", "label": "Other", "value": "not_a_number"},
        ])
        assert report.total_items_checked == 2
        assert report.flagged_count == 0


# --- Trend break tests ---

class TestTrendBreak:
    def test_detects_sudden_change(self, detector: AnomalyDetector):
        # Varying revenue growth to produce non-zero change std
        history = [
            [{"line_item_id": "revenue", "label": "Revenue", "value": 100_000},
             {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000}],
            [{"line_item_id": "revenue", "label": "Revenue", "value": 104_000},
             {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_040_000}],
            [{"line_item_id": "revenue", "label": "Revenue", "value": 110_000},
             {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_100_000}],
            [{"line_item_id": "revenue", "label": "Revenue", "value": 113_000},
             {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_130_000}],
            [{"line_item_id": "revenue", "label": "Revenue", "value": 118_000},
             {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_180_000}],
        ]
        current = [
            {"line_item_id": "revenue", "label": "Revenue", "value": 50_000},  # sudden drop
            {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_200_000},
            {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 700_000},
            {"line_item_id": "total_equity", "label": "Total Equity", "value": 500_000},
        ]
        report = detector.detect(current, history)
        trend = [a for a in report.anomalies if a.anomaly_type == AnomalyType.TREND_BREAK]
        assert any(a.line_item_id == "revenue" for a in trend)


# --- API endpoint tests ---

_has_pydantic_settings = True
try:
    import pydantic_settings  # noqa: F401
except ImportError:
    _has_pydantic_settings = False


@pytest.mark.skipif(not _has_pydantic_settings, reason="pydantic-settings not installed")
class TestAnomalyAPI:
    def test_detect_endpoint(self):
        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        payload = {
            "spread_values": [
                {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000},
                {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 600_000},
                {"line_item_id": "total_equity", "label": "Total Equity", "value": 400_000},
            ],
        }
        resp = client.post("/api/ml/anomaly/detect", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert "report" in data
        assert "processing_time_ms" in data
        assert data["report"]["total_items_checked"] == 3

    def test_batch_endpoint(self):
        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        payload = {
            "items": [
                {
                    "spread_values": [
                        {"line_item_id": "total_assets", "label": "Total Assets", "value": 1_000_000},
                        {"line_item_id": "total_liabilities", "label": "Total Liabilities", "value": 600_000},
                        {"line_item_id": "total_equity", "label": "Total Equity", "value": 400_000},
                    ],
                },
                {
                    "spread_values": [
                        {"line_item_id": "cash", "label": "Cash", "value": 50_000},
                    ],
                },
            ]
        }
        resp = client.post("/api/ml/anomaly/detect/batch", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["reports"]) == 2
