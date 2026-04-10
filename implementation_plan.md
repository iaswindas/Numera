# Numera — Detailed Implementation Plan

> **Product**: Numera — AI-First Financial Spreading & Covenant Intelligence Platform  
> **Founder**: Solo bootstrapped engineer  
> **Target Markets**: Europe & Middle East (IFRS-based)  
> **Pilot Target**: HSBC (via former VP contact)  
> **Timeline**: 18 months (6 phases)  
> **Start Date**: April 2026

---

## Strategic Context

### Why HSBC Is the Perfect Pilot

- **IFRS-first**: HSBC reports under IFRS — exactly what we're building for.
- **Dual-region**: Headquartered in London, massive Middle East operations (UAE, Saudi, Bahrain, Qatar). One client validates both target markets.
- **Scale**: HSBC's credit teams process thousands of corporate borrower financial statements annually. If Numera works for them, it works for anyone.
- **Warm intro**: A former VP who knows your engineering quality is worth more than 100 cold emails.

### Timeline to Pitch

```
Month 1-3:  Build Demo MVP
Month 3:    PITCH to HSBC VP → show autonomous spreading on a real HSBC-relevant financial statement
Month 4-6:  If interested → negotiate pilot terms while building Phase 1
Month 7:    Pilot goes live on production-ready spreading module
```

### IFRS & Regional Focus

Both Europe and Middle East use **IFRS** as the primary accounting standard. This simplifies training data strategy:

- **Training data sources**: 
  - London Stock Exchange (LSE) annual reports — FTSE 100/250 companies
  - Euronext (Amsterdam, Paris) filings
  - Abu Dhabi Securities Exchange (ADX), Dubai Financial Market (DFM) filings
  - SEC EDGAR (many dual-listed companies file IFRS)
- **Model template**: Build one IFRS-standard model template first. Region-specific variants (CBUAE reporting, PRA requirements) come in later phases.
- **Languages**: English first, Arabic RTL support in Phase 1.
- **Currencies**: Multi-currency from day one (EUR, GBP, AED, SAR, USD, CHF).

---

## Phase 0: Demo MVP (Weeks 1–12)

> **Goal**: A polished, end-to-end demo that makes the HSBC VP say *"This is what we need."*  
> **Demo scenario**: Upload an IFRS annual report PDF → AI autonomously extracts tables, classifies zones, maps values to a financial model → analyst reviews with confidence-coded cells → submits.

---

### P0.1 — Project Scaffolding (Week 1)

#### P0.1.1 Repository & Monorepo Setup
- [ ] Create GitHub/GitLab private monorepo: `numera`
- [ ] Structure:
  ```
  numera/
  ├── frontend/          # React + TypeScript
  ├── backend/           # Kotlin + Spring Boot
  ├── ml-service/        # Python + FastAPI
  ├── infra/             # Docker Compose, Kubernetes configs
  ├── ml-training/       # Training scripts, data pipelines
  ├── docs/              # Architecture docs, API specs
  └── data/              # Sample documents, model templates
  ```
- [ ] Set up Docker Compose for local development (PostgreSQL, Redis, MinIO, all services)
- [ ] CI pipeline: GitHub Actions for build + test on every push

#### P0.1.2 Backend Scaffold (Kotlin + Spring Boot)
- [ ] Initialize Spring Boot 3 project with:
  - Spring Web (REST controllers)
  - Spring Security (JWT authentication)
  - Spring Data JPA + Hibernate (PostgreSQL)
  - Spring Validation
  - Flyway (database migrations)
  - Jackson (JSON serialization)
  - Kotlin coroutines support
- [ ] Configure application profiles: `dev`, `staging`, `prod`
- [ ] Set up database connection with HikariCP connection pool
- [ ] Create base entity classes: `BaseEntity` (id, createdAt, updatedAt, createdBy)
- [ ] Configure global exception handling and standardized API error responses
- [ ] Set up OpenAPI/Swagger documentation

#### P0.1.3 Frontend Scaffold (React)
- [ ] Initialize with Vite + React 19 + TypeScript
- [ ] Install and configure:
  - Tailwind CSS 4
  - Shadcn UI (install core components: Button, Input, Dialog, Table, Tabs, Toast)
  - React Router v7 (routing)
  - Zustand (state management)
  - Axios (HTTP client)
  - React Query / TanStack Query (server state, caching)
- [ ] Set up folder structure:
  ```
  frontend/src/
  ├── components/        # Reusable UI components
  │   ├── ui/            # Shadcn components
  │   └── shared/        # App-specific shared components
  ├── features/          # Feature-based modules
  │   ├── auth/
  │   ├── spreading/
  │   ├── file-store/
  │   └── models/
  ├── layouts/           # Page layouts (sidebar, header)
  ├── hooks/             # Custom React hooks
  ├── stores/            # Zustand stores
  ├── services/          # API service layer
  ├── types/             # TypeScript types/interfaces
  └── utils/             # Helper functions
  ```
- [ ] Create app shell: sidebar navigation, header with user info, main content area
- [ ] Set up Axios interceptor for JWT token attachment and refresh

#### P0.1.4 ML Service Scaffold (Python + FastAPI)
- [ ] Initialize FastAPI project with:
  - Uvicorn (ASGI server)
  - Pydantic v2 (request/response models)
  - PaddleOCR library
  - PaddlePaddle
  - sentence-transformers (Sentence-BERT)
  - Pillow, pdf2image, PyMuPDF (document processing)
- [ ] Create endpoint structure:
  ```
  POST /api/ml/ocr/extract          # OCR text + layout extraction
  POST /api/ml/tables/detect        # Table detection in document
  POST /api/ml/zones/classify       # Zone classification for detected tables
  POST /api/ml/mapping/suggest      # Suggest line-item mappings
  GET  /api/ml/health               # Health check + model status
  ```
- [ ] Set up model loading on startup (lazy loading for large models)
- [ ] Configure CORS for backend communication

---

### P0.2 — Authentication & Core Data Model (Week 2)

#### P0.2.1 Authentication (Simple for Demo)
- [ ] **Backend**: JWT-based form authentication
  - `POST /api/auth/login` — email + password → JWT access token + refresh token
  - `POST /api/auth/refresh` — refresh token → new access token
  - `POST /api/auth/logout` — invalidate refresh token
  - Access token expiry: 15 minutes. Refresh token: 7 days.
  - Password hashing with BCrypt
- [ ] **Frontend**: Login page with email/password form
  - Store JWT in httpOnly cookie (secure) or in-memory (for demo, localStorage is acceptable)
  - Protected route wrapper component
  - Auto-redirect to login on 401

#### P0.2.2 Database Schema — Core Tables
- [ ] Flyway migration V1:
  ```sql
  -- Users & Auth
  users (id, email, password_hash, full_name, role, status, created_at, updated_at)
  
  -- Customers (borrowers whose financials are being spread)
  customers (id, entity_id, long_name, short_name, financial_year_end, 
             source_currency, target_currency, group_id, status, created_at, updated_at)
  
  -- Financial Models (templates)
  model_templates (id, name, standard, version, description, status, created_at)
  model_line_items (id, template_id, parent_id, label, item_type, formula, 
                    display_order, is_hidden, is_grouped, cell_type, created_at)
  -- cell_type: INPUT, FORMULA, VALIDATION, CATEGORY
  
  -- Customer Model Instances (copy of template per customer)
  customer_models (id, customer_id, template_id, version, created_at)
  customer_model_items (id, customer_model_id, line_item_id, custom_label, 
                        is_hidden, is_grouped, created_at)
  
  -- Documents
  documents (id, filename, original_filename, file_type, file_size, language,
             storage_path, ocr_status, processing_status, uploaded_by, created_at)
  
  -- Spread Items (a financial period to be spread)
  spread_items (id, customer_id, customer_model_id, document_id, 
                statement_date, audit_method, statement_type, frequency,
                source_currency, consolidation, status, locked_by, locked_at,
                created_by, created_at, updated_at)
  -- status: DRAFT, SUBMITTED, APPROVED, PUSHED
  -- frequency: MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL
  
  -- Spread Values (mapped cell values)
  spread_values (id, spread_item_id, line_item_id, mapped_value, expression,
                 confidence_score, source_page, source_coordinates, 
                 is_manual_override, version, created_at, updated_at)
  
  -- Spread Versions (Git-like snapshots)
  spread_versions (id, spread_item_id, version_number, snapshot_data, 
                   action, comments, created_by, created_at)
  -- action: CREATED, SAVED, SUBMITTED, APPROVED, ROLLED_BACK
  
  -- Detected Zones (AI-detected tables in documents)
  detected_zones (id, document_id, page_number, zone_type, zone_label,
                  bounding_box, confidence_score, account_column, 
                  value_columns, header_row, status, created_at)
  -- zone_type: INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW, NOTES, OTHER
  ```

