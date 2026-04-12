# Autonomous Implementation Plan — Numera Platform

> **Generated**: April 11, 2026  
> **Baseline**: Application Specification v1.0  
> **Scope**: Gap analysis of current codebase vs full enterprise delivery + actionable task breakdown for AI agent execution

---

## Executive Summary

### What's Built (≈55% of total enterprise delivery)

| Area | Completion | Notes |
|------|-----------|-------|
| **Authentication & Security** | 85% | SSO, MFA, JWT, RBAC done. Missing: password policies, bulk provisioning, session limits |
| **Customer Management** | 70% | Basic CRUD done. Missing: group-based visibility enforcement, external sync, extended metadata |
| **Document Ingestion Pipeline** | 75% | Upload, OCR, table detection, zone classification done. Missing: multi-file merge, password-protected handling, clean file post-processing, file store views |
| **AI Spreading Engine** | 70% | Mapping orchestrator, confidence scoring, autofill, version control done. Missing: expression editor UI, split doc view, variance columns, category navigation, 20-period comparison |
| **Financial Model Engine** | 60% | Templates, line items, formula engine done. Missing: customer model copies, model versioning, row hide/freeze/group, US GAAP & regional templates |
| **Covenant Module** | 75% | Definitions, monitoring, breach detection, waiver workflow, email templates done. Missing: formula builder UI, skip-overlap logic, predictive ML model, automated reminders, signature management UI |
| **Workflow Engine** | 5% | Stub controller exists. No Camunda/Flowable integration, no BPMN designer |
| **Reporting & Analytics** | 15% | Basic dashboard stats, JSON report display. Missing: live dashboards, portfolio analytics, MIS export, scheduled reports |
| **Administration** | 60% | User mgmt, taxonomy, exclusion rules done. Missing: language mgmt, AI model admin UI, system config UI, bulk user provisioning |
| **External Integrations** | 5% | Adapter architecture referenced. No CreditLens/nCino/Finastra connectors |
| **Infrastructure & DevOps** | 40% | Docker, Compose files exist. Missing: Kubernetes/Helm, multi-region, Kafka/RabbitMQ, monitoring stack |
| **ML Training Pipeline** | 70% | 23 notebooks, training scripts, configs exist. Missing: actual trained models, evaluation benchmarks |
| **LLM Copilot** | 0% | Phase 4 — not started |
| **Natural Language Querying** | 0% | Phase 4 — not started |

### Overall: ~55% complete. Remaining work organized into 147 discrete tasks below.

---

## Task Organization

Tasks are grouped into **Workstreams** matching the spec's module structure. Each task includes:
- **ID**: Unique identifier (WS-XX-NNN)
- **Priority**: P0 (blocking), P1 (core), P2 (important), P3 (nice-to-have)
- **Layer**: Backend / Frontend / ML / Infra / Data
- **Dependencies**: Tasks that must complete first
- **Files to Create/Modify**: Specific paths for the AI agent
- **Acceptance Criteria**: What "done" looks like

---

## Workstream 1: Authentication & Access Control Hardening

### WS-01-001: Configurable Password Policies
- **Priority**: P1
- **Layer**: Backend
- **Dependencies**: None
- **Description**: Add configurable password policies per tenant — complexity rules (min length, uppercase, lowercase, digit, special char requirements), expiry period, password history (prevent reuse of last N passwords).
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/auth/domain/User.kt` — add `passwordChangedAt`, `passwordHistory` fields
  - `backend/src/main/kotlin/com/numera/auth/application/AuthService.kt` — add password validation logic
  - Create `backend/src/main/kotlin/com/numera/auth/domain/PasswordPolicy.kt` — entity for per-tenant password rules
  - Create `backend/src/main/kotlin/com/numera/auth/application/PasswordPolicyService.kt`
  - `backend/src/main/resources/db/migration/` — new migration V014
- **Acceptance Criteria**:
  - Password complexity enforced on registration and password change
  - Password expiry triggers forced change on next login
  - Last N passwords cannot be reused
  - Policies configurable per tenant via admin API

### WS-01-002: Session Management Controls
- **Priority**: P1
- **Layer**: Backend
- **Dependencies**: None
- **Description**: Add configurable session timeout, concurrent session limits, and forced logout capability per tenant.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/shared/config/SecurityConfig.kt` — add session tracking
  - `backend/src/main/kotlin/com/numera/auth/application/AuthService.kt` — enforce concurrent session limits
  - Create `backend/src/main/kotlin/com/numera/auth/application/SessionManagementService.kt` — Redis-backed session tracker
  - `backend/src/main/kotlin/com/numera/auth/api/AuthController.kt` — add `POST /api/auth/force-logout/{userId}` endpoint
- **Acceptance Criteria**:
  - Configurable idle timeout per tenant (default 30 min)
  - Max concurrent sessions enforced (default 1)
  - Admin can force-logout any user
  - Sessions tracked in Redis with TTL

### WS-01-003: Bulk User Provisioning via CSV
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Support bulk user creation via CSV upload and API endpoint.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/admin/UserManagementController.kt` — add `POST /api/admin/users/bulk-upload` endpoint
  - Create `backend/src/main/kotlin/com/numera/admin/application/BulkUserProvisioningService.kt` — CSV parsing, validation, batch creation
  - `numera-ui/src/app/(dashboard)/admin/users/page.tsx` — add CSV upload button and results display
  - `numera-ui/src/services/adminApi.ts` — add `useBulkUploadUsers()` hook
- **Acceptance Criteria**:
  - Upload CSV with columns: email, fullName, role, group
  - Validation errors returned per row
  - Successfully created users get welcome emails
  - Results summary: created count, error count, error details

### WS-01-004: User Account Lifecycle States
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: WS-01-003
- **Description**: Implement full user lifecycle: Pending Approval → Active → Inactive → Rejected. Self-registration with admin approval workflow.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/auth/domain/User.kt` — add `accountStatus` enum field (PENDING, ACTIVE, INACTIVE, REJECTED)
  - `backend/src/main/kotlin/com/numera/auth/api/AuthController.kt` — add `POST /api/auth/register` self-registration endpoint
  - `backend/src/main/kotlin/com/numera/admin/UserManagementController.kt` — add `POST /api/admin/users/{id}/approve`, `POST /api/admin/users/{id}/reject`
  - `numera-ui/src/app/(auth)/register/page.tsx` — create self-registration page
  - `numera-ui/src/app/(dashboard)/admin/users/page.tsx` — add approval/rejection actions
- **Acceptance Criteria**:
  - New users land in PENDING state
  - Admin sees pending approvals with approve/reject buttons
  - Rejected users notified via email
  - Only ACTIVE users can log in

### WS-01-005: Group-Based Customer Visibility Enforcement
- **Priority**: P1
- **Layer**: Backend
- **Dependencies**: None
- **Description**: Enforce that users can only see customers within their assigned user group. Currently groups exist but visibility is not enforced at the query level.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/customer/infrastructure/CustomerRepository.kt` — add filtered queries by group membership
  - `backend/src/main/kotlin/com/numera/customer/application/CustomerService.kt` — inject current user group filtering
  - `backend/src/main/kotlin/com/numera/spreading/infrastructure/SpreadItemRepository.kt` — filter by group
  - `backend/src/main/kotlin/com/numera/covenant/infrastructure/CovenantCustomerRepository.kt` — filter by group
- **Acceptance Criteria**:
  - All customer-related queries filter by the current user's group membership
  - Admin/Global Manager roles bypass group filtering
  - API returns 403 if user accesses customer outside their group

---

## Workstream 2: Document Ingestion Pipeline Completion

### WS-02-001: Multi-File Upload & Merge
- **Priority**: P1
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Support uploading multiple documents for a single financial period, merged into one processing unit.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/document/api/DocumentController.kt` — modify upload endpoint to accept multiple files
  - `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt` — add merge logic (combine pages from multiple PDFs into single processing unit)
  - `numera-ui/src/app/(dashboard)/documents/page.tsx` — multi-file drag-and-drop upload
  - `numera-ui/src/services/documentApi.ts` — update upload mutation for multi-file
- **Acceptance Criteria**:
  - Upload 2+ PDFs simultaneously for one customer+period
  - Backend merges pages into single processing pipeline
  - Individual file tracking preserved (source file attribution per page)
  - UI shows merge progress

