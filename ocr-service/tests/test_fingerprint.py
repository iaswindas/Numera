"""Tests for the OCR-side STGH implementation."""

from fastapi import FastAPI
from fastapi.testclient import TestClient


def _make_table(revenue_text: str, cost_text: str):
    from app.api.models import BoundingBox, DetectedTable, TableCell

    bbox = BoundingBox(x=0.1, y=0.1, width=0.8, height=0.3)
    cells = [
        TableCell(text="Account", bbox=bbox, row_index=0, col_index=0, is_header=True, cell_type="TEXT"),
        TableCell(text="2025", bbox=bbox, row_index=0, col_index=1, is_header=True, cell_type="TEXT"),
        TableCell(text="Revenue", bbox=bbox, row_index=1, col_index=0, cell_type="TEXT"),
        TableCell(text=revenue_text, bbox=bbox, row_index=1, col_index=1, cell_type="NUMERIC"),
        TableCell(text="Cost of Sales", bbox=bbox, row_index=2, col_index=0, cell_type="TEXT"),
        TableCell(text=cost_text, bbox=bbox, row_index=2, col_index=1, cell_type="NUMERIC"),
        TableCell(text="Gross Profit", bbox=bbox, row_index=3, col_index=0, cell_type="TEXT"),
        TableCell(text="100", bbox=bbox, row_index=3, col_index=1, cell_type="NUMERIC"),
    ]
    return DetectedTable(
        table_id="table-1",
        page_number=1,
        bbox=bbox,
        confidence=0.95,
        rows=4,
        cols=2,
        cells=cells,
        header_rows=[0],
        account_column=0,
        value_columns=[1],
    )


def test_fingerprinter_is_stable_for_same_template_with_new_values():
    from app.ml.stgh import STGHConfig, STGHFingerprinter

    fingerprinter = STGHFingerprinter(STGHConfig())
    first = fingerprinter.fingerprint_document([_make_table("1,000", "900")])[0]
    second = fingerprinter.fingerprint_document([_make_table("2,500", "2,400")])[0]

    assert fingerprinter.similarity(first, second) > 0.9


def test_fingerprint_generate_endpoint_returns_page_fingerprint():
    from app.api.fingerprint import router
    from app.ml.stgh import STGHConfig, STGHFingerprinter

    app = FastAPI()
    app.state.stgh_fingerprinter = STGHFingerprinter(STGHConfig())
    app.include_router(router, prefix="/api/ocr/fingerprint")

    client = TestClient(app)
    response = client.post(
        "/api/ocr/fingerprint/generate",
        json={
            "document_id": "doc-1",
            "tables": [_make_table("1,000", "900").model_dump()],
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["total_pages"] == 1
    assert len(data["fingerprints"]) == 1
    assert data["fingerprints"][0]["node_count"] == 8