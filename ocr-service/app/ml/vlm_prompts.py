"""Structured prompts for Qwen3-VL financial document extraction.

These prompts are engineered for:
1. Table extraction with full layout preservation
2. Zone classification (financial statement type)
3. OCR text extraction with confidence
4. Multi-page document handling
"""

# --- System message for all financial document tasks ---
SYSTEM_PROMPT = """You are a financial document AI specialist. You analyze images of financial statements (annual reports, 10-K filings, balance sheets, income statements, cash flow statements, notes to accounts) and extract structured data with perfect accuracy.

Rules:
- Extract ALL tables visible in the image
- Preserve exact row and column structure including merged cells
- Classify each table into its financial statement zone
- Extract ALL numeric values exactly as shown (preserve negative signs, parentheses)
- Detect periods (years/dates) from column headers
- Detect currency and unit (thousands/millions) from header text
- Return valid JSON only, no markdown fencing
"""

# --- Full page extraction: tables + text + zones ---
PAGE_EXTRACTION_PROMPT = """Analyze this financial statement page and extract ALL tables with their complete structure.

For each table found, provide:
1. "zone_type": one of [BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, NOTES_FIXED_ASSETS, NOTES_RECEIVABLES, NOTES_DEBT, NOTES_OTHER, OTHER]
2. "zone_label": human-readable name (e.g., "Consolidated Balance Sheet")
3. "zone_confidence": your confidence in the zone classification (0.0-1.0)
4. "periods": list of column date/period headers detected (e.g., ["2025", "2024"])
5. "currency": detected currency (e.g., "USD", "EUR", "GBP", "AED") or null
6. "unit": detected unit (e.g., "thousands", "millions", "actual") or null
7. "headers": list of column header texts
8. "rows": list of objects, each with:
   - "label": the row label text (account name)
   - "values": list of numeric values matching each period column (use null for empty cells)
   - "is_total": boolean, true if this is a total/subtotal row
   - "indent_level": integer 0-3 indicating nesting depth
   - "is_header": boolean, true if this is a section header with no values

Return JSON format:
{
  "tables": [...],
  "page_text": "full OCR text of the page outside tables",
  "page_type": "financial_statement" or "notes" or "text_only" or "cover"
}"""

# --- Table-only extraction (for a cropped table image) ---
TABLE_EXTRACTION_PROMPT = """Extract the complete table structure from this image.

Return JSON:
{
  "headers": ["col1", "col2", ...],
  "rows": [
    {"label": "Row name", "values": [123, 456], "is_total": false, "indent_level": 0}
  ],
  "merged_cells": [
    {"text": "Section Header", "start_row": 0, "end_row": 0, "start_col": 0, "end_col": 3}
  ]
}"""

# --- Zone classification only ---
ZONE_CLASSIFICATION_PROMPT = """What type of financial statement is shown in this image?

Choose one:
- BALANCE_SHEET (Statement of Financial Position)
- INCOME_STATEMENT (Statement of Profit or Loss / Comprehensive Income)
- CASH_FLOW (Statement of Cash Flows)
- NOTES_FIXED_ASSETS (Property, Plant & Equipment schedule)
- NOTES_RECEIVABLES (Trade receivables / aging)
- NOTES_DEBT (Borrowings / debt maturity)
- NOTES_OTHER (Other notes schedules)
- OTHER (Non-financial content)

Return JSON:
{"zone_type": "...", "zone_label": "...", "confidence": 0.95}"""

# --- OCR-only extraction ---
OCR_EXTRACTION_PROMPT = """Extract all text from this document image. Preserve the reading order, paragraph structure, and any table layouts. Return the full text as a single string."""

# --- Gemini auto-labeling prompt (for training data generation) ---
GEMINI_LABELING_PROMPT = """You are labeling financial document pages for ML training.

Analyze this page image and extract EVERY table with complete structure.

For EACH table, provide:
{
  "table_id": "page{page}_table{n}",
  "zone_type": "BALANCE_SHEET|INCOME_STATEMENT|CASH_FLOW|NOTES_FIXED_ASSETS|NOTES_RECEIVABLES|NOTES_DEBT|NOTES_OTHER|OTHER",
  "zone_label": "Human readable name",
  "bbox": [x1_pct, y1_pct, x2_pct, y2_pct],
  "periods": ["2025", "2024"],
  "currency": "USD",
  "unit": "millions",
  "headers": ["Account", "2025", "2024"],
  "rows": [
    {
      "label": "Revenue",
      "values": [12500, 11200],
      "row_bbox": [x1_pct, y1_pct, x2_pct, y2_pct],
      "is_total": false,
      "indent_level": 0
    }
  ]
}

All bbox coordinates as percentages (0-100) of page dimensions.
Return: {"page_number": N, "tables": [...], "non_table_text": "..."}"""