### WS-02-002: Password-Protected Document Handling
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Detect password-protected PDFs and prompt user for password. Decrypt in-memory for processing, never store the password.
- **Files to Modify**:
  - `ocr-service/app/api/ocr.py` — detect password protection, accept password parameter
  - `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt` — pass password to OCR service
  - `backend/src/main/kotlin/com/numera/document/api/DocumentController.kt` — accept optional password in upload
  - `numera-ui/src/app/(dashboard)/documents/page.tsx` — password prompt dialog on upload failure
- **Acceptance Criteria**:
  - PDF_PASSWORD_PROTECTED error returned when detected
  - User prompted for password in UI
  - Password used once for decryption, never persisted
  - Processing continues normally after decryption

### WS-02-003: Clean File Post-Processing
- **Priority**: P2
- **Layer**: Backend (OCR Service)
- **Dependencies**: None
- **Description**: Implement image cleanup pipeline: despeckle (low/high), watermark removal, deskew correction before OCR.
- **Files to Modify**:
  - Create `ocr-service/app/services/image_preprocessor.py` — OpenCV-based cleanup pipeline
  - `ocr-service/app/api/ocr.py` — integrate preprocessor before OCR extraction
  - `ocr-service/app/config.py` — add preprocessing config (enable/disable each step)
- **Acceptance Criteria**:
  - Despeckle removes noise from scanned documents
  - Deskew corrects rotated scans (up to 15 degrees)
  - Watermark detection and removal for common patterns
  - Each step configurable (on/off) per request

### WS-02-004: File Store Views (My Files / All Files / Error Files)
- **Priority**: P1
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: The spec requires three document views: My Files (current user), All Files (group), Error Files (failed processing). Currently only one flat list exists.
- **Files to Modify**:
  - `numera-ui/src/app/(dashboard)/documents/page.tsx` — add tabbed views (My Files / All Files / Error Files)
  - `numera-ui/src/services/documentApi.ts` — add filter parameters for uploadedBy, status=ERROR
  - `backend/src/main/kotlin/com/numera/document/api/DocumentController.kt` — add `uploadedBy` and `status` filter parameters
  - `backend/src/main/kotlin/com/numera/document/infrastructure/DocumentRepository.kt` — add filtered queries
- **Acceptance Criteria**:
  - Three tabs visible on documents page
  - My Files shows only current user's uploads
  - All Files shows group-level documents
  - Error Files shows only documents with ERROR status
  - Each view supports search and sorting

### WS-02-005: Background Bulk Pre-Processing (File Store)
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-02-004
- **Description**: Allow users to upload documents in off-hours for background pre-processing. System processes (OCR + classification) asynchronously. Pre-processed files are ready when analysts start work.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt` — add scheduled batch processing mode
  - Create `backend/src/main/kotlin/com/numera/document/application/BulkProcessingScheduler.kt` — @Scheduled job to process queued documents
  - `backend/src/main/kotlin/com/numera/document/domain/Document.kt` — add `scheduledProcessing` flag
- **Acceptance Criteria**:
  - Documents can be uploaded without immediate processing
  - Background scheduler picks up unprocessed documents
  - Documents transition: Uploaded → Processing → Ready
  - Status visible in File Store UI

### WS-02-006: Multi-Language OCR Support
- **Priority**: P2
- **Layer**: Backend (OCR Service) + Frontend
- **Dependencies**: None
- **Description**: Currently English and Arabic are selectable. Expand to support French, Chinese, and other languages. Add language auto-detection.
- **Files to Modify**:
  - `ocr-service/app/config.py` — expand supported languages list
  - `ocr-service/app/api/ocr.py` — add auto-detection via langdetect or VLM
  - `numera-ui/src/app/(dashboard)/documents/page.tsx` — expand language dropdown
  - `backend/src/main/kotlin/com/numera/document/domain/Document.kt` — already has `language` field
- **Acceptance Criteria**:
  - At least English, Arabic, French, Chinese supported
  - Auto-detect option available
  - Language stored with document metadata
  - OCR accuracy maintained for each language

---

## Workstream 3: Spreading Workspace UI Completion

### WS-03-001: Dual-Pane Document Viewer with Zone Overlays
- **Priority**: P0 (Critical)
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: The current PDF viewer is a basic iframe. The spec requires a coordinate-aware PDF viewer with clickable zone overlays, color-coded bounding boxes per zone type, and source value highlights. Must use PDF.js for coordinate-level rendering.
- **Files to Modify**:
  - `numera-ui/src/components/spreading/PdfViewer.tsx` — complete rewrite with PDF.js (@pdfjs-dist)
  - Create `numera-ui/src/components/spreading/ZoneOverlay.tsx` — SVG/Canvas overlay for zone bounding boxes
  - Create `numera-ui/src/components/spreading/SourceHighlight.tsx` — clickable value highlights on PDF
  - `numera-ui/src/stores/spreadStore.ts` — add zone overlay state, highlighted cell ↔ source location linking
  - `numera-ui/package.json` — add pdfjs-dist dependency
- **Acceptance Criteria**:
  - PDF rendered via PDF.js with full text layer
  - Zone bounding boxes overlay with color coding (IS=blue, BS=green, CF=orange)
  - Clicking a cell in the grid scrolls PDF to source location
  - Clicking a value in PDF highlights corresponding grid cell
  - Zoom, pan, rotate controls functional
  - Page navigation works

### WS-03-002: Split Document View
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: WS-03-001
- **Description**: Support two side-by-side document panes for mapping from different pages simultaneously.
- **Files to Modify**:
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — add split-view toggle
  - `numera-ui/src/components/spreading/PdfViewer.tsx` — support multiple instances
  - Create `numera-ui/src/components/spreading/SplitDocumentView.tsx` — side-by-side PDF viewer
- **Acceptance Criteria**:
  - Toggle button switches between single and split view
  - Each pane independently navigable
  - Zone overlays work in both panes
  - Value highlighting works across panes

### WS-03-003: Expression Editor with Visual Formula Builder
- **Priority**: P1
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: When an analyst clicks a cell, they should see the expression that computed it and be able to edit it visually. Currently, expressions are JSON blobs. Need a visual editor.
- **Files to Modify**:
  - Create `numera-ui/src/components/spreading/ExpressionEditor.tsx` — visual formula builder component
  - Create `numera-ui/src/components/spreading/FormulaBar.tsx` — Excel-like formula bar above the grid
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — integrate expression editor
  - `numera-ui/src/services/spreadApi.ts` — add expression update endpoint hook
- **Acceptance Criteria**:
  - Click cell → see formula in formula bar (e.g., "= Zone2.Row3 + Zone2.Row4")
  - Click references in formula → PDF highlights source
  - Edit formula with point-and-click (click source values in PDF to add to expression)
  - Operators (+, -, ×, ÷) selectable
  - Expression saved and value recalculated on confirm

### WS-03-004: Variance Column (Period-over-Period Comparison)
- **Priority**: P1
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Add variance column showing side-by-side comparison with base period, highlighting major variances.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add endpoint to fetch comparison data between two spreads
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — add comparison logic
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — add variance column with conditional formatting
  - `numera-ui/src/types/spread.ts` — add VarianceData type
- **Acceptance Criteria**:
  - Variance column shows absolute and percentage change vs prior period
  - Variances > 10% highlighted in amber, > 25% in red
  - Toggle variance column on/off
  - Works with autofilled spreads

### WS-03-005: Category Navigation Bubbles
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: Quick-jump navigation between sections of the model grid (Assets → Liabilities → Equity, etc.).
- **Files to Modify**:
  - Create `numera-ui/src/components/spreading/CategoryNav.tsx` — horizontal bubble navigation
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — scroll-to-section support
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — integrate above grid
- **Acceptance Criteria**:
  - Horizontal bar of category bubbles above grid
  - Click bubble → grid scrolls to that section
  - Active section highlighted as user scrolls
  - Categories pulled from model template zones

### WS-03-006: Multi-Period Historical Comparison (Up to 20 Periods)
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Load up to 20 historical spread periods for side-by-side comparison in the grid.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add `GET /api/customers/{id}/spread-items/comparison` endpoint
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — multi-period data assembly
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — dynamic column generation for multiple periods
  - `numera-ui/src/services/spreadApi.ts` — add `useMultiPeriodComparison()` hook
- **Acceptance Criteria**:
  - Period selector allows choosing up to 20 historical periods
  - Grid adds columns dynamically per selected period
  - Horizontal scrolling for many periods
  - Variance highlighting between any two selected periods

### WS-03-007: Auto-Generated Source Comments per Cell
- **Priority**: P1
- **Layer**: Backend + Frontend
- **Dependencies**: WS-03-001
- **Description**: For every mapped cell, auto-record: source PDF name, page number, line-item name, extracted value, and a clickable URL that navigates to the source location.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/domain/SpreadValue.kt` — already has sourceText/sourcePage; add sourceDocumentName, sourceBbox fields
  - `backend/src/main/kotlin/com/numera/spreading/application/MappingOrchestrator.kt` — populate source reference fields during mapping
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — add cell tooltip/popover showing source reference
  - Create `numera-ui/src/components/spreading/SourceReference.tsx` — clickable source reference component
- **Acceptance Criteria**:
  - Hover/click cell → popover shows source document, page, line item, raw value
  - Click "View Source" → PDF viewer navigates to exact location
  - Source reference exported with spread data

### WS-03-008: CL Notes (Free-Text Notes per Spread Item)
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Free-text notes per spread line item, storable and exportable.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/domain/SpreadValue.kt` — add `notes` text field
  - `backend/src/main/kotlin/com/numera/spreading/dto/SpreadValueUpdateRequest.kt` — add `notes` field
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — add notes icon/column
  - Create `numera-ui/src/components/spreading/NoteEditor.tsx` — rich text note popover
- **Acceptance Criteria**:
  - Click notes icon → popover editor
  - Notes saved per cell and included in version snapshots
  - Notes visible on hover
  - Notes included in spread export

### WS-03-009: Show/Hide Unmapped Rows Toggle
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: Toggle to show or hide rows in the model grid that have no mapped values.
- **Files to Modify**:
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — add toggle button
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — filter rows based on mapped status
- **Acceptance Criteria**:
  - Toggle button: "Show All" / "Show Mapped Only"
  - Unmapped rows hidden when toggled
  - Count of hidden rows displayed

### WS-03-010: Spread Approval Workflow (Checker Actions)
- **Priority**: P0 (Critical)
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Currently spreads can be submitted but there's no checker approval UI. Managers need approve/reject capabilities.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add `POST /api/spread-items/{id}/approve`, `POST /api/spread-items/{id}/reject` endpoints
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — add approval/rejection logic with comments
  - `numera-ui/src/app/(dashboard)/customers/[customerId]/items/page.tsx` — add Approve/Reject buttons for SUBMITTED spreads (visible to Checker role)
  - Create `numera-ui/src/app/(dashboard)/approvals/page.tsx` — pending approvals dashboard
  - `numera-ui/src/services/spreadApi.ts` — add `useApproveSpread()`, `useRejectSpread()` hooks
  - `numera-ui/src/components/layout/Sidebar.tsx` — add "Pending Approvals" nav item
- **Acceptance Criteria**:
  - Checker sees list of SUBMITTED spreads awaiting approval
  - Can open spread in read-only mode to review
  - Approve with comments → status becomes APPROVED
  - Reject with mandatory comments → status returns to DRAFT
  - Email notification sent on approve/reject
  - Spread version recorded for approval action

---

## Workstream 4: Financial Model Engine Completion

### WS-04-001: Customer Model Copies
- **Priority**: P1
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: When creating a spread for a customer, copy the global template to create a customer-specific model copy. Customer modifications don't affect the global template.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/model/application/TemplateService.kt` — add `copyTemplateForCustomer()` method
  - `backend/src/main/kotlin/com/numera/model/domain/ModelTemplate.kt` — add `customerId`, `parentTemplateId`, `isGlobal` fields
  - `backend/src/main/resources/db/migration/` — new migration for template copy fields
  - `backend/src/main/kotlin/com/numera/spreading/application/MappingOrchestrator.kt` — use customer copy if exists, else global
- **Acceptance Criteria**:
  - First spread for customer auto-copies global template
  - Customer can add/remove/rename line items on their copy
  - Global template changes don't retroactively affect customer copies
  - Admin can "push" global template updates to customer copies (opt-in)

### WS-04-002: Model Versioning & Change Tracking
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-04-001
- **Description**: Track changes to model template structure over time with version history.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/model/domain/ModelTemplateVersion.kt` — version entity
  - `backend/src/main/kotlin/com/numera/model/application/TemplateService.kt` — version on every structural change
  - `backend/src/main/kotlin/com/numera/model/api/TemplateController.kt` — add version history endpoint
  - `backend/src/main/resources/db/migration/` — new migration for model versions table
- **Acceptance Criteria**:
  - Every template modification creates a new version
  - Version diff showing added/removed/changed line items
  - Rollback to previous template version

### WS-04-003: Row Grouping, Hiding, and Freezing
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Support grouping, hiding, and freezing rows in the model grid.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/model/domain/ModelLineItem.kt` — add `hidden`, `frozen`, `groupId` fields
  - `numera-ui/src/components/spreading/SpreadTable.tsx` — implement AG Grid row grouping, row pinning, row visibility toggle
- **Acceptance Criteria**:
  - Rows can be grouped with expand/collapse
  - Individual rows can be hidden/shown
  - Header rows can be frozen (pinned to top)
  - State persisted per customer model copy

### WS-04-004: Additional Model Templates (US GAAP, Regional)
- **Priority**: P2
- **Layer**: Data + Backend
- **Dependencies**: None
- **Description**: Create additional model templates beyond IFRS Corporate: US GAAP, UAE Central Bank, Banking-specific.
- **Files to Create**:
  - `data/model_templates/us_gaap_corporate.json` — US GAAP corporate model (~180 line items)
  - `data/model_templates/ifrs_banking.json` — IFRS banking model with banking-specific lines
  - `data/model_templates/uae_cbuae.json` — UAE Central Bank reporting format
  - `backend/src/main/resources/db/migration/` — seed migration for new templates
- **Acceptance Criteria**:
  - Each template has complete line items with synonyms
  - Templates selectable when creating a new spread
  - Formulas and validations configured per template
  - Taxonomy entries aligned with each template

### WS-04-005: Real-Time Validation Engine (Balance Checks)
- **Priority**: P1
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: The validation panel exists but needs real-time balance checks (Assets = Liabilities + Equity) computed on every value change.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/spreading/application/ValidationEngine.kt` — real-time validation using template validation rules
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add `GET /api/spread-items/{id}/validate` endpoint
  - `numera-ui/src/components/spreading/ValidationPanel.tsx` — live update on cell changes
  - `numera-ui/src/services/spreadApi.ts` — add `useValidateSpread()` hook
- **Acceptance Criteria**:
  - Balance check runs after every value edit
  - Shows Pass/Fail with difference amount
  - Configurable validation rules per template
  - Validation warnings don't block submission (override option exists)

---

## Workstream 5: Covenant Module Completion

### WS-05-001: Visual Formula Builder for Financial Covenants
- **Priority**: P1
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: The admin formulas page exists as a basic token editor. Need a proper visual formula builder that lets users construct formulas by clicking model line items.
- **Files to Modify**:
  - `numera-ui/src/app/(dashboard)/admin/formulas/page.tsx` — complete rewrite with visual builder
  - Create `numera-ui/src/components/covenant/FormulaBuilder.tsx` — drag/click formula builder component
  - `numera-ui/src/services/covenantApi.ts` — add formula CRUD hooks
  - `numera-ui/src/services/adminApi.ts` — add model line items fetch for formula building
- **Acceptance Criteria**:
  - Browse model line items in tree view
  - Click to add item to formula (inserts {ITEM_CODE})
  - Operators (+, -, ×, ÷) and parentheses via buttons
  - Live formula preview: "Debt / EBITDA"
  - Formula validation (syntax check) before save
  - Save formula and link to covenant definition

### WS-05-002: Monitoring Item Auto-Generation with Skip-Overlap
- **Priority**: P1
- **Layer**: Backend
- **Dependencies**: None
- **Description**: When a covenant frequency is defined, monitoring items should auto-generate for the fiscal year. If annual covenant exists, Q4 quarterly should be auto-skipped for same audit method.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/covenant/application/CovenantMonitoringService.kt` — enhance auto-generation with skip-overlap logic
  - `backend/src/main/kotlin/com/numera/covenant/domain/Covenant.kt` — add `auditMethod` field
- **Acceptance Criteria**:
  - Create covenant → monitoring items auto-generated for full year
  - If Annual + Quarterly exist for same audit method, Q4 is skipped
  - Items respect fiscal year end date
  - Manual generation endpoint still works as override

### WS-05-003: Predictive Breach Probability ML Model
- **Priority**: P1
- **Layer**: ML Service + Backend
- **Dependencies**: None
- **Description**: Replace the simple linear regression breach prediction with a proper ML model. The CovenantPredictionService currently uses basic trend analysis.
- **Files to Modify**:
  - Create `ml-service/app/ml/covenant_predictor.py` — time-series breach probability model (Prophet or custom LSTM)
  - Create `ml-service/app/api/covenant_prediction.py` — prediction endpoint
  - `ml-service/app/api/router.py` — mount covenant prediction routes
  - `backend/src/main/kotlin/com/numera/covenant/application/CovenantPredictionService.kt` — call ML service instead of local calculation
  - `backend/src/main/kotlin/com/numera/document/infrastructure/MlServiceClient.kt` — add prediction API call
- **Acceptance Criteria**:
  - ML model trained on historical covenant values
  - Predicts breach probability for next N periods
  - Considers: historical trend, seasonal patterns, related covenant correlations
  - Returns probability score (0-1) with confidence interval
  - Triggered automatically when new spread submitted

### WS-05-004: Covenant Early Warning Dashboard (Breach Risk Heatmap)
- **Priority**: P1
- **Layer**: Frontend
- **Dependencies**: WS-05-003
- **Description**: The covenant intelligence page exists with basic tables. Add a visual risk heatmap showing all covenants color-coded by breach probability, and trend charts.
- **Files to Modify**:
  - `numera-ui/src/app/(dashboard)/covenant-intelligence/page.tsx` — add heatmap and trend charts
  - Create `numera-ui/src/components/covenant/BreachHeatmap.tsx` — customer × covenant heatmap grid
  - Create `numera-ui/src/components/covenant/TrendChart.tsx` — per-covenant trend over time with threshold bands
  - Create `numera-ui/src/components/covenant/CovenantCalendar.tsx` — timeline view of upcoming due dates
  - `numera-ui/src/services/covenantApi.ts` — add trend data and calendar endpoints hooks
- **Acceptance Criteria**:
  - Heatmap: rows=customers, columns=covenants, cells colored by breach probability
  - Trend charts: line chart over 8+ periods with threshold band overlay
  - Calendar: timeline view of due dates with risk coloring
  - Drill-down from heatmap cell → specific covenant monitoring item

### WS-05-005: Automated Email Reminders (Scheduled)
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: None
- **Description**: Configure automated reminder emails X days before due date and Y days after (overdue).
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/covenant/application/CovenantReminderScheduler.kt` — @Scheduled job for due/overdue reminders
  - `backend/src/main/kotlin/com/numera/covenant/domain/Covenant.kt` — add `reminderDaysBefore`, `reminderDaysAfter` fields
  - `backend/src/main/kotlin/com/numera/covenant/application/EmailNotificationService.kt` — add reminder email methods
  - `backend/src/main/resources/db/migration/` — add reminder config columns
- **Acceptance Criteria**:
  - Configurable: remind X days before due, Y days after overdue
  - Emails sent to configured RMs and customer contacts
  - Uses configured email templates
  - Reminders logged in audit trail

### WS-05-006: Signature Management UI
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Managers should be able to create and manage standard signatures that auto-append to generated waiver letters.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/covenant/api/` — add SignatureController with CRUD endpoints
  - Create `numera-ui/src/app/(dashboard)/admin/signatures/page.tsx` — signature management page
  - `numera-ui/src/components/layout/Sidebar.tsx` — add nav item under Admin
- **Acceptance Criteria**:
  - Create/edit/delete signatures with rich text (name, title, HTML content)
  - Active/inactive toggle
  - Signature selectable when generating waiver letters
  - Preview signature in letter generation flow

### WS-05-007: Waiver Letter Generation & Delivery End-to-End
- **Priority**: P1
- **Layer**: Frontend
- **Dependencies**: WS-05-006
- **Description**: Complete the waiver workflow UI: select waiver type (instance/permanent), add comments, select template, select contacts, preview letter, send via email or download.
- **Files to Modify**:
  - Create `numera-ui/src/app/(dashboard)/covenants/[itemId]/waiver/page.tsx` — waiver processing page
  - Create `numera-ui/src/components/covenant/WaiverForm.tsx` — waiver type selection, template selection, contact selection
  - Create `numera-ui/src/components/covenant/LetterPreview.tsx` — generated letter preview with edit capability
  - `numera-ui/src/services/covenantApi.ts` — add waiver-specific API hooks
- **Acceptance Criteria**:
  - Select Waive or Not-Waive
  - Choose instance-only or permanent waiver
  - Select email template and signature
  - Select/add recipients
  - Preview auto-populated letter
  - Edit letter before sending
  - Send via email or download PDF
  - Item status → CLOSED after send

---

## Workstream 6: Configurable Workflow Engine

### WS-06-001: Camunda Zeebe Integration Setup
- **Priority**: P1
- **Layer**: Backend + Infra
- **Dependencies**: None
- **Description**: Integrate Camunda 8 (Zeebe) or Flowable as the BPMN workflow engine. This is a foundational requirement for configurable approval hierarchies.
- **Files to Modify**:
  - `backend/build.gradle.kts` — add Camunda/Flowable dependency
  - Create `backend/src/main/kotlin/com/numera/workflow/` package with:
    - `config/WorkflowEngineConfig.kt` — Camunda/Flowable engine configuration
    - `domain/WorkflowDefinition.kt` — workflow definition entity
    - `domain/WorkflowInstance.kt` — running workflow instance entity
    - `domain/WorkflowStep.kt` — individual step in a workflow
    - `application/WorkflowService.kt` — start/advance/complete workflow instances
    - `api/WorkflowController.kt` — API for workflow operations
    - `infrastructure/WorkflowRepository.kt`
  - `backend/docker-compose.yml` — add Camunda/Zeebe container
  - `backend/src/main/resources/db/migration/` — new migration for workflow tables
- **Acceptance Criteria**:
  - Workflow engine starts and connects to backend
  - Simple sequential approval workflow can be defined
  - Workflow instances can be started, advanced, and completed
  - Workflow state persisted and recoverable after restart

### WS-06-002: Spread Approval Workflow (BPMN)
- **Priority**: P1
- **Layer**: Backend
- **Dependencies**: WS-06-001
- **Description**: Replace the current simple submit/approve flow with a BPMN workflow for spread approval. Support configurable approval chains per tenant.
- **Files to Modify**:
  - Create `backend/src/main/resources/bpmn/spread_approval.bpmn` — BPMN definition
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — integrate workflow engine on submit
  - `backend/src/main/kotlin/com/numera/workflow/application/WorkflowService.kt` — spread-specific workflow logic
- **Acceptance Criteria**:
  - Submit spread → workflow instance started
  - Each step notifies the assigned approver
  - Configurable: 2-step (Analyst→Manager), 4-step (Analyst→Senior→Manager→Director)
  - Conditional routing: if spread total > threshold → extra approval step
  - SLA timers: auto-escalate if not actioned within N hours

### WS-06-003: Covenant Workflow (BPMN)
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-06-001
- **Description**: Define BPMN workflows for covenant document verification and waiver processing.
- **Files to Modify**:
  - Create `backend/src/main/resources/bpmn/covenant_verification.bpmn` — non-financial document workflow
  - Create `backend/src/main/resources/bpmn/waiver_processing.bpmn` — waiver approval workflow
  - `backend/src/main/kotlin/com/numera/covenant/application/CovenantMonitoringService.kt` — integrate workflow on document submission
  - `backend/src/main/kotlin/com/numera/covenant/application/WaiverService.kt` — integrate workflow on waiver request
- **Acceptance Criteria**:
  - Document submission → verification workflow started
  - Waiver request → approval workflow started
  - Multi-level approval supported
  - Workflow steps logged in audit trail

### WS-06-004: Visual Workflow Designer (Frontend)
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: WS-06-001
- **Description**: The admin workflows page is currently read-only. Build a BPMN-inspired visual workflow designer.
- **Files to Modify**:
  - `numera-ui/src/app/(dashboard)/admin/workflows/page.tsx` — rewrite with visual designer
  - Create `numera-ui/src/components/workflow/WorkflowDesigner.tsx` — drag-and-drop BPMN designer
  - Create `numera-ui/src/components/workflow/WorkflowNodePalette.tsx` — node types (approval, condition, parallel, timer)
  - `numera-ui/package.json` — add reactflow or bpmn-js dependency
  - `numera-ui/src/services/adminApi.ts` — add workflow CRUD hooks
- **Acceptance Criteria**:
  - Drag-and-drop nodes: Start, Approval, Condition, Parallel Gateway, Timer, End
  - Connect nodes with arrows
  - Configure each node (assignee role, condition expression, timer duration)
  - Save workflow definition
  - Activate/deactivate workflows
  - Preview workflow as read-only flowchart

### WS-06-005: Escalation Rules & SLA Tracking
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-06-002
- **Description**: Auto-escalate workflow steps if not actioned within configured time. Track SLA per step.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/workflow/application/WorkflowService.kt` — add escalation logic
  - Create `backend/src/main/kotlin/com/numera/workflow/application/EscalationScheduler.kt` — @Scheduled job checking overdue steps
  - `backend/src/main/kotlin/com/numera/workflow/domain/WorkflowStep.kt` — add `slaHours`, `escalateTo` fields
- **Acceptance Criteria**:
  - Each workflow step has configurable SLA (hours)
  - Overdue steps auto-escalated to next-level approver
  - Escalation notification sent
  - SLA metrics tracked and reportable

---

## Workstream 7: Reporting, Analytics & Dashboards

### WS-07-001: Live Spreading Dashboard
- **Priority**: P1
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: The current dashboard has basic stat cards. Need comprehensive spreading analytics: spreads by status/analyst/time, analyst productivity, AI accuracy trending.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/api/DashboardController.kt` — expand with detailed analytics endpoints
  - Create `backend/src/main/kotlin/com/numera/spreading/application/SpreadingAnalyticsService.kt` — aggregate analytics queries
  - `numera-ui/src/app/(dashboard)/dashboard/page.tsx` — expand with:
    - Spreads by status (bar chart)
    - Spreads by analyst (leaderboard)
    - Average time per spread (line chart trending)
    - AI accuracy rate trending (line chart)
    - Spreads per day (bar chart)
  - `numera-ui/src/services/dashboardApi.ts` — add analytics hooks
- **Acceptance Criteria**:
  - All charts from spec section 3.6.1 implemented
  - Real-time updates (10s refetch)
  - Date range filtering
  - Drill-down from chart → specific spreads

### WS-07-002: Live Covenant Dashboard
- **Priority**: P1
- **Layer**: Frontend + Backend
- **Dependencies**: WS-05-003, WS-05-004
- **Description**: Comprehensive covenant health dashboard with status distribution, risk heatmap, trend charts, and calendar.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/covenant/api/CovenantReportController.kt` — expand with dashboard endpoints (trend data, calendar, heatmap data)
  - Create `backend/src/main/kotlin/com/numera/covenant/application/CovenantAnalyticsService.kt` — aggregate covenant analytics
  - `numera-ui/src/app/(dashboard)/covenant-intelligence/page.tsx` — already started, expand significantly
- **Acceptance Criteria**:
  - Status distribution pie chart (Due/Overdue/Met/Breached/Closed)
  - Breach risk heatmap (WS-05-004)
  - Per-covenant trend charts with threshold bands
  - Calendar view of upcoming due dates
  - Filtering by customer, covenant type, date range

### WS-07-003: Portfolio-Level Analytics
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: WS-07-001
- **Description**: Cross-client financial ratio comparisons with sector/geography filtering and drill-down. Custom queries like "Show all clients whose current ratio dropped >15%".
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/analytics/` package:
    - `api/PortfolioAnalyticsController.kt`
    - `application/PortfolioAnalyticsService.kt`
    - `application/CustomQueryEngine.kt` — parameterized query builder
  - Create `numera-ui/src/app/(dashboard)/analytics/page.tsx` — portfolio analytics page
  - Create `numera-ui/src/components/analytics/PortfolioComparisonChart.tsx`
  - Create `numera-ui/src/components/analytics/CustomQueryBuilder.tsx`
  - `numera-ui/src/components/layout/Sidebar.tsx` — add Analytics nav item
- **Acceptance Criteria**:
  - Cross-client ratio comparison charts
  - Filter by sector, geography, portfolio group
  - Drill-down: portfolio → client → spread → cell
  - Custom parameterized queries (predefined templates)

### WS-07-004: MIS Report Generation & Export
- **Priority**: P1
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: The reports page shows raw JSON. Need proper report generation with formatted Excel/PDF/HTML export.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/reporting/` package:
    - `api/MISReportController.kt` — report generation endpoints
    - `application/SpreadingReportService.kt` — spreading reports (details, customer, user activity)
    - `application/CovenantReportService.kt` — covenant reports (pending, default, history, change history)
    - `application/ReportExportService.kt` — Excel (Apache POI), PDF (iText/OpenPDF), HTML renderers
  - `backend/build.gradle.kts` — add iText/OpenPDF dependency for PDF export
  - `numera-ui/src/app/(dashboard)/reports/page.tsx` — rewrite with:
    - Report type selector
    - Filter panel (status, date range, RM, customer, group)
    - Report preview table
    - Export buttons (Excel, PDF, HTML)
  - `numera-ui/src/services/reportApi.ts` — add report generation and download hooks
- **Acceptance Criteria**:
  - All MIS reports from spec section 3.6.4 implemented:
    - Spreading: details, customer, user activity, OCR accuracy
    - Covenant: pending, default/breach, history, change history, non-financial items
  - Filters: status, date range, RM, customer, group (default: last 3 months)
  - Export to Excel, PDF, HTML formats
  - Download triggers browser file save

### WS-07-005: Scheduled Report Delivery
- **Priority**: P3
- **Layer**: Backend + Frontend
- **Dependencies**: WS-07-004
- **Description**: Configure scheduled delivery of reports via email.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/reporting/application/ScheduledReportService.kt` — @Scheduled report generation and email delivery
  - Create `backend/src/main/kotlin/com/numera/reporting/domain/ReportSchedule.kt` — entity for schedule config
  - Create `numera-ui/src/app/(dashboard)/reports/schedules/page.tsx` — schedule management UI
- **Acceptance Criteria**:
  - Configure: report type, filters, frequency (daily/weekly/monthly), recipients
  - Scheduled job generates report and emails as attachment
  - Schedule management UI for creating/editing/deleting schedules

---

## Workstream 8: Administration Module Completion

### WS-08-001: Language Management
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Admin panel to enable/disable supported languages for OCR and taxonomy.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/admin/api/LanguageController.kt`
  - Create `backend/src/main/kotlin/com/numera/admin/application/LanguageService.kt`
  - Create `backend/src/main/kotlin/com/numera/admin/domain/SupportedLanguage.kt`
  - Create `numera-ui/src/app/(dashboard)/admin/languages/page.tsx`
  - `numera-ui/src/components/layout/Sidebar.tsx` — add nav item
- **Acceptance Criteria**:
  - List supported languages with enable/disable toggle
  - Language availability affects OCR language dropdown
  - Taxonomy entries filterable by language

### WS-08-002: AI Model Management Admin UI
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Admin panel showing current model accuracy, retraining status, training data volume. Ability to trigger manual retraining and A/B test model versions.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/admin/api/AIModelAdminController.kt` — endpoints for model status, retraining trigger
  - Create `backend/src/main/kotlin/com/numera/admin/application/AIModelManagementService.kt` — calls ML service for model health
  - `ml-service/app/api/` — add model management endpoints (status, retrain trigger, A/B test config)
  - Create `numera-ui/src/app/(dashboard)/admin/ai-models/page.tsx` — AI model dashboard
  - `numera-ui/src/components/layout/Sidebar.tsx` — add nav item under Admin
- **Acceptance Criteria**:
  - View: current model versions (zone classifier, semantic matcher), accuracy metrics, last retrain date
  - View: training data volume (total corrections, per-client counts)
  - Action: trigger manual retraining
  - Action: promote staging model to production (A/B test completion)
  - View: A/B test results comparison

### WS-08-003: System Configuration UI
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: Admin panel for system-wide configuration: tenant settings, default values, feature toggles.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/admin/SystemConfigController.kt` — expand from stub to full CRUD
  - Create `backend/src/main/kotlin/com/numera/admin/application/SystemConfigService.kt` — typed config management
  - Create `backend/src/main/kotlin/com/numera/admin/domain/SystemConfig.kt` — config entity
  - Create `numera-ui/src/app/(dashboard)/admin/settings/page.tsx` — settings page with sections
- **Acceptance Criteria**:
  - Sections: General (tenant name, timezone), Security (session timeout, MFA policy), AI (confidence thresholds), Email (SMTP config)
  - Save/load configuration
  - Changes take effect immediately (or on next login for auth settings)

### WS-08-004: Global Zone Management
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: Admin panel to manage the master list of zone names (Income Statement, Balance Sheet, Cash Flow, etc.) and add custom zones.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/admin/api/ZoneManagementController.kt`
  - Create `backend/src/main/kotlin/com/numera/admin/application/ZoneManagementService.kt`
  - Create `backend/src/main/kotlin/com/numera/admin/domain/GlobalZone.kt`
  - `numera-ui/src/app/(dashboard)/admin/taxonomy/page.tsx` — add Zones tab or create separate page
- **Acceptance Criteria**:
  - List all zones with descriptions
  - Add custom zones
  - Activate/deactivate zones
  - Zones available in model template and taxonomy configuration

### WS-08-005: Taxonomy Bulk Upload/Download Enhancement
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: The backend supports bulk upload/download of taxonomy via Excel. Frontend needs upload button and download button on taxonomy page.
- **Files to Modify**:
  - `numera-ui/src/app/(dashboard)/admin/taxonomy/page.tsx` — add upload/download buttons, upload modal
  - `numera-ui/src/services/adminApi.ts` — add `useBulkUploadTaxonomy()`, `useExportTaxonomy()` hooks
- **Acceptance Criteria**:
  - Download button → Excel file with current taxonomy
  - Upload button → select Excel file → import results shown
  - Error rows highlighted with reasons
  - Upload preview before confirmation

---

## Workstream 9: External System Integrations

### WS-09-001: Integration Adapter Architecture
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: None
- **Description**: Build the pluggable adapter framework for external system integrations. Standardize output format with adapter transform layer.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/integration/` package:
    - `domain/IntegrationAdapter.kt` — adapter interface
    - `domain/IntegrationConfig.kt` — per-tenant integration configuration entity
    - `domain/SyncDirection.kt` — PUSH, PULL, BIDIRECTIONAL
    - `application/IntegrationService.kt` — orchestrate sync operations
    - `api/IntegrationController.kt` — integration management endpoints
    - `infrastructure/IntegrationConfigRepository.kt`
  - `backend/src/main/resources/db/migration/` — new migration for integration config table
- **Acceptance Criteria**:
  - Adapter interface defined with `push()`, `pull()`, `sync()` methods
  - Per-tenant integration configuration stored in DB
  - Integration health check endpoint
  - Audit trail for all sync operations

### WS-09-002: CreditLens Adapter (2-Way Sync)
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-09-001
- **Description**: Build CreditLens integration adapter for 2-way spread sync.
- **Files to Create**:
  - `backend/src/main/kotlin/com/numera/integration/creditlens/CreditLensAdapter.kt`
  - `backend/src/main/kotlin/com/numera/integration/creditlens/CreditLensClient.kt` — HTTP client for CreditLens API
  - `backend/src/main/kotlin/com/numera/integration/creditlens/CreditLensMapper.kt` — data format transformation
- **Acceptance Criteria**:
  - Push spread values to CreditLens in vendor format
  - Pull model/metadata from CreditLens
  - Metadata sync: Statement Date, Audit Method, Frequency, Currency
  - Values rounded to entity unit before push
  - Retained Earnings fetch from CreditLens
  - Conflict detection on pull

### WS-09-003: Integration Configuration UI
- **Priority**: P3
- **Layer**: Frontend
- **Dependencies**: WS-09-001
- **Description**: Admin UI for configuring external system integrations per tenant.
- **Files to Modify**:
  - Create `numera-ui/src/app/(dashboard)/admin/integrations/page.tsx`
  - Create `numera-ui/src/components/admin/IntegrationConfigForm.tsx`
- **Acceptance Criteria**:
  - List configured integrations
  - Add/edit integration: type (CreditLens/nCino), URL, credentials, sync direction
  - Test connection button
  - Integration health status indicator

---

## Workstream 10: Event-Driven Architecture (Kafka/RabbitMQ)

### WS-10-001: Replace Spring Events with Message Broker
- **Priority**: P1
- **Layer**: Backend + Infra
- **Dependencies**: None
- **Description**: Currently using in-process Spring ApplicationEvents. For production reliability and microservice readiness, migrate to Kafka or RabbitMQ.
- **Files to Modify**:
  - `backend/build.gradle.kts` — add spring-kafka or spring-amqp dependency
  - Create `backend/src/main/kotlin/com/numera/shared/messaging/` package:
    - `config/KafkaConfig.kt` — Kafka producer/consumer config
    - `EventPublisher.kt` — abstraction over Kafka/Spring Events
    - `topics/Topics.kt` — topic name constants
  - `backend/src/main/kotlin/com/numera/spreading/events/SpreadSubmittedEvent.kt` — make Kafka-serializable
  - `backend/src/main/kotlin/com/numera/covenant/events/CovenantBreachedEvent.kt` — make Kafka-serializable
  - `backend/src/main/kotlin/com/numera/covenant/application/SpreadCovenantEventListener.kt` — convert to Kafka consumer
  - `backend/src/main/kotlin/com/numera/covenant/application/EmailNotificationService.kt` — convert to Kafka consumer
  - `backend/docker-compose.yml` — add Kafka + Zookeeper containers
- **Acceptance Criteria**:
  - All events published to Kafka topics
  - Consumers reliably process events with at-least-once delivery
  - Dead letter queue for failed processing
  - Events: spread.submitted, spread.approved, covenant.breached, covenant.status-changed, document.processed
  - Spring Events still work as fallback (feature toggle)

---

## Workstream 11: Infrastructure & DevOps

### WS-11-001: Kubernetes & Helm Charts
- **Priority**: P2
- **Layer**: Infra
- **Dependencies**: None
- **Description**: Create Kubernetes manifests and Helm charts for production deployment.
- **Files to Create**:
  - `k8s/` directory with:
    - `helm/numera/Chart.yaml`
    - `helm/numera/values.yaml` — configurable values
    - `helm/numera/templates/backend-deployment.yaml`
    - `helm/numera/templates/backend-service.yaml`
    - `helm/numera/templates/ml-service-deployment.yaml`
    - `helm/numera/templates/ml-service-service.yaml`
    - `helm/numera/templates/ocr-service-deployment.yaml`
    - `helm/numera/templates/ocr-service-service.yaml`
    - `helm/numera/templates/frontend-deployment.yaml`
    - `helm/numera/templates/frontend-service.yaml`
    - `helm/numera/templates/ingress.yaml`
    - `helm/numera/templates/postgres-statefulset.yaml`
    - `helm/numera/templates/redis-deployment.yaml`
    - `helm/numera/templates/minio-statefulset.yaml`
    - `helm/numera/templates/configmap.yaml`
    - `helm/numera/templates/secrets.yaml`
    - `helm/numera/templates/hpa.yaml` — horizontal pod autoscaler
- **Acceptance Criteria**:
  - `helm install numera ./k8s/helm/numera` deploys full stack
  - Configurable via values.yaml (replicas, resources, storage, env vars)
  - Health checks and readiness probes configured
  - HPA for backend and ML services based on CPU/memory
  - Persistent volumes for PostgreSQL and MinIO

### WS-11-002: Monitoring Stack (Prometheus + Grafana)
- **Priority**: P2
- **Layer**: Infra + Backend
- **Dependencies**: None
- **Description**: Set up Prometheus metrics collection and Grafana dashboards.
- **Files to Create**:
  - `monitoring/prometheus/prometheus.yml` — scrape config
  - `monitoring/grafana/dashboards/` — pre-built dashboards
    - `backend-metrics.json` — JVM, HTTP, DB metrics
    - `ml-service-metrics.json` — model inference latency, accuracy
    - `ocr-service-metrics.json` — OCR throughput, processing time
  - `monitoring/grafana/provisioning/` — auto-provision dashboards
  - `docker-compose.monitoring.yml` — Prometheus + Grafana containers
- **Files to Modify**:
  - `ml-service/app/` — add Prometheus metrics (processing time, model load time, accuracy)
  - `ocr-service/app/` — add Prometheus metrics (OCR time per page, table detection count)
  - `backend/src/main/resources/application.yml` — already has Micrometer, ensure Prometheus endpoint enabled
- **Acceptance Criteria**:
  - Prometheus scrapes all services
  - Grafana dashboards show: HTTP request rates, latencies, error rates, JVM metrics
  - ML-specific metrics: model inference time, confidence score distribution
  - OCR-specific metrics: pages processed/min, average processing time
  - Alert rules for error rate > 1%, latency P95 > 5s

### WS-11-003: CI/CD Pipeline
- **Priority**: P2
- **Layer**: Infra
- **Dependencies**: None
- **Description**: GitHub Actions CI/CD pipelines for automated testing, building, and deployment.
- **Files to Create**:
  - `.github/workflows/backend-ci.yml` — build, test, Docker push for backend
  - `.github/workflows/frontend-ci.yml` — lint, test, build, Docker push for frontend
  - `.github/workflows/ml-service-ci.yml` — lint, test, Docker push for ML service
  - `.github/workflows/ocr-service-ci.yml` — lint, test, Docker push for OCR service
  - `.github/workflows/deploy-staging.yml` — deploy to staging K8s cluster
  - `.github/workflows/deploy-production.yml` — deploy to production (manual trigger)
- **Acceptance Criteria**:
  - PRs trigger: lint + unit tests + build
  - Merge to main triggers: build + Docker push + staging deploy
  - Production deploy via manual workflow dispatch
  - Dependency scanning (Snyk/Dependabot)
  - SAST scanning

### WS-11-004: Multi-Region Data Sovereignty Configuration
- **Priority**: P3
- **Layer**: Backend + Infra
- **Dependencies**: WS-11-001
- **Description**: Per-tenant configuration of data residency region. Documents and DB can be pinned to specific regions.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/shared/domain/Tenant.kt` — add `dataRegion` field
  - Create `backend/src/main/kotlin/com/numera/shared/config/DataResidencyConfig.kt` — region-aware routing
  - `k8s/helm/numera/values.yaml` — multi-region configuration section
- **Acceptance Criteria**:
  - Tenant configuration includes data region setting
  - Document storage routed to region-specific MinIO/S3 bucket
  - Database queries routed to region-specific schema/cluster
  - API gateway routes to nearest regional cluster

---

## Workstream 12: ML Training Pipeline Completion

### WS-12-001: Execute Training Notebooks & Produce Baseline Models
- **Priority**: P0 (Critical)
- **Layer**: ML
- **Dependencies**: None
- **Description**: The 23 training notebooks exist but need to be executed to produce trained LayoutLM and SBERT models. This is prerequisite for the AI pipeline to actually work.
- **Steps**:
  1. Execute notebooks 00-03 (environment setup, data collection, XBRL labeling)
  2. Execute notebooks 04-06 (OCR batch processing, table extraction eval, zone annotation)
  3. Execute notebook 07 (LayoutLM zone classifier training — target 92% accuracy)
  4. Execute notebooks 08-09 (SBERT eval and fine-tuning — target 85% accuracy)
  5. Execute notebooks 10-12 (taxonomy builder, evaluation report, MLflow export)
  6. Execute VLM notebooks 00-05 (Qwen3-VL fine-tuning)
- **Acceptance Criteria**:
  - LayoutLM zone classifier: ≥92% accuracy on test set
  - SBERT IFRS matcher: ≥85% accuracy on test set
  - Models registered in MLflow with Production stage
  - Evaluation report with confusion matrices and per-class metrics
  - Models deployable via MLflow model serving

### WS-12-002: Feedback Retraining Pipeline Validation
- **Priority**: P1
- **Layer**: ML
- **Dependencies**: WS-12-001
- **Description**: Validate the feedback retraining pipeline end-to-end: corrections collected → exported → notebook retrained → model promoted.
- **Steps**:
  1. Simulate analyst corrections via feedback API
  2. Export corrections via `/ml/feedback/export`
  3. Run notebook 20 (feedback retraining)
  4. Verify improved model uploaded to MLflow staging
  5. Validate A/B testing routes traffic to staging model
- **Acceptance Criteria**:
  - End-to-end pipeline works without manual intervention
  - Retrained model shows measurable improvement
  - A/B testing correctly splits traffic
  - Rollback possible if staging model underperforms

---

## Workstream 13: LLM Copilot & Natural Language Querying (Phase 4)

### WS-13-001: LLM Integration Architecture
- **Priority**: P3
- **Layer**: ML Service + Backend
- **Dependencies**: None
- **Description**: Set up LLM integration (OpenAI API or local model) for copilot and NL querying.
- **Files to Create**:
  - `ml-service/app/ml/llm_client.py` — LLM abstraction (OpenAI, Anthropic, local)
  - `ml-service/app/api/copilot.py` — copilot chat endpoint
  - `ml-service/app/api/nl_query.py` — natural language query endpoint
- **Acceptance Criteria**:
  - LLM client supports multiple backends (OpenAI, Anthropic, local)
  - Rate limiting and token budget controls
  - Conversation context management
  - Configurable per tenant (which LLM, token limits)

### WS-13-002: Analyst Copilot Chat
- **Priority**: P3
- **Layer**: Frontend + ML Service
- **Dependencies**: WS-13-001
- **Description**: LLM-powered conversational copilot for analysts: explain mappings, suggest corrections, answer financial questions.
- **Files to Create**:
  - `numera-ui/src/components/copilot/CopilotPanel.tsx` — chat panel component
  - `numera-ui/src/components/copilot/CopilotMessage.tsx` — message rendering
  - `numera-ui/src/services/copilotApi.ts` — copilot API hooks
  - `numera-ui/src/stores/copilotStore.ts` — conversation state
- **Acceptance Criteria**:
  - Sliding chat panel accessible from spreading workspace
  - Ask questions about current spread ("Why was this mapped here?")
  - Get suggestions ("This looks like depreciation, should map to IS020")
  - Context-aware (knows current document, spread, customer)

### WS-13-003: Natural Language Dashboard Querying
- **Priority**: P3
- **Layer**: Frontend + ML Service + Backend
- **Dependencies**: WS-13-001, WS-07-003
- **Description**: LLM-powered query interface for ad-hoc analysis.
- **Files to Create**:
  - Create `numera-ui/src/app/(dashboard)/query/page.tsx` — NL query page
  - Create `numera-ui/src/components/analytics/NLQueryInput.tsx` — query input with examples
  - Create `numera-ui/src/components/analytics/QueryResultsRenderer.tsx` — render tables, charts, narrative
  - `ml-service/app/api/nl_query.py` — NL → SQL/query translation
- **Acceptance Criteria**:
  - Type natural language query → get results
  - Examples: "Which covenants are at risk?", "Average DSCR across portfolio?"
  - Results as tables, charts, or narrative text
  - Query history and saved queries

---

## Workstream 14: Customer Management Enhancements

### WS-14-001: Customer Sync from External Systems
- **Priority**: P2
- **Layer**: Backend
- **Dependencies**: WS-09-001
- **Description**: Sync customer data from CreditLens or core banking systems.
- **Files to Modify**:
  - Create `backend/src/main/kotlin/com/numera/customer/application/CustomerSyncService.kt`
  - `backend/src/main/kotlin/com/numera/customer/api/CustomerController.kt` — add `POST /api/customers/sync` endpoint
  - `backend/src/main/kotlin/com/numera/customer/domain/Customer.kt` — add `externalId`, `dataSource` fields
- **Acceptance Criteria**:
  - Sync customers from configured external system
  - Create new or update existing based on external ID
  - Sync metadata: name, industry, country, RM
  - Manual trigger + scheduled sync options
  - Conflict resolution: external system wins (configurable)

### WS-14-002: Extended Customer Metadata
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: Add financial year end, custom metadata fields, and group assignment to customer records.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/customer/domain/Customer.kt` — add `financialYearEnd`, `customMetadata` (JSONB), `group` fields
  - `backend/src/main/resources/db/migration/` — migration for new columns
  - `numera-ui/src/app/(dashboard)/customers/page.tsx` — show additional fields in table and create form
- **Acceptance Criteria**:
  - Financial year end date stored per customer
  - Custom key-value metadata fields (JSONB)
  - Group assignment for visibility control
  - All fields searchable/filterable

---

## Workstream 15: Spread Lifecycle Completion

### WS-15-001: Push to External System (PUSHED Status)
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: WS-09-001, WS-09-002
- **Description**: After approval, spread can be pushed to external system (CreditLens, etc.). Add PUSHED status and push workflow.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/domain/SpreadStatus.kt` — ensure PUSHED status exists
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add `POST /api/spread-items/{id}/push` endpoint
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — push logic using integration adapter
  - `numera-ui/src/app/(dashboard)/customers/[customerId]/items/page.tsx` — add Push button for APPROVED spreads
- **Acceptance Criteria**:
  - Push button visible only for APPROVED spreads
  - Push transforms values to target system format
  - Status transitions: APPROVED → PUSHED
  - Push failure → error displayed, status remains APPROVED
  - Push logged in audit trail

### WS-15-002: Override vs Duplicate Spread Re-Submission
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: When re-submitting a spread, allow choice: Override (update same statement ID) or Duplicate (create restated version: Restated 1, 2, 3...).
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/domain/SpreadItem.kt` — add `restatementNumber`, `originalSpreadId` fields
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — add override vs duplicate logic
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add parameter to submit endpoint
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — add override/duplicate dialog on resubmission
- **Acceptance Criteria**:
  - Override: same spread ID, version incremented
  - Duplicate: new spread item created with restatement tracking
  - UI prompts user to choose on resubmission
  - Both options maintain full version history

### WS-15-003: Submit & Continue (Multi-Period Documents)
- **Priority**: P2
- **Layer**: Frontend + Backend
- **Dependencies**: None
- **Description**: After submitting one period from a multi-period document, immediately start spreading the next period.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt` — add `POST /api/spread-items/{id}/submit-and-continue` endpoint
  - `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt` — create next period spread from same document
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — add "Submit & Continue" button alongside regular Submit
- **Acceptance Criteria**:
  - "Submit & Continue" submits current period and creates next period spread
  - Next period auto-selects next detected period from same document
  - Navigates to new spread workspace automatically
  - Period sequence tracking maintained

---

## Workstream 16: Real-Time Features

### WS-16-001: WebSocket Notifications Enhancement
- **Priority**: P2
- **Layer**: Backend + Frontend
- **Dependencies**: None
- **Description**: WebSocket exists but needs comprehensive notification for all key events: spread submitted, covenant breached, document ready, lock acquired/released.
- **Files to Modify**:
  - `backend/src/main/kotlin/com/numera/shared/notification/WebSocketNotificationService.kt` — add notification types
  - Create `numera-ui/src/hooks/useWebSocket.ts` — WebSocket connection hook
  - Create `numera-ui/src/components/notifications/NotificationCenter.tsx` — notification dropdown in header
  - `numera-ui/src/components/layout/Header.tsx` — integrate notification center
  - `numera-ui/src/stores/` — create notificationStore.ts
- **Acceptance Criteria**:
  - Real-time notifications for: spread status changes, covenant breaches, document processing complete, lock events
  - Notification bell with unread count
  - Notification dropdown with recent items
  - Click notification → navigate to relevant page
  - Sound notification option (configurable)

### WS-16-002: Exclusive Lock Status Banner
- **Priority**: P2
- **Layer**: Frontend
- **Dependencies**: None
- **Description**: When another user has a spread locked, show a clear read-only banner with the lock holder's name.
- **Files to Modify**:
  - `numera-ui/src/app/spreading/[spreadId]/page.tsx` — add prominent banner when locked by another user
  - Create `numera-ui/src/components/spreading/LockBanner.tsx` — read-only banner component
- **Acceptance Criteria**:
  - Banner: "Currently being edited by [Name]. View mode only."
  - All edit controls disabled when locked by another user
  - Auto-refresh lock status every 30 seconds
  - Banner disappears when lock released

---

## Implementation Order & Dependencies

### Phase 1 Priorities (Complete Foundation — Weeks 1-8)

| Week | Tasks | Rationale |
|------|-------|-----------|
| 1-2 | WS-12-001 (Train ML models) | Nothing works without trained models |
| 1-2 | WS-03-001 (PDF.js viewer), WS-03-010 (Approval workflow) | Core spreading UX + workflow |
| 3-4 | WS-03-003 (Expression editor), WS-03-004 (Variance column), WS-03-007 (Source comments) | Spreading workspace completion |
| 3-4 | WS-04-005 (Validation engine), WS-04-001 (Customer model copies) | Model engine completion |
| 5-6 | WS-01-005 (Group visibility), WS-01-001 (Password policies), WS-01-002 (Session mgmt) | Security hardening |
| 5-6 | WS-05-001 (Formula builder), WS-05-002 (Skip-overlap), WS-05-007 (Waiver UI) | Covenant completion |
| 7-8 | WS-07-001 (Spreading dashboard), WS-07-004 (MIS reports) | Reporting MVP |
| 7-8 | WS-10-001 (Kafka), WS-02-001 (Multi-file upload), WS-02-004 (File store views) | Infrastructure + ingestion |

### Phase 2 Priorities (Workflow & Intelligence — Weeks 9-14)

| Week | Tasks | Rationale |
|------|-------|-----------|
| 9-10 | WS-06-001 (Camunda setup), WS-06-002 (Spread BPMN workflow) | Workflow engine |
| 9-10 | WS-05-003 (Predictive breach ML), WS-05-004 (Risk heatmap) | Covenant intelligence |
| 11-12 | WS-06-004 (Workflow designer), WS-06-003 (Covenant workflow) | Workflow completion |
| 11-12 | WS-07-002 (Covenant dashboard), WS-07-003 (Portfolio analytics) | Analytics |
| 13-14 | WS-08-002 (AI model admin), WS-08-003 (System config) | Admin completion |
| 13-14 | WS-12-002 (Feedback retraining validation) | ML pipeline validation |

### Phase 3 Priorities (Enterprise & Scale — Weeks 15-20)

| Week | Tasks | Rationale |
|------|-------|-----------|
| 15-16 | WS-11-001 (K8s/Helm), WS-11-002 (Monitoring) | Production infrastructure |
| 15-16 | WS-09-001 (Integration framework), WS-09-002 (CreditLens) | External integrations |
| 17-18 | WS-11-003 (CI/CD), WS-15-001 (Push to external) | DevOps + spread lifecycle |
| 17-18 | WS-16-001 (WebSocket notifications), WS-16-002 (Lock banner) | Real-time UX |
| 19-20 | WS-13-001 (LLM architecture), WS-13-002 (Copilot) | AI copilot |
| 19-20 | WS-11-004 (Multi-region), WS-13-003 (NL querying) | Enterprise features |

### Remaining P2/P3 tasks can be parallelized during any phase based on capacity.

---

## Agent Execution Guidelines

### For Each Task, the AI Agent Should:

1. **Read** all referenced files first to understand current state
2. **Create** new files or modify existing ones per the task spec
3. **Run** relevant tests to validate changes (backend: `./gradlew test`, frontend: `npm test`, ML: `pytest`)
4. **Check** for compilation errors and fix any introduced issues
5. **Verify** no regressions by running full test suite
6. **Commit** atomically — one task per commit with descriptive message

### Coding Conventions (Extracted from Codebase):

**Backend (Kotlin/Spring Boot)**:
- Package structure: `com.numera.{module}.{layer}` where layer = api/application/domain/infrastructure/dto/events
- Controllers use `@RestController` with `@RequestMapping`
- Services use `@Service` with `@Transactional` where needed
- Entities extend `TenantAwareEntity` for multi-tenancy
- DTOs are Kotlin data classes
- Repositories extend `JpaRepository`
- Events use `ApplicationEventPublisher`
- Async processing via `@Async`
- Error handling via `ApiException` with `ErrorCode`
- Tests use MockK and JUnit 5

**Frontend (React/Next.js/TypeScript)**:
- Pages in `src/app/(dashboard)/` for dashboard routes
- Components in `src/components/{feature}/`
- State management via Zustand stores in `src/stores/`
- API layer via React Query hooks in `src/services/`
- Types in `src/types/`
- Forms use React Hook Form + Zod
- UI components: Tailwind CSS + Radix UI primitives
- Charts: Recharts
- Grid: AG Grid

**ML Service (Python/FastAPI)**:
- Routes in `app/api/`
- ML models in `app/ml/`
- Services in `app/services/`
- Data models in `app/models.py`
- Config in `app/config.py`
- Tests in `tests/`

**Database Migrations**:
- Flyway format: `V{NNN}__{description}.sql`
- Next available: V014

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Total Workstreams | 16 |
| Total Tasks | 75 |
| P0 (Blocking) Tasks | 3 |
| P1 (Core) Tasks | 28 |
| P2 (Important) Tasks | 35 |
| P3 (Nice-to-Have) Tasks | 9 |
| Backend Tasks | 42 |
| Frontend Tasks | 38 |
| ML Tasks | 8 |
| Infrastructure Tasks | 12 |
| Estimated Execution Weeks | 20 |