#### P0.2.3 Seed Data
- [ ] Create seed script:
  - Default admin user (admin@numera.ai / temporary password)
  - Demo analyst user
  - One IFRS model template with ~150 line items covering:
    - Balance Sheet (Assets, Liabilities, Equity)
    - Income Statement (Revenue → Net Income)
    - Cash Flow Statement (Operating, Investing, Financing)
  - One sample customer record

---

### P0.3 — Document Processing Pipeline (Weeks 3–5)

#### P0.3.1 Document Upload & Storage
- [ ] **Backend API**:
  - `POST /api/documents/upload` — multipart file upload
    - Validate file type (PDF, DOCX, XLSX, JPG, PNG, TIFF)
    - Validate file size (max 50MB)
    - Store in MinIO (S3-compatible) object storage
    - Create `documents` DB record with status = `UPLOADED`
    - Return document ID and processing status
  - `GET /api/documents/{id}` — document metadata
  - `GET /api/documents/{id}/download` — download original file
  - `GET /api/documents/{id}/pages/{page}` — render specific page as image
- [ ] **Frontend**: File upload component
  - Drag-and-drop zone with file type validation
  - Upload progress bar
  - Language selector dropdown (English default, Arabic option)
  - Processing status indicator (Uploaded → Processing → Ready → Error)

#### P0.3.2 OCR Processing (PaddleOCR Integration)
- [ ] **ML Service endpoint**: `POST /api/ml/ocr/extract`
  - Input: Document storage path
  - Processing pipeline:
    1. Convert PDF pages to images (pdf2image / PyMuPDF at 300 DPI)
    2. Run PaddleOCR on each page:
       - Text detection (DB algorithm)
       - Text recognition (CRNN algorithm)
       - Layout analysis
    3. For each detected text block, return:
       - Text content
       - Bounding box coordinates (x, y, width, height)
       - Confidence score
       - Page number
  - Output: JSON with structured OCR results per page
- [ ] Store OCR results as JSON in object storage (linked to document record)
- [ ] **Backend orchestration**: When document is uploaded:
  1. Save to storage → status = `UPLOADED`
  2. Call ML service OCR endpoint (async via message queue or simple HTTP)
  3. Store results → status = `OCR_COMPLETE`
  4. Trigger table detection → status = `PROCESSING`
  5. Complete → status = `READY`

#### P0.3.3 Table Detection (PP-StructureV2)
- [ ] **ML Service endpoint**: `POST /api/ml/tables/detect`
  - Input: Document ID + OCR results
  - Use PaddlePaddle's PP-StructureV2 pre-trained model:
    - Detects table regions in each page
    - Extracts table structure (rows, columns, cells)
    - Identifies header rows vs data rows
  - For each detected table, return:
    - Page number
    - Table bounding box
    - Cell grid: array of rows, each row = array of cells
    - Each cell: text content, bounding box, row_span, col_span
    - Detected column types: ACCOUNT (text), VALUE (numeric), OTHER
  - Output: Array of structured table objects

#### P0.3.4 Zone Classification
- [ ] **ML Service endpoint**: `POST /api/ml/zones/classify`
  - Input: Detected table (cell grid + text content)
  - Classification approach (Phase 0 — heuristic + lightweight ML):
    1. **Keyword heuristic** (fast, high precision):
       - If table contains "Total Assets", "Total Liabilities", "Total Equity" → BALANCE_SHEET
       - If table contains "Revenue", "Net Income", "Operating Profit" → INCOME_STATEMENT
       - If table contains "Cash from Operations", "Cash from Investing" → CASH_FLOW
       - If table contains "Note", "Schedule" → NOTES
    2. **LayoutLM-based classifier** (fallback for ambiguous cases):
       - Pre-trained LayoutLM (HuggingFace `microsoft/layoutlm-base-uncased`)
       - Fine-tuned on 200-500 IFRS annual reports from SEC EDGAR / LSE
       - Input: Table text + layout coordinates → Output: Zone type + confidence
  - Also detect:
    - **Reporting period/year**: Parse column headers for dates (e.g., "2024", "31 Dec 2024", "FY2024")
    - **Currency**: Look for currency symbols or labels (€, £, AED, "in thousands of USD")
    - **Unit scale**: Detect "in thousands", "in millions", etc.
  - Output per table: zone_type, zone_label, confidence, detected_periods, detected_currency, detected_unit

#### P0.3.5 Pipeline Orchestration
- [ ] **Backend**: Create `DocumentProcessingService`:
  ```kotlin
  class DocumentProcessingService {
      fun processDocument(documentId: UUID) {
          // 1. Update status → PROCESSING
          // 2. Call ML OCR → store results
          // 3. Call ML table detection → store results
          // 4. Call ML zone classification → store results
          // 5. Create detected_zones DB records
          // 6. Update status → READY
          // Error handling → status = ERROR with error message
      }
  }
  ```
- [ ] For demo: Synchronous processing is fine (async queue comes in Phase 1)
- [ ] Processing time target: < 60 seconds for a 30-page PDF on CPU

---

### P0.4 — Autonomous Mapping Engine (Weeks 5–7)

#### P0.4.1 Line-Item Semantic Matching
- [ ] **ML Service endpoint**: `POST /api/ml/mapping/suggest`
  - Input: 
    - Array of source rows (from detected table: row text + zone type)
    - Array of target model line items (from financial model template)
  - Matching pipeline:
    1. **Embedding generation**:
       - Encode all source row texts using Sentence-BERT (`all-MiniLM-L6-v2`)
       - Encode all target model line item labels using same model
       - Pre-compute and cache model line item embeddings (they don't change)
    2. **Cosine similarity scoring**:
       - For each source row, compute cosine similarity against all target items
       - Filter by zone context (a Balance Sheet row should only match BS model items)
       - Return top-3 candidates with similarity scores
    3. **Confidence classification**:
       - Score ≥ 0.85 → HIGH confidence (auto-accept / green)
       - Score 0.65–0.84 → MEDIUM confidence (suggest but flag / amber)  
       - Score < 0.65 → LOW confidence (unresolved / red)
    4. **Expression building**:
       - For HIGH/MEDIUM matches: Extract the numeric value from the matched source cell
       - Build expression: `=PAGE3.R12.C4` (reference to page, row, column)
       - Detect sign (positive/negative) from source context
       - Apply unit scale conversion if document unit differs from model unit
  - Output per source row:
    ```json
    {
      "source_row_text": "Revenue from contracts with customers",
      "source_value": "1,234,567",
      "source_page": 3,
      "source_coordinates": {"x": 450, "y": 212, "w": 80, "h": 16},
      "suggested_mappings": [
        {
          "target_line_item_id": "uuid",
          "target_label": "Revenue",
          "confidence": 0.92,
          "confidence_level": "HIGH",
          "expression": "1234567",
          "adjustments": {"unit_scale": "thousands", "sign": "positive"}
        }
      ]
    }
    ```

#### P0.4.2 Backend Mapping Orchestration
- [ ] **API**: `POST /api/spread-items/{id}/auto-map`
  - Triggered after document processing completes
  - Flow:
    1. Fetch all detected zones for the document
    2. Fetch the customer's financial model line items
    3. For each zone, call ML mapping service
    4. Store suggested mappings in `spread_values` table with confidence scores
    5. Return summary: X mapped (high), Y flagged (medium), Z unresolved (low)
- [ ] **API**: `GET /api/spread-items/{id}/mappings`
  - Return all mappings grouped by zone, with confidence levels
- [ ] **API**: `PUT /api/spread-items/{id}/mappings/{mappingId}`
  - Analyst overrides: accept, reject, remap to different line item, edit expression
  - Store override with `is_manual_override = true`
  - Log correction for ML feedback

#### P0.4.3 ML Feedback Collection
- [ ] **API**: `POST /api/ml/feedback`
  - When analyst corrects a mapping, send:
    - Source text, source zone type
    - Original suggestion (line item ID, confidence)
    - Correction (new line item ID)
  - Store in `ml_feedback` table:
    ```sql
    ml_feedback (id, source_text, source_zone_type, suggested_item_id,
                 corrected_item_id, document_id, customer_id, created_by, created_at)
    ```
  - Batch retraining uses this table (implemented in Phase 1)

---

### P0.5 — Spreading Workspace UI (Weeks 6–9)

#### P0.5.1 Document Viewer (Left Pane)
- [ ] PDF rendering using **PDF.js**
  - Page-by-page rendering with zoom (50%–300%), pan, scroll
  - Page thumbnail sidebar for navigation
  - Current page indicator
- [ ] **Zone overlay layer** (HTML Canvas or SVG overlay on top of PDF):
  - Draw colored bounding boxes around detected zones
  - Color coding: Blue = Balance Sheet, Green = Income Statement, Orange = Cash Flow, Purple = Notes
  - Zone label badges (e.g., "Income Statement (94%)")
  - Clickable zones: clicking a zone scrolls the model pane to the corresponding section
- [ ] **Value highlight overlays**:
  - When a model cell is selected, highlight the source value in the PDF
  - Mapped values shown with green dots, unmapped with gray
- [ ] **Manual zone adjustment** (fallback):
  - Draw rectangle tool for adding missed zones
  - Resize handles on existing zone boundaries
  - Zone type selector dropdown
  - Delete zone button

#### P0.5.2 Model Grid (Right Pane)
- [ ] Build using **Jspreadsheet CE** (open-source) or custom React table:
  - Columns: Line Item Label | Mapped Value | Expression | Confidence | Source
  - Row grouping by category (Assets, Liabilities, etc.) with expand/collapse
  - Category navigation bubbles (floating nav for quick jump between sections)
- [ ] **Confidence coding**:
  - Cell background: Green (≥90%), Amber (70-89%), Red (<70%), White (unmapped)
  - Confidence badge showing exact percentage on hover
- [ ] **Cell interaction**:
  - Click on green cell → show mapping details in tooltip (source text, page, coordinates)
  - Click on amber cell → show top-3 suggestions in dropdown, click to accept one
  - Click on red cell → enter "mapping mode": click a value in the PDF to map it
  - Double-click any cell → open expression editor
- [ ] **Expression editor** (modal or inline):
  - Visual display of expression components (e.g., `[Page 3, Row 12] + [Page 5, Row 8]`)
  - Operator buttons: +, -, ×, ÷
  - Adjustment buttons: Abs, Neg, Contra, Unit Scale
  - Live preview of computed value
- [ ] **Bulk actions**:
  - "Accept All High Confidence" button → bulk accept all green cells
  - "Reset All" → clear all mappings

#### P0.5.3 Workspace Layout & Coordination
- [ ] Split-pane layout with draggable divider (left = document, right = model)
- [ ] **Bidirectional navigation**:
  - Click model cell → PDF scrolls to source location and highlights value (Doc Navigation)
  - Click PDF value → model scrolls to mapped cell and highlights it
- [ ] **Toolbar**:
  - Map Mode toggle (single-click vs double-click mapping)
  - Autofill trigger button
  - Save as Draft button
  - Submit Spread button
  - Zoom controls for PDF
- [ ] **Status bar**: 
  - Mapping progress: "87/142 cells mapped (61%)"
  - Confidence summary: "72 auto-accepted, 10 flagged, 5 unresolved"

#### P0.5.4 Spread Lifecycle (Demo Version)
- [ ] **Save as Draft**: `PUT /api/spread-items/{id}/draft`
  - Save all current mappings, create version snapshot
- [ ] **Submit Spread**: `POST /api/spread-items/{id}/submit`
  - Validate: All required cells must be mapped
  - Run validation checks (Assets = Liabilities + Equity)
  - Show validation results popup (Passed/Failed with differences)
  - If passed → status = SUBMITTED, create version snapshot
  - If failed → show warnings, allow override with comment
- [ ] **Validation Panel**:
  - List of validation rules with Pass/Fail status
  - Show computed difference for each validation
  - Click on failed validation → highlight relevant cells in model

---

### P0.6 — IFRS Model Template (Week 8)

#### P0.6.1 Build IFRS Model Template
- [ ] Create comprehensive IFRS financial model with ~200 line items:
  
  **Balance Sheet** (~80 items):
  - Current Assets: Cash, Short-term investments, Trade receivables, Inventories, Prepayments, Other current assets
  - Non-Current Assets: PPE (Land, Buildings, Equipment, Vehicles, Accumulated depreciation), Intangible assets (Goodwill, Software, Patents), Investment property, Right-of-use assets, Deferred tax assets, Other non-current assets
  - Current Liabilities: Trade payables, Short-term borrowings, Current portion of long-term debt, Accrued expenses, Tax payable, Provisions, Other current liabilities
  - Non-Current Liabilities: Long-term borrowings, Bonds payable, Lease liabilities, Deferred tax liabilities, Pension obligations, Other non-current liabilities
  - Equity: Share capital, Share premium, Retained earnings, Other reserves, Treasury shares, Non-controlling interests
  - Validation: Total Assets = Total Liabilities + Total Equity

  **Income Statement** (~50 items):
  - Revenue, Cost of sales, Gross profit
  - Distribution costs, Administrative expenses, Other operating income/expenses
  - Operating profit (EBIT)
  - Finance income, Finance costs, Net finance costs
  - Share of profit from associates
  - Profit before tax, Income tax expense, Profit after tax
  - Attributable to: Owners of parent, Non-controlling interests
  - EPS: Basic, Diluted

  **Cash Flow Statement** (~50 items):
  - Operating activities (indirect method): Profit before tax, adjustments (depreciation, amortization, impairment, finance costs, etc.), working capital changes
  - Investing activities: Purchase/sale of PPE, intangibles, investments, subsidiaries
  - Financing activities: Proceeds/repayment of borrowings, dividends, share transactions
  - Net change in cash, Opening cash, Closing cash
  - Validation: Closing cash = Balance Sheet cash

  **Key Ratios** (~20 items):
  - Current Ratio, Quick Ratio, Debt/Equity, Debt/EBITDA, Interest Coverage (DSCR)
  - ROE, ROA, Net Profit Margin, Gross Margin, Operating Margin
  - Asset Turnover, Inventory Days, Receivable Days, Payable Days

- [ ] Store as seed data in database migration
- [ ] Include formula definitions for all computed cells

---

### P0.7 — ML Training on Public Data (Weeks 3–10, parallel track)

> [!IMPORTANT]
> This runs in parallel with the application development. Use Google Colab free tier + Kaggle notebooks for all training.

#### P0.7.1 Training Data Collection
- [ ] **SEC EDGAR pipeline**:
  - Script to download 10-K filings (annual reports) via EDGAR full-text search API
  - Target: 500 companies, 2 years each = ~1,000 annual reports
  - Download both PDF and XBRL versions
  - XBRL provides structured labels (concept → value mappings) = free ground truth
- [ ] **European reports**:
  - Download 100 IFRS annual reports from FTSE 100 companies (investor relations pages)
  - Download 50 GCC (Gulf) company annual reports (ADX, DFM listed companies)
  - These provide regional financial statement formatting patterns
- [ ] **Data preparation**:
  - Convert all PDFs to page images (300 DPI)
  - Run PaddleOCR on all pages → store OCR results
  - Run PP-StructureV2 → extract all tables
  - Manually verify zone labels for 200 documents (your manual annotation effort)
  - For EDGAR docs: auto-label using XBRL concept mappings

#### P0.7.2 Zone Classification Model Training
- [ ] **Model**: Fine-tune `microsoft/layoutlm-base-uncased` on Colab
  - Input: Table text + layout bounding boxes
  - Output: Zone type (BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, NOTES_FIXED_ASSETS, NOTES_RECEIVABLES, NOTES_DEBT, NOTES_OTHER)
  - Training set: 800 annotated tables from EDGAR/LSE/GCC reports
  - Validation set: 200 tables
  - Target accuracy: ≥ 92% on validation set
- [ ] Export model → package for inference in ML service

#### P0.7.3 Semantic Matching Model Calibration
- [ ] **Sentence-BERT calibration**:
  - Pre-trained `all-MiniLM-L6-v2` works well out of the box for English
  - Create evaluation set: 500 (source_text, target_label) pairs from XBRL mappings
  - Measure baseline accuracy
  - If < 85%, fine-tune on IFRS-specific financial term pairs using Colab
- [ ] **Synonym expansion**:
  - Build initial taxonomy of ~500 IFRS financial terms with synonyms
  - Source: IFRS Taxonomy published by IFRS Foundation (free, public)
  - Examples: "Revenue" ↔ "Turnover" ↔ "Net Sales" ↔ "Total Income"

#### P0.7.4 Period/Year Detection Model
- [ ] Rule-based parser for column headers:
  - Regex patterns for dates: `\d{4}`, `\d{1,2}[-/]\d{1,2}[-/]\d{2,4}`, `FY\d{4}`, `Q[1-4]\s*\d{4}`
  - Named month patterns: "December 2024", "31 Dec 2024", "Year ended 31 December 2024"
  - Relative period detection: "Current Year", "Prior Year"
- [ ] Currency/unit parser:
  - Symbol detection: €, £, $, AED, SAR, CHF
  - Text detection: "in thousands", "in millions", "in '000s", "(€m)"

---

### P0.8 — Demo Polish & Pitch Preparation (Weeks 10–12)

#### P0.8.1 Demo Flow
- [ ] Prepare 3 demo documents:
  1. A clean FTSE 100 annual report (e.g., Unilever, BP — publicly available)
  2. A GCC company annual report (e.g., Emirates NBD, ENOC)
  3. A challenging document (scanned, slightly rotated, multi-column notes)
- [ ] Optimize processing speed for demo (pre-cache OCR results if needed)
- [ ] Build a clean "onboarding" flow:
  1. Login screen → Dashboard (empty state with "Upload your first document" CTA)
  2. Click upload → drag-drop PDF → processing indicator
  3. Processing complete → navigate to Spreading Workspace
  4. Show AI-mapped spread with confidence colors → accept/correct → submit

#### P0.8.2 UI Polish
- [ ] Dark theme option (bankers love dark themes)
- [ ] Professional typography (Inter font family)
- [ ] Loading states with branded skeleton screens
- [ ] Toast notifications for actions (saved, submitted, error)
- [ ] Branded logo and "Numera" wordmark in sidebar
- [ ] Empty states with helpful illustrations

#### P0.8.3 Dashboard (Basic)
- [ ] Simple dashboard showing:
  - Recent spreads with status badges
  - Quick stats: Total spreads, Average AI accuracy, Documents processed
  - Quick action: "Upload New Document" button

#### P0.8.4 Pitch Deck Preparation
- [ ] Prepare a 10-slide pitch deck:
  1. Problem: Manual spreading wastes analyst hours
  2. Solution: Numera — AI-first autonomous spreading
  3. Demo: Live walkthrough (not screenshots)
  4. Differentiators vs Spreadsmart (the table from our spec)
  5. Architecture: Enterprise-grade (Kotlin/Spring Boot, not a prototype)
  6. Security: Encryption, audit trails, hybrid deployment ready
  7. Roadmap: Covenants, predictive intelligence, LLM copilot (coming)
  8. Pricing: Pilot program proposal
  9. Team: Solo founder + plan to hire (shows lean efficiency)
  10. Ask: 6-month pilot engagement

---

## Phase 1: Production-Ready Spreading (Weeks 13–32)

> **Goal**: Transform demo MVP into a system that a real bank can use daily in production, with all spreading features from the original scope.

---

### P1.1 — Authentication & Security Hardening (Weeks 13–15)

#### P1.1.1 SSO Integration
- [ ] Implement SAML 2.0 support using Spring Security SAML extension
  - Configure IdP metadata URL
  - SP (Service Provider) metadata generation
  - Assertion consumer service endpoint
  - Single logout support
- [ ] Implement OpenID Connect (OIDC) support
  - Authorization code flow
  - Token validation + user info endpoint
  - Support for Azure AD, Okta, Ping Identity
- [ ] SSO + Form auth coexistence: configurable per tenant
- [ ] Account approval workflow:
  - User authenticates via SSO → if not in Numera DB → create PENDING account
  - Admin receives notification → approves/rejects with role assignment
  - User states: PENDING → APPROVED / REJECTED → ACTIVE / INACTIVE

#### P1.1.2 Multi-Factor Authentication
- [ ] TOTP-based MFA (RFC 6238)
  - QR code generation for authenticator app setup
  - Backup recovery codes (10 one-time codes)
  - Mandatory for Admin role, configurable for others
- [ ] MFA enforcement per role via admin configuration

#### P1.1.3 Session & Security Management
- [ ] Configurable session timeout (default 30 min idle)
- [ ] Concurrent session limits (configurable: default 1 per user)
- [ ] Force-logout capability for admin
- [ ] Login audit log: IP, timestamp, success/failure, device info
- [ ] Password policies (when form auth is used): min length, complexity, history, expiry

---

### P1.2 — RBAC & Multi-Tenancy (Weeks 14–16)

#### P1.2.1 Full RBAC Implementation
- [ ] Database schema:
  ```sql
  roles (id, tenant_id, name, description, is_system_role, created_at)
  permissions (id, module, action, resource)  
  -- module: SPREADING, COVENANTS, ADMIN, REPORTING, FILE_STORE
  -- action: CREATE, READ, UPDATE, DELETE, APPROVE, SUBMIT
  role_permissions (role_id, permission_id)
  user_roles (user_id, role_id)
  ```
- [ ] Pre-defined system roles: Admin, Analyst (Maker), Manager (Checker), Global Manager, Auditor
- [ ] Custom role creation per tenant
- [ ] Permission checks as Spring Security method-level annotations
- [ ] **Is RM (Reporting Manager)** flag on user profile → enables Checker capabilities

#### P1.2.2 Multi-Tenancy
- [ ] Schema-per-tenant approach:
  - Each tenant gets a separate PostgreSQL schema
  - Shared `public` schema for tenant registry and global config
  - Tenant resolution from JWT claims or subdomain
- [ ] Tenant isolation: queries scoped to tenant schema automatically via Hibernate filters
- [ ] Tenant onboarding API: create schema, seed default data (roles, model templates)

#### P1.2.3 Group-Based Customer Visibility
- [ ] Database:
  ```sql
  groups (id, tenant_id, name, description)
  user_groups (user_id, group_id)
  customer_groups (customer_id, group_id)
  ```
- [ ] Users see only customers belonging to their assigned groups
- [ ] Managers see all customers in their groups; Global Managers see all

---

### P1.3 — File Store (Full Implementation) (Weeks 16–18)

#### P1.3.1 Bulk Upload & Pre-Processing
- [ ] File Store page with three tabs: My Files, All Files, Error Files
- [ ] Bulk upload: Select multiple files + language → upload all
- [ ] Background processing queue:
  - Implement with Spring's `@Async` + `ThreadPoolTaskExecutor` (or RabbitMQ if needed)
  - Process uploaded files sequentially per tenant
  - Update processing status in real-time (WebSocket or polling)
- [ ] File states: UPLOADED → PROCESSING → READY → MAPPED → ERROR
- [ ] Error handling: Capture error reason (password-protected, corrupt, unsupported language, OCR failure)

#### P1.3.2 File Management
- [ ] Search and filter: by filename, upload date, status, uploader
- [ ] Download original file (click on filename)
- [ ] Download processed/searchable file (download icon)
- [ ] Password-protected file handling: lock icon → password entry modal
- [ ] Delete file (disabled if mapped to a customer spread)

#### P1.3.3 Map Files to Customers
- [ ] "Map to Customer" workflow:
  1. Select files from My Files / New Files (unmapped) / All Files tabs
  2. **Recommended Files** tab: AI predicts which customer a file belongs to (based on content analysis)
  3. Shortlist files → click "Map to Customer"
  4. Fill metadata form (statement date, audit method, frequency, etc.)
  5. Document Classification: AI auto-populates metadata fields from document content
  6. Analyst validates AI predictions → clicks "Save and View Items"
- [ ] Navigate to Existing Items page with newly mapped item

---

### P1.4 — Customer Management (Weeks 17–18)

#### P1.4.1 Search Customer
- [ ] Search page with filters: Long Name, Entity ID
- [ ] Search results table: Customer Name, Entity ID, Year End, Group, Status, Actions
- [ ] Group-based visibility enforced (users only see their group's customers)
- [ ] Integration hook: Customer data can sync from external systems via adapter (Phase 5)

#### P1.4.2 Modify Customer
- [ ] Edit customer: Financial Year End, group assignment, metadata
- [ ] Customer creation: manual entry or external system sync
- [ ] Customer status: Active / Inactive

#### P1.4.3 Existing Items (Spread Items Listing)
- [ ] Per-customer page listing all financial statement items
- [ ] Table columns: Statement Date, Frequency, Audit Method, Status, Spread Type, Actions
- [ ] Actions: Spread, Read Only, Override, Duplicate, Add Item
- [ ] Status badges with colors: Draft (gray), Submitted (blue), Approved (green), Pushed (purple)
- [ ] "Migrated from CL" indicator for externally-imported historical spreads (Phase 5)

---

### P1.5 — Spreading Workspace (Full Feature Set) (Weeks 18–24)

#### P1.5.1 Exclusive Locking
- [ ] When analyst clicks "Spread" on an item:
  - Check `locked_by` field — if another user holds the lock, show banner: *"Currently being edited by [User X] since [time]. View mode only."*
  - If unlocked, acquire lock: set `locked_by = current_user`, `locked_at = now()`
  - Lock expiry: auto-release after 30 minutes of inactivity
  - Redis-based lock with TTL for distributed safety
- [ ] Read-only mode: Identical UI but all edit controls disabled, banner at top

#### P1.5.2 Expression Builder (Full)
- [ ] Operators: +, −, ×, ÷
- [ ] Adjustment factors:
  - **Unit Scale**: Multiply or divide mapped values (actual ↔ thousands ↔ millions)
  - **Conversion**: Apply conversion factor to specific values
  - **Absolute**: Abs(-1) = 1
  - **Negative**: Neg(5) = -5
  - **Contra**: Multiply by -1
- [ ] Constants and keyboard entry:
  - Manual numeric entry for values not in the document
  - Keyboard operators between mapped value and constant
- [ ] Expression display: Visual formula bar showing each component with source references
- [ ] Expression editing: Click on any component to replace or delete it

#### P1.5.3 OCR Error Correction
- [ ] If a detected value has low OCR confidence, highlight in red on PDF
- [ ] Click to edit: inline text correction
- [ ] Visual cue: corrected value shown above the line item in the PDF view
- [ ] Corrections persist for subsequent spreads (stored per customer)

#### P1.5.4 Document Viewer — Full Features
- [ ] Page merging: Merge two pages into one combined page (appended to end of document)
- [ ] Split view: Two document panes for mapping from different pages simultaneously
- [ ] Split page: Split a book-view (double-page) into two single pages
- [ ] Rotate page: 90°, 180°, 270° rotation
- [ ] Clean file: Despeckle (low/high), watermark removal
- [ ] Links button: Download all documents uploaded for this spread item
- [ ] Information icon: Hover help for all icons and symbols

#### P1.5.5 Model Grid — Full Features
- [ ] Show/Hide rows: Toggle to show only rows with mapped values
- [ ] Show/Hide SmartFill: Highlight duplicate line items matching base period or taxonomy autofill
- [ ] Show Variance: Additional column comparing current period vs base period with variance highlighting
- [ ] Show Currency: Two additional columns for currency and unit per value
- [ ] Account Category Bubbles: Draggable floating nav for quick section jump
- [ ] Model editing (limited):
  - Right-click context menu: Group/Ungroup rows, Hide/Unhide rows, Freeze/Unfreeze rows
  - Customer-specific model modifications (don't affect global template)
- [ ] Navigation Tab → Model to Doc: Click cell → show source details (line item, value, page, coordinates)
- [ ] Navigation Tab → Doc to Model: Click PDF value → show all model cells mapped from this page

#### P1.5.6 Metadata Editing (2-Way Sync Ready)
- [ ] Expandable metadata panel on model pane:
  - Editable fields: Statement Date, Audit Method, Statement Type (Frequency), Source Currency, Consolidation
  - Read-only fields: Target Currency (default AED or configurable), Unit (synced from external system)
  - Submit button to save metadata changes
- [ ] Changes flagged for sync to external systems (actual sync in Phase 5)

#### P1.5.7 Auto-Generated Comments
- [ ] For every mapped cell, auto-generate comment containing:
  - Source PDF name
  - Page number
  - Line-item name from document
  - Extracted value
  - Clickable URL that navigates to the exact location in the document
- [ ] Manual comment addition per cell
- [ ] Export spread with comments for offline audit
- [ ] Comment URLs work without Numera login (public shareable link to document location)

#### P1.5.8 Load Historical Spreads
- [ ] "Load Spread" under Utilities menu
  - Select from previously submitted spreads for this customer
  - Load up to 20 historical periods as comparison columns in the model grid
  - Shows values side-by-side for trend analysis

---

### P1.6 — Subsequent Spreading & Autofill (Weeks 22–26)

#### P1.6.1 Base Period Selection
- [ ] Auto-select: Most recent same-frequency submitted spread
- [ ] Manual override: "Base Spread" selector in toolbar
- [ ] Warning: Changing base period clears all current mappings

#### P1.6.2 AI Autofill (Enhanced)
- [ ] When analyst works on a subsequent period for the same customer:
  1. System auto-selects base spread
  2. Detects zones in new document
  3. For each zone: match against base period zones by type
  4. For each row in new zone: semantic match against base period row labels
  5. If match found with high confidence → auto-map value from new document
  6. Apply same expressions/adjustments from base period
- [ ] Autofill options: "Current Page" or "All Pages"
- [ ] Duplicate cell handling: Same label in different zones resolved by zone context
- [ ] Error log: Auto-generated warnings for:
  - Missing mappings (base period had value, new period doesn't)
  - New line items (new period has rows not in base period)
  - Value anomalies (>50% change from base period)

#### P1.6.3 Notes Processing (Free Text Extraction)
- [ ] For values embedded in paragraphs (not tables):
  - AI scans document text for financial keywords (depreciation, rent, etc.)
  - Highlights paragraphs containing financial data
  - Shows predictions in "Other Adjustments" panel with page number
  - Click page number → navigate to highlighted paragraph
  - Manual drag-drop mapping from paragraph to model cell
- [ ] Autofill Keywords button: Auto-map predicted keyword values
- [ ] Keyword training: When analyst maps a free-text value, log for ML retraining

#### P1.6.4 Submit & Continue
- [ ] For multi-period documents (one PDF with multiple years):
  - Submit current period spread
  - System auto-creates next period item with same document
  - Navigate directly to new spread with autofill pre-populated

---

### P1.7 — Spread Version Control (Weeks 24–26)

#### P1.7.1 Git-Like Versioning
- [ ] Every action creates an immutable snapshot:
  - `DRAFT_SAVED` — auto-save or manual save
  - `SUBMITTED` — submitted for approval
  - `APPROVED` — checker approved
  - `OVERRIDDEN` — submitted as override of previous
  - `ROLLED_BACK` — reverted to a previous version
- [ ] Snapshot data: Full JSON serialization of all spread values + metadata
- [ ] Version listing: Chronological list with actor, action, timestamp, comments
- [ ] **Diff view**: Side-by-side comparison of any two versions
  - Highlight added, removed, and changed values
  - Show changed expressions
- [ ] **Rollback**: Restore any previous version as the current working state

#### P1.7.2 Override vs Duplicate
- [ ] **Override**: Submit changes to the same statement ID in external system
- [ ] **Duplicate**: Create a new restated statement (Restated 1 → Restated 5)
  - Copies all metadata from original
  - Spread type set to "Restated N"
  - Submits as new statement to external system

#### P1.7.3 Audit Trail
- [ ] Comprehensive audit log for every spread:
  - Who created, modified, submitted, approved, overridden
  - What changed (diff of values)
  - When (timestamp)
  - Why (mandatory comments on submission/approval)
- [ ] Audit log export for compliance teams

---

### P1.8 — Admin Panel (Weeks 26–30)

#### P1.8.1 User Management
- [ ] User listing with search, filter by role/status/group
- [ ] Approve / reject pending accounts
- [ ] Assign/modify roles and group memberships
- [ ] Activate / deactivate accounts
- [ ] Bulk user provisioning via CSV upload

#### P1.8.2 Global Taxonomy Management
- [ ] CRUD for keywords and synonyms
- [ ] Organized by taxonomy groups (e.g., "IFRS Banking", "IFRS Corporate")
- [ ] Multi-language support (English, Arabic, French)
- [ ] Bulk upload/download via Excel template
- [ ] Pipe-separated synonyms (e.g., "Revenue | Turnover | Net Sales")
- [ ] Zone tagging per keyword

#### P1.8.3 Global Zone Management
- [ ] Master list of zone names
- [ ] Add/edit/delete zones (e.g., "Income Statement", "Balance Sheet", "Fixed Assets Note")
- [ ] Active/inactive toggle

#### P1.8.4 Exclusion List Management
- [ ] 12 exclusion categories (Prefix, Suffix, Superscript, Subscript, Punctuation, Spaces, Text Removal, Dots, Dates, Numbers, Brackets, Customer-specific)
- [ ] Add/modify/remove items per category
- [ ] Per-tenant defaults + customer-level overrides

#### P1.8.5 Language Management
- [ ] List of supported OCR languages
- [ ] Enable/disable per tenant

#### P1.8.6 Model Template Management
- [ ] List existing model templates
- [ ] Add Template to model (upload from taxonomy)
- [ ] Configure Template: Map taxonomy keywords to model line items
- [ ] Modify Template / Configuration (for adding new keywords, adjusting mappings)

#### P1.8.7 AI Model Management
- [ ] View current ML model versions and accuracy metrics
- [ ] View retraining status and history
- [ ] Trigger manual model retraining (from accumulated feedback data)
- [ ] A/B test: Deploy new model version to subset of users, compare accuracy

---

### P1.9 — ML Feedback Loop & Retraining (Weeks 28–32)

#### P1.9.1 Feedback Aggregation
- [ ] Batch job: Aggregate all `ml_feedback` records since last training
- [ ] Generate training pairs: (source_text, zone_context) → correct_line_item_id
- [ ] Quality filter: Require ≥ 3 consistent corrections for same pattern before using as training data

#### P1.9.2 Automated Retraining Pipeline
- [ ] Weekly retraining job (configurable frequency):
  1. Pull new training pairs from feedback table
  2. Fine-tune Sentence-BERT on new pairs (incremental training)
  3. Evaluate on held-out validation set
  4. If accuracy improved → promote new model
  5. If accuracy degraded → keep existing model, flag for review
- [ ] A/B deployment: New model serves 10% of requests, old model serves 90%, compare

#### P1.9.3 Per-Client Model Specialization
- [ ] After client accumulates 100+ corrections:
  - Fine-tune a client-specific model variant
  - Use for that client's documents only
  - Fall back to global model for new/unknown patterns

---

## Phase 2: Covenants & Intelligence (Weeks 33–52)

> **Goal**: Complete covenant tracking module with predictive breach intelligence and all workflow features.

---

### P2.1 — Covenant Data Model & Customer Management (Weeks 33–35)

#### P2.1.1 Database Schema — Covenants
- [ ] Migration:
  ```sql
  -- Covenant Customers
  covenant_customers (id, tenant_id, customer_name, rim_id, cl_entity_id,
                      financial_year_end, status, created_by, created_at, updated_at)
  
  -- Customer Contacts (for notifications/letters)
  covenant_customer_contacts (id, covenant_customer_id, contact_type,
                              username, email, full_name, designation,
                              is_internal, created_at)
  -- contact_type: INTERNAL, EXTERNAL
  
  -- Covenant Definitions
  covenant_definitions (id, covenant_customer_id, covenant_type, 
                        item_name, other_description, frequency,
                        audit_method, threshold_operator, threshold_value,
                        formula_id, status, created_by, created_at, updated_at)
  -- covenant_type: FINANCIAL, NON_FINANCIAL
  -- frequency: MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL, FY_TO_DATE
  -- threshold_operator: GTE, LTE, EQ, BETWEEN
  
  -- Formulas
  covenant_formulas (id, tenant_id, name, expression, applicable_groups,
                     status, created_by, created_at, updated_at)
  covenant_formula_items (id, formula_id, line_item_id, period, 
                          operator, order_index)
  
  -- Monitoring Items (auto-generated)
  covenant_monitoring_items (id, definition_id, period_date, due_date,
                             calculated_value, manual_value, threshold_value,
                             status, breach_probability, waiver_status,
                             locked_by, created_at, updated_at)
  -- status: DUE, OVERDUE, MET, BREACHED, SUBMITTED, APPROVED, 
  --         REJECTED, TRIGGER_ACTION, CLOSED
  -- waiver_status: NULL, WAIVED_INSTANCE, WAIVED_PERMANENT, NOT_WAIVED
  
  -- Non-Financial Documents
  covenant_documents (id, monitoring_item_id, document_name, storage_path,
                      uploaded_by, verified_by, verification_status, 
                      comments, created_at)
  
  -- Email Templates
  covenant_email_templates (id, tenant_id, name, category, subject,
                            body_html, field_tags, status, created_by, 
                            created_at, updated_at)
  
  -- Email Signatures
  covenant_email_signatures (id, tenant_id, signature_html, 
                             created_by, created_at, updated_at)
  
  -- Waiver Records
  covenant_waivers (id, monitoring_item_id, waiver_type, waiver_scope,
                    comments, template_id, recipient_contacts,
                    generated_document_path, sent_via, sent_at,
                    created_by, created_at)
  -- waiver_type: WAIVED, NOT_WAIVED
  -- waiver_scope: INSTANCE, PERMANENT
  -- sent_via: SYSTEM, OUTLOOK_DOWNLOAD
  
  -- Audit Logs (covenant-specific)
  covenant_audit_logs (id, entity_type, entity_id, action, 
                       old_values, new_values, performed_by, created_at)
  ```

#### P2.1.2 Covenant Customer CRUD
- [ ] Create customer form: Basic Info + Additional Info (internal/external contacts)
- [ ] Customer listing page: Search by RIM ID, Customer Name
- [ ] View/Update customer details
- [ ] Active/Inactive toggle
- [ ] Link covenant customers to spreading customers (by Entity ID)

---

### P2.2 — Covenant Definitions & Formulas (Weeks 35–38)

#### P2.2.1 Financial Covenant Creation
- [ ] Create Covenant page with fields per specification:
  - Customer selector, Item Name, Frequency, Audit Method
  - Threshold: Operator (≥, ≤, =, between) + Value(s)
  - Formula: Select from library or create inline
- [ ] Covenant listing per customer: Financial/Non-Financial tabs

#### P2.2.2 Non-Financial Covenant Creation
- [ ] Item name selector (document types)
- [ ] "Other" option with mandatory description field
- [ ] Same frequency/audit method configuration

#### P2.2.3 Formula Management (Manager)
- [ ] Formula Management page: List, Add, Edit, Delete, Active/Inactive
- [ ] Visual Formula Builder:
  - Select model line items from dropdown (searchable)
  - Apply operators between items
  - Group applicability (which customer business portfolios)
  - Preview formula output with sample data
- [ ] Audit log per formula
- [ ] Formulas shared across customers within applicable groups

#### P2.2.4 Covenant Listing & Actions
- [ ] Per-customer covenant listing with two tabs: Financial / Non-Financial
- [ ] Actions per covenant: Edit, View Items, Active/Inactive, Delete
- [ ] Action matrix as per original spec (different actions based on status)

---

### P2.3 — Monitoring Item Engine (Weeks 38–42)

#### P2.3.1 Auto-Generation Logic
- [ ] Scheduled job: Generate monitoring items based on covenant frequency and year-end
  - Monthly: Create at each month end
  - Quarterly: Create at quarter end
  - Annual: Create at year end
  - Semi-annual: Use Q2 quarterly as proxy (per CBD CL compatibility)
  - FY-To-Date: Create when matching spread is submitted
- [ ] Skip-overlap logic: If annual exists, skip Q4 quarterly for same audit method and RIM
- [ ] Due date calculation based on frequency and year-end

#### P2.3.2 Financial Covenant Monitoring
- [ ] All Covenants tab: All items regardless of status
- [ ] Pending Covenants tab: Due or Overdue items awaiting values
- [ ] Violated Covenants tab: Breached items
- [ ] Auto-calculation: When spread is submitted, system:
  1. Finds all covenant definitions linked to the customer
  2. Matches by statement date, frequency, audit method
  3. Evaluates formula using spread values → calculated value
  4. Compares calculated value against threshold → update status (Met/Breached)
- [ ] Manual value override: Analyst enters manual value with justification
- [ ] Trigger Action: Analyst triggers breached item → sends to Manager

#### P2.3.3 Non-Financial Covenant Monitoring
- [ ] Same tab structure (All / Pending)
- [ ] Status flow: Due → Submitted → Approved/Rejected → Overdue/Closed
- [ ] Financial Statement type auto-approval: When spread submitted with matching metadata (Entity ID, Statement Date, Audit Method) → item auto-marked as Approved
- [ ] Auto-fetch documents from spreading module for Financial Statement type items

#### P2.3.4 Real-Time Triggers
- [ ] Event-driven architecture:
  - Spread submitted → publish `SPREAD_SUBMITTED` event to Kafka/RabbitMQ
  - Covenant service listens → recalculates affected covenants
  - Status changes → publish `COVENANT_STATUS_CHANGED` event
  - Notification service listens → sends alerts to configured contacts
- [ ] Processing time: < 5 seconds from spread submission to covenant recalculation

---

### P2.4 — Predictive Covenant Intelligence (Weeks 40–44)

#### P2.4.1 Breach Probability Model
- [ ] Statistical forecasting based on historical financial metrics:
  - For each financial covenant, track the metric value over time (e.g., Debt/EBITDA per quarter)
  - Fit trend models (linear regression, exponential smoothing, ARIMA)
  - Project N periods forward
  - Calculate probability of projected value crossing threshold
- [ ] Simple but effective approach (no expensive ML needed):
  ```python
  # Example for Debt/EBITDA ≤ 4.0x covenant
  historical_values = [3.2, 3.5, 3.7, 3.8]  # Last 4 quarters
  trend = linear_regression(historical_values)
  projected_next = trend.predict(next_period)  # 3.95
  std_dev = np.std(residuals)
  breach_probability = norm.cdf(threshold, loc=projected_next, scale=std_dev)
  # Result: 78% probability of breaching 4.0x next quarter
  ```
- [ ] Update probabilities whenever new spread data is submitted
- [ ] Store `breach_probability` on each monitoring item

#### P2.4.2 Early Warning Dashboard
- [ ] Risk heatmap: Grid of customers × covenants, color-coded by breach probability
  - Green: < 25% probability
  - Yellow: 25-50%
  - Orange: 50-75%
  - Red: > 75%
- [ ] Trend charts per covenant per customer: Historical values + projected trendline + threshold bands
- [ ] Alert panel: Top 10 highest-risk covenants across portfolio

#### P2.4.3 Covenant Calendar
- [ ] Timeline view showing upcoming due dates
- [ ] Color-coded by risk: Green (likely Met), Orange (at risk), Red (likely Breach)
- [ ] Click on calendar item → navigate to covenant details

---

### P2.5 — Document Verification Workflow (Weeks 42–44)

#### P2.5.1 Maker: Submit Documents
- [ ] Upload documents against non-financial covenant item
- [ ] Document management: Preview, download, delete uploaded documents
- [ ] Add comments
- [ ] Click "Submit for Verification" → status changes to SUBMITTED

#### P2.5.2 Checker: Verify Documents
- [ ] Notification: Checker receives alert for submitted items
- [ ] Verify Documents screen:
  - Read-only item metadata
  - Document preview, download, upload additional docs, delete
  - Comments section
  - Approve button: Status → APPROVED
  - Reject button (with mandatory comment): Status → REJECTED
  - Cancel: No changes saved
- [ ] Rejected items return to Maker for resubmission

---

### P2.6 — Waiver Workflow & Letter Generation (Weeks 44–48)

#### P2.6.1 Email Template Document Configuration
- [ ] Template editor: Rich text editor with drag-and-drop field tags
- [ ] Field list: Dynamic variables that auto-populate from covenant data:
  - Customer Name, RIM ID, Covenant Name, Period, Due Date
  - Calculated Value, Threshold Value, Breach Date
  - Analyst Name, Manager Name, Date
- [ ] Template CRUD: Create, Edit, Active/Inactive, Delete, Audit Log
- [ ] Category: Financial / Non-Financial

#### P2.6.2 Signature Management
- [ ] Rich text signature editor with formatting options
- [ ] Saved signatures appear in template creation as selectable option

#### P2.6.3 Waiver Flow Implementation
- [ ] Step 1: Manager clicks Waive/Not-Waive on breached/overdue item
- [ ] Step 2 (Waive only): Select scope: Instance (single period) or Permanent
- [ ] Step 3: Provide comments, view waiver history
- [ ] Step 4: Select waiver letter template from pre-configured list
- [ ] Step 5: Select/add recipient contacts (internal + external)
- [ ] Step 6: Generate Document — system populates template with live data
- [ ] Step 7: Preview generated letter with options: Edit, Print, Cancel
- [ ] Step 8: Send:
  - **Send by System**: Email sent automatically via configured SMTP
  - **Send by Outlook**: Download .eml file for manual sending
- [ ] Step 9: Item status → CLOSED, waiver record created

#### P2.6.4 Automated Email Reminders
- [ ] Configurable reminder schedule per tenant:
  - X days before due date → send "upcoming due" reminder
  - Y days after due date (overdue) → send "overdue" reminder
  - Repeat overdue reminders every Z days
- [ ] Recipients: All contacts configured for the covenant customer
- [ ] Email content: Template-based with auto-populated fields

---

### P2.7 — Live Dashboards (Weeks 48–52)

#### P2.7.1 Spreading Dashboard
- [ ] Overview widgets:
  - Total spreads (by status) — donut chart
  - Spreads this month vs last month — bar chart
  - Average AI accuracy rate — gauge chart
  - Average time per spread — trend line
- [ ] User productivity table:
  - Analyst name, spreads completed, average time, AI accuracy
  - Date range filter
- [ ] Drill-down: Click on any metric → filtered detail view

#### P2.7.2 Covenant Dashboard
- [ ] Health overview widgets:
  - Covenant status distribution (Due/Overdue/Met/Breached/Closed) — donut chart
  - Breach risk heatmap (customers × covenants)
  - Upcoming covenant calendar (next 30 days)
- [ ] Trend charts: Covenant metrics over time with threshold bands
- [ ] Alert panel: Highest-risk covenants requiring attention
- [ ] Filter: By customer, group, covenant type, date range

---

## Phase 3: Workflow Engine & Reporting (Weeks 53–64)

> **Goal**: Replace hard-coded approval flows with configurable BPMN workflows, and build comprehensive MIS reporting.

---

### P3.1 — BPMN Workflow Engine (Weeks 53–58)

#### P3.1.1 Workflow Engine Integration
- [ ] Integrate Camunda 8 (Zeebe) or Flowable as embedded engine
- [ ] BPMN process definitions for:
  - **Spread Approval Workflow**: Configurable steps (Analyst → Reviewer → Approver → Final Approver)
  - **Non-Financial Covenant Verification**: Maker → Checker (with multi-level option)
  - **Waiver Processing**: Analyst trigger → Manager review → Senior Manager approval (optional)
  - **User Account Approval**: Registration → Admin review → Approval/Rejection
- [ ] Conditional routing:
  - If spread amount > threshold → add VP approval step
  - If covenant is permanent waiver → require Global Manager approval
- [ ] Parallel approval: Two department heads must approve simultaneously
- [ ] Escalation: Auto-escalate to next level if not actioned within configurable time
- [ ] SLA tracking: Time spent at each workflow step

#### P3.1.2 Workflow Designer UI
- [ ] Visual BPMN editor for admin users:
  - Drag-and-drop workflow steps
  - Configure step: Assignee role, SLA duration, escalation rules
  - Add conditions (gateway): Based on data attributes (amount, customer type, etc.)
  - Save/publish/version workflow definitions
- [ ] Workflow monitoring: View active workflow instances, their current step, SLA status
- [ ] Workflow history: Complete audit trail per workflow instance

---

### P3.2 — MIS Reporting Module (Weeks 58–62)

#### P3.2.1 Report Framework
- [ ] Report engine architecture:
  - Report definitions stored in DB (query, filters, columns, format options)
  - Runtime: Execute query with applied filters → render as table → export
  - Export formats: Excel (.xlsx), PDF, HTML
  - Scheduled report delivery via email (daily/weekly/monthly)

#### P3.2.2 Spreading Reports
- [ ] Spread Details Report: All spreads by status, date range, analyst, customer
- [ ] Customer Details Report: Customer information with spread history
- [ ] User Activity Report: Analyst productivity, login frequency, actions taken
- [ ] OCR Accuracy Report: AI extraction accuracy metrics per document type
- [ ] AI Performance Report: Mapping confidence distribution, correction rates

#### P3.2.3 Covenant Reports
- [ ] Covenant Pending Report: All Due/Overdue items requiring action
- [ ] Covenant Default/Breach Report: All breached items with details
- [ ] Covenant History Report: Historical status changes per covenant
- [ ] Covenant Change History Report: All modifications to covenant definitions
- [ ] Non-Financial Covenant Item Report: Filterable by status (Rejected, Overdue, etc.)

#### P3.2.4 Report UI
- [ ] Report listing page categorized by module (Spreading / Covenant)
- [ ] Filter panel per report:
  - Date range (default: last 3 months)
  - Status filter (default: All)
  - Customer/group filter
  - RM filter
- [ ] Info icon: View applied filter details
- [ ] Refresh button: Reset to defaults
- [ ] Generate Report button: Preview in browser
- [ ] Export buttons: Excel / PDF / HTML download

---

### P3.3 — Portfolio Analytics (Weeks 60–64)

#### P3.3.1 Portfolio-Level Views
- [ ] Cross-client financial ratio comparisons:
  - Table view: All clients with key ratios side by side
  - Chart view: Scatter plots, box plots for ratio distributions
- [ ] Custom queries with builder UI:
  - Select metric (e.g., "Current Ratio")
  - Select condition (e.g., "dropped > 15%")
  - Select comparison (e.g., "vs last quarter")
  - Execute → results table with drill-down
- [ ] Sector/geography/group filtering
- [ ] Drill-down path: Portfolio → Client → Spread → Cell

#### P3.3.2 Export & Sharing
- [ ] Dashboard snapshots: Export as PDF
- [ ] Scheduled dashboard email delivery
- [ ] Shareable dashboard links (with role-based access)

---

## Phase 4: LLM Copilot & Advanced AI (Weeks 65–76)

> **Goal**: Add conversational AI capabilities and advanced ML features.

---

### P4.1 — LLM Copilot (Weeks 65–70)

#### P4.1.1 Copilot Architecture
- [ ] **Local LLM deployment** (cost-effective):
  - Ollama + Llama 3.1 8B (or latest open model) running on ML service GPU
  - Alternatively: API integration with cost-capped provider (Anthropic/OpenAI with spending limits)
- [ ] RAG (Retrieval-Augmented Generation) pipeline:
  - Index all financial statements, spread data, covenant records into vector store (ChromaDB / Qdrant)
  - User query → retrieve relevant context → send to LLM → generate response
- [ ] Copilot UI: Slide-out chat panel accessible from any page

#### P4.1.2 Copilot Capabilities
- [ ] Spreading assistance:
  - *"Show me the depreciation note for Q3"* → Navigate to the relevant page and highlight
  - *"Why doesn't the balance sheet balance?"* → Analyze validation failures and suggest fixes
  - *"Compare revenue recognition across last 4 periods"* → Generate comparison table
- [ ] Covenant assistance:
  - *"Why is the DSCR breaching?"* → Analyze component values and explain
  - *"Which covenants are at risk this month?"* → Query and summarize
  - *"Draft a waiver justification for [customer]'s coverage ratio"* → Generate text
- [ ] General queries:
  - *"Show me [customer]'s latest financial summary"* → Retrieve and format
  - *"What's the average processing time this week?"* → Query and respond

---

### P4.2 — Natural Language Querying for Dashboards (Weeks 70–74)

#### P4.2.1 NL Query Engine
- [ ] Text input on dashboard: "Show me all clients whose current ratio dropped >15% vs last quarter"
- [ ] LLM translates natural language → SQL/filter parameters
- [ ] Execute query → render results as table, chart, or narrative
- [ ] Query history: Save and re-run previous queries
- [ ] Suggested queries: Pre-configured common queries as clickable templates

---

### P4.3 — Advanced ML Features (Weeks 72–76)

#### P4.3.1 Enhanced Notes Processing
- [ ] Train dedicated NLP model for extracting financial values from free-text paragraphs
- [ ] Expand keyword library beyond 52 pre-trained keywords
- [ ] Multi-sentence reasoning: Extract values even when context spans multiple sentences

#### P4.3.2 Per-Client Model Management
- [ ] Admin UI for managing client-specific model variants
- [ ] View accuracy metrics per client model
- [ ] A/B testing: Compare client model vs global model performance
- [ ] Auto-promote: If client model consistently outperforms, auto-switch

#### P4.3.3 Document Classification Enhancement
- [ ] Train classifier to auto-identify document type (annual report, interim, management accounts)
- [ ] Auto-populate metadata fields with higher accuracy
- [ ] Reduce manual metadata entry to near-zero

---

## Phase 5: Enterprise Hardening (Weeks 77–84)

> **Goal**: Production hardening, external system integrations, on-prem packaging, and compliance.

---

### P5.1 — External System Adapters (Weeks 77–80)

#### P5.1.1 Adapter Architecture
- [ ] Plugin-based adapter system:
  ```kotlin
  interface ExternalSystemAdapter {
      fun pushSpread(spread: SpreadData): PushResult
      fun pullModel(entityId: String): ModelTemplate
      fun pullHistoricalSpreads(entityId: String): List<SpreadData>
      fun syncMetadata(entityId: String): MetadataSync
  }
  ```
- [ ] CreditLens Adapter:
  - Push spread values
  - Pull model templates and historical spreads (2 most recent)
  - 2-way metadata sync
  - Retained Earnings fetch
  - Unit rounding per entity unit
- [ ] Adapter configuration UI: URL, credentials (encrypted), sync schedule, field mapping
- [ ] Generic REST Adapter: Configurable for custom bank-internal systems

#### P5.1.2 Historical Data Migration
- [ ] Pull up to 2 historical spreads per customer from external systems
- [ ] " Migrated from CL" badge on imported items
- [ ] Migrated spreads: editable but not auto-fillable (no base period)

---

### P5.2 — On-Premise Deployment (Weeks 79–82)

#### P5.2.1 Kubernetes Packaging
- [ ] Helm charts for all services:
  - Core API (Kotlin/Spring Boot)
  - ML Service (Python/FastAPI)
  - Workflow Engine (Camunda/Flowable)
  - PostgreSQL
  - Redis/Valkey
  - MinIO (document storage)
  - Kafka/RabbitMQ (message broker)
- [ ] Configurable `values.yaml`: Resource limits, replicas, storage classes, ingress
- [ ] Offline/air-gapped installation: Pre-built container images, no internet dependency
- [ ] Installation guide: Step-by-step for client's infrastructure team

#### P5.2.2 Data Sovereignty
- [ ] Per-tenant region configuration:
  - Data stored in region-specific clusters
  - API gateway routes to nearest region
  - Cross-region data transfer controls
- [ ] GDPR compliance: Data export (right to portability), data deletion (right to erasure), consent management
- [ ] DIFC/ADGM compliance: Data residency within UAE for Middle East clients
- [ ] Audit-ready documentation: SOC 2 Type II control descriptions, ISO 27001 ISMS documentation

---

### P5.3 — Performance & Scale Hardening (Weeks 81–84)

#### P5.3.1 Load Testing
- [ ] Simulate 100 concurrent users
- [ ] Simulate 1,000 documents/day processing
- [ ] Target response times: < 500ms UI interactions, < 2s search, < 3min full spread processing
- [ ] Identify and fix bottlenecks: Database queries, ML inference, document rendering

#### P5.3.2 Monitoring & Observability
- [ ] Prometheus metrics: API latency, error rates, queue depths, ML inference times
- [ ] Grafana dashboards: Infrastructure health, application performance, ML model accuracy
- [ ] Structured logging (ELK stack or equivalent)
- [ ] Alerting: PagerDuty/Slack integration for critical failures

#### P5.3.3 Backup & Disaster Recovery
- [ ] Automated daily backups (database + object storage)
- [ ] Cross-region backup replication
- [ ] Recovery testing: RPO < 1 hour, RTO < 4 hours
- [ ] Runbook for common failure scenarios

---

## Summary Timeline

```
MONTH   1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16   17   18
       ┌─────────────┐
       │  PHASE 0    │ ← Demo MVP ready → PITCH TO HSBC VP
       │  Demo MVP   │
       └─────────────┘
                      ┌──────────────────────────────┐
                      │         PHASE 1               │ ← Production spreading (pilot goes live)
                      │  Production Spreading          │
                      └──────────────────────────────┘
                                                       ┌──────────────────────┐
                                                       │      PHASE 2         │ ← Covenants + intelligence
                                                       │  Covenants Module    │
                                                       └──────────────────────┘
                                                                              ┌────────────┐
                                                                              │  PHASE 3   │ ← Workflow + reports
                                                                              │ Workflow+  │
                                                                              │ Reporting  │
                                                                              └────────────┘
                                                                                           ┌────────────┐
                                                                                           │  PHASE 4   │ ← LLM copilot
                                                                                           │  LLM + AI  │
                                                                                           └────────────┘
                                                                                                        ┌──────┐
                                                                                                        │ PH 5 │ ← Enterprise
                                                                                                        │      │
                                                                                                        └──────┘
```

**Key Milestones**:
- **Month 3**: Demo ready → pitch HSBC VP
- **Month 7**: Pilot goes live on production spreading
- **Month 10**: Covenant module delivered
- **Month 13**: Full MIS reporting + configurable workflows
- **Month 16**: LLM copilot
- **Month 18**: Enterprise-ready (on-prem, multi-region, compliance)
