# Numera — Comprehensive Gap Analysis & Detailed Implementation Plan

> **Generated**: April 2026  
> **Scope**: Full enterprise-grade application completion  
> **Method**: Systematic codebase review + PhD engineering analysis alignment  
> **Audience**: AI agents executing implementation chunks independently

---

## Executive Summary

This document identifies every gap between the current codebase state and the full application scope defined in `implementation_plan.md` and `IPRD/IPimplementation_plan.md`. Each gap is assigned a priority, estimated effort, dependency chain, and broken into executable chunks that an AI agent can work on independently.

### Current State at a Glance

| Module | Implementation Status | Gaps |
|--------|----------------------|------|
| Auth (JWT/MFA/SSO) | ✅ Complete | Minor: tenant-specific session timeout |
| Customer Management | ✅ Complete | — |
| Document Processing | ✅ Complete | — |
| OCR Pipeline | ✅ Complete | STGH GCN uses untrained weights; VLM needs model download |
| ML Pipeline | ✅ Complete | Mapping step produces empty results (target_items=[]); GNN pruner untrained |
| Spreading Engine | ✅ Complete | UI: Expression editor, zone overlay, source highlights missing |
| Spreading Workspace UI | 🟡 Partial | Missing: expression editor, zone overlay, page ops, comments, historical loads |
| Covenant Management | ✅ Complete | Waiver contacts hardcoded; PDF generation returns HTML |
| Covenant Intelligence | ✅ Complete | — |
| Admin Panels | 🟡 Partial | Missing: model template mgmt, zone mgmt, AI model mgmt; workflow is read-only |
| Reports | 🔴 Missing | Empty reporting package; no Excel/PDF generation |
| Workflow Designer | 🔴 Missing | Read-only table; no BPMN editor |
| FSO (Federated Learning) | 🔴 Missing | Zero code exists |
| LLM Copilot | 🔴 Missing | Zero code exists |
| Portfolio Analytics | 🔴 Missing | Zero code exists |
| Enterprise Hardening | 🔴 Missing | No Helm charts, no load testing, no observability stack |
| IP Implementation | 🟡 Partial | IP-4 (FSO) missing; STGH/NG-MILP GNN need training pipelines |

### Gap Classification Legend

- ✅ = Fully implemented with real business logic
- 🟡 = Partially implemented (missing features or uses stubs)
- 🔴 = Not implemented or exists only as empty directory/scaffold

---

## Chunk 1: Spreading Workspace UI Completion

**Priority**: CRITICAL — This is the core user-facing feature.  
**Dependencies**: None (can start immediately)  
**Estimated Effort**: 8-10 agent-days  
**PhD Reference**: IP-2 (NG-MILP) expression engine outputs need a UI; IP-5 (STGH) fingerprints need zone overlay

### 1.1 Expression Editor Component

**File**: `numera-ui/src/features/spreading/components/ExpressionEditor.tsx` (NEW)

**What**: A visual formula editor for the spread table that allows analysts to build, edit, and preview expressions for mapped values.

**Requirements**:
- Modal/inline editor triggered by double-clicking any cell in the spread table
- Display current expression with visual components: `[Page 3, Row 12] + [Page 5, Row 8]`
- Operator buttons: `+`, `-`, `×`, `÷`, `(`, `)`
- Adjustment buttons: `ABS()`, `NEG()`, `CONTRA()` (multiply by -1), `Unit Scale` (multiply/divide by 1000, 1000000)
- Live preview of computed value
- Keyboard entry for manual numeric constants
- Source reference chips showing page number and row for each referenced value
- Expression validation: check parentheses balance, consecutive operators, circular references
- Save expression via `PUT /api/spread-items/{id}/values/{valueId}` with `expressionType: 'FORMULA'`

**Backend Changes**:
- The `SpreadValueController.updateValue()` already accepts `expression` and `expressionType` fields
- The `FormulaEngine.kt` already supports: arithmetic, `{CODE}` references, `ABS()`, `SUM({from}:{to})`, `IF(condition, trueExpr, falseExpr)`, comparison operators
- No backend changes needed

**Chunk Size**: 1 file (frontend component) + integration into `SpreadTable.tsx`

---

### 1.2 Zone Overlay & Source Highlights Integration

**Files**:
- `numera-ui/src/features/spreading/components/ZoneOverlay.tsx` (EXISTS, needs integration)
- `numera-ui/src/features/spreading/components/SourceHighlight.tsx` (EXISTS, needs integration)
- `numera-ui/src/features/spreading/PdfViewer.tsx` (MODIFY)

**What**: Render the zone overlay and source highlights on top of the PDF viewer. These components exist but are never imported or rendered.

**Requirements**:
- Import `ZoneOverlay` and `SourceHighlight` into `PdfViewer.tsx`
- Render `ZoneOverlay` as an SVG layer on top of the PDF canvas with pointer-events on zone rectangles
- Color coding: Blue = BALANCE_SHEET, Green = INCOME_STATEMENT, Orange = CASH_FLOW, Purple = NOTES
- Zone label badges (e.g., "Income Statement (94%)")
- Click on zone → scroll model grid to corresponding section (bidirectional navigation)
- Render `SourceHighlight` when a cell is selected → highlight the source value on the PDF
- The `spreadStore` already tracks `highlightedSourcePage` and `highlightedSourceCoords` — use these
- Add a toggle button in the toolbar: "Show Zones" / "Hide Zones"

**Backend**: Already returns `DetectedZone` entities with `boundingBox`, `zoneType`, `confidenceScore`, `zoneLabel`. No changes needed.

**Chunk Size**: 2 component integrations into PdfViewer

---

### 1.3 Spreading Page Operations (Merge, Rotate, Split, Clean)

**Files**:
- `numera-ui/src/features/spreading/components/PageToolbar.tsx` (NEW)
- `backend/src/main/kotlin/com/numera/document/api/DocumentController.kt` (MODIFY)
- `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt` (MODIFY)

**What**: Add PDF page manipulation operations to the spreading workspace.

**New Backend Endpoints**:
```
POST /api/documents/{id}/pages/merge      # Merge two pages into one
POST /api/documents/{id}/pages/rotate     # Rotate a page (90, 180, 270)
POST /api/documents/{id}/pages/split      # Split a book-view page into two
POST /api/documents/{id}/pages/clean      # Despeckle/watermark removal
```

