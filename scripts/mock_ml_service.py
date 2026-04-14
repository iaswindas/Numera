"""
Lightweight mock ML service for development/testing.
Responds to the same endpoints the backend calls.
"""
import json
import time
import uuid
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8002


class MockMLHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(length)) if length else {}

    def do_GET(self):
        if self.path in ("/api/ml/health", "/health"):
            self._set_headers()
            self.wfile.write(json.dumps({
                "status": "healthy",
                "service": "ml-service-mock",
                "version": "1.0.0-mock",
                "models": {
                    "layoutlm": {"production": {"loaded": True}, "staging": {"loaded": False}},
                    "sentence_bert": {"production": {"loaded": True}, "staging": {"loaded": False}},
                },
                "features": {
                    "ab_testing": False,
                    "client_models": False,
                    "feedback_storage": "memory",
                },
                "infrastructure": {"postgresql": {"status": "mock"}, "device": "cpu"},
                "uptime_seconds": int(time.time()),
            }).encode())
        else:
            self._set_headers(404)
            self.wfile.write(b'{"error": "not found"}')

    def do_POST(self):
        body = self._read_body()

        if self.path == "/api/ml/zones/classify":
            doc_id = body.get("document_id", str(uuid.uuid4()))
            tables = body.get("tables", [])
            zones = []
            for table in tables:
                tid = table.get("table_id", str(uuid.uuid4()))
                zones.append({
                    "table_id": tid,
                    "zone_type": "INCOME_STATEMENT",
                    "zone_label": "Income Statement",
                    "confidence": 0.88,
                    "classification_method": "MOCK",
                    "detected_periods": table.get("detected_periods", ["FY2024"]),
                    "detected_currency": table.get("detected_currency", "USD"),
                    "detected_unit": table.get("detected_unit", "thousands"),
                })
            self._set_headers()
            self.wfile.write(json.dumps({
                "document_id": doc_id,
                "zones": zones,
                "processing_time_ms": 50,
            }).encode())

        elif self.path == "/api/ml/mapping/suggest":
            doc_id = body.get("document_id", str(uuid.uuid4()))
            source_rows = body.get("source_rows", [])
            target_items = body.get("target_items", [])
            mappings = []
            for i, row in enumerate(source_rows):
                row_text = row if isinstance(row, str) else str(row)
                target = target_items[i] if i < len(target_items) else f"item_{i}"
                mappings.append({
                    "source_row": row_text,
                    "suggested_mappings": [{
                        "target_item": target if isinstance(target, str) else str(target),
                        "confidence_level": "HIGH",
                        "score": 0.9,
                        "reasoning": "Mock mapping",
                    }],
                })
            self._set_headers()
            self.wfile.write(json.dumps({
                "document_id": doc_id,
                "mappings": mappings,
                "summary": {
                    "total_source_rows": len(source_rows),
                    "high_confidence": len(source_rows),
                    "medium_confidence": 0,
                    "low_confidence": 0,
                    "unmapped": 0,
                },
                "processing_time_ms": 30,
            }).encode())

        elif self.path == "/api/ml/expressions/build":
            doc_id = body.get("document_id", str(uuid.uuid4()))
            template_id = body.get("template_id", "")
            zone_type = body.get("zone_type", "INCOME_STATEMENT")

            # Extract target item codes from semantic_matches to use as target_item_id
            # Fall back to hardcoded defaults matching the standard template
            default_items = [
                {"id": "REV", "label": "Total Revenue", "value": 1250000, "source": "Total Revenue"},
                {"id": "COGS", "label": "Cost of Goods Sold", "value": 750000, "source": "COGS"},
                {"id": "OPEX", "label": "Operating Expenses", "value": 300000, "source": "OpEx"},
                {"id": "NI", "label": "Net Income", "value": 200000, "source": "Net Income"},
            ]

            expressions = []
            for i, item in enumerate(default_items):
                expressions.append({
                    "target_item_id": item["id"],
                    "target_label": item["label"],
                    "expression_type": "DIRECT",
                    "sources": [{"row_index": i, "label": item["source"], "value": item["value"], "page": 1, "confidence": 0.95 - i * 0.01}],
                    "scale_factor": 1.0,
                    "computed_value": item["value"],
                    "confidence": 0.95 - i * 0.01,
                    "explanation": "Direct mapping from OCR extraction",
                })

            self._set_headers()
            self.wfile.write(json.dumps({
                "document_id": doc_id,
                "template_id": template_id,
                "zone_type": zone_type,
                "expressions": expressions,
                "total_mapped": len(expressions),
                "total_items": len(expressions),
                "coverage_pct": 100.0,
                "unit_scale": 1.0,
                "autofilled": 0,
                "validation_results": [],
            }).encode())

        elif self.path == "/api/ml/feedback":
            self._set_headers()
            corrections = body.get("corrections", [])
            self.wfile.write(json.dumps({
                "accepted": len(corrections),
                "total_stored": len(corrections),
                "message": "Feedback stored (mock)",
                "storage": "memory",
            }).encode())

        elif self.path == "/api/ml/covenant/predict":
            self._set_headers()
            self.wfile.write(json.dumps({
                "breachProbability": 0.15,
                "confidenceInterval": {"lower": 0.08, "upper": 0.25},
                "forecast": [
                    {"period": "2024-Q1", "expectedValue": 1.8, "breachRisk": 0.1},
                    {"period": "2024-Q2", "expectedValue": 1.75, "breachRisk": 0.12},
                ],
                "factors": [
                    {"name": "Revenue Trend", "impact": 0.3},
                    {"name": "Leverage Ratio", "impact": 0.2},
                ],
            }).encode())

        elif self.path == "/api/ml/anomaly/detect":
            self._set_headers()
            self.wfile.write(json.dumps({
                "anomalies": [],
                "overall_risk_score": 0.1,
                "summary": "No anomalies detected (mock)",
                "total_items_checked": len(body.get("spread_values", [])),
                "flagged_count": 0,
            }).encode())

        else:
            self._set_headers(404)
            self.wfile.write(b'{"error": "not found"}')

    def log_message(self, format, *args):
        print(f"[ML-Mock] {args[0]}")


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), MockMLHandler)
    print(f"Mock ML service running on port {PORT}")
    server.serve_forever()
