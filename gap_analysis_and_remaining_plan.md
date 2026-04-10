# Numera — Gap Analysis & Remaining Implementation Plan

> **Date**: April 10, 2026  
> **Scope**: Full audit of codebase vs Application Specification §1–§6  
> **Status**: Phase 1 core is ~65% complete. Phases 2–5 range from 10–40% to 0%.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Module-by-Module Gap Analysis](#2-module-by-module-gap-analysis)
3. [Remaining Work — Prioritized Backlog](#3-remaining-work--prioritized-backlog)
4. [Sprint Plan](#4-sprint-plan)
5. [Risk Register](#5-risk-register)

---

## 1. Executive Summary

### What's Built

| Layer | Status | Notes |
|-------|--------|-------|
| **Backend (Kotlin/Spring Boot)** | ~70% | Auth, RBAC, Customers, Documents, Spreading, Covenants, Audit — all API-complete |
| **Frontend (Next.js/React)** | ~55% | Login, Dashboard, Customers, Documents, Spread Editor, Covenants, Admin pages built |
| **ML Service (Python/FastAPI)** | ~75% | Zone classification, semantic matching, expression builder, feedback, A/B testing, pipeline orchestration |
| **OCR Service (Python/FastAPI)** | ~80% | Qwen3-VL + PaddleOCR, smart PDF routing, table detection, native/scanned handling |
| **ML Training (Colab notebooks)** | ~10% | Only 00_environment_setup complete; all training notebooks are stubs |
| **Infrastructure** | ~40% | Docker Compose works locally; no K8s/Helm, no CI/CD pipeline, no monitoring stack |

### What's Missing (Critical Path)

1. **Spread Review Workspace** — The spec's flagship dual-pane (PDF viewer + model grid) is not built. Current UI is a flat table editor, not the production vision.
2. **PDF Viewer with zone overlays** — No PDF.js integration. Analysts cannot see the source document alongside the spread.
3. **ML Model Training** — No trained models exist. All 20 training notebooks are stubs. The system runs on pre-trained HuggingFace fallbacks.
4. **Workflow Engine** — No Camunda/Flowable integration. Approval is a simple status toggle.
5. **Exclusive Locking** — Store exists in frontend (`spreadStore.isLocked`) but no Redis-backed locking on backend.
6. **External System Integrations** — No CreditLens, nCino, or Finastra adapters.
7. **Kubernetes / Helm** — No deployment manifests for production.
8. **Real-time Events** — No Kafka/RabbitMQ. Backend uses Spring events (in-process only).

---

## 2. Module-by-Module Gap Analysis

### 2.1 Authentication & Access Control (Spec §3.1)

| Requirement | Spec Reference | Status | Detail |
|---|---|---|---|
| SSO (SAML 2.0 / OIDC) | §3.1.1 | ❌ Not started | Login page has "SSO" button placeholder but no backend integration |
| Form-based login (email/password) | §3.1.1 | ✅ Done | JWT access + refresh token rotation, BCrypt passwords |
| MFA (TOTP) | §3.1.1 | ❌ Not started | No 2FA implementation |
| Configurable RBAC | §3.1.2 | ⚠️ Partial | Roles + Permissions exist (entities, DB migration), but permission enforcement on endpoints is basic (`@PreAuthorize` not granular) |
| Group-based customer visibility | §3.1.2 | ❌ Not started | No user-group → customer-group mapping |
| Session management (timeout, concurrent limits) | §3.1.2 | ❌ Not started | JWT is stateless; no server-side session tracking |
| User lifecycle (self-registration, approval) | §3.1.3 | ❌ Not started | Admin creates users directly; no self-registration flow |
| Bulk user provisioning (CSV/API) | §3.1.3 | ❌ Not started | |

**Gap Score: 30% complete**

---

### 2.2 Financial Spreading Module (Spec §3.2)

#### 2.2.1 Document Ingestion Pipeline

| Requirement | Status | Detail |
|---|---|---|
| PDF upload | ✅ Done | Multi-format (PDF, images); stored in MinIO |
| Word (.docx) support | ❌ Missing | Only PDF/images handled |
| Excel (.xlsx) support | ❌ Missing | |
| Password-protected PDF handling | ❌ Missing | |
| Multi-file merge per period | ❌ Missing | One document per spread currently |
| File Store (bulk pre-processing) | ⚠️ Partial | Document list page exists with status tracking, but no off-hours batch queue |
| Language detection | ❌ Missing | User selects manually (EN/AR) |
| Clean File (despeckle, deskew, watermark) | ❌ Missing | |
| OCR with PaddleOCR | ✅ Done | Qwen3-VL primary + PaddleOCR fallback |
| Layout analysis (columns, merged cells) | ✅ Done | PP-Structure + VLM |
| Visual cue correction (red highlights) | ❌ Missing | No OCR-correction-in-place UI |
| Zone auto-detection | ✅ Done | Keyword + LayoutLM + VLM hybrid |
| Year/Period detection | ✅ Done | PeriodParser extracts dates from headers |
| Zone manual override (draw/split/merge) | ❌ Missing | No zone drawing UI |
| Autonomous value mapping | ✅ Done | SBERT semantic matching + expression engine |
| Expression auto-building | ✅ Done | DIRECT, SUM, NEGATE, SCALE types |
| Unit scale conversion | ✅ Done | Detects thousands/millions from headers |
| Confidence scoring (green/amber/red) | ✅ Done | HIGH ≥85%, MEDIUM ≥65%, LOW <65% |

**Gap Score: 55% complete**

#### 2.2.2 Financial Model Engine

| Requirement | Status | Detail |
|---|---|---|
| Global IFRS model template | ✅ Done | `ifrs_corporate.json` with 195 line items, synonyms, formulas |
| Model hierarchy (parent-child) | ✅ Done | `indentLevel`, `total` flag |
| Formula cells + validation | ✅ Done | FormulaEngine with expression parser |
| Customer model copies | ⚠️ Partial | SpreadItem links to template; no per-customer template fork |
| Model versioning | ⚠️ Partial | `version` field on ModelTemplate, but no diff/history UI |
| Category navigation bubbles | ❌ Missing | |
| Row grouping/hiding/freezing | ❌ Missing | |

**Gap Score: 60% complete**

#### 2.2.3 External System Integrations

| Requirement | Status | Detail |
|---|---|---|
| Integration adapter architecture | ❌ Not started | No adapter pattern |
| CreditLens 2-way sync | ❌ Not started | |
| nCino adapter | ❌ Not started | |
| Metadata sync (statement date, currency) | ❌ Not started | |
| Retained earnings fetch | ❌ Not started | |

**Gap Score: 0% complete** (Phase 5 item per spec)

#### 2.2.4 Spreading Workspace UI

| Requirement | Status | Detail |
|---|---|---|
| **Dual-pane view (PDF + Grid)** | ❌ Missing | **CRITICAL GAP** — current UI is a single-pane table editor |
| PDF.js viewer with zoom/pan/rotate | ❌ Missing | No PDF rendering component |
| Zone overlay visualization | ❌ Missing | |
| Click-to-navigate source highlights | ❌ Missing | `spreadStore` has `highlightedSourcePage/Coords` but no viewer to target |
| Spreadsheet-style grid | ⚠️ Partial | AG Grid table exists but not spreadsheet-grade (no inline formulas, no cell drag) |
| Expression editor / formula builder | ❌ Missing | Values are edited as plain numbers |
| Variance column (period-over-period) | ❌ Missing | |
| Load up to 20 historical periods | ❌ Missing | |
| **Exclusive locking** | ❌ Missing | Frontend store has `isLocked` field; no Redis-backed lock on backend |
| Validation panel (balance checks) | ❌ Missing | Backend has validations but no frontend panel |
| Auto-generated comments (source tracking) | ⚠️ Partial | SpreadValue stores `sourcePage`, `sourceText` but not rendered in UI |
| CL Notes (free-text sync) | ❌ Missing | |

**Gap Score: 20% complete** — This is the primary user-facing gap.

#### 2.2.5 Subsequent Spreading

| Requirement | Status | Detail |
|---|---|---|
| Auto-select base period | ❌ Missing | No base-period logic |
| AI autofill from prior period | ⚠️ Partial | ExpressionPattern entity + AutofillService exist in backend; not wired to UI |
| Duplicate cell disambiguation | ❌ Missing | |
| Submit & Continue (multi-period) | ❌ Missing | |

**Gap Score: 20% complete**

#### 2.2.6 Spread Lifecycle & Version Control

| Requirement | Status | Detail |
|---|---|---|
| States: Draft → Submitted → Approved → Pushed | ✅ Done | SpreadStatus enum + transitions |
| Version control (immutable snapshots) | ✅ Done | SpreadVersion with JSON snapshots |
| Diff view between versions | ✅ Done | Backend `diff(v1, v2)` endpoint |
| Rollback | ✅ Done | Backend + frontend button |
| Override vs Duplicate (restated) | ❌ Missing | |

**Gap Score: 75% complete**

---

### 2.3 Taxonomy & Learning System (Spec §3.3)

| Requirement | Status | Detail |
|---|---|---|
| Global taxonomy (keyword/synonym) | ✅ Done | IFRS taxonomy JSON + DB-backed, admin UI (read-only) |
| Bulk upload/download taxonomy (Excel) | ❌ Missing | Admin page is read-only |
| Multi-language taxonomy | ❌ Missing | English only |
| Exclusion list (clean text pipeline) | ❌ Missing | No configurable exclusion categories |
| Continuous learning pipeline | ⚠️ Partial | Feedback collected via `/api/ml/feedback` + export endpoint; no automated retrain trigger |
| Per-client model specialization | ⚠️ Partial | ClientModelResolver framework exists; no models trained |
| AI training interface (admin) | ❌ Missing | No online/offline training UI |
| Trained data dashboard | ❌ Missing | |

**Gap Score: 30% complete**

---

### 2.4 Covenants Module (Spec §3.4)

| Requirement | Status | Detail |
|---|---|---|
| Covenant customer CRUD | ✅ Done | API + (partially mocked) UI |
| Financial covenant definitions | ✅ Done | Formula, threshold, frequency |
| Non-financial covenant definitions | ✅ Done | Document types, item types |
| Formula builder (visual) | ❌ Missing | Backend FormulaEngine exists; no visual builder UI |
| Monitoring item auto-generation | ✅ Done | `generateMonitoringItems()` by frequency |
| Status engine (DUE → OVERDUE → MET → BREACHED → CLOSED) | ✅ Done | Full enum + transitions |
| Real-time recalculation on spread submit | ❌ Missing | No event-driven trigger from spread → covenant |
| Predictive breach probability | ⚠️ Partial | `breachProbability` field on entity; CovenantPredictionService exists but uses simple heuristic, not ML |
| Non-financial document verification workflow | ✅ Done | Upload, checker approve/reject |
| Waiver / Not-Waiver workflow | ⚠️ Partial | WaiverController exists; letter generation not implemented |
| Letter template editor (rich text, dynamic fields) | ⚠️ Partial | Email templates CRUD exists; no rich text editor, no drag-and-drop tags |
| Signature management | ⚠️ Partial | Entity exists; no UI for creating/managing signatures |
| Automated email notifications | ❌ Missing | No email sending integration (no SMTP/SendGrid) |
| Skip-overlap logic (Q4 vs Annual) | ❌ Missing | |
| Financial Statement auto-approval (non-financial) | ❌ Missing | |

**Gap Score: 50% complete**

---

### 2.5 Configurable Workflow Engine (Spec §3.5)

| Requirement | Status | Detail |
|---|---|---|
| BPMN workflow designer | ❌ Missing | Admin page lists "workflows" read-only; no Camunda/Flowable integration |
| Arbitrary approval chains | ❌ Missing | Hard-coded Maker → submit → approve |
| Conditional routing | ❌ Missing | |
| Parallel approvals | ❌ Missing | |
| Escalation rules (SLA timers) | ❌ Missing | |
| Separate flows for spread/covenant/waiver/user | ❌ Missing | |
| Workflow audit trail | ⚠️ Partial | AuditEvent captures actions, but no workflow-step-level tracking |

**Gap Score: 5% complete** (UI structure only)

---

### 2.6 Reporting, Analytics & Dashboards (Spec §3.6)

| Requirement | Status | Detail |
|---|---|---|
| Spreading Dashboard (productivity, accuracy) | ⚠️ Partial | Dashboard page shows KPI cards + area chart; limited to basic stats |
| Covenant Dashboard (health overview) | ⚠️ Partial | Covenant Intelligence page shows breach stats + top-risk table |
| Breach Risk Heatmap | ❌ Missing | |
| Trendline charts with threshold bands | ❌ Missing | |
| Upcoming covenant calendar | ❌ Missing | |
| Portfolio-level analytics (cross-client) | ❌ Missing | |
| Custom queries / drill-down | ❌ Missing | |
| Natural Language Querying (LLM) | ❌ Missing | Phase 2 per spec |
| MIS Reports (Spreading/Covenant) | ⚠️ Partial | Reports page exists; renders raw JSON, no formatted reports |
| Export formats (Excel/PDF/HTML) | ⚠️ Partial | CSV/Excel buttons exist; unclear if backend generates actual files |
| Scheduled report delivery | ❌ Missing | |

**Gap Score: 25% complete**

---

### 2.7 Customer Management (Spec §3.7)

| Requirement | Status | Detail |
|---|---|---|
| Search by name/ID | ✅ Done | |
| CRUD operations | ✅ Done | |
| Customer data sync from external system | ❌ Missing | |
| Group-based visibility | ❌ Missing | |

**Gap Score: 60% complete**

---

### 2.8 Administration Module (Spec §3.8)

| Requirement | Status | Detail |
|---|---|---|
| User management (approve/reject/activate) | ⚠️ Partial | CRUD exists; no approval workflow for new accounts |
| Bulk user operations (CSV) | ❌ Missing | |
| Taxonomy management (CRUD + bulk) | ⚠️ Partial | Read-only UI; API has upsert |
| Global zone management | ❌ Missing | |
| Language management | ❌ Missing | |
| Exclusion list management | ❌ Missing | |
| AI Model management (accuracy, retrain trigger) | ❌ Missing | |
| A/B test management UI | ❌ Missing | Backend supports A/B; no admin toggle UI |

**Gap Score: 20% complete**

---

### 2.9 Technical Architecture (Spec §4)

| Requirement | Status | Detail |
|---|---|---|
| Multi-tenant data isolation | ✅ Done | TenantAwareEntity + TenantContext (ThreadLocal) |
| API-first (documented REST) | ⚠️ Partial | APIs exist; Swagger/OpenAPI endpoint configured but not verified |
| Event-driven (spread → covenant triggers) | ❌ Missing | Spring in-process events only; no Kafka/RabbitMQ |
| Data sovereignty (per-tenant region) | ❌ Missing | |
| Kubernetes + Helm | ❌ Missing | Docker Compose only |
| CI/CD pipeline | ❌ Missing | No GitHub Actions workflows |
| Prometheus + Grafana monitoring | ⚠️ Partial | OpenTelemetry configured; no Prometheus/Grafana stack deployed |
| Redis for exclusive locking | ❌ Missing | Redis in docker-compose but not used for locking |
| WebSocket real-time updates | ⚠️ Partial | Configured in backend; no frontend WebSocket client |

**Gap Score: 25% complete**

---

### 2.10 ML Training Pipeline

| Requirement | Status | Detail |
|---|---|---|
| 00_environment_setup | ✅ Done | 8-cell Colab notebook |
| 01_edgar_data_collection | ✅ Done | EDGAR 20-F downloader with resume |
| 02_lse_gcc_data_collection | ❌ Stub | |
| 03_xbrl_parsing_autolabeling | ❌ Stub | |
| 04_ocr_batch_processing | ❌ Stub | |
| 05_table_extraction_eval | ❌ Stub | |
| 06_zone_annotation_tool | ❌ Stub | |
| 07_layoutlm_zone_training | ❌ Stub | **Critical** — no zone classifier trained |
| 08_sbert_baseline_eval | ❌ Stub | |
| 09_sbert_finetuning | ❌ Stub | **Critical** — no SBERT fine-tuned |
| 10_ifrs_taxonomy_builder | ❌ Stub | |
| 11_model_evaluation_report | ❌ Stub | |
| 12_export_to_mlflow | ❌ Stub | **Critical** — cannot deploy models |
| VLM notebooks (00–05) | ❌ Stub | |
| 20_feedback_retraining | ❌ Stub | |
| Training scripts (4 files) | ✅ Done | edgar_downloader, xbrl_parser, data_splitter, evaluation_utils |

**Gap Score: 15% complete**

---

## 3. Remaining Work — Prioritized Backlog

### Priority Levels
- **P0** — Must have for demo/pilot. Blocking.
- **P1** — Required for Phase 1 production launch.
- **P2** — Phase 2–3 features.
- **P3** — Phase 4–5 enterprise features.

---

### P0 — Demo Critical (Required for HSBC Pitch)

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P0-1 | **Dual-pane Spread Workspace** — PDF.js viewer (left) + AG Grid spreadsheet (right) with zone overlays, click-to-navigate, confidence-coded cells | Frontend | 2 weeks | None |
| P0-2 | **Train LayoutLM zone classifier** — Implement notebooks 03 (XBRL auto-labeling) + 07 (LayoutLM training) + 12 (export to MLflow) | ML Training | 1.5 weeks | Notebooks 01 done |
| P0-3 | **Train SBERT matcher** — Implement notebooks 08 (baseline eval) + 09 (fine-tuning) + 12 (export) | ML Training | 1 week | Taxonomy data |
| P0-4 | **Exclusive locking** — Redis-backed lock acquire/release on SpreadItem; WebSocket lock status push | Backend + Frontend | 3 days | Redis already in docker-compose |
| P0-5 | **Validation panel** — Frontend panel showing balance-check results (Assets = L+E) with pass/fail indicators | Frontend | 2 days | Backend validation exists |

### P1 — Phase 1 Production

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P1-1 | **SSO Integration** (SAML 2.0 + OIDC) | Backend | 1 week | Spring Security SAML/OIDC libraries |
| P1-2 | **MFA (TOTP)** — Google Authenticator-style 2FA for admin roles | Backend + Frontend | 4 days | |
| P1-3 | **Group-based customer visibility** — User-group-to-customer-group mapping with filtered queries | Backend + Frontend | 4 days | |
| P1-4 | **Subsequent spreading** — Base period selection, autofill from prior mappings, multi-period submit | Backend + Frontend | 1 week | AutofillService exists |
| P1-5 | **Covenant management UI completion** — Replace mock data with live API; add covenant definition CRUD forms | Frontend | 3 days | Backend APIs exist |
| P1-6 | **Visual Formula Builder** — Drag-and-drop formula editor for covenant financial formulas | Frontend | 1 week | FormulaEngine exists |
| P1-7 | **Covenant → Spread event trigger** — When spread submitted, auto-recalculate affected covenants | Backend | 3 days | Need event bus |
| P1-8 | **Waiver letter generation** — Template variable substitution, PDF generation, download | Backend + Frontend | 4 days | Email templates exist |
| P1-9 | **Automated email notifications** — SMTP/SendGrid integration; due/overdue/breach reminders | Backend | 3 days | |
| P1-10 | **Taxonomy CRUD + bulk upload** — Admin Excel upload/download for synonyms | Backend + Frontend | 3 days | |
| P1-11 | **Exclusion list management** — 12-category text cleaning config UI | Backend + Frontend | 3 days | |
| P1-12 | **Password-protected PDF handling** | OCR Service | 2 days | |
| P1-13 | **Word/Excel document support** — .docx → PDF conversion, .xlsx direct table import | OCR Service | 3 days | |
| P1-14 | **WebSocket real-time updates** — Document processing status, lock status, covenant changes | Backend + Frontend | 4 days | Backend configured |
| P1-15 | **CI/CD pipeline** — GitHub Actions: build, test, Docker push on every PR merge | Infra | 2 days | |
| P1-16 | **ML training notebooks (remaining)** — 02, 04, 05, 06, 10, 11 + VLM notebooks | ML Training | 2 weeks | |

### P2 — Phase 2 (Covenants & Intelligence)

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P2-1 | **Predictive breach probability (ML-based)** — Train time-series model on covenant value history | ML Service + Backend | 1 week | Historical data needed |
| P2-2 | **Breach risk heatmap** — Recharts heatmap visualization (customer × covenant) | Frontend | 3 days | P2-1 |
| P2-3 | **Covenant trend charts** — Per-covenant value over time with threshold bands | Frontend | 2 days | |
| P2-4 | **Covenant calendar view** — Timeline of upcoming due dates with risk coloring | Frontend | 3 days | |
| P2-5 | **Skip-overlap logic** — Q4 auto-skip when annual exists for same audit method | Backend | 1 day | |
| P2-6 | **Non-financial auto-approval** — Auto-approve "Financial Statement" items on spread submit | Backend | 1 day | P1-7 |

### P3 — Phase 3 (Workflow Engine & Reporting)

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P3-1 | **Camunda/Flowable integration** — Embed BPMN engine for configurable approval chains | Backend | 2 weeks | Major integration |
| P3-2 | **Workflow designer UI** — Visual BPMN editor for admin workflow configuration | Frontend | 2 weeks | P3-1 |
| P3-3 | **Spreading dashboard enhancements** — Analyst productivity metrics, AI accuracy trendlines | Frontend + Backend | 4 days | |
| P3-4 | **MIS reports engine** — Formatted report generation with filters, export to Excel/PDF/HTML | Backend + Frontend | 1 week | |
| P3-5 | **Scheduled report delivery** — Cron-based report generation + email delivery | Backend | 3 days | P1-9 |
| P3-6 | **Portfolio analytics** — Cross-client ratio comparisons, sector/geography filtering, drill-down | Backend + Frontend | 1.5 weeks | |

### P4 — Phase 4 (LLM Copilot & Advanced AI)

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P4-1 | **LLM conversational copilot** — Natural language interface for analysts | ML Service + Frontend | 2 weeks | LLM API access |
| P4-2 | **Natural language querying** — "Which covenants are at risk this month?" → chart/table result | ML Service + Frontend | 1.5 weeks | P4-1 |
| P4-3 | **Notes processing** — Extract values from free-text paragraphs in financial statements | ML Service | 1 week | |
| P4-4 | **Per-client model specialization** — Automated fine-tuning pipeline when correction threshold met | ML Training + ML Service | 1.5 weeks | P0-3, feedback data |
| P4-5 | **A/B testing management UI** — Admin toggle for model versions, traffic split configuration | Frontend | 3 days | Backend A/B exists |
| P4-6 | **Feedback retraining notebook** (20_feedback_retraining) | ML Training | 3 days | |

### P5 — Phase 5 (Enterprise Readiness)

| # | Item | Layer | Effort | Dependencies |
|---|------|-------|--------|-------------|
| P5-1 | **Kubernetes + Helm charts** | Infra | 1 week | |
| P5-2 | **Multi-region deployment** — Per-tenant data residency configuration | Backend + Infra | 1 week | P5-1 |
| P5-3 | **Air-gapped deployment packaging** | Infra | 3 days | P5-1 |
| P5-4 | **CreditLens adapter** — 2-way sync (push spreads, pull models/metadata) | Backend | 2 weeks | CL API access |
| P5-5 | **nCino / Finastra adapters** | Backend | 1 week each | API access |
| P5-6 | **SOC 2 / ISO 27001 preparation** — Security audit, pen testing, documentation | Infra + Backend | 2 weeks | |
| P5-7 | **Load testing & performance hardening** | All layers | 1 week | |
| P5-8 | **Client onboarding toolkit** — Documentation, data migration scripts, training materials | Docs | 1 week | |

---

## 4. Sprint Plan

### Sprint 1–2: Demo-Critical Workspace (Weeks 1–4)

**Goal**: The analyst uploads a PDF and sees a real dual-pane review workspace.

| Week | Backend | Frontend | ML Training |
|------|---------|----------|-------------|
| 1 | P0-4 (Exclusive locking with Redis) | P0-1 Start: PDF.js viewer component + zone overlay rendering | P0-2 Start: Notebooks 03+07 (XBRL auto-label + LayoutLM training) |
| 2 | P1-7 (Covenant → Spread event trigger) | P0-1 Complete: Dual-pane layout, AG Grid with confidence colors, click-to-navigate | P0-2 Complete + P0-3 (SBERT fine-tuning + export to MLflow) |
| 3 | P1-14 (WebSocket real-time) | P0-5 (Validation panel) + P0-1 Polish (expression editor) | P1-16 Start: Remaining training notebooks (02, 04, 05) |
| 4 | P1-4 (Subsequent spreading + autofill) | P1-5 (Covenant management live API) + P1-6 Start (Formula builder) | P1-16 Continue: Notebooks 06, 10, 11, VLM notebooks |

### Sprint 3–4: Auth & Security Hardening (Weeks 5–8)

| Week | Backend | Frontend | Infra |
|------|---------|----------|-------|
| 5 | P1-1 (SSO: SAML 2.0 + OIDC) | P1-6 Complete (Visual formula builder) | P1-15 (CI/CD pipeline) |
| 6 | P1-2 (MFA/TOTP) | P1-10 (Taxonomy CRUD + bulk upload) | |
| 7 | P1-3 (Group-based visibility) | P1-11 (Exclusion list management UI) | |
| 8 | P1-8 (Waiver letter generation) + P1-9 (Email notifications) | P1-5 refinement + P1-12/13 (docx/xlsx support) | |

### Sprint 5–6: Covenants Intelligence (Weeks 9–12)

| Week | Backend | Frontend |
|------|---------|----------|
| 9 | P2-1 (Predictive breach ML model) | P2-2 (Breach risk heatmap) |
| 10 | P2-5 (Skip-overlap) + P2-6 (Auto-approval) | P2-3 (Trend charts) + P2-4 (Calendar view) |
| 11 | P3-4 Start (MIS reports engine) | P3-3 (Dashboard enhancements) |
| 12 | P3-4 Complete + P3-5 (Scheduled reports) | P3-6 Start (Portfolio analytics) |

### Sprint 7–8: Workflow Engine (Weeks 13–16)

| Week | Backend | Frontend |
|------|---------|----------|
| 13–14 | P3-1 (Camunda/Flowable integration) | P3-2 Start (Workflow designer UI) |
| 15–16 | P3-1 Complete (all workflow types) | P3-2 Complete + P3-6 Complete (Portfolio analytics) |

### Sprint 9–10: LLM & Advanced AI (Weeks 17–20)

| Week | ML Service | Frontend |
|------|------------|----------|
| 17–18 | P4-1 (LLM copilot backend) | P4-1 (Chat interface) + P4-5 (A/B test UI) |
| 19–20 | P4-2 (NL querying) + P4-3 (Notes processing) | P4-2 (NL dashboard) + P4-4 (Client model pipeline) |

### Sprint 11–12: Enterprise Readiness (Weeks 21–24)

| Week | Backend/Infra | Docs |
|------|---------------|------|
| 21–22 | P5-1 (K8s+Helm) + P5-2 (Multi-region) | P5-8 Start (Onboarding toolkit) |
| 23–24 | P5-4 (CreditLens adapter) + P5-6 (SOC 2 prep) + P5-7 (Load testing) | P5-8 Complete |

---

## 5. Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| **No trained models** — System uses HuggingFace fallbacks; demo accuracy will be poor | High | High | P0-2 and P0-3 are the #1 priority; block all other work until models are training |
| **Spread workspace is a flat table** — Demo won't impress if analyst can't see the PDF | High | Certain | P0-1 is the longest-lead frontend item; start Week 1 |
| **Solo developer** — 24 weeks of planned work at solo capacity | High | Certain | Focus ruthlessly on P0 items; defer P3+ until after pilot | 
| **EDGAR data quality** — 20-F PDFs may be inconsistent; training labels from XBRL may be noisy | Medium | Medium | Start with 100 clean documents, iteratively expand |
| **Camunda integration complexity** — BPMN engines have steep learning curves | Medium | Medium | Evaluate Flowable (lighter) first; consider simpler state-machine if BPMN too heavy |
| **External API dependencies** — CreditLens/nCino APIs may require partnership agreements | Medium | High | Start adapter work only when client confirms target system |
| **Colab GPU quota** — Free tier T4 may not be enough for LayoutLM + SBERT training | Low | Medium | Kaggle backup (30 hrs/week); most training fits in 2–4 hours |

---

## Appendix A: Effort Summary

| Priority | Items | Estimated Effort |
|----------|-------|-----------------|
| P0 (Demo-critical) | 5 items | ~4.5 weeks |
| P1 (Phase 1 production) | 16 items | ~8 weeks |
| P2 (Covenants intelligence) | 6 items | ~2 weeks |
| P3 (Workflow & reporting) | 6 items | ~6 weeks |
| P4 (LLM & advanced AI) | 6 items | ~5.5 weeks |
| P5 (Enterprise readiness) | 8 items | ~7 weeks |
| **Total** | **47 items** | **~33 weeks** |

> **Note**: Effort estimates assume a single developer working full-time. Parallelizable items (frontend + backend + ML training) could compress the timeline to ~20 weeks with 2 engineers or by running ML training asynchronously on Colab while coding.

---

## Appendix B: What's Solid & Shouldn't Change

These components are production-grade and should not be refactored:

1. **Backend modular architecture** — Clean Architecture with Spring Modulith. Don't merge modules.
2. **Audit trail with hash-chain** — Immutable, tamper-proof. Industry-grade.
3. **OCR smart routing** — Native PDF → zero ML cost; scanned → VLM. Optimal.
4. **ML Service A/B testing framework** — Production + Staging model routing. Ready.
5. **Feedback collection pipeline** — PostgreSQL-backed, export endpoint for Colab. Complete loop.
6. **Frontend API layer** — React Query + Zustand + fetchApi with auto-refresh. Solid.
7. **IFRS model template** — 195 line items with synonyms, formulas, hierarchy. Comprehensive.
8. **Flyway migrations** — 10 migration files, clean schema evolution. Don't touch.
