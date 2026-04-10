# Model Template Architecture — Deep Dive

## The Core Problem

```
                    PDF (source)                          Model Template (target)
                    ────────────                          ──────────────────────
  Bank A's customer:                        Bank A's template:
  "Turnover: 12,500"                        "Revenue" ← ???
  "Export Sales: 3,200"                     "Gross Profit" ← FORMULA(Revenue - COGS)
  "Domestic Sales: 9,300"                   "Current Ratio" ← FORMULA(CA / CL)
  "Other Income: 800"
  "COGS: (8,100)"

  ---                                       Bank B's template:
  Bank B uses the SAME PDF                  "Net Sales" ← ???
  but has a DIFFERENT template              "Operating Income" ← ???
  with different line items                 "Leverage Ratio" ← ???
```

**The model template is the bank's standardized format.** Different banks have different templates based on:
- Their internal credit analysis methodology
- Regulatory requirements (Basel III, IFRS, US GAAP)
- Industry focus (corporate vs. SME vs. Islamic banking)
- Region (GCC banks need Arabic, EU needs IFRS)

---

## 1. Model Template Structure

### What is a "Model"?

A model template is a **grid of cells** organized into sections:

```
┌─────────────────────────────────────────────────┐
│ INCOME STATEMENT          │  2025    │  2024    │
├─────────────────────────────────────────────────┤
│ Revenue                   │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ Cost of Sales             │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ ──────────────────────────│──────────│──────────│
│ Gross Profit              │ [FORMULA]│ [FORMULA]│  ← = Revenue - COGS
│ Gross Margin %            │ [FORMULA]│ [FORMULA]│  ← = Gross Profit / Revenue
│                           │          │          │
│ Operating Expenses        │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ Other Income              │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ ──────────────────────────│──────────│──────────│
│ Operating Profit (EBIT)   │ [FORMULA]│ [FORMULA]│  ← = GP - OpEx + OI
│ ──────────────────────────│──────────│──────────│
│ Finance Costs             │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ Profit Before Tax         │ [FORMULA]│ [FORMULA]│  ← = EBIT - Finance Costs
│ Tax                       │ [INPUT]  │ [INPUT]  │  ← mapped from PDF
│ ──────────────────────────│──────────│──────────│
│ Net Profit                │ [FORMULA]│ [FORMULA]│  ← = PBT - Tax
└─────────────────────────────────────────────────┘
```

### Cell Types

| Type | Description | Example |
|---|---|---|
| `INPUT` | Value mapped from PDF (by AI or analyst) | Revenue, COGS, Tax |
| `FORMULA` | Computed from other cells in the model | Gross Profit = Revenue - COGS |
| `VALIDATION` | Cross-check rule (must equal zero) | Assets - (Liabilities + Equity) = 0 |
| `CATEGORY` | Section header (no value) | "Current Assets", "Operating Activities" |
| `MANUAL` | Keyboard entry (not in document) | Analyst's adjustment, override |

### Database Schema

```sql
-- Each bank/tenant has one or more model templates
model_templates (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,          -- Bank
    name            TEXT NOT NULL,           -- "IFRS Corporate", "Islamic Banking"
    description     TEXT,
    version         INT DEFAULT 1,
    is_default      BOOLEAN DEFAULT false,
    created_at      TIMESTAMPTZ
);

-- ~200 line items per template
model_line_items (
    id              UUID PRIMARY KEY,
    template_id     UUID NOT NULL REFERENCES model_templates(id),
    zone_type       TEXT NOT NULL,           -- BALANCE_SHEET, INCOME_STATEMENT, etc.
    label           TEXT NOT NULL,           -- "Revenue", "Trade Receivables"
    category        TEXT,                    -- "Current Assets", "Operating Expenses"
    item_type       TEXT NOT NULL,           -- INPUT, FORMULA, VALIDATION, CATEGORY
    formula         TEXT,                    -- "= {Revenue} - {Cost_of_Sales}"
    display_order   INT NOT NULL,
    indent_level    INT DEFAULT 0,
    is_total        BOOLEAN DEFAULT false,
    is_required     BOOLEAN DEFAULT false,   -- Must be mapped before submission
    synonyms        TEXT[],                  -- ["Revenue", "Turnover", "Net Sales", "الإيرادات"]
    sign_convention TEXT DEFAULT 'NATURAL',  -- NATURAL, ALWAYS_POSITIVE, ALWAYS_NEGATIVE
    parent_item_id  UUID REFERENCES model_line_items(id)
);

-- Validation rules
model_validations (
    id              UUID PRIMARY KEY,
    template_id     UUID NOT NULL,
    name            TEXT NOT NULL,           -- "Balance Sheet Check"
    formula         TEXT NOT NULL,           -- "{Total_Assets} - {Total_Liabilities} - {Total_Equity}"
    expected_value  DECIMAL DEFAULT 0,
    tolerance       DECIMAL DEFAULT 0.01,    -- Allow rounding differences
    severity        TEXT DEFAULT 'ERROR'      -- ERROR, WARNING
);
```

---

## 2. How AI Maps PDF → Model (The 3-Pass Algorithm)

This is the core intelligence. It happens in 3 passes:

### Pass 1: Zone Matching (VLM)

```
PDF Table                          Model Template
─────────                          ──────────────
"Statement of Financial Position"  → BALANCE_SHEET section
   (VLM classified this table)       (contains ~80 line items)

"Profit and Loss Account"          → INCOME_STATEMENT section
   (VLM classified this table)       (contains ~50 line items)
```

**This narrows 200 line items → ~50-80 candidates per table.**
Zone matching is already 95%+ accurate — this is the easy part.

### Pass 2: Semantic Matching (BGE)

For each row extracted from the PDF, BGE finds the best matching line item in the model:

```
PDF Row                    │  BGE Score  │  Model Line Item
───────────────────────────│─────────────│──────────────────
"Turnover"                 │   0.94 HIGH │  Revenue
"Cost of goods sold"       │   0.92 HIGH │  Cost of Sales
"Bank charges"             │   0.71 MED  │  Finance Costs
"Miscellaneous income"     │   0.65 MED  │  Other Income
"Provision for taxation"   │   0.88 HIGH │  Income Tax Expense
"Depreciation on PPE"      │   0.62 LOW  │  ??? (could be multiple items)
```

**BGE uses the synonyms from `model_line_items.synonyms[]` to boost accuracy.**

### Pass 3: Expression Detection (Rule Engine)

This is the crucial part. Sometimes one model cell maps to **multiple PDF values**:

```
Model Cell: "Revenue"

PDF has:                          Detection Logic:
├─ "Domestic Sales: 9,300"       ──► These are CHILDREN of Revenue
├─ "Export Sales: 3,200"         ──► (indented under Revenue in PDF)
└─ "Total Revenue: 12,500"      ──► 9,300 + 3,200 = 12,500 ✓

System creates: Revenue = Domestic_Sales + Export_Sales
                OR
                Revenue = Total_Revenue (direct map to the total row)
```

**Expression detection rules:**

| Pattern | Detection Method | Expression Created |
|---|---|---|
| **Total row** in PDF matches model item | Row has `is_total=true` in VLM output | Direct map to total |
| **Children sum to parent** | VLM indent levels + arithmetic check | Sum expression |
| **Sign flip** | PDF shows `(8,100)` but model expects positive | `= ABS(source)` |
| **Unit conversion** | PDF says "in thousands", model is actual | `= source × 1000` |
| **Contra items** | Revenue adjustments, returns, discounts | `= Revenue - Returns` |
| **Cross-zone** | Depreciation from notes → Income Statement | Cross-reference by label match |

---

## 3. Expression Builder — Concrete Example

### Scenario: Bank's model has "Revenue" but PDF has 5 revenue-related lines

```
╔══════════════════════════════════════════════════════════════╗
║  PDF (left pane)              │  Model (right pane)         ║
╠══════════════════════════════════════════════════════════════╣
║  Revenue from operations      │                             ║
║    Product sales     7,200    │  Revenue         [_____]    ║
║    Service income    2,100    │                             ║
║    Commission        1,000    │                             ║
║  Other operating income  800  │  Other Income    [_____]    ║
║  ─────────────────────        │                             ║
║  Total Revenue      11,100    │                             ║
╚══════════════════════════════════════════════════════════════╝
```

### What the AI does automatically:

**Step 1**: VLM extracts all rows with indentation levels:
```json
{"label": "Revenue from operations", "values": [null], "indent_level": 0, "is_header": true}
{"label": "Product sales", "values": [7200], "indent_level": 1}
{"label": "Service income", "values": [2100], "indent_level": 1}
{"label": "Commission", "values": [1000], "indent_level": 1}
{"label": "Other operating income", "values": [800], "indent_level": 0}
{"label": "Total Revenue", "values": [11100], "indent_level": 0, "is_total": true}
```

**Step 2**: BGE matches "Total Revenue" → Model's "Revenue" (score: 0.96)

**Step 3**: Expression engine detects:
- "Total Revenue" is marked `is_total`
- Its children sum: 7200 + 2100 + 1000 = 10300 ≠ 11100
- But 10300 + 800 (Other operating income) = 11100 ✓
- Hmm, should "Other operating income" be included in Revenue or separate?

**Step 4**: AI checks model template:
- Model has BOTH "Revenue" AND "Other Income" as separate line items
- So: Revenue = Product_sales + Service_income + Commission = 10,300
- And: Other Income = Other_operating_income = 800

**Step 5**: Result:
```
Revenue     = Product sales + Service income + Commission
            = 7,200 + 2,100 + 1,000 = 10,300

Other Income = Other operating income = 800

Validation: Total Revenue in PDF (11,100) = Revenue + Other Income (10,300 + 800) ✓
```

### What the analyst sees:

```
┌──────────────────────────────────────────────────────────────┐
│ Revenue:  10,300  [HIGH ●]                                   │
│ Expression: Product sales (7,200) + Service income (2,100)   │
│           + Commission (1,000)                               │
│ Source: Page 4, lines 3-5                                    │
│                                                              │
│ Other Income:  800  [HIGH ●]                                 │
│ Expression: Other operating income (800)                     │
│ Source: Page 4, line 6                                       │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. How Different Banks Get Different Models

### Template Management Flow

```
Admin creates template once
        │
        ▼
┌─────────────────────────────────┐
│  "IFRS Corporate" Template      │
│  200 line items                 │
│  40 formulas                    │
│  12 validation rules            │
│  Per-item synonyms (EN/AR/FR)   │
└─────────┬───────────────────────┘
          │
          ├── Bank A uses this template (default)
          │
          ├── Bank B clones it → customizes 20 items
          │   └── Adds "Murabaha Income" (Islamic banking)
          │   └── Removes "Interest Income"
          │
          └── Bank C creates completely new template
              └── Different structure for SME lending
```

### Tenant-Specific Customization

```sql
-- Bank B's customization
INSERT INTO model_line_items (template_id, label, synonyms, zone_type, item_type)
VALUES
  ('bank_b_template', 'Murabaha Income',
   ARRAY['Murabaha Income', 'دخل المرابحة', 'Income from Murabaha'],
   'INCOME_STATEMENT', 'INPUT'),

  ('bank_b_template', 'Takaful Expense',
   ARRAY['Takaful Expense', 'مصروف التكافل', 'Islamic Insurance'],
   'INCOME_STATEMENT', 'INPUT');
```

### How mapping adapts per bank:

1. When Bank A uploads a PDF → system loads Bank A's template → maps against Bank A's line items
2. When Bank B uploads the SAME PDF → loads Bank B's template → maps against Bank B's different line items
3. The **VLM extraction is the same** (same tables, same values)
4. Only the **BGE matching target** changes (different line items, different synonyms)

---

## 5. How the System Learns

### From corrections → better synonyms

```
Analyst corrects: "Turnover" was mapped to "Other Income" → changed to "Revenue"

System learns:
1. Add "Turnover" to Revenue.synonyms[] if not already there
2. Remove "Turnover" from Other Income's matching candidates
3. Increase BGE confidence for "Turnover" ↔ "Revenue" pair
4. After 100 corrections → fine-tune client SBERT model (notebook 21)
```

### From expressions → pattern memory

```
Analyst builds: Revenue = Product sales + Service income + Commission

System learns:
1. Store this expression pattern for this customer
2. Next period: auto-apply same expression structure
3. If new PDF has same row labels → pre-fill expression
4. This is "Autofill" from Phase 1 (P1.6 in implementation_plan.md)
```

### Base Period Intelligence

The **biggest accuracy boost** comes from subsequent spreads:

```
First spread (no history):  AI accuracy = 75-85%
Second spread (same customer): AI accuracy = 92-95%
Tenth spread (same customer):  AI accuracy = 98%+
```

Because the system remembers: "For this customer, Revenue always = Product sales + Service income + Commission"

---

## 6. Expression Types Reference

| Expression | Example | When auto-detected |
|---|---|---|
| **Direct** | `Revenue = {row_3}` | 1:1 label match, high confidence |
| **Sum** | `Revenue = {row_3} + {row_4} + {row_5}` | Children sum to parent in PDF |
| **Negate** | `Finance Costs = NEG({row_12})` | PDF shows positive, model expects negative |
| **Absolute** | `Tax = ABS({row_15})` | PDF shows (parentheses), model needs positive |
| **Scale** | `Revenue = {row_3} × 1000` | PDF "in thousands", model in actual |
| **Cross-zone** | `Depreciation = {notes_row_7}` | Notes page has detail for I/S line |
| **Manual** | `Adjustment = MANUAL(500)` | Analyst types value not in document |
| **Constant** | `Shares = CONST(1000000)` | Fixed value from metadata |
| **Formula** | `Gross Margin = {Revenue} - {COGS}` | Model template formula (not from PDF) |

---

## 7. Implementation Priority

| Component | Where | Status |
|---|---|---|
| Model template DB schema | Kotlin backend | Not yet built |
| Template CRUD API | Kotlin backend | Not yet built |
| IFRS seed template (200 items) | `data/model_templates/` | Not yet created |
| Expression engine | ML service + Kotlin | Stub exists, needs full logic |
| Autofill (base period reuse) | ML service + Kotlin | Phase 1 |
| Zone → template section routing | ML service | ✅ Done (zone_classifier) |
| Semantic matching (rows → items) | ML service | ✅ Done (semantic_matcher) |
| Synonym management | Admin UI + backend | Phase 1 |
| Customer-level expression memory | Backend + ML | Phase 1 |

> [!IMPORTANT]
> The model template system is a **backend + database** feature, not an ML feature. The ML service provides the AI mapping intelligence, but the template storage, expression evaluation, and formula computation all live in the Kotlin backend with PostgreSQL.
