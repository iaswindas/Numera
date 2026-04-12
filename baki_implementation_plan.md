# Numera Platform — Enterprise Gap Analysis & Phased Implementation Plan

> **Audit Date**: April 10, 2026  
> **Scope**: Full enterprise parity against [application_specification.md](file:///f:/Context/application_specification.md)  
> **Codebases Audited**: Backend (Kotlin/Spring Boot), Frontend (Next.js), ML Service (FastAPI), OCR Service (FastAPI)

---

## Executive Summary

The platform has a **solid scaffolding** across all four services but is far from enterprise-ready. Key findings:

| Layer | Files | Lines (est.) | Completion vs Spec |
|---|---|---|---|
| **Backend** (Kotlin) | 74 source files | ~15,000 | **~40%** |
| **Frontend** (Next.js) | 55 source files | ~8,500 | **~30%** |
| **ML Service** (Python) | 28 source files | ~4,500 | **~45%** |
| **OCR Service** (Python) | 26 source files | ~4,200 | **~40%** |
| **Infrastructure** | CI + Docker | ~600 | **~35%** |
| **Tests** | 21 test files | ~3,000 | **~15%** |

**Overall Platform Completion: ~35%**

---

## Module-by-Module Status Assessment

### 1. Authentication & Access Control (§3.1) — 45% Complete

#### ✅ What's Implemented
- JWT-based authentication with access + refresh tokens ([AuthService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/auth/application/AuthService.kt))
- SSO skeleton with SAML/OIDC config entities ([SsoService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/auth/application/SsoService.kt))
- MFA TOTP service with backup codes ([MfaService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/auth/application/MfaService.kt))
- Role/Permission domain model ([Role.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/auth/domain/Role.kt))
- Login page UI ([login/page.tsx](file:///f:/Context/numera-ui/src/app/(auth)/login/page.tsx))
- Security filter chain with JWT validation ([SecurityConfig.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/shared/config/SecurityConfig.kt))
- Flyway migrations for auth tables (V002, V012)

#### ❌ What's Missing
- **SSO actual integration** — `SsoService` has method stubs but no real SAML/OIDC token exchange
- **Password policy enforcement** — No complexity, expiry, or history checks
- **Session management** — No concurrent session limits, configurable timeout, or forced logout
- **User lifecycle** — No self-registration, admin approval, bulk CSV provisioning
- **Group-based data visibility** — `UserGroup` entities exist but no query-level filtering (users see ALL data)
- **MFA enrollment UI** — Backend ready, no frontend pages for QR code display/verification
- **@PreAuthorize granularity** — Only `hasRole('ADMIN')` used; no module-level or action-level checks
- **Account states machine** — No Pending → Active → Inactive → Rejected workflow

---

### 2. Financial Spreading Module (§3.2) — 45% Complete

#### ✅ What's Implemented
- Full spread CRUD lifecycle: create, process, update values, submit ([SpreadService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt))
- MappingOrchestrator calling ML service for semantic matching + expression building ([MappingOrchestrator.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/application/MappingOrchestrator.kt))
- Confidence scoring (HIGH/MEDIUM/LOW) with thresholds
- Bulk accept by confidence level
- Formula engine with arithmetic, SUM, IF, ABS, comparisons ([FormulaEngine.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/model/application/FormulaEngine.kt))
- Spread version control — snapshots, history, diff, rollback ([SpreadVersionService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/application/SpreadVersionService.kt))
- Exclusive locking via Redis ([SpreadLockService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/application/SpreadLockService.kt))
- Autofill from prior period ([AutofillService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/application/AutofillService.kt))
- Expression pattern learning per customer
- Dual-pane spreading workspace UI with PDF viewer
- Validation panel (balance checks)

#### ❌ What's Missing
- **PDF.js zone overlay visualization** — `PdfViewer.tsx` is placeholder; no coordinate-aware rendering, no zone bounding boxes overlaid on PDF
- **SpreadTable UX** — Uses basic HTML table, not AG-Grid/Jspreadsheet with Excel-like editing spec calls for
- **Category bubble navigation** — No quick-jump between Assets/Liabilities/Equity
- **Variance column** — No side-by-side comparison with base period highlighting major variances
- **Currency & Unit display** — No per-cell currency/unit indication
- **Show/Hide unmapped rows** toggle
- **Expression editor UI** — No visual formula builder in frontend
- **Submit & Continue** — No workflow to immediately start next period from same document
- **Override vs Duplicate** submission — No restated statement handling
- **Auto-generated comments** — No source PDF page/line-item clickable URL per cell
- **CL Notes** — No free-text notes per spread item
- **Split view** — No dual-document pane for mapping from different pages
- **Approve workflow** — Submit works, but no Manager approval/rejection flow
- **Document download via Links** button

---

### 3. Document Ingestion Pipeline (§3.2.1) — 40% Complete

#### ✅ What's Implemented
- Upload with MinIO storage ([DocumentProcessingService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt))
- Async processing pipeline: Upload → OCR → Table Detection → Zone Classification → Ready
- ML client calls to OCR and ML services ([MlServiceClient.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/document/infrastructure/MlServiceClient.kt))
- Zone update/override API
- Document listing, status tracking, error handling
- File store UI page ([documents/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/documents/page.tsx))
- Qwen3-VL processor integrated ([vlm_processor.py](file:///f:/Context/ocr-service/app/ml/vlm_processor.py))
- PaddleOCR as legacy fallback ([paddle_ocr.py](file:///f:/Context/ocr-service/app/ml/paddle_ocr.py))

#### ❌ What's Missing
- **Multi-file upload** — No merging multiple documents per financial period
- **Password-protected document** handling  
- **File Store bulk pre-processing** — No background queue for off-hours processing
- **File states** (Uploaded → Processing → Ready → Mapped → Error) only partially tracked
- **Three views** (My Files, All Files, Error Files) — No filter implementation
- **Language detection** — Hardcoded language parameter, no auto-detection
- **Clean File post-processing** — No despeckle, watermark removal, deskew
- **Page merge/split** operations
- **Document converter** — `document_converter.py` exists but limited (no Word/Excel → PDF conversion)
- **Handwritten annotation support** (future phase, but needs skeleton)

---

### 4. Financial Model Engine (§3.2.2) — 35% Complete

#### ✅ What's Implemented
- Model template CRUD with hierarchical line items ([TemplateService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/model/application/TemplateService.kt))
- Formula/Input/Group item types
- Validation rules with severity levels
- Template → Customer copy mechanism

#### ❌ What's Missing
- **Global model templates** — No pre-configured IFRS, US GAAP, regional templates seeded
- **Model structure UI** — No admin page for managing templates with parent-child hierarchy
- **Row grouping, hiding, freezing** in the grid
- **Model versioning** — No change tracking on template structure
- **Category navigation bubbles** for quick jump between sections

---

### 5. External System Integrations (§3.2.3) — 0% Complete

> [!CAUTION]
> No integration adapters exist. This is an entire subsystem that needs to be built.

- **No CreditLens adapter** (2-way sync)
- **No nCino / Finastra / S&P adapters**
- **No adapter architecture** — No pluggable adapter interface defined
- **No metadata sync, value sync, or retained earnings fetch**

---

### 6. Taxonomy & Learning System (§3.3) — 30% Complete

#### ✅ What's Implemented
- Taxonomy CRUD with keyword/synonym entries ([TaxonomyService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/application/TaxonomyService.kt))
- Exclusion rules by category ([ExclusionService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/application/ExclusionService.kt))
- Taxonomy admin UI page (basic)
- ML feedback store collecting corrections ([feedback_store.py](file:///f:/Context/ml-service/app/services/feedback_store.py))
- Training notebooks exist (21 notebooks in `ml-training/`)

#### ❌ What's Missing
- **Bulk upload/download via Excel** — Backend has Apache POI dependency but no endpoint
- **Multi-language taxonomy** support
- **Per-taxonomy-group** organization
- **AI Model Training Interface** — No admin-facing page for Online/Offline training
- **Trained Data Dashboard** — No accuracy metrics, retraining history UI
- **Continuous learning pipeline** — Corrections are captured but no automated retraining trigger
- **Per-client model specialization** — `client_model_resolver.py` exists but only stub logic

---

### 7. Covenants Module (§3.4) — 50% Complete

#### ✅ What's Implemented
- Covenant customer management with contacts ([CovenantService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/CovenantService.kt))
- Financial + Non-Financial covenant definitions
- Monitoring item auto-generation with skip-overlap logic ([CovenantMonitoringService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/CovenantMonitoringService.kt))
- Real-time covenant recalculation on spread submission ([SpreadCovenantEventListener.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/SpreadCovenantEventListener.kt))
- Prediction service with linear regression ([CovenantPredictionService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/CovenantPredictionService.kt))
- Waiver/Not-Waiver letter generation with template substitution ([WaiverService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/WaiverService.kt))
- Email template CRUD ([EmailTemplateService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/EmailTemplateService.kt))
- Email notification service ([EmailNotificationService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/covenant/application/EmailNotificationService.kt))
- WebSocket real-time notifications ([CovenantWebSocketListener.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/shared/notification/CovenantWebSocketListener.kt))
- Covenant management UI pages

#### ❌ What's Missing
- **Formula Builder UI** — No visual drag-and-drop formula builder using model line items
- **Document verification workflow** — Maker upload + Checker approval for non-financial covenants not implemented
- **Breach Risk Heatmap** — No visual heatmap on covenant dashboard
- **Trend charts per covenant** over time
- **Upcoming covenant calendar** — No timeline view with risk coloring
- **Automated due/overdue email scheduling** — `EmailNotificationService` exists but no scheduler trigger
- **Signature management** UI and full CRUD
- **Waiver letter Preview/Edit/Print** flow in UI
- **Multiple waiver views** (All/Pending/Violated tabs) — Partially implemented
- **Manual override justification** enforcement

---

### 8. Configurable Workflow Engine (§3.5) — 10% Complete

#### ✅ What's Implemented
- In-memory workflow config stub ([WorkflowConfigController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/WorkflowConfigController.kt)) — hardcoded, non-persistent

#### ❌ What's Missing
> [!WARNING]
> The workflow engine is essentially non-existent. The current controller uses an in-memory `mutableListOf` and has no domain model, no persistence, no execution engine.

- **No Camunda/Flowable integration** — No BPMN 2.0 engine
- **No visual workflow designer** UI
- **No configurable approval chains** — System is hardcoded Maker → auto-submit
- **No conditional routing** (value-based approval levels)
- **No parallel approvals**
- **No escalation rules** or SLA timers
- **No workflow audit trail** with per-step tracking

---

### 9. Reporting, Analytics & Dashboards (§3.6) — 20% Complete

#### ✅ What's Implemented
- Dashboard page with stat cards, area chart, pie chart ([dashboard/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/dashboard/page.tsx))
- Dashboard API endpoint ([DashboardController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/api/DashboardController.kt))
- Reports page shell ([reports/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/reports/page.tsx))
- Audit report controller ([AuditReportController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/shared/audit/AuditReportController.kt))

#### ❌ What's Missing
- **Reporting module is empty** — `com.numera.reporting` package has zero files
- **Spreading Dashboard** — No analyst productivity, spreads per day, AI accuracy trending
- **Covenant Dashboard** — No breach risk heatmap, trend charts, covenant calendar
- **Portfolio-level analytics** — No cross-client ratio comparisons, custom queries
- **MIS report generation** — No Covenant Pending/Default/Breach/History reports
- **Export to Excel/PDF/HTML** — No export functionality at all
- **Scheduled report delivery** via email
- **Drill-down navigation** from portfolio → client → spread → cell
- **Natural language querying** (Phase 2 feature but needs skeleton)

---

### 10. Customer Management (§3.7) — 50% Complete

#### ✅ What's Implemented
- Customer CRUD with search ([CustomerService.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/customer/application/CustomerService.kt))
- Customer search page ([customers/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/customers/page.tsx))
- Customer items drilldown page

#### ❌ What's Missing
- **Customer sync from external systems** (CreditLens, core banking)
- **Group-based visibility enforcement** — Query-level data isolation per user group
- **Custom metadata fields** per customer
- **Financial Year End management** UI

---

### 11. Administration Module (§3.8) — 25% Complete

#### ✅ What's Implemented
- User management basic UI ([users/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/admin/users/page.tsx))
- User management controller ([UserManagementController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/UserManagementController.kt))
- Taxonomy admin ([TaxonomyController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/TaxonomyController.kt))
- User groups CRUD ([UserGroupController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/api/UserGroupController.kt))
- Exclusion rules ([ExclusionController.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/admin/api/ExclusionController.kt))

#### ❌ What's Missing
- **User approve/reject/activate/deactivate** workflow
- **Bulk CSV user provisioning** 
- **Role assignment UI** with permission matrix
- **Global Zone management** — No UI or dedicated API
- **Language management** — No UI
- **AI Model Management** — No accuracy dashboard, no retrain trigger UI, no A/B test management
- **System configuration** page is minimal

---

### 12. Infrastructure & DevOps — 35% Complete

#### ✅ What's Implemented
- Docker Compose for ML stack (OCR + ML + MLflow + MinIO + PostgreSQL)
- Full Docker Compose including backend ([docker-compose.full.yml](file:///f:/Context/docker-compose.full.yml))
- CI/CD pipeline with build/test/Docker for all 4 services ([ci.yml](file:///f:/Context/.github/workflows/ci.yml))
- Flyway migrations (V001–V013)
- OpenTelemetry + Prometheus metrics configured
- Dockerfiles for all services

#### ❌ What's Missing
- **Redis/Valkey not in main docker-compose** (only in CI)
- **Kafka/RabbitMQ** — No event bus configured; events are Spring internal only
- **Kubernetes manifests** — No Helm charts
- **Multi-region deployment** — No per-tenant data residency
- **Air-gapped deployment** support
- **Secrets management** — Passwords hardcoded in docker-compose
- **Rate limiting** — No API gateway (Kong/Spring Cloud Gateway)
- **SAST/DAST** — No Snyk/Dependabot in CI pipeline
- **Load testing** setup
- **Monitoring dashboards** — Prometheus configured but no Grafana dashboards

---

### 13. Testing — 15% Complete

#### ✅ What's Implemented
- 11 backend unit/integration test files
- 6 ML service test files  
- 6 OCR service test files
- WireMock stubs for ML/OCR integration tests
- Module boundary test ([ModuleBoundaryTest.kt](file:///f:/Context/backend/src/test/kotlin/com/numera/ModuleBoundaryTest.kt))

#### ❌ What's Missing
- **~80% of services have no test coverage**
- **No frontend tests** — zero test files in `numera-ui`
- **No E2E/Playwright tests**
- **No API contract tests** (OpenAPI validation)
- **No performance benchmarks** — `PerformanceBenchmarkTest.kt` exists but is skeleton
- **No test for auth flows** beyond basic login
- **No test for covenant monitoring workflow**
- **No test for document processing pipeline end-to-end**

---

## 10-Phase Implementation Plan

Each phase is designed to be independently implementable by an AI agent. Phases are ordered by dependency graph — each phase builds on the previous.

---

### Phase 1: Data Foundation & Auth Hardening
**Effort**: ~3-4 days | **Dependencies**: None

> [!IMPORTANT]
> This phase makes the platform actually secure and enforces proper data isolation — prerequisite for everything else.

#### 1.1 Database Migrations
- [ ] `V014__password_policy.sql` — Add password policy columns to `tenants` table (complexity regex, expiry days, history count)
- [ ] `V015__session_management.sql` — Add `user_sessions` table (session_id, user_id, device_info, ip_address, created_at, expires_at, is_active)
- [ ] `V016__user_lifecycle.sql` — Add `account_status` enum column to `users` (PENDING, ACTIVE, INACTIVE, REJECTED), approval request table
- [ ] `V017__workflow_engine.sql` — Add `workflow_definitions`, `workflow_steps`, `workflow_instances`, `workflow_step_instances` tables
- [ ] `V018__reporting_tables.sql` — Add `report_definitions`, `scheduled_reports`, `report_executions` tables

#### 1.2 Auth Hardening
- [ ] Implement `PasswordPolicyService` — validate complexity, expiry, history
- [ ] Implement `SessionService` — concurrent session limits, configurable timeout, forced logout
- [ ] Add `AccountLifecycleService` — self-registration, admin approval, bulk CSV import
- [ ] Wire up real SAML 2.0 token exchange in `SsoService` (using Spring Security SAML)
- [ ] Wire up real OIDC token exchange in `SsoService` (using existing `spring-boot-starter-oauth2-client`)
- [ ] Add `@PreAuthorize` annotations at method-level across ALL controllers (not just ADMIN)
- [ ] Implement data-level filtering — intercept all JPA queries to add `WHERE tenant_id = :currentTenant AND group_access_check`

#### 1.3 Frontend Auth
- [ ] MFA enrollment page — QR code display, backup code download
- [ ] User registration page with approval status
- [ ] Session timeout warning modal
- [ ] Role/permission-aware navigation (hide menu items user can't access)

**Acceptance Criteria**: All endpoints require valid JWT + role check. Users only see data from their tenant/group. MFA works end-to-end. Failed login after 5 attempts locks account.

---

### Phase 2: Document Pipeline Completion
**Effort**: ~3-4 days | **Dependencies**: Phase 1

#### 2.1 Backend — Document Service Enhancements
- [ ] Add multi-file merge endpoint — accept multiple files, combine into single processing unit
- [ ] Implement password-protected PDF handling (Apache PDFBox `StandardProtectionPolicy`)
- [ ] Add language auto-detection (Apache Tika `LanguageIdentifier`)
- [ ] Implement File Store bulk pre-processing with Spring `@Scheduled` background job
- [ ] Add file state machine enum: UPLOADED → PROCESSING → OCR_COMPLETE → TABLES_DETECTED → ZONES_CLASSIFIED → READY → MAPPED → ERROR
- [ ] Add "My Files" / "All Files" / "Error Files" filtered views
- [ ] Implement document merge/split API (page-level operations using PDFBox)
- [ ] Add document clean-file post-processing (deskew via OCR service dispatch)

#### 2.2 OCR Service Enhancements
- [ ] Add `/clean` endpoint — despeckle (high/low), watermark removal, deskew using PIL/OpenCV
- [ ] Add language detection endpoint
- [ ] Implement Word (.docx) → PDF conversion using python-docx + reportlab or LibreOffice headless
- [ ] Implement Excel (.xlsx) → structured data extraction using openpyxl
- [ ] Add page merge/split operations (`/pages/merge`, `/pages/split`)

#### 2.3 Frontend — File Store
- [ ] Redesign documents page with My Files / All Files / Error Files tabs
- [ ] Add multi-file upload drag-and-drop with progress
- [ ] Add document re-process action button
- [ ] Add document preview modal (PDF inline viewer)
- [ ] Add bulk select + bulk process action

**Acceptance Criteria**: Upload a 50-page multi-language PDF → system auto-detects language, processes OCR, detects tables, classifies zones, shows status per file. Error files are retryable.

---

### Phase 3: Spreading Workspace Production-Grade
**Effort**: ~5-6 days | **Dependencies**: Phase 2

#### 3.1 PDF Viewer with Zone Overlays
- [ ] Integrate PDF.js into `PdfViewer.tsx` with coordinate-aware rendering
- [ ] Add zone bounding box overlays — color-coded by zone type (Income=blue, Balance=green, Cash=purple)
- [ ] Add clickable overlays on detected numbers — clicking a value in the PDF highlights it in the grid
- [ ] Add zoom, pan, rotate controls
- [ ] Add split-view mode (two document panes side by side)
- [ ] Add page navigation with zone jump pills

#### 3.2 AG-Grid Spread Table
- [ ] Replace HTML table with AG-Grid Enterprise-like grid using `ag-grid-community`
- [ ] Implement cell editing with expression editor modal
- [ ] Add confidence-coded cell backgrounds (green ≥90%, amber 70-89%, red <70%)
- [ ] Add "Show/Hide Unmapped Rows" toggle
- [ ] Add variance column (difference from base period, highlight >10% changes)
- [ ] Add currency & unit indicator columns
- [ ] Add category bubble navigation bar (Assets → Liabilities → Equity quick jump)
- [ ] Add right-click context menu: Accept / Reject & Remap / Edit Expression / View Source
- [ ] Implement auto-generated comments per cell (source PDF, page, line-item, clickable link)
- [ ] Add CL Notes inline sidebar
- [ ] Add row grouping/hiding/freezing

#### 3.3 Expression Editor UI
- [ ] Create visual formula builder dialog
- [ ] Show source values with drag-drop to expression area
- [ ] Operator palette (+, -, ×, ÷, parentheses)
- [ ] Adjustment factor selectors (unit scale, absolute/negative/contra)
- [ ] Expression preview with computed result

#### 3.4 Spread Workflow
- [ ] Implement approval flow: Submit → Manager Review → Approve/Reject → Pushed
- [ ] Add Manager approval page with side-by-side diff view
- [ ] Add "Submit & Continue" — after submitting, auto-create next period spread from same document
- [ ] Override vs Duplicate submission (Restated 1…5)
- [ ] Add load historical periods (up to 20) for comparison in grid

**Acceptance Criteria**: Analyst opens PDF → sees zone overlays → edits values in AG-Grid → uses formula builder → submits → Manager sees it in approval queue → approves. All with confidence highlighting and auto-comments.

---

### Phase 4: Financial Model Engine & Templates
**Effort**: ~3 days | **Dependencies**: Phase 3

#### 4.1 Backend
- [ ] Create seed data migration `V019__global_templates.sql` with IFRS, US GAAP, UAE CB model templates (50+ line items each)
- [ ] Add model versioning — track template structure changes over time with diff
- [ ] Add model copy-to-customer API with override protection
- [ ] Add model validation rules for common checks (A=L+E, Revenue-Expenses=NetIncome, etc.)

#### 4.2 Frontend — Template Management
- [ ] Create `/admin/templates` page with tree-view editor for hierarchical line items
- [ ] Add drag-drop reorder for line items
- [ ] Add formula assignment dialog for computed cells
- [ ] Add template import/export via Excel
- [ ] Add template version history viewer

**Acceptance Criteria**: Admin creates IFRS template with 80 line items, assigns formulas, copies to customer, versioning tracks all changes.

---

### Phase 5: Covenant Module Completion
**Effort**: ~4-5 days | **Dependencies**: Phase 4

#### 5.1 Visual Formula Builder
- [ ] Create `/admin/formulas` — full formula management page with visual builder
- [ ] Link formulas to model line items with drag-drop
- [ ] Add formula audit trail (creation, modification history)
- [ ] Support active/inactive toggle with soft delete

#### 5.2 Non-Financial Document Verification
- [ ] Implement Maker upload flow for non-financial covenant docs
- [ ] Implement Checker verification screen — preview, download, approve, reject
- [ ] Add rejection comments + re-submission flow
- [ ] Auto-approve "Financial Statement" type when corresponding spread is submitted

#### 5.3 Covenant Dashboard
- [ ] Build breach risk heatmap (customers × covenants matrix with color-coded breach probability)
- [ ] Add trend charts per covenant per customer over time (Recharts line charts)
- [ ] Build upcoming covenant calendar (timeline/Gantt view with risk coloring)
- [ ] Add covenant drill-down: portfolio → customer → specific covenant → monitoring items

#### 5.4 Waiver Flow UI
- [ ] Build waiver dialog with letter type selection, template picker, recipient selector
- [ ] Add letter preview/edit (rich text) with Print button
- [ ] Implement "Send" action (email dispatch) and "Download" action (PDF generation)
- [ ] Add waiver history per monitoring item

#### 5.5 Automated Notifications
- [ ] Implement `CovenantReminderScheduler` — Spring `@Scheduled` job
- [ ] Configure: X days before due date (reminder), Y days after (overdue) per tenant
- [ ] Email template rendering with dynamic fields

**Acceptance Criteria**: Create covenant → monitoring items auto-generated → spread submitted → values auto-calculated → breach detected → Manager waives → letter generated → item closed. Risk heatmap shows live probabilities.

---

### Phase 6: Workflow Engine
**Effort**: ~5-6 days | **Dependencies**: Phase 5

> [!IMPORTANT]
> This replaces the in-memory stub with a real BPMN-style engine.

#### 6.1 Domain Model
- [ ] Create `com.numera.workflow` package with domain entities: `WorkflowDefinition`, `WorkflowStep`, `WorkflowInstance`, `WorkflowStepInstance`
- [ ] Step types: APPROVE, REVIEW, CONDITIONAL, PARALLEL, ESCALATION
- [ ] Condition expressions (e.g., `spread.totalAssets > 10000000 → require VP approval`)
- [ ] SLA timer per step with escalation rules

#### 6.2 Workflow Execution Engine
- [ ] Implement `WorkflowExecutionService` — create instance from definition, advance through steps
- [ ] Conditional router — evaluate expressions against spread/covenant data
- [ ] Parallel step support — wait for all branches before proceeding
- [ ] Escalation scheduler — auto-escalate if step not actioned within N hours
- [ ] Notification dispatch on step transitions

#### 6.3 Workflow Designer UI  
- [ ] Create `/admin/workflows` designer page with visual step editor
- [ ] Drag-drop step creation (Approve, Review, Conditional, Parallel)
- [ ] Step property panel (assignee role, SLA hours, escalation rules)
- [ ] Workflow preview/simulation mode
- [ ] Separate workflow definitions for: Spread Approval, Covenant Doc Verification, Waiver Processing, User Approval

#### 6.4 Wire Workflow Engine into Domain Events
- [ ] Spread submission → create workflow instance from matching definition
- [ ] Covenant breach → trigger waiver workflow
- [ ] Document upload → trigger verification workflow
- [ ] User registration → trigger approval workflow

**Acceptance Criteria**: Admin designs 4-step approval chain → Analyst submits spread → System creates workflow instance → Steps advance through roles → Escalation fires if SLA exceeded.

---

### Phase 7: Reporting & Analytics
**Effort**: ~4-5 days | **Dependencies**: Phase 6

#### 7.1 Reporting Module Backend
- [ ] Create `com.numera.reporting` package: `ReportService`, `ReportController`, `ReportDefinition`, `ScheduledReport`
- [ ] Implement report generators:
  - Spread Details Report
  - Customer Activity Report  
  - User Productivity Report
  - AI Accuracy Report (OCR accuracy, mapping accuracy trending)
  - Covenant Pending Report
  - Covenant Default/Breach Report
  - Covenant History Report
  - Non-Financial Covenant Item Report
- [ ] Add export engine: Excel (Apache POI), PDF (OpenPDF/iText), HTML
- [ ] Add scheduled report delivery via `@Scheduled` + email dispatch

#### 7.2 Analytics Dashboard
- [ ] Spreading Dashboard page:
  - Analyst productivity (spreads/day, avg time)
  - AI accuracy trending over time
  - Retrain impact analysis (before/after accuracy)
- [ ] Covenant Dashboard page (enhance existing):
  - Breach risk heatmap (AG-Grid + color coding)
  - Trend charts per covenant (Recharts)
  - Covenant calendar (timeline view)
- [ ] Portfolio Analytics page:
  - Cross-client financial ratio comparisons
  - Sector/geography/portfolio group filtering
  - Drill-down: portfolio → client → spread → cell

#### 7.3 Reports UI
- [ ] Create `/reports` page with:
  - Report catalog (available report definitions)
  - Filter panel (status, date range, RM, customer, group)
  - Generate button → download Excel/PDF/HTML
  - Schedule configuration (frequency, recipients)

**Acceptance Criteria**: Manager generates "Covenant Breach Report" filtered by Q1 2026, exports to Excel, sets up weekly email delivery.

---

### Phase 8: External System Integrations
**Effort**: ~4-5 days | **Dependencies**: Phase 7

#### 8.1 Adapter Architecture
- [ ] Create `com.numera.integration` package
- [ ] Define `IntegrationAdapter` interface: `pushSpread()`, `pullMetadata()`, `syncValues()`, `pullRetainedEarnings()`
- [ ] Create `IntegrationConfiguration` entity (per-tenant adapter settings, credentials, endpoints)
- [ ] Add adapter registry with dynamic loading

#### 8.2 CreditLens Adapter
- [ ] Implement CreditLens 2-way sync adapter
  - Metadata sync: Statement Date, Audit Method, Frequency, Currency, Consolidation
  - Value push: Spread values rounded to entity unit
  - Value pull: External changes pulled back
  - Retained Earnings fetch
- [ ] Add CreditLens API client (REST)

#### 8.3 Generic REST Adapter
- [ ] Implement configurable REST adapter for nCino / Finastra / custom systems
- [ ] Configurable field mapping (source field → target field)
- [ ] Auth support (API key, OAuth2, basic)
- [ ] Retry/circuit-breaker resilience (Spring Retry + Resilience4j)

#### 8.4 Customer Sync
- [ ] Add customer sync from CreditLens — pull customer list, map to Numera customers
- [ ] Add sync scheduler (daily/hourly configurable)
- [ ] Add manual sync trigger in admin UI

**Acceptance Criteria**: Spread is submitted → system auto-pushes to CreditLens → values appear in CL. Change value in CL → pull sync updates Numera.

---

### Phase 9: Enterprise Infrastructure
**Effort**: ~4-5 days | **Dependencies**: Phase 8

#### 9.1 Event Bus
- [ ] Add Kafka or RabbitMQ to docker-compose
- [ ] Create `EventPublisher` abstraction (currently Spring internal events)
- [ ] Migrate key events to async messaging:
  - `SpreadSubmittedEvent` → covenant recalculation
  - `CovenantBreachedEvent` → notifications + workflow trigger
  - `DocumentProcessedEvent` → downstream processing
- [ ] Add dead letter queue handling

#### 9.2 API Gateway
- [ ] Add Spring Cloud Gateway or Nginx as reverse proxy
- [ ] Configure rate limiting (per user, per tenant)
- [ ] Configure CORS properly (currently `withDefaults()`)
- [ ] Add request/response logging middleware

#### 9.3 Kubernetes & Helm
- [ ] Create Helm chart with values.yaml for all 5 services
- [ ] Configure horizontal pod autoscaling for OCR service (GPU-aware)
- [ ] Create Kubernetes secrets for all passwords
- [ ] Create configurable data residency per tenant
- [ ] Create air-gapped deployment variant (all images pre-pulled)

#### 9.4 Observability
- [ ] Create Grafana dashboards for:
  - API latency + error rate per service
  - ML model accuracy metrics
  - Document processing pipeline throughput
  - Covenant breach prediction accuracy
- [ ] Configure AlertManager rules (error rate > 5%, latency > 2s, etc.)
- [ ] Add distributed tracing (already `micrometer-tracing-bridge-otel` imported)

#### 9.5 Security Hardening
- [ ] Enable CSRF protection with token-based approach (not disabled)
- [ ] Configure CORS with explicit origin whitelist
- [ ] Add Snyk/Dependabot to CI pipeline
- [ ] Add SAST scan (SonarQube/Semgrep) to CI pipeline
- [ ] Configure TLS 1.3 termination
- [ ] Ensure all Docker images run as non-root user

**Acceptance Criteria**: `helm install numera ./charts/numera` deploys all services to K8s cluster. Grafana shows live metrics. Rate limiting blocks abusive clients.

---

### Phase 10: Testing, Documentation & Polish
**Effort**: ~5-6 days | **Dependencies**: Phase 9

#### 10.1 Backend Tests
- [ ] Add unit tests for ALL service classes (target: >80% coverage)
- [ ] Add integration tests for full document pipeline
- [ ] Add integration tests for covenant lifecycle
- [ ] Add integration tests for workflow engine
- [ ] Add API contract tests (validate OpenAPI spec against controllers)
- [ ] Add performance benchmark tests (document processing throughput)

#### 10.2 Frontend Tests
- [ ] Set up Playwright for E2E testing
- [ ] Test: Login → Dashboard → Upload Document → Spreading Workspace → Submit → Approval
- [ ] Test: Covenant creation → Monitoring → Breach → Waiver flow
- [ ] Test: Admin pages (users, taxonomy, workflows, templates)
- [ ] Add component unit tests with Vitest + React Testing Library

#### 10.3 ML Service Tests
- [ ] Add integration tests for full OCR → table detection → zone classification pipeline
- [ ] Add tests for semantic matcher with real SBERT model (small model)
- [ ] Add tests for expression engine
- [ ] Add tests for A/B testing routing

#### 10.4 Documentation
- [ ] Generate OpenAPI spec with complete descriptions
- [ ] Create API documentation site (Swagger UI is configured but descriptions are sparse)
- [ ] Create deployment guide (Docker, Kubernetes, air-gapped)
- [ ] Create user guide / onboarding documentation
- [ ] Create client onboarding toolkit

#### 10.5 UI Polish
- [ ] Responsive design audit — ensure mobile/tablet compatibility
- [ ] Accessibility (ARIA) audit across all pages
- [ ] Loading states, error states, empty states across all pages
- [ ] Keyboard navigation support
- [ ] Animation refinements (Framer Motion polish)
- [ ] Dark/light mode toggle (currently dark mode only)

**Acceptance Criteria**: CI passes with >80% backend coverage, E2E tests cover 5 critical user flows, API docs are complete, deployment guide works from scratch.

---

## Priority Matrix

| Priority | Phase | Business Value | Technical Risk |
|---|---|---|---|
| 🔴 Critical | Phase 1 (Auth) | Security compliance | Low |
| 🔴 Critical | Phase 3 (Spreading) | Core product value | Medium |
| 🟡 High | Phase 2 (Documents) | Pipeline completeness | Medium |
| 🟡 High | Phase 5 (Covenants) | Revenue feature | Medium |
| 🟡 High | Phase 6 (Workflow) | Enterprise requirement | High |
| 🟢 Medium | Phase 4 (Models) | Data foundation | Low |
| 🟢 Medium | Phase 7 (Reports) | Manager value | Low |
| 🟢 Medium | Phase 10 (Testing) | Quality assurance | Low |
| 🔵 Lower | Phase 8 (Integrations) | Client-specific | High |
| 🔵 Lower | Phase 9 (Infrastructure) | Scale & deploy | Medium |

---

## Open Questions

> [!IMPORTANT]
> **Workflow Engine Decision**: Should we embed Camunda Zeebe/Flowable as a real BPMN engine, or build a lightweight custom workflow executor? Camunda adds significant complexity but gives BPMN 2.0 compliance. A custom engine is simpler but won't support visual BPMN import/export.

> [!IMPORTANT]
> **Event Bus**: Should we introduce Kafka/RabbitMQ now, or keep using Spring ApplicationEvent and defer to Phase 9? Spring events work but don't survive process restarts and can't be consumed by other services.

> [!WARNING]
> **Frontend Architecture**: The current UI mixes Next.js App Router with Vite config files and react-router-dom. This should be cleaned up — pick one routing strategy (Next.js App Router recommended).

> [!NOTE]
> **AG-Grid Licensing**: The spec mentions Jspreadsheet CE or Luckysheet but `ag-grid-community` is already in package.json. AG-Grid Community Edition is free but lacks some enterprise features (row grouping, Excel export). Should we stick with AG-Grid Community or switch?
