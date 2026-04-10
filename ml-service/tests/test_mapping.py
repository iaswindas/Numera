"""Tests for mapping suggestion endpoint."""

import pytest


class TestMappingHealth:
    def test_health_check(self, client):
        response = client.get("/api/ml/health")
        assert response.status_code == 200


class TestFeedback:
    def test_submit_feedback(self, client):
        response = client.post("/api/ml/feedback", json={
            "corrections": [
                {
                    "source_text": "Turnover",
                    "source_zone_type": "INCOME_STATEMENT",
                    "suggested_item_id": "item-1",
                    "suggested_item_label": "Cost of Sales",
                    "corrected_item_id": "item-2",
                    "corrected_item_label": "Revenue",
                    "document_id": "doc-123",
                }
            ]
        })
        assert response.status_code == 200
        data = response.json()
        assert data["accepted"] == 1

    def test_feedback_stats(self, client):
        response = client.get("/api/ml/feedback/stats")
        assert response.status_code == 200
