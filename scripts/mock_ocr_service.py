"""
Lightweight mock OCR service for development/testing.
Responds to the same endpoints the backend calls.
"""
import json
import time
import uuid
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8001


class MockOCRHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(length)) if length else {}

    def do_GET(self):
        if self.path in ("/api/ocr/health", "/health"):
            self._set_headers()
            self.wfile.write(json.dumps({
                "status": "healthy",
                "service": "ocr-service-mock",
                "models": {
                    "paddleocr": {"loaded": True, "version": "mock", "lang": "en"},
                    "pp_structure": {"loaded": True, "version": "mock"},
                    "stgh": {"loaded": False, "version": "1.0"},
                },
                "device": "cpu",
                "uptime_seconds": int(time.time()),
            }).encode())
        elif self.path == "/api/ocr/languages":
            self._set_headers()
            self.wfile.write(json.dumps({
                "supported": ["en", "ar", "zh", "fr"],
                "default": "en",
                "auto_enabled": False,
            }).encode())
        else:
            self._set_headers(404)
            self.wfile.write(b'{"error": "not found"}')

    def do_POST(self):
        body = self._read_body()

        if self.path == "/api/ocr/extract":
            doc_id = body.get("document_id", str(uuid.uuid4()))
            self._set_headers()
            self.wfile.write(json.dumps({
                "document_id": doc_id,
                "total_pages": 1,
                "pages_processed": 1,
                "pages_failed": 0,
                "text_blocks": [
                    {
                        "text": "Total Revenue  1,250,000",
                        "confidence": 0.95,
                        "bbox": [50, 100, 400, 120],
                        "page": 1,
                    },
                    {
                        "text": "Cost of Goods Sold  750,000",
                        "confidence": 0.93,
                        "bbox": [50, 130, 400, 150],
                        "page": 1,
                    },
                    {
                        "text": "Operating Expenses  300,000",
                        "confidence": 0.92,
                        "bbox": [50, 160, 400, 180],
                        "page": 1,
                    },
                    {
                        "text": "Net Income  200,000",
                        "confidence": 0.94,
                        "bbox": [50, 190, 400, 210],
                        "page": 1,
                    },
                ],
                "full_text": "Total Revenue  1,250,000\nCost of Goods Sold  750,000\nOperating Expenses  300,000\nNet Income  200,000",
                "processing_time_ms": 150,
                "backend": "mock",
                "pdf_type": "native",
                "errors": [],
            }).encode())

        elif self.path == "/api/ocr/tables/detect":
            doc_id = body.get("document_id", str(uuid.uuid4()))
            table_id = f"table_{uuid.uuid4().hex[:8]}"
            self._set_headers()
            self.wfile.write(json.dumps({
                "document_id": doc_id,
                "total_pages": 1,
                "tables_detected": 1,
                "tables_filtered": 0,
                "tables": [
                    {
                        "table_id": table_id,
                        "page": 1,
                        "page_number": 1,
                        "bbox": {"x": 40, "y": 80, "width": 500, "height": 200},
                        "confidence": 0.92,
                        "rows": 5,
                        "row_count": 5,
                        "cols": 2,
                        "cells": [
                            {"text": "Account", "bbox": {"x": 40, "y": 80, "width": 250, "height": 20}, "row_index": 0, "col_index": 0, "is_header": True, "cell_type": "TEXT"},
                            {"text": "Amount", "bbox": {"x": 290, "y": 80, "width": 250, "height": 20}, "row_index": 0, "col_index": 1, "is_header": True, "cell_type": "TEXT"},
                            {"text": "Total Revenue", "bbox": {"x": 40, "y": 100, "width": 250, "height": 20}, "row_index": 1, "col_index": 0, "is_header": False, "cell_type": "TEXT"},
                            {"text": "1,250,000", "bbox": {"x": 290, "y": 100, "width": 250, "height": 20}, "row_index": 1, "col_index": 1, "is_header": False, "cell_type": "NUMERIC"},
                            {"text": "Cost of Goods Sold", "bbox": {"x": 40, "y": 120, "width": 250, "height": 20}, "row_index": 2, "col_index": 0, "is_header": False, "cell_type": "TEXT"},
                            {"text": "750,000", "bbox": {"x": 290, "y": 120, "width": 250, "height": 20}, "row_index": 2, "col_index": 1, "is_header": False, "cell_type": "NUMERIC"},
                            {"text": "Operating Expenses", "bbox": {"x": 40, "y": 140, "width": 250, "height": 20}, "row_index": 3, "col_index": 0, "is_header": False, "cell_type": "TEXT"},
                            {"text": "300,000", "bbox": {"x": 290, "y": 140, "width": 250, "height": 20}, "row_index": 3, "col_index": 1, "is_header": False, "cell_type": "NUMERIC"},
                            {"text": "Net Income", "bbox": {"x": 40, "y": 160, "width": 250, "height": 20}, "row_index": 4, "col_index": 0, "is_header": False, "cell_type": "TEXT"},
                            {"text": "200,000", "bbox": {"x": 290, "y": 160, "width": 250, "height": 20}, "row_index": 4, "col_index": 1, "is_header": False, "cell_type": "NUMERIC"},
                        ],
                        "header_rows": [0],
                        "account_column": 0,
                        "value_columns": [1],
                        "detected_periods": ["FY2024"],
                        "detected_currency": "USD",
                        "detected_unit": "thousands",
                    }
                ],
                "processing_time_ms": 200,
                "backend": "mock",
                "pdf_type": "native",
                "errors": [],
            }).encode())
        else:
            self._set_headers(404)
            self.wfile.write(b'{"error": "not found"}')

    def log_message(self, format, *args):
        print(f"[OCR-Mock] {args[0]}")


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), MockOCRHandler)
    print(f"Mock OCR service running on port {PORT}")
    server.serve_forever()
