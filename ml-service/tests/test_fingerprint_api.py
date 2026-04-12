"""Tests for the Phase 1 fingerprint matching API."""

from fastapi import FastAPI
from fastapi.testclient import TestClient


def _build_client():
    from app.api.fingerprint import router

    app = FastAPI()
    app.include_router(router, prefix="/api/ml/fingerprint")
    return TestClient(app)


def test_matches_supplied_template_fingerprints():
    client = _build_client()
    fingerprint = {
        "hash": "f" * 64,
        "embedding": [1.0, 0.0, 0.0],
        "page_idx": 0,
        "node_count": 12,
        "created_at": "2026-01-01T00:00:00Z",
        "table_ids": ["table-1"],
    }

    response = client.post(
        "/api/ml/fingerprint/match",
        json={
            "document_id": "doc-1",
            "candidate_fingerprints": [fingerprint],
            "templates": [{"template_id": "tpl-1", "fingerprints": [fingerprint]}],
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["matched"] is True
    assert data["best_match"]["template_id"] == "tpl-1"
    assert data["best_match"]["similarity"] == 1.0


def test_matches_repo_template_catalog():
    from app.api.fingerprint import _load_template_fingerprints

    client = _build_client()
    candidate = _load_template_fingerprints("ifrs-corporate-v1")[0]

    response = client.post(
        "/api/ml/fingerprint/match",
        json={
            "document_id": "doc-2",
            "candidate_fingerprints": [candidate],
            "template_ids": ["ifrs-corporate-v1"],
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["matched"] is True
    assert data["best_match"]["template_id"] == "ifrs-corporate-v1"