**Implementation**:
- Use PyMuPDF (already a dependency) for page manipulation in the OCR service
- Create `PageOperationRequest` DTO with operation type, page numbers, parameters
- Store the modified document as a new version (don't modify original)
- Update detected zones after page operations (recalculate coordinates for rotation/merge)
- Frontend: Add toolbar buttons above PDF viewer with icons for each operation

**Chunk Size**: 4 API endpoints + OCR service endpoints + frontend toolbar

---

### 1.4 Auto-Generated Comments

**Files**:
- `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` (MODIFY)
- `numera-ui/src/features/spreading/components/CommentPanel.tsx` (NEW)

**What**: For every mapped cell, auto-generate a comment containing source PDF name, page number, line-item name, extracted value, and a clickable URL navigating to the exact location.

**Backend Changes**:
- After `MappingOrchestrator` creates spread values, generate a `comment` field for each value:
  ```kotlin
  val comment = """
    Source: ${document.originalFilename}
    Page: ${value.sourcePage}
    Line Item: ${value.sourceText}
    Value: ${value.mappedValue}
    [View in Document](/api/documents/${document.id}/pages/${value.sourcePage}?x=${value.sourceCoordinates?.x}&y=${value.sourceCoordinates?.y})
  """.trimIndent()
  ```
- The `SpreadValue` entity already has a `notes` field. Store auto-generated comments there.
- Add `GET /api/documents/{id}/share-link` endpoint that generates a time-limited, public shareable link (no auth required) for document viewing

**Frontend**:
- Comment icon on each cell in the spread table
- Click → expandable comment panel showing auto-generated + manual comments
- Clickable URL that navigates PDF viewer to exact coordinates

**Chunk Size**: 1 backend method modification + 1 new endpoint + 1 new component

---

### 1.5 Load Historical Spreads

**Files**:
- `numera-ui/src/features/spreading/components/LoadHistoricalModal.tsx` (NEW)
- `backend/src/main/kotlin/com/numera/spreading/api/SpreadItemController.kt` (MODIFY)

**New Endpoint**:
```
GET /api/customers/{customerId}/spread-items/historical?limit=20
```

**What**: Allow analysts to load up to 20 historical spread periods as comparison columns in the model grid.

**Implementation**:
- Backend: Query spread items for the same customer with status APPROVED, ordered by statement date DESC, limit 20
- Return: Array of `{ spreadItemId, statementDate, values: [{lineItemId, mappedValue}] }`
- Frontend: "Load Historical" button in toolbar → modal with date range selector → loads historical values as additional columns in AG Grid
- Show variance highlighting between current and historical periods

**Chunk Size**: 1 endpoint + 1 modal component + grid column addition

---

### 1.6 SmartFill, Variance, and Currency Columns

**Files**:
- `numera-ui/src/features/spreading/SpreadTable.tsx` (MODIFY)
- `numera-ui/src/features/spreading/components/CategoryNav.tsx` (MODIFY)

**What**: Add missing grid features from the implementation plan.

**Features**:
- **Show/Hide Rows Toggle**: Button to toggle between "All Rows" and "Mapped Rows Only"
- **Show/Hide SmartFill**: Highlight duplicate line items matching base period or taxonomy autofill. Button toggles highlighting
- **Show Variance Column**: Already partially implemented (`useSpreadVariance`). Ensure it works with base period data
- **Show Currency Column**: Two additional columns for source currency and unit per value
- **Account Category Bubbles**: Draggable floating nav for quick section jump (already exists as `CategoryNav` component, but needs drag capability)

**Chunk Size**: 4 feature additions to existing components

---

### 1.7 Split View / Dual Document Panes

**File**: `numera-ui/src/features/spreading/[spreadId]/page.tsx` (MODIFY)

**What**: Allow analysts to view two document pages simultaneously for mapping from different pages.

**Implementation**:
- Add "Split View" toggle button in toolbar
- When active: render two `PdfViewer` instances side-by-side (50/50 split)
- Each viewer has independent page navigation
- Both viewers share the same spread table
- Clicking a value in either viewer maps to the selected cell in the grid

**Chunk Size**: 1 page layout modification

---

## Chunk 2: PDF Generation & Report Service

**Priority**: CRITICAL — Reports and waiver letters are essential enterprise features  
**Dependencies**: None  
**Estimated Effort**: 5-6 agent-days  
**PhD Reference**: IP-3 (ZK-RFA) audit trail needs PDF export

### 2.1 PDF Generation Service

**Files**:
- `backend/src/main/kotlin/com/numera/shared/pdf/PdfGenerator.kt` (NEW)
- `backend/src/main/kotlin/com/numera/covenant/application/WaiverService.kt` (MODIFY)

**What**: Replace the HTML-as-PDF stub with real PDF generation.

**Implementation**:
- Add dependency: `org.apache.pdfbox:pdfbox:3.0.3` (or `com.itextpdf:itext7-core:8.0.4`)
- Create `PdfGenerator` service that:
  - Takes HTML content + optional CSS template
  - Renders to PDF using iText's HTML-to-PDF converter or PDFBox
  - Supports header/footer with page numbers, company logo, date
  - Returns `ByteArrayResource` for download
- Modify `WaiverService.generatePDF()` to use `PdfGenerator` instead of returning raw HTML bytes
- Ensure the `/api/covenants/waivers/{id}/download` endpoint returns proper `application/pdf` content type

**Chunk Size**: 1 new service + 1 modification

---

### 2.2 Report Generation Service

**Files**:
- `backend/src/main/kotlin/com/numera/reporting/application/ReportService.kt` (NEW)
- `backend/src/main/kotlin/com/numera/reporting/api/ReportController.kt` (MODIFY)
- `backend/src/main/kotlin/com/numera/reporting/application/SpreadingReportGenerator.kt` (NEW)
- `backend/src/main/kotlin/com/numera/reporting/application/CovenantReportGenerator.kt` (NEW)
- `backend/src/main/kotlin/com/numera/reporting/application/AuditReportGenerator.kt` (NEW)

**What**: Build a complete report generation service with Excel, PDF, and HTML export.

**Report Definitions** (per implementation plan P3.2):

| Report | Source Data | Formats |
|--------|-------------|---------|
| Spread Details | spread_items + spread_values | Excel, PDF, HTML |
| Customer Details | customers + spread history | Excel, PDF |
| User Activity | audit_event_log | Excel, PDF |
| OCR Accuracy | documents + detected_zones | Excel |
| AI Performance | spread_values (confidence stats) | Excel |
| Covenant Pending | covenant_monitoring_items | Excel, PDF |
| Covenant Breach | covenant_monitoring_items (BREACHED) | Excel, PDF |
| Covenant History | covenant_monitoring_items (all statuses) | Excel, PDF |
| Audit Trail | audit_event_log | Excel, PDF |

**Implementation**:
- Use Apache POI for Excel generation (`org.apache.poi:poi-ooxml:5.3.0`)
- Use iText/PDFBox for PDF generation
- Each report generator: accepts filter parameters → queries data → builds document
- Report definitions stored in DB for custom filtering
- `GET /api/reports/spreading-summary` — existing endpoint, enhance with filters
- `GET /api/reports/covenant-summary` — existing endpoint, enhance with filters
- `GET /api/reports/audit-trail` — existing endpoint, enhance with filters
- `GET /api/reports/export?report=spreading&format=xlsx` — generate and download
- Add report parameter DTOs: date range, status filter, customer/group filter, RM filter

**Frontend**:
- `numera-ui/src/features/reports/ReportsPage.tsx` (MODIFY or NEW)
- Replace raw JSON `<pre>` display with proper tables, filters, and charts
- Add filter panel: date range, status dropdown, customer/group selector
- Add export buttons: Excel, PDF, HTML download
- Add "Generate Report" button with loading indicator
- Use `react-data-table-component` or AG Grid for tabular display

**Chunk Size**: 3 new service files + 1 controller modification + 1 frontend page rewrite

---

### 2.3 Scheduled Report Delivery

**Files**:
- `backend/src/main/kotlin/com/numera/reporting/application/ReportScheduler.kt` (NEW)

**What**: Scheduled report delivery via email.

**Implementation**:
- Create `ReportScheduler` with `@Scheduled` methods for daily/weekly/monthly reports
- Use existing `EmailNotificationService` infrastructure
- New table: `report_schedules (id, tenant_id, report_type, frequency, recipients, filters, last_run, created_at)`
- Admin UI for configuring scheduled reports

**Chunk Size**: 1 scheduler + 1 migration + 1 admin API endpoint

---

## Chunk 3: Admin Panels Completion

**Priority**: HIGH — Required for enterprise configuration  
**Dependencies**: None  
**Estimated Effort**: 6-8 agent-days

### 3.1 Model Template Management UI

**Files**:
- `numera-ui/src/features/admin/model-templates/page.tsx` (NEW)
- `numera-ui/src/features/admin/model-templates/TemplateEditor.tsx` (NEW)
- `numera-ui/src/features/admin/model-templates/LineItemEditor.tsx` (NEW)

**What**: Full CRUD for financial model templates with visual editor.

**Backend**: Already has `GET/POST/PUT /api/model-templates` and `GET /api/model-templates/{id}/items?zone=`

**Features**:
- List templates with name, standard (IFRS/GAAP), version, status
- Create template from scratch or import from taxonomy
- Visual template editor:
  - Drag-and-drop line items within and between sections
  - Add/edit/delete line items (label, type, formula, display order)
  - Toggle hidden/grouped rows
  - Formula editor with item reference autocomplete
  - Validation rules editor (e.g., Total Assets = Total Liabilities + Total Equity)
- Map taxonomy keywords to model line items
- Preview template as a spreadsheet view
- Active/Inactive toggle

**Chunk Size**: 3 new frontend files + 2-3 backend enhancements (PUT for line items CRUD)

---

### 3.2 Zone Management UI

**Files**:
- `numera-ui/src/features/admin/zones/page.tsx` (NEW)

**What**: Master list of zone names with CRUD operations.

**Backend**: Create new endpoints:
```
GET    /api/admin/zones          # List all zones
POST   /api/admin/zones          # Create zone
PUT    /api/admin/zones/{id}     # Update zone
DELETE /api/admin/zones/{id}     # Delete zone
PATCH  /api/admin/zones/{id}/active  # Toggle active
```

**Features**:
- Zone name, zone type (BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, NOTES, OTHER)
- Active/Inactive toggle
- Sort order for display
- Search by name

**Chunk Size**: 1 backend controller + service + migration + 1 frontend page

---

### 3.3 AI Model Management UI

**Files**:
- `numera-ui/src/features/admin/ai-models/page.tsx` (NEW)

**What**: View ML model versions, accuracy metrics, retraining status, and A/B test results.

**Backend**: Create new endpoints that proxy to ML service:
```
GET /api/admin/ai-models                    # List models and versions
GET /api/admin/ai-models/{modelId}/metrics  # Accuracy metrics
POST /api/admin/ai-models/retrain           # Trigger retraining
GET /api/admin/ai-models/ab-tests          # A/B test results
POST /api/admin/ai-models/promote/{version} # Promote staging to production
```

**Features**:
- Table showing: model name, current version, accuracy, last retrained date, status
- Metrics chart: accuracy over time, by model version
- A/B test comparison: model A vs model B accuracy
- "Retrain" button with confirmation dialog
- "Promote" button for staging → production

**Chunk Size**: 1 backend controller + ML service proxy endpoints + 1 frontend page

---

### 3.4 Workflow Designer UI

**Files**:
- `numera-ui/src/features/admin/workflows/page.tsx` (REWRITE)
- `numera-ui/src/features/admin/workflows/WorkflowDesigner.tsx` (NEW)
- `numera-ui/src/features/admin/workflows/StepEditor.tsx` (NEW)

**What**: Visual BPMN-like workflow editor for configuring approval workflows.

**Backend**: Modify `WorkflowConfigController` to use database instead of in-memory mock data. Create:
```
GET    /api/admin/workflows              # List definitions (from DB)
POST   /api/admin/workflows              # Create definition
PUT    /api/admin/workflows/{id}         # Update definition
DELETE /api/admin/workflows/{id}         # Delete definition
PATCH  /api/admin/workflows/{id}/active   # Activate/deactivate
```

**Workflow Step Configuration**:
- Step type: APPROVAL, REVIEW, NOTIFICATION
- Assignee role: ANALYST, MANAGER, GLOBAL_MANAGER, ADMIN
- SLA duration (hours)
- Escalation: auto-approve after SLA, escalate to next role
- Conditions: amount threshold, customer type, etc.
- Parallel approval support: require N of M approvers

**Frontend**:
- Workflow list with name, type, steps, status
- Visual flow editor: drag-and-drop steps, connect with arrows
- Step configuration modal: assignee, SLA, conditions
- Active instances viewer: current step, SLA status
- Activate/Deactivate toggle

**Chunk Size**: 1 backend rewrite + 3 new frontend components

---

### 3.5 Language Management Completion

**File**: `numera-ui/src/features/admin/languages/page.tsx` (MODIFY)

**What**: Complete the stubbed "Add New Language" form.

**Backend**: Create endpoint:
```
POST /api/admin/languages      # Create new language
```

**Implementation**:
- Wire up the existing form (name, code) to actually call the API
- Add language code validation (ISO 639-1 format)
- Show available OCR languages from `GET /api/ocr/languages`

**Chunk Size**: 1 backend endpoint + 1 frontend form fix

---

### 3.6 Admin User Management Completion

**File**: `numera-ui/src/features/admin/users/page.tsx` (MODIFY)

**What**: Add missing user management features.

**Missing Features**:
- Edit user (change roles, group assignments)
- Delete/Deactivate user
- User detail view with activity log
- Bulk user provisioning via CSV upload (backend: `POST /api/admin/users/bulk-upload`)
- Approve/reject pending users (already has backend but needs UI)

**Chunk Size**: 1 frontend page enhancement + 1 bulk upload endpoint

---

## Chunk 4: ML Pipeline Fixes & Training Pipelines

**Priority**: HIGH — ML accuracy directly impacts product quality  
**Dependencies**: Chunk 1 (spreading workspace needs trained models for optimal results)  
**Estimated Effort**: 10-12 agent-days (includes training time)  
**PhD Reference**: All IP PhD analyses specify trained models

### 4.1 Fix ML Pipeline Empty Mapping Results

**File**: `ml-service/app/api/pipeline.py` (MODIFY)

**Problem**: The mapping step always produces zero results because `target_items` is initialized as an empty list and never populated from the model template.

**Fix**:
```python
# In pipeline.py, step 4 (mapping):
# Currently:
target_items = []  # "In production, these come from the model template via backend"

# Fix:
# Fetch model template line items from the backend
async with httpx.AsyncClient() as client:
    response = await client.get(
        f"{settings.backend_url}/api/model-templates/{request.template_id}/items",
        headers={"Authorization": f"Bearer {request.auth_token}"}
    )
    target_items = [
        MappingTarget(label=item["label"], zone=item.get("zone", ""), 
                      taxonomy_id=item.get("taxonomyId", ""))
        for item in response.json()
    ]
```

- Add `template_id` and `auth_token` fields to `PipelineRequest`
- Handle case where template_id is not provided (use heuristic-only mapping)
- Add error handling for backend call failures

**Chunk Size**: 1 file modification + request model update

---

### 4.2 STGH GCN Training Pipeline

**Files**:
- `ml-training/notebooks/11_train_stgh_gcn.py` (NEW)
- `ml-training/configs/stgh_training_config.yaml` (NEW)

**What**: Train the STGH DocumentGCN on real financial document data to produce meaningful weights instead of random initialization.

**Training Data**: Use existing OCR-processed documents from SEC EDGAR / LSE filings (already collected in `ml-training/` notebooks).

**Architecture** (per `ocr-service/app/ml/stgh/gcn.py`):
- Input: 391-dim features (384 SBERT + 4 spatial + 3 type)
- Layer 1: GraphConvolution(391, 128) + BatchNorm + ReLU
- Layer 2: GraphConvolution(128, 64) + BatchNorm + ReLU  
- Layer 3: GraphConvolution(64, 32) + ReLU
- Readout: Mean pooling → MLP(32, 256) → L2 normalize

**Training Task**: Same-document pages should produce similar fingerprints; different-document pages should produce dissimilar fingerprints.

**Loss**: Contrastive loss or triplet loss on fingerprint embeddings.

**Steps**:
1. Collect 1000+ document pages with known document identity (ground truth)
2. Build spatial graphs from OCR bounding boxes
3. Generate positive pairs (same document) and negative pairs (different documents)
4. Train GCN with contrastive loss
5. Export weights to `ml-service/models/stgh_gcn_weights.pt`
6. Modify `STGHConfig` to accept `gcn_weights_path` parameter
7. Modify `DocumentGCN.__init__` to load trained weights via `load_state_dict()`

**Verification**: Test that same-document fingerprints have Hamming distance < 5% of bits, different-document fingerprints have Hamming distance > 50%.

**Chunk Size**: 2 new files + 2 modifications (gcn.py config, gcn.py weight loading)

---

### 4.3 NG-MILP GNN Pruner Training Pipeline

**Files**:
- `ml-training/notebooks/12_train_ng_milp_gnn.py` (NEW)
- `ml-training/configs/ng_milp_gnn_config.yaml` (NEW)

**What**: Train the GNN edge scorer on analyst-corrected expression data to produce meaningful edge scores instead of uniform 0.5.

**Architecture** (per `ml-service/app/ml/ng_milp/gnn_pruner.py`):
- Input: Node features (7-dim: value, row_idx, col_idx, indent_level, is_bold, is_total, sign)
- Edge features: (3-dim: spatial_distance, hierarchy_gap, value_ratio)
- Layer 1: GCNConv(10, 32) + ReLU + Dropout(0.2)
- Layer 2: GCNConv(32, 16) + ReLU
- Edge scorer: MLP(32, 16, 1) → sigmoid

**Training Data**: Use `ml_feedback` table entries where analysts corrected mapping expressions. Each correction provides a positive example (correct mapping) and negative examples (rejected mappings).

**Steps**:
1. Export feedback data from PostgreSQL
2. Build candidate graphs for each document
3. Label edges: 1 if the edge is a correct sum relationship, 0 otherwise
4. Train GNN with binary cross-entropy loss
5. Evaluate on held-out validation set
6. Export weights to `ml-service/models/gnn_pruner_weights.pt`
7. Update `NGMILPConfig.gnn_weights_path` to point to trained weights

**Verification**: Test that trained GNN pruner scores correct edges > 0.8 and incorrect edges < 0.3 on validation data.

**Chunk Size**: 2 new files + 1 modification (config default path)

---

### 4.4 SBERT Fine-Tuning for IFRS Terms

**Files**:
- `ml-training/notebooks/05_sbert_fine_tuning.py` (MODIFY or REPLACE)

**What**: Fine-tune Sentence-BERT on IFRS-specific financial term pairs to improve mapping accuracy.

**Current State**: The notebook exists but uses generic training. Needs IFRS-specific training data.

**Steps**:
1. Load IFRS taxonomy from `data/taxonomy/ifrs_taxonomy.json`
2. Generate positive pairs: (synonym, canonical_label) from taxonomy
3. Generate hard negative pairs: (similar_but_different_term, canonical_label)
4. Fine-tune `all-MiniLM-L6-v2` with `sentence-transformers` library using MultipleNegativesRankingLoss
5. Evaluate on held-out pairs (target accuracy: > 85% top-1)
6. Export to MLflow as `sbert-ifrs-matcher`
7. Update `ml-service/app/ml/semantic_matcher.py` to load the fine-tuned model from MLflow

**Verification**: Test mapping accuracy on 50 real financial statement pages. Target: > 80% top-1 accuracy for standard IFRS line items.

**Chunk Size**: 1 notebook modification + 1 model export

---

### 4.5 LayoutLM Zone Classification Training

**Files**:
- `ml-training/notebooks/04_zone_classification.py` (MODIFY or REPLACE)

**What**: Fine-tune LayoutLM on IFRS financial statement zone classification.

**Current State**: The zone classifier uses keyword heuristics with LayoutLM as a fallback, but LayoutLM weights are not trained on financial data.

**Steps**:
1. Collect 500+ IFRS annual report pages with labeled zones (BS, IS, CF, Notes)
2. Convert to LayoutLM format: `(text, bbox, label)` per token
3. Fine-tune `microsoft/layoutlm-base-uncased` for sequence classification
4. Evaluate: target accuracy > 92% on validation set
5. Export to MLflow as `layoutlm-zone-classifier`
6. Update `ml-service/app/ml/zone_classifier.py` to load from MLflow

**Verification**: Test zone classification on 100 held-out pages. Target: > 90% accuracy.

**Chunk Size**: 1 notebook modification + 1 model export

---

### 4.6 STGH Semantic Model Integration

**Files**:
- `ocr-service/app/ml/stgh/fingerprinter.py` (MODIFY)
- `ocr-service/app/config.py` (MODIFY)

**What**: Enable SBERT semantic embeddings in STGH instead of SHA-256 hash-based encoding.

**Current State**: `STGHConfig` has `use_semantic_model: bool = False` (default). The `sbert_model` field is defined but never loaded.

**Fix**:
1. Add `sentence-transformers` to `ocr-service/requirements.txt`
2. In `STGHFingerprinter.__init__`, conditionally load SBERT model:
   ```python
   if self.config.use_semantic_model:
       from sentence_transformers import SentenceTransformer
       self.sbert = SentenceTransformer(self.config.sbert_model)
   else:
       self.sbert = None
   ```
3. In `_encode_text`, use `self.sbert.encode(text)` when SBERT is loaded, fall back to `_hash_text` otherwise
4. Set `use_semantic_model: True` in production config
5. Set `sbert_model: "sentence-transformers/all-MiniLM-L6-v2"` or the fine-tuned model path

**Chunk Size**: 2 file modifications + 1 dependency addition

---

## Chunk 5: IP-4 FSO (Federated Subspace Orthogonalization) — NEW MODULE

**Priority**: MEDIUM — Enterprise differentiator for multi-tenant learning without data sharing  
**Dependencies**: None (standalone module)  
**Estimated Effort**: 5-6 agent-days  
**PhD Reference**: `IPRD/ip4_phd_engineering_analysis.md`

### 5.1 FSO Core Algorithm

**Files**:
- `ml-service/app/ml/fso/__init__.py` (NEW)
- `ml-service/app/ml/fso/aggregator.py` (NEW)
- `ml-service/app/ml/fso/trainer.py` (NEW)
- `ml-service/app/ml/fso/models.py` (NEW)

**What**: Implement the Federated Subspace Orthogonalization algorithm per the PhD engineering analysis.

**`models.py`**:
```python
@dataclass
class FSOConfig:
    enable_fso: bool = False
    min_tenants_for_aggregation: int = 3
    shared_subspace_dim: int = 64
    local_epochs: int = 3
    aggregation_schedule: str = "weekly"  # daily, weekly, manual
    learning_rate: float = 1e-4

@dataclass
class ModelUpdate:
    tenant_id: str
    model_name: str
    weight_delta: np.ndarray  # Flattened weight difference
    num_samples: int
    accuracy_before: float
    accuracy_after: float

@dataclass
class GlobalUpdate:
    round_number: int
    global_weights_delta: np.ndarray
    participating_tenants: list[str]
    shared_subspace_projection: np.ndarray  # U_shared matrix
    timestamp: datetime
    privacy_guarantee: float  # Should be 0.0 (orthogonal to private)
```

**`aggregator.py`** — Core FSO Logic:
```python
class FSOAggregator:
    """
    Federated Subspace Orthogonalization aggregator.
    
    Mathematical formulation:
      For each tenant t with model update Δ_t:
        Δ_t = U_shared @ α_t + U_private_t @ β_t
      
      Aggregate only the shared component:
        Δ_global = (1/T) * Σ_t (U_shared @ α_t)
    
      Privacy guarantee: inner product of any tenant's private
      component with the global update is mathematically zero.
    """
    
    def __init__(self, config: FSOConfig):
        self.config = config
        self.shared_subspace = None  # Computed from public IFRS embeddings
        self._compute_shared_subspace()
    
    def _compute_shared_subspace(self):
        """Compute U_shared from public IFRS taxonomy embeddings via SVD.
        
        Load IFRS taxonomy terms, embed with SBERT, compute SVD,
        take top-k singular vectors as shared subspace basis.
        """
        # Load IFRS taxonomy from data/taxonomy/ifrs_taxonomy.json
        # Embed all terms with SBERT
        # SVD decomposition
        # Take top-64 components as U_shared
    
    def aggregate(self, tenant_updates: list[ModelUpdate]) -> GlobalUpdate:
        """Aggregate tenant updates via subspace orthogonalization.
        
        1. Project each Δ_t onto U_shared: α_t = U_shared.T @ Δ_t
        2. Compute private component: β_t = Δ_t - U_shared @ α_t
        3. Verify orthogonality: assert |<α_t, β_t>| < 1e-6
        4. Aggregate: Δ_global = (1/T) * Σ_t (U_shared @ α_t)
        5. Return GlobalUpdate
        """
    
    def _project_to_shared(self, update: np.ndarray) -> np.ndarray:
        """Project update onto shared subspace.
        shared_component = U_shared @ (U_shared.T @ update)
        """
    
    def _project_to_private(self, update: np.ndarray) -> np.ndarray:
        """Project update onto private (orthogonal) subspace.
        private_component = update - self._project_to_shared(update)
        """
    
    def verify_privacy(self, tenant_updates: list[ModelUpdate], 
                        global_update: GlobalUpdate) -> bool:
        """Verify that no tenant's private component leaks into the global update.
        For each tenant: |<private_component_t, global_delta>| < epsilon
        """
```

**`trainer.py`** — Local Training:
```python
class FSOLocalTrainer:
    """Local fine-tuning for a single tenant's model update."""
    
    def train_local(self, base_model_path: str, feedback_data: list[dict],
                     epochs: int = 3) -> ModelUpdate:
        """
        1. Load base SBERT model
        2. Fine-tune on tenant's feedback data for `epochs` iterations
        3. Compute weight delta: Δ_t = θ_tuned - θ_base
        4. Return ModelUpdate with delta and metrics
        """
```

**Verification Tests**:
```python
# tests/ml/fso/test_aggregator.py
def test_orthogonality():  # U_shared.T @ U_private ≈ 0 (< 1e-6)
def test_privacy_guarantee():  # Private component inner product with global = 0
def test_model_improvement():  # Global model accuracy increases after aggregation
def test_single_tenant_isolation():  # One tenant's corrections don't appear in other's predictions
```

**Chunk Size**: 4 new files + test suite

---

### 5.2 FSO API Endpoints

**Files**:
- `ml-service/app/api/federated.py` (NEW)
- `ml-service/app/api/router.py` (MODIFY)
- `ml-service/app/config.py` (MODIFY)

**New Endpoints**:
```python
POST /api/ml/federated/train-round    # Execute one round of federated training
GET  /api/ml/federated/status          # Get status (rounds completed, participants)
POST /api/ml/federated/tenant-update   # Submit tenant's local update for aggregation
```

**Configuration** (add to `config.py`):
```python
# IP-4: FSO Federated Learning
enable_fso: bool = False
fso_min_tenants: int = 3
fso_shared_subspace_dim: int = 64
fso_local_epochs: int = 3
fso_aggregation_schedule: str = "weekly"
```

**Chunk Size**: 3 files (1 new, 2 modified)

---

### 5.3 FSO Backend Integration

**Files**:
- `backend/src/main/kotlin/com/numera/shared/config/FeatureFlagService.kt` (MODIFY)
- `backend/src/main/resources/application.yml` (MODIFY)
- `backend/src/main/resources/db/migration/V030__fso_config.sql` (NEW)

**What**: Add FSO feature flag and configuration to the backend.

**Migration**:
```sql
-- FSO configuration per tenant
ALTER TABLE tenants ADD COLUMN fso_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tenants ADD COLUMN fso_last_round BIGINT DEFAULT 0;
```

**Chunk Size**: 1 migration + 2 file modifications

---

## Chunk 6: Integration Sync Completion

**Priority**: MEDIUM — Required for enterprise external system connectivity  
**Dependencies**: None  
**Estimated Effort**: 4-5 agent-days

### 6.1 CreditLens Canonical Payload

**File**: `backend/src/main/kotlin/com/numera/integration/application/IntegrationSyncService.kt` (MODIFY)

**Problem**: `buildCanonicalPayload()` returns empty stub data.

**Fix**:
```kotlin
private fun buildCanonicalPayload(spreadItem: SpreadItem): CanonicalSpreadPayload {
    val values = spreadValueRepository.findBySpreadItemId(spreadItem.id)
    val customer = customerRepository.findById(spreadItem.customerId).orElseThrow()
    
    val lineItems = values.map { v ->
        CanonicalLineItem(
            code = v.lineItem?.code ?: "",
            label = v.lineItem?.label ?: "",
            value = v.mappedValue,
            period = spreadItem.statementDate.toString(),
            sourceCurrency = spreadItem.sourceCurrency,
            targetCurrency = spreadItem.targetCurrency,
            confidence = v.confidenceScore,
            isManualOverride = v.isManualOverride
        )
    }
    
    return CanonicalSpreadPayload(
        entityId = customer.entityId,
        customerName = customer.longName,
        statementDate = spreadItem.statementDate.toString(),
        auditMethod = spreadItem.auditMethod?.name ?: "",
        frequency = spreadItem.frequency?.name ?: "",
        sourceCurrency = spreadItem.sourceCurrency ?: "USD",
        targetCurrency = spreadItem.targetCurrency ?: "USD",
        consolidation = spreadItem.consolidation ?: false,
        lineItems = lineItems
    )
}
```

**Chunk Size**: 1 file modification

---

### 6.2 Sync Retry Logic Fix

**File**: `backend/src/main/kotlin/com/numera/integration/application/IntegrationSyncService.kt` (MODIFY)

**Problem**: `handleFailure()` always sets status to FAILED regardless of retry count. Dead-letter branch is unreachable.

**Fix**:
```kotlin
private fun handleFailure(record: SyncRecord, exception: Exception) {
    record.retryCount++
    if (record.retryCount >= record.maxRetries) {
        record.status = SyncStatus.FAILED
        record.errorMessage = exception.message
        record.completedAt = Instant.now()
    } else {
        record.status = SyncStatus.RETRYING
        record.errorMessage = "Attempt ${record.retryCount}/${record.maxRetries}: ${exception.message}"
        record.nextRetryAt = Instant.now().plus(Duration.ofMinutes(5 * record.retryCount))
    }
    syncRecordRepository.save(record)
}
```

**Chunk Size**: 1 method fix

---

### 6.3 CreditLens Adapter Implementation

**Files**:
- `backend/src/main/kotlin/com/numera/integration/adapter/CreditLensAdapter.kt` (NEW)

**What**: Implement the `ExternalSystemAdapter` interface for CreditLens, the primary external system target.

**Implementation**:
```kotlin
@Service
@ConditionalOnProperty(name = "numera.integration.creditlens.enabled", havingValue = "true")
class CreditLensAdapter(
    private val webClient: WebClient,
    @Value("\${numera.integration.creditlens.url}") private val baseUrl: String,
    @Value("\${numera.integration.creditlens.api-key}") private val apiKey: String
) : ExternalSystemAdapter {
    
    override fun pushSpread(spread: SpreadData): PushResult { ... }
    override fun pullModel(entityId: String): ModelTemplate { ... }
    override fun pullHistoricalSpreads(entityId: String): List<SpreadData> { ... }
    override fun syncMetadata(entityId: String): MetadataSync { ... }
}
```

**Authentication**: OAuth2 client credentials flow for CreditLens API.

**Chunk Size**: 1 new adapter class + configuration

---

## Chunk 7: Bug Fixes & Hardening

**Priority**: HIGH — Production readiness  
**Dependencies**: None  
**Estimated Effort**: 2-3 agent-days

### 7.1 Covenant Reminder Bug Fix

**File**: `backend/src/main/kotlin/com/numera/covenant/application/CovenantReminderScheduler.kt`

**Bug**: Line ~157 uses `UUID.randomUUID()` as the lookup key in `wasSentToday()`, making the dedup check non-functional.

**Fix**: Use a deterministic key combining the monitoring item ID and the date:
```kotlin
private fun wasSentToday(itemId: UUID): Boolean {
    val key = "${itemId}:${LocalDate.now()}"
    return sentReminders.containsKey(key)
}

// In the reminder sending method:
val key = "${item.id}:${LocalDate.now()}"
sentReminders[key] = true
```

**Chunk Size**: 1 method fix

---

### 7.2 Session Timeout Configuration

**File**: `backend/src/main/kotlin/com/numera/auth/application/SessionManagementService.kt`

**TODO**: Replace hardcoded 30-minute timeout with tenant-specific configuration.

**Fix**:
```kotlin
// Instead of:
val timeout = Duration.ofMinutes(30)

// Use:
val timeout = Duration.ofMinutes(
    featureFlagService.getLong(tenantId, "session.timeout.minutes", 30L)
)
```

**Add migration**:
```sql
INSERT INTO feature_flags (tenant_id, flag_key, flag_type, default_value, description)
VALUES (NULL, 'session.timeout.minutes', 'LONG', '30', 'Session timeout in minutes');
```

**Chunk Size**: 1 method fix + 1 migration

---

### 7.3 Analytics Materialization

**File**: `backend/src/main/kotlin/com/numera/shared/infrastructure/EventConsumers.kt`

**TODO**: Implement analytics materialization logic (currently placeholder).

**Fix**: When `CovenantStatusChangedEvent` or `CovenantBreachedEvent` is received, update the `risk_heatmap` and `covenant_trendlines` tables. The `CovenantIntelligenceService` already has `recomputeRiskHeatmap()` and `recomputeTrendlines()` — call them from the event consumer.

```kotlin
@EventListener
fun onAnalyticsMaterialization(event: DomainEvent) {
    when (event) {
        is CovenantStatusChangedEvent -> {
            covenantIntelligenceService.recomputeRiskHeatmap(event.tenantId)
        }
        is SpreadSubmittedEvent -> {
            covenantIntelligenceService.recomputeTrendlines(event.tenantId, event.customerId)
        }
    }
}
```

**Chunk Size**: 1 event consumer fix

---

### 7.4 Report Export Fix

**File**: `backend/src/main/kotlin/com/numera/spreading/api/SpreadingReportController.kt`

**Problem**: `/api/reports/export` generates a trivial CSV with no data rows.

**Fix**: Generate proper CSV with all spread data including headers, customer info, line items, values, and confidence scores.

**Chunk Size**: 1 controller method fix

---

## Chunk 8: LLM Copilot (Phase 4)

**Priority**: MEDIUM — Differentiator feature, not blocking for pilot  
**Dependencies**: None  
**Estimated Effort**: 8-10 agent-days  
**PhD Reference**: IP-1 (H-SPAR knowledge graph provides context for RAG)

### 8.1 RAG Pipeline

**Files**:
- `ml-service/app/ml/copilot/__init__.py` (NEW)
- `ml-service/app/ml/copilot/rag_engine.py` (NEW)
- `ml-service/app/ml/copilot/vector_store.py` (NEW)
- `ml-service/app/api/copilot.py` (NEW)

**What**: Build a Retrieval-Augmented Generation pipeline for the LLM copilot.

**Architecture**:
```
User Query → Embed Query → Vector Search → Retrieve Context → Build Prompt → LLM Generate → Response
```

**Vector Store**: Use ChromaDB (lightweight, embeds in Python process):
```python
# vector_store.py
class VectorStore:
    def __init__(self, persist_dir: str):
        import chromadb
        self.client = chromadb.PersistentClient(path=persist_dir)
        self.collections = {
            "spreads": self.client.get_or_create_collection("spreads"),
            "documents": self.client.get_or_create_collection("documents"),
            "covenants": self.client.get_or_create_collection("covenants"),
        }
    
    def index_spread(self, spread_id: str, values: list[dict]):
        """Index spread values with their line items, values, and metadata."""
    
    def index_document(self, document_id: str, pages: list[dict]):
        """Index OCR-extracted text from documents."""
    
    def index_covenant(self, covenant_id: str, definition: dict, monitoring: list[dict]):
        """Index covenant definitions and monitoring history."""
    
    def search(self, collection: str, query: str, top_k: int = 5) -> list[dict]:
        """Search for relevant context."""
```

**LLM Integration**: Support both local (Ollama) and API (Anthropic/OpenAI):
```python
# rag_engine.py
class RAGEngine:
    def __init__(self, config: CopilotConfig):
        self.vector_store = VectorStore(config.chroma_dir)
        self.llm = self._init_llm(config)  # Ollama or API
    
    async def query(self, question: str, context_filter: dict = None) -> CopilotResponse:
        """
        1. Embed the question using SBERT
        2. Search vector store for relevant context
        3. Build prompt with system instructions + retrieved context + question
        4. Generate response via LLM
        5. Add source citations (page numbers, spread IDs)
        6. Return structured response
        """
    
    async def index_spread_data(self, tenant_id: str, spread_item: SpreadItem):
        """Index spread data for RAG retrieval."""
    
    async def index_document_data(self, tenant_id: str, document: Document):
        """Index document OCR text for RAG retrieval."""
```

**API Endpoints**:
```python
POST /api/ml/copilot/query          # Ask a question
POST /api/ml/copilot/index/spread   # Index spread data
POST /api/ml/copilot/index/document # Index document data
GET  /api/ml/copilot/status         # Check LLM status
```

**Configuration**:
```python
class CopilotConfig:
    enable_copilot: bool = False
    llm_provider: str = "ollama"  # ollama, anthropic, openai
    ollama_model: str = "llama3.1:8b"
    anthropic_model: str = "claude-3-sonnet-20240229"
    openai_model: str = "gpt-4o"
    chroma_persist_dir: str = "/data/chroma"
    max_context_documents: int = 5
    max_context_spreads: int = 3
    temperature: float = 0.3
```

**Chunk Size**: 4 new files + 1 config update

---

### 8.2 Copilot UI

**Files**:
- `numera-ui/src/features/copilot/CopilotPanel.tsx` (NEW)
- `numera-ui/src/features/copilot/CopilotMessage.tsx` (NEW)
- `numera-ui/src/features/copilot/useCopilot.ts` (NEW hook)

**What**: Slide-out chat panel accessible from any page.

**Features**:
- Floating button in bottom-right corner (chat bubble icon)
- Click → slide-out panel from right
- Chat interface with message bubbles
- Context-aware: automatically includes current page data (spread ID, customer ID, etc.)
- Pre-defined quick queries:
  - "Why doesn't the balance sheet balance?"
  - "Show me the depreciation note"
  - "Which covenants are at risk this month?"
  - "Compare revenue across last 4 periods"
- Source citations with clickable links (navigates to exact spread cell or document page)
- Markdown rendering for responses
- Auto-scroll to latest message
- Clear conversation button

**Backend Proxy**:
```
POST /api/copilot/query  # Proxies to ML service /api/ml/copilot/query
```

**Chunk Size**: 3 new frontend files + 1 backend proxy endpoint

---

### 8.3 NL Query Engine for Dashboards

**Files**:
- `ml-service/app/ml/copilot/query_parser.py` (NEW)

**What**: Natural language → SQL/filter parameters for dashboard queries.

**Implementation**:
```python
class NLQueryParser:
    """Parse natural language queries into structured filter parameters."""
    
    async def parse(self, query: str) -> ParsedQuery:
        """
        Examples:
          "Show me all clients whose current ratio dropped >15% vs last quarter"
          → {metric: "current_ratio", condition: "dropped", threshold: 0.15, comparison: "vs_last_quarter"}
          
          "Which covenants are due this month?"
          → {type: "covenants", filter: "due_this_month"}
          
          "Revenue trend for Acme Corp"
          → {type: "trend", metric: "revenue", customer: "Acme Corp"}
        """
        # Use LLM to extract structured intent from natural language
        # Return ParsedQuery with entity, metric, filter, and comparison params
```

**Chunk Size**: 1 new file

---

## Chunk 9: Enterprise Hardening (Phase 5)

**Priority**: MEDIUM — Required for production deployment, not blocking for pilot  
**Dependencies**: Chunks 1-4 should be complete  
**Estimated Effort**: 10-12 agent-days

### 9.1 Kubernetes Packaging

**Files**:
- `infra/helm/Chart.yaml` (NEW)
- `infra/helm/values.yaml` (NEW)
- `infra/helm/templates/backend-deployment.yaml` (NEW)
- `infra/helm/templates/backend-service.yaml` (NEW)
- `infra/helm/templates/ml-service-deployment.yaml` (NEW)
- `infra/helm/templates/ocr-service-deployment.yaml` (NEW)
- `infra/helm/templates/frontend-deployment.yaml` (NEW)
- `infra/helm/templates/postgresql-statefulset.yaml` (NEW)
- `infra/helm/templates/redis-statefulset.yaml` (NEW)
- `infra/helm/templates/minio-statefulset.yaml` (NEW)
- `infra/helm/templates/rabbitmq-statefulset.yaml` (NEW)
- `infra/helm/templates/mlflow-deployment.yaml` (NEW)
- `infra/helm/templates/ingress.yaml` (NEW)
- `infra/helm/templates/configmap.yaml` (NEW)
- `infra/helm/templates/secrets.yaml` (NEW)

**What**: Helm charts for Kubernetes deployment of all services.

**Requirements**:
- Configurable resource limits (CPU, memory)
- Configurable replicas per service
- Persistent volume claims for PostgreSQL, Redis, MinIO, MLflow
- Ingress with TLS termination
- Liveness/readiness probes for all services
- Horizontal Pod Autoscaler for backend and ML service
- Network policies for inter-service communication
- ConfigMap for environment-specific configuration
- Secrets management (DB passwords, JWT secrets, API keys)
- Values.yaml with sensible defaults for dev/staging/prod

**Air-Gapped Installation**:
- Pre-built container images pushed to a private registry
- `helm template` for generating static Kubernetes manifests
- Installation guide with prerequisites

**Chunk Size**: 13 new files

---

### 9.2 Observability Stack

**Files**:
- `infra/grafana/dashboards/numera-overview.json` (NEW)
- `infra/grafana/dashboards/ml-performance.json` (NEW)
- `infra/grafana/dashboards/covenant-health.json` (NEW)
- `infra/grafana/datasources/prometheus.yml` (NEW)
- `infra/prometheus/prometheus.yml` (NEW)
- `infra/prometheus/alerts/numera-alerts.yml` (NEW)
- `backend/src/main/kotlin/com/numera/shared/observability/MetricsConfig.kt` (NEW)

**What**: Prometheus + Grafana monitoring and alerting.

**Metrics to Expose**:
- API latency histograms (per endpoint)
- Error rates (4xx, 5xx)
- Queue depths (RabbitMQ)
- ML inference times (per model)
- Document processing times
- Active spread locks
- Covenant breach rates
- User session counts

**Alerts**:
- API error rate > 5%
- ML inference time P99 > 10s
- Document processing queue depth > 100
- Database connection pool > 80%
- Disk usage > 85%

**Backend**: Add Micrometer + Prometheus dependencies, expose `/actuator/prometheus` endpoint.

**Chunk Size**: 6 new infrastructure files + 1 backend config

---

### 9.3 Load Testing Suite

**Files**:
- `infra/load-testing/locustfile.py` (NEW)
- `infra/load-testing/config/staging.yml` (NEW)

**What**: Locust load testing suite simulating realistic user scenarios.

**Scenarios**:
1. **Analyst Login & Spread**: Login → Upload document → Wait for processing → Open spread → Map values → Submit
2. **Manager Approval**: Login → View pending spreads → Approve/reject
3. **Covenant Monitoring**: Login → View covenants → Check monitoring items → Upload document → Verify
4. **Concurrent Document Upload**: 10 users uploading 5 documents each simultaneously
5. **Dashboard Heavy Load**: 50 users refreshing dashboard simultaneously

**Targets**:
- < 500ms UI interactions
- < 2s search queries
- < 3min full spread processing
- 100 concurrent users
- 1000 documents/day processing

**Chunk Size**: 2 new files

---

### 9.4 Data Sovereignty & Compliance

**Files**:
- `backend/src/main/kotlin/com/numera/shared/compliance/DataExportService.kt` (NEW)
- `backend/src/main/kotlin/com/numera/shared/compliance/DataDeletionService.kt` (NEW)
- `backend/src/main/kotlin/com/numera/shared/compliance/ConsentService.kt` (NEW)

**What**: GDPR/DIFC/ADGM compliance features.

**GDPR Compliance**:
- Right to portability: `GET /api/compliance/export/{userId}` → export all user data as JSON
- Right to erasure: `DELETE /api/compliance/erase/{userId}` → delete all PII, redact audit trail using ZK-RFA chameleon hash
- Consent management: `POST /api/compliance/consent` → record consent with timestamp and scope

**DIFC/ADGM Compliance**:
- Per-tenant region configuration in `tenants` table
- Data residency enforcement: queries scoped to tenant's designated region
- Cross-region transfer controls: block data export outside region

**Chunk Size**: 3 new service files + 2 API controllers + 1 migration

---

## Chunk 10: Portfolio Analytics & Advanced Features

**Priority**: LOW — Post-pilot enhancement  
**Dependencies**: Chunk 1 (spreading workspace), Chunk 8 (LLM copilot for NL queries)  
**Estimated Effort**: 5-6 agent-days

### 10.1 Portfolio Analytics Dashboard

**Files**:
- `backend/src/main/kotlin/com/numera/portfolio/application/PortfolioService.kt` (NEW)
- `backend/src/main/kotlin/com/numera/portfolio/api/PortfolioController.kt` (NEW)
- `numera-ui/src/features/portfolio/PortfolioPage.tsx` (NEW)
- `numera-ui/src/features/portfolio/components/RatioComparisonTable.tsx` (NEW)
- `numera-ui/src/features/portfolio/components/RatioScatterPlot.tsx` (NEW)
- `numera-ui/src/features/portfolio/components/TrendChart.tsx` (NEW)

**What**: Cross-client financial ratio comparisons and trend analysis.

**Backend Endpoints**:
```
GET  /api/portfolio/ratios              # All clients with key ratios side by side
GET  /api/portfolio/ratios/trends        # Ratio trends over time
POST /api/portfolio/query               # Custom NL or structured query
GET  /api/portfolio/alerts               # Clients with significant ratio changes
```

**Frontend**:
- Table view: All clients with key ratios (Current Ratio, D/E, DSCR, ROE, etc.) side by side
- Scatter plot: X/Y axis selector for any two ratios
- Box plot: Distribution of a single ratio across the portfolio
- Trend lines: Time series of ratios per client
- NL query bar (from Chunk 8.3): "Show me all clients whose current ratio dropped >15%"

**Chunk Size**: 2 backend files + 4 frontend files

---

### 10.2 Dashboard Export & Sharing

**Files**:
- `numera-ui/src/features/dashboard/components/DashboardExport.tsx` (NEW)

**What**: Export dashboard snapshots as PDF and share via links.

**Implementation**:
- Use `html2canvas` + `jsPDF` for client-side PDF generation
- `POST /api/dashboard/share` → create shareable link with expiry and role-based access
- `GET /api/dashboard/shared/{token}` → view shared dashboard (no auth required if token valid)

**Chunk Size**: 1 frontend component + 2 backend endpoints

---

## Chunk 11: Email Template & Waiver Completion

**Priority**: MEDIUM — Enterprise workflow completion  
**Dependencies**: None  
**Estimated Effort**: 3-4 agent-days

### 11.1 Rich Email Template Editor

**Files**:
- `numera-ui/src/features/covenants/components/TemplateEditor.tsx` (NEW)
- `numera-ui/src/features/covenants/components/VariableInserter.tsx` (NEW)

**What**: Replace the plain textarea with a WYSIWYG rich text editor for email templates.

**Implementation**:
- Use `@tiptap/react` (rich text editor) for template body editing
- Variable insertion panel: click to insert `{{CUSTOMER_NAME}}`, `{{RIM_ID}}`, `{{COVENANT_NAME}}`, `{{PERIOD}}`, `{{DUE_DATE}}`, `{{CALCULATED_VALUE}}`, `{{THRESHOLD_VALUE}}`, `{{BREACH_DATE}}`, `{{ANALYST_NAME}}`, `{{MANAGER_NAME}}`, `{{DATE}}`
- Live preview: render template with sample data
- Category filter (Financial/Non-Financial)
- Template duplication feature
- Update template API integration (`PUT /api/covenants/templates/{id}`)

**Chunk Size**: 2 new components + 1 existing page modification

---

### 11.2 Waiver Contacts from API

**File**: `numera-ui/src/features/covenants/[itemId]/waiver/page.tsx` (MODIFY)

**Problem**: Recipient contacts are hardcoded placeholders.

**Fix**:
```typescript
// Replace hardcoded contacts with API call:
const { data: contacts } = useCovenantCustomerContacts(covenantCustomerId);
// Use real contacts in the recipient selector
```

The backend already has `CovenantContact` entities with `contactType` (INTERNAL, EXTERNAL), `email`, `fullName`, `designation`.

**Chunk Size**: 1 file modification

---

### 11.3 Waiver Letter HTML Preview

**File**: `numera-ui/src/features/covenants/[itemId]/waiver/page.tsx` (MODIFY)

**What**: Replace the plain textarea showing raw HTML with a rendered HTML preview.

**Implementation**:
- Use `dangerouslySetInnerHTML` for the preview pane
- Split view: left side = editable fields, right side = rendered preview
- Print button: `window.print()` with print-specific CSS
- Email sending: call `POST /api/covenants/waivers/{id}/send`
- Download PDF: call `GET /api/covenants/waivers/{id}/download` (after Chunk 2.1 makes this return real PDF)

**Chunk Size**: 1 file modification

---

## Chunk 12: Document Verification Workflow (Covenants P2.5)

**Priority**: MEDIUM — Covenant workflow completion  
**Dependencies**: None  
**Estimated Effort**: 3-4 agent-days

### 12.1 Document Verification UI

**Files**:
- `numera-ui/src/features/covenants/components/DocumentVerification.tsx` (NEW)

**What**: Checker workflow for verifying non-financial covenant documents.

**Implementation**:
- Two views: "Maker" (submit documents) and "Checker" (verify documents)
- Maker view:
  - Upload documents against a monitoring item
  - Add comments
  - Submit for verification → status changes to SUBMITTED
  - View verification status
- Checker view:
  - List of submitted items awaiting verification
  - Document preview, download
  - Comments section
  - Approve button → status changes to APPROVED
  - Reject button (mandatory comment) → status changes to REJECTED
  - Rejected items return to Maker for resubmission
- Non-financial auto-approval: When a spread is submitted with matching entity/statement date/audit method, the corresponding non-financial covenant item auto-marks as APPROVED

**Backend**: Already has `POST /api/covenants/monitoring/{id}/documents`, `POST /api/covenants/monitoring/{id}/checker-decision`, `POST /api/covenants/monitoring/{id}/trigger-action`

**Chunk Size**: 1 new component + integration into covenant detail page

---

## Implementation Execution Order

### Phase A: Critical Path (Weeks 1-4) — Must complete before pilot

| Week | Chunks | Focus |
|------|--------|-------|
| 1 | 1.1, 1.2, 7.1-7.4 | Expression editor, zone overlay integration, bug fixes |
| 2 | 1.3, 1.4, 2.1, 6.1 | Page operations, comments, PDF generation, sync payload fix |
| 3 | 1.5-1.7, 4.1 | Historical loads, grid features, split view, ML pipeline fix |
| 4 | 4.2-4.5 | STGH GCN training, GNN pruner training, SBERT fine-tuning, LayoutLM training |

### Phase B: Enterprise Features (Weeks 5-8) — Required for production

| Week | Chunks | Focus |
|------|--------|-------|
| 5 | 2.2-2.3, 3.1-3.2 | Report generation, scheduled reports, model template UI, zone management |
| 6 | 3.3-3.6 | AI model management, workflow designer, language management, user management |
| 7 | 5.1-5.3, 6.2-6.3 | FSO implementation, sync retry fix, CreditLens adapter |
| 8 | 11.1-11.3, 12.1 | Email template editor, waiver contacts, verification workflow |

### Phase C: Advanced Features (Weeks 9-12) — Post-pilot

| Week | Chunks | Focus |
|------|--------|-------|
| 9 | 4.6, 8.1 | SBERT integration in STGH, RAG pipeline |
| 10 | 8.2-8.3 | Copilot UI, NL query engine |
| 11 | 9.1-9.3 | Kubernetes, observability, load testing |
| 12 | 9.4, 10.1-10.2 | Data sovereignty, portfolio analytics |

---

## Total Effort Estimate

| Chunk | Description | Effort (agent-days) |
|-------|-------------|---------------------|
| 1 | Spreading Workspace UI Completion | 8-10 |
| 2 | PDF Generation & Report Service | 5-6 |
| 3 | Admin Panels Completion | 6-8 |
| 4 | ML Pipeline Fixes & Training | 10-12 |
| 5 | FSO Implementation | 5-6 |
| 6 | Integration Sync Completion | 4-5 |
| 7 | Bug Fixes & Hardening | 2-3 |
| 8 | LLM Copilot | 8-10 |
| 9 | Enterprise Hardening | 10-12 |
| 10 | Portfolio Analytics | 5-6 |
| 11 | Email Template & Waiver | 3-4 |
| 12 | Document Verification | 3-4 |
| **Total** | | **70-86 agent-days** |

---

## Cross-Cutting Concerns

### Feature Flags

All new features should be behind feature flags in the `feature_flags` table:

| Flag | Default | Description |
|------|---------|-------------|
| `copilot.enabled` | false | LLM copilot panel |
| `copilot.nl_query` | false | Natural language dashboard queries |
| `fso.enabled` | false | Federated subspace orthogonalization |
| `report.pdf_export` | true | PDF report generation |
| `report.excel_export` | true | Excel report generation |
| `report.scheduled` | false | Scheduled report delivery |
| `portfolio.analytics` | false | Portfolio analytics dashboard |
| `page.merge` | true | PDF page merge operations |
| `page.rotate` | true | PDF page rotation |
| `page.split` | true | PDF page split |
| `page.clean` | false | PDF despeckle/watermark removal |
| `expression.editor` | true | Visual expression editor |
| `historical.loads` | true | Load historical spreads |
| `split.view` | true | Dual document pane |

### Database Migrations Required

| Migration | Purpose |
|-----------|---------|
| V030__fso_config.sql | FSO configuration per tenant |
| V031__zone_management.sql | Zone CRUD table |
| V032__report_schedules.sql | Scheduled report delivery |
| V033__page_operations.sql | Document page operation tracking |
| V034__copilot_conversations.sql | LLM copilot conversation history |
| V035__portfolio_analytics.sql | Portfolio ratio snapshots |
| V036__gdpr_compliance.sql | Consent records and data export tracking |

### New Dependencies

| Service | Package | Version | Purpose |
|---------|---------|---------|---------|
| Backend | org.apache.poi:poi-ooxml | 5.3.0 | Excel report generation |
| Backend | com.itextpdf:itext7-core | 8.0.4 | PDF report generation |
| Backend | io.micrometer:micrometer-registry-prometheus | (managed) | Prometheus metrics |
| ML Service | chromadb | 0.4.* | RAG vector store |
| ML Service | sentence-transformers | (existing) | SBERT fine-tuning |
| Frontend | @tiptap/react | 2.x | Rich text editor |
| Frontend | html2canvas | 1.x | Dashboard screenshot |
| Frontend | jspdf | 2.x | Client-side PDF export |

---

## Verification Checklist

After completing each chunk, verify:

### Per-Chunk Verification
1. **All new API endpoints return correct HTTP status codes** (200, 201, 400, 401, 403, 404, 500)
2. **All new database migrations apply cleanly** on top of existing V029
3. **All new frontend pages render without console errors**
4. **All new services have unit tests** (target: >80% line coverage)
5. **All new API endpoints have integration tests**
6. **Feature flags default to OFF** for new features

### Full-Stack Verification
1. `docker-compose.full.yml` starts all services without errors
2. Backend health check: `GET /actuator/health` returns UP
3. ML service health check: `GET /api/ml/health` returns model status
4. OCR service health check: `GET /api/ocr/health` returns model status
5. Full pipeline: Upload PDF → OCR → Table detection → Zone classification → Mapping → Validation → Submit
6. Full covenant flow: Create customer → Create covenant → Generate monitoring → Upload document → Verify → Approve
7. All 7 IP feature flags work: Enable/disable each independently without errors

---

## Appendix A: PhD Engineering Analysis Alignment Matrix

Each IP's PhD analysis specifies algorithms and requirements. This matrix maps what's implemented vs. what still needs work:

| IP | Algorithm | Implementation Status | Gap |
|----|-----------|----------------------|-----|
| IP-1 (H-SPAR) | Spectral partitioning, anchor detection, cross-document linking | ✅ Implemented | Need: cross-document temporal linking for same entity |
| IP-2 (NG-MILP) | GNN pruner + CP-SAT solver | ✅ Implemented | Need: trained GNN weights (Chunk 4.3) |
| IP-3 (ZK-RFA) | Chameleon hash + MMR accumulator | ✅ Implemented | Need: HSM/Vault integration for trapdoor key |
| IP-4 (FSO) | SVD subspace decomposition + federated aggregation | 🔴 Not implemented | Full implementation needed (Chunk 5) |
| IP-5 (STGH) | GCN fingerprinting + LSH | ✅ Implemented | Need: trained GCN weights (Chunk 4.2), SBERT integration (Chunk 4.6) |
| IP-6 (RS-BSN) | HMM regime detection + Kalman filter + Monte Carlo | ✅ Implemented | Need: macro indicator data feed integration |
| IP-7 (OW-PGGR) | Materiality-weighted proximal gradient descent | ✅ Implemented | Need: IFRS taxonomy materiality tiers (static M_tax cache) |

### IP-6 (RS-BSN) Macro Indicator Data Feed

The RS-BSN model supports `macro_features` in `CovenantInput`, but the backend's `CovenantPredictionService` currently only passes historical covenant values. To fully leverage RS-BSN:

**Backend Changes**:
```kotlin
// In CovenantPredictionService, enrich prediction input with macro indicators:
val macroFeatures = macroIndicatorRepository.getLatestIndicators(
    tenantId = tenantId,
    indicators = listOf("VIX", "SOFR", "CPI", "PMI")
)
// Pass to ML service
```

**New Infrastructure**:
- Macro indicator data source (e.g., FRED API for US, ECB for EU)
- Scheduled job to fetch and cache indicators daily
- Storage in `macro_indicators` table

This is a lower-priority enhancement since the RS-BSN model degrades gracefully without macro features (it uses coefficient-of-variation heuristics as fallback).

---

## Appendix B: Unimplemented UI Features from Implementation Plan

The following features from `implementation_plan.md` are not yet in the UI and are prioritized accordingly:

| Plan Section | Feature | Priority | Chunk |
|-------------|---------|----------|-------|
| P0.5.1 | Zone overlay on PDF | CRITICAL | 1.2 |
| P0.5.2 | Expression editor | CRITICAL | 1.1 |
| P0.5.4 | PDF page operations | HIGH | 1.3 |
| P0.5.5 | Show/Hide rows, SmartFill, Variance, Currency | HIGH | 1.6 |
| P0.5.6 | Metadata editing panel | MEDIUM | 1.4 (partial) |
| P0.5.7 | Auto-generated comments | HIGH | 1.4 |
| P0.5.8 | Load historical spreads | HIGH | 1.5 |
| P1.3 | File Store recommended files tab | LOW | — |
| P1.5.3 | OCR error correction | MEDIUM | — |
| P1.5.7 | Auto-generated comment URLs (public share) | MEDIUM | — |
| P1.8.2 | Global taxonomy management | ✅ Done | — |
| P1.8.3 | Global zone management | HIGH | 3.2 |
| P1.8.4 | Exclusion list management | ✅ Done | — |
| P1.8.6 | Model template management | HIGH | 3.1 |
| P1.8.7 | AI model management | HIGH | 3.3 |
| P2.5 | Document verification workflow | MEDIUM | 12.1 |
| P2.6.1 | Rich email template editor | MEDIUM | 11.1 |
| P3.1 | BPMN workflow designer | HIGH | 3.4 |
| P3.2 | MIS reporting (Excel/PDF) | CRITICAL | 2.2 |
| P3.3 | Portfolio analytics | LOW | 10.1 |
| P4.1 | LLM Copilot | MEDIUM | 8 |
| P5.2 | Kubernetes packaging | MEDIUM | 9.1 |
| P5.3 | Load testing & observability | MEDIUM | 9.2-9.3 |