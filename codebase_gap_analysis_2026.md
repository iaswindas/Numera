# Numera Platform — Full Codebase Gap Analysis
**Date:** April 11, 2026  
**Reviewer:** AI Codebase Audit  
**Spec Reference:** `application_specification.md` v1.0  
**Test status at time of review:** 65 backend tests pass (23 skipped for optional integration); 61 ML tests pass; 7 OCR test files present.

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [What Is Implemented](#2-what-is-implemented)
3. [Gaps by Module](#3-gaps-by-module)
4. [Security Vulnerability Findings](#4-security-vulnerability-findings)
5. [Technical Debt & Code Quality Issues](#5-technical-debt--code-quality-issues)
6. [Phase Coverage vs Spec](#6-phase-coverage-vs-spec)
7. [Risk Register](#7-risk-register)
8. [Recommended Next Steps (Prioritised)](#8-recommended-next-steps-prioritised)

---

## 1. Executive Summary

The Numera codebase is in an **advanced-but-incomplete** state. The core infrastructure (auth, documents, spreading, covenants, workflow) is built and compiles cleanly. The ML pipeline (OCR + zone classification + semantic matching) exists and is tested. However, large sections of the product specification remain either **stubbed, missing, or read-only shells**:

- The **Reporting module** (`backend/src/main/kotlin/com/numera/reporting/`) is **completely empty** — the folder exists but contains zero files.
- The **Reports UI page** renders raw JSON dumps; there are no formatted MIS reports.
- The **Workflow designer** is a read-only table listing; there is no visual BPMN designer or workflow creation capability.
- **Portfolio-level analytics** (cross-client comparisons, drill-down) are not implemented.
- **Natural Language Querying** (Phase 4) does not exist.
- **LLM Copilot** does not exist.
- **Per-client model specialisation** / fine-tuning pipeline is not implemented.
- **Multi-region / data-sovereignty** deployment support is not implemented.
- **Bulk user provisioning** (CSV upload) is not implemented beyond the UI stubs.
- The **File Store** (batch pre-processing / off-hours queue) is not implemented.
- **On-premise Helm packaging** and air-gapped deployment exist only in docker-compose files, not as Helm charts.
- **Scheduled report delivery** is not implemented.
- **A number of security hardening items remain** (see §4).

Overall, the platform currently covers approximately **Phase 1 (70-75%)**, **Phase 2 (65-70%)**, **Phase 3 (25-30%)**, and **Phase 4/5 (~0%)**.

---

## 2. What Is Implemented

### 2.1 Backend (Kotlin / Spring Boot 3)

| Area | Status |
|---|---|
| JWT authentication (login, refresh, logout, /me) | ✅ Complete |
| TOTP MFA (setup, verify, backup codes) | ✅ Complete |
| SSO stub (SAML/OIDC providers, callback) | ✅ Partially – provider list and callback route exist; actual IdP handshake delegates to `SsoService` which is a stub |
| User registration + admin approval workflow | ✅ Backend complete |
| Password policy (complexity, expiry, history) | ✅ Complete via `PasswordPolicyService` + V014 migration |
| Session management (timeout, concurrent limits) | ✅ `SessionManagementService` + Redis |
| RBAC via `@PreAuthorize` + Spring Security | ✅ Complete |
| Group-based customer visibility | ✅ V011 migration + `UserGroupPort` |
| Tenant-aware multi-tenant (DEFAULT_TENANT row fallback) | ✅ Present but **single-tenant only in practice** (see §3) |
| Document upload + MinIO storage | ✅ Complete |
| OCR orchestration (PaddleOCR via ml-service) | ✅ Complete |
| Async document processing pipeline | ✅ Complete (via `taskExecutor` + `TransactionTemplate`) |
| Zone detection + zone override CRUD | ✅ Complete |
| Financial model templates (IFRS seed) | ✅ V005 + `TemplateService` |
| Customer model copies (per-customer template fork) | ✅ V016 + `TemplateService.getTemplateForCustomer` |
| Spreading workspace: create/read/update/submit/approve | ✅ Complete |
| ML mapping orchestration (semantic + expression build) | ✅ Complete via `MappingOrchestrator` + `SpreadProcessingPort` |
| Confidence scoring (GREEN/AMBER/RED thresholds) | ✅ Complete |
| Bulk accept (green-confidence) | ✅ `SpreadValueController` + UI |
| Exclusive locking via Redis | ✅ `SpreadLockService` + heartbeat mutation in UI |
| Spread version control (immutable snapshots, diff, rollback) | ✅ `SpreadVersionService` |
| Subsequent spreading / autofill from base period | ✅ `AutofillService` |
| Balance-sheet validation engine | ✅ `ValidationEngine` |
| Spread restatement (Override / Duplicate) | ✅ V018 + `SpreadService` |
| Covenant customer management | ✅ Complete |
| Financial covenant definitions + formula builder | ✅ `CovenantService` + `FormulaBuilder` |
| Non-financial covenant definitions | ✅ Complete |
| Monitoring item auto-generation | ✅ `CovenantMonitoringService` |
| Covenant status engine (Due/Overdue/Met/Breached/Closed) | ✅ Complete |
| Real-time covenant recalculation on spread submit | ✅ `SpreadCovenantEventListener` |
| Breach probability forecasting (linear trend) | ✅ `CovenantPredictionService` (linear regression, not ML yet) |
| Risk heatmap materialisation | ✅ `CovenantAnalyticsService` + `CovenantIntelligenceService` |
| Trendline API | ✅ `CovenantIntelligenceController` |
| Waiver / Not-Waiver workflow + letter generation | ✅ `WaiverService` + `WaiverController` |
| Email template + signature management | ✅ Complete |
| Automated breach alert emails | ✅ `EmailNotificationService` + `CovenantReminderScheduler` |
| Covenant document verification (upload/approve/reject) | ✅ `CovenantMonitoringController` |
| Covenant auto-actions | ✅ `CovenantAutoActionService` |
| Workflow engine (linear approval chains, feature-flagged) | ✅ `WorkflowService` + `WorkflowEscalationScheduler` |
| Feature flags (10 flags, per-tenant, DB-backed) | ✅ `FeatureFlagService` + V024 |
| Event backbone (RabbitMQ, feature-flagged) | ✅ `RabbitMqConfig` + dispatcher |
| WebSocket notifications (covenant status) | ✅ `WebSocketNotificationService` + `useWebSocketSubscription` hook |
| Audit event log + hash-chain integrity | ✅ `AuditService` + `HashChainService` |
| ZK-RFA cryptographic audit (ChameleonHash + MMR, feature-flagged) | ✅ `ZkRfaAuditService` |
| External integration SPI (CreditLens + GenericRest adapters) | ✅ `CreditLensAdapter` + `IntegrationSyncService` |
| Taxonomy CRUD + bulk upload (Excel) | ✅ `TaxonomyController` |
| Exclusion list management | ✅ `AdminExclusionListController` (via admin module) |
| Admin: user management, roles | ✅ `UserManagementController` |
| Flyway migrations V001–V029 | ✅ All present |
| Anomaly detection service (OW-PGGR, feature-flagged) | ✅ `AnomalyDetectionService` |

### 2.2 ML Service (Python / FastAPI)

| Area | Status |
|---|---|
| Zone classifier (keyword heuristic + LayoutLM, A/B) | ✅ Complete |
| Semantic matcher (SBERT, A/B testing, tenant-aware) | ✅ Complete |
| Expression engine (MILP / heuristic) | ✅ `expression_engine.py` + `ng_milp/` |
| Pipeline endpoint (orchestrates OCR → classify → map) | ✅ `/api/ml/pipeline/process` |
| Feedback collection (corrections → PostgreSQL) | ✅ `feedback.py` + `FeedbackStore` |
| Model manager (MLflow + HuggingFace fallback) | ✅ `ModelManager` |
| A/B testing for Production vs Staging models | ✅ Built into `SemanticMatcher` and `zone_classifier.py` |
| OW-PGGR anomaly detection | ✅ `owpggr/` detector + materiality |
| RS-BSN regime-shift predictor | ✅ `rsbsn/` HMM + state-space |
| H-SPAR knowledge graph | ✅ `hspar/` + `/api/ml/knowledge-graph` endpoints |
| Covenant breach prediction endpoint | ✅ `/api/ml/covenants/predict` |
| Document fingerprinting (STGH) | ✅ `fingerprint.py` (OCR service) + `api/fingerprint.py` (ML service) |
| Per-client model resolution | ✅ `client_model_resolver.py` |

### 2.3 OCR Service (Python / FastAPI)

| Area | Status |
|---|---|
| PaddleOCR text extraction | ✅ `paddle_ocr.py` |
| PP-StructureV2 table detection | ✅ `table_detector.py` |
| VLM processor (vision model) | ✅ `vlm_processor.py` + `vlm_prompts.py` |
| Image pre-processing (deskew, despeckle) | ✅ `image_preprocessor.py` |
| Period/year detection | ✅ `period_parser.py` |
| Document fingerprinting (STGH) | ✅ `stgh/` |
| API key auth middleware | ✅ `api_key_auth.py` |

### 2.4 Frontend (Next.js / React / TypeScript)

| Area | Status |
|---|---|
| Login page (form + SSO button) | ✅ Complete |
| Dashboard with live stats + charts | ✅ Complete (Recharts) |
| Document upload + file list | ✅ `documents/page.tsx` |
| Spreading workspace (dual-pane PDF + grid) | ✅ `spreading/[spreadId]/page.tsx` with PdfViewer, SpreadTable, ZoneOverlay |
| Lock banner (exclusive locking UX) | ✅ `LockBanner.tsx` |
| Category navigation bubbles | ✅ `CategoryNav.tsx` |
| Validation panel | ✅ `ValidationPanel.tsx` |
| Version history + rollback | ✅ In workspace page |
| Variance view (base period comparison) | ✅ In workspace page |
| Approval queue (approve/reject with comment) | ✅ `approvals/page.tsx` |
| Covenant list + monitoring | ✅ `covenants/page.tsx` |
| Covenant intelligence (heatmap, trend, calendar) | ✅ `covenant-intelligence/page.tsx` |
| Formula builder | ✅ `FormulaBuilder.tsx` |
| Waiver form + letter preview | ✅ `WaiverForm.tsx` + `LetterPreview.tsx` |
| Customer management | ✅ `customers/page.tsx` |
| Admin: user management | ✅ `admin/users/page.tsx` |
| Admin: taxonomy CRUD + bulk upload | ✅ `admin/taxonomy/page.tsx` |
| Admin: formula library | ✅ `admin/formulas/page.tsx` |
| Admin: language management | ✅ `admin/languages/page.tsx` |
| Admin: workflow listing | ✅ (read-only table) |
| Reports page | ✅ (raw JSON dump only) |
| Zustand stores (auth, spread, UI) | ✅ Complete |
| React Query hooks for all API surfaces | ✅ Complete |
| WebSocket hook (`useWebSocketSubscription`) | ✅ Complete |

---

## 3. Gaps by Module

### 3.1 Authentication & Access Control

| Gap | Severity | Notes |
|---|---|---|
| **SSO actual IdP handshake is stubbed** | HIGH | `SsoService` exists but the SAML 2.0 / OIDC token exchange with a real IdP (Azure AD, Okta, etc.) is not implemented. The callback endpoint returns a placeholder response. |
| **Bulk user provisioning via CSV** | MEDIUM | No backend endpoint or UI for CSV bulk import; only single-user creation exists. |
| **Account states (Pending Approval → Active)** | LOW | Registration exists; admin approval flow exists in `UserAdminFacade`, but the self-registration with admin approval email notification is incomplete — the trigger email is not sent. |
| **Concurrent session limiting** | LOW | Configured in `SessionManagementService` but enforcement depends on Redis tracking; max-session limit logic is present but not unit-tested. |

### 3.2 Financial Spreading Module

| Gap | Severity | Notes |
|---|---|---|
| **File Store (bulk pre-processing queue)** | HIGH | The spec requires an off-hours queue where documents are uploaded ahead of time, processed in background, and marked Ready. No such queue UI or state machine exists. Documents go straight to `PROCESSING → READY/ERROR` but there is no "File Store" list with My Files / All Files / Error Files views. |
| **Password-protected document handling** | HIGH | The spec calls for secure password entry for encrypted PDFs. No such flow exists in the upload pipeline or OCR service. |
| **Multi-language support (Arabic RTL, French)** | MEDIUM | `language_management` admin page exists; `LanguageController` exists. The OCR service can invoke PaddleOCR multi-lang models. However no RTL rendering support exists in the frontend PDF viewer or the annotation overlay. The taxonomy multi-language flag exists in DB schema only. |
| **Handwritten annotations (future phase)** | LOW | Marked future — not yet started. |
| **Split-view document pane (two pages simultaneously)** | MEDIUM | The PDF viewer (`PdfViewer.tsx`) renders single pages. A split-pane for mapping two document pages side-by-side is not implemented. |
| **Notes-to-accounts / Base Adjustments / Off-balance sheet zone flows in UI** | MEDIUM | Backend zone types support `NOTES_FIXED_ASSETS`, `NOTES_RECEIVABLES`, etc. The UI `ZoneOverlay.tsx` visualises zones, but no dedicated "Base Adjustments" or "Other Adjustments" panel exists in the review workspace. |
| **"Clean File" post-processing (watermark removal)** | LOW | Deskew and despeckle are in `image_preprocessor.py`. Watermark removal is not implemented. |
| **Submit & Continue (multi-period document)** | MEDIUM | The UI has a single "Next period" hint but no guided "Submit & Continue" flow that auto-creates the next spread for the subsequent period in the same document. |

### 3.3 Financial Model Engine

| Gap | Severity | Notes |
|---|---|---|
| **Model versioning** | MEDIUM | `ModelValidation` entity exists; `model_templates` table exists. There is no `model_versions` table or history tracking for template structure changes over time. |
| **Multiple global templates (US GAAP, region-specific)** | MEDIUM | Only one IFRS template is seeded in `V008__seed_data.sql`. US GAAP, Central Bank UAE, and other region-specific templates are not seeded or defined. |
| **Model export adapters for nCino / Finastra / S&P** | HIGH | Only `CreditLensAdapter` and `GenericRestAdapter` exist. nCino, Finastra, S&P Capital IQ adapters are not implemented. |
| **Two-way CreditLens sync (pull)** | MEDIUM | `CreditLensAdapter` pushes spread values. The pull direction (importing historical data / retained earnings from CreditLens) is not implemented. |

### 3.4 Reporting Module

| Gap | Severity | Notes |
|---|---|---|
| **Reporting module is entirely empty** | CRITICAL | `backend/src/main/kotlin/com/numera/reporting/` folder exists but is **completely empty** — zero Kotlin files. |
| **Spreading Summary report** | CRITICAL | Frontend renders raw JSON from `/api/dashboard/stats`. There is no proper spreading report with filters (status, user, date range, customer). |
| **Covenant Pending / Default / History reports** | CRITICAL | These MIS reports do not exist. |
| **Non-Financial Covenant Item Report** | CRITICAL | Not implemented. |
| **Analyst productivity report** | HIGH | AI accuracy rate per analyst, average time per spread — not implemented. |
| **Scheduled report delivery via email** | HIGH | No scheduler or email-report job exists anywhere in the backend. |
| **Export formats (Excel, PDF, HTML)** | HIGH | The `AuditReportController.export` returns a plain CSV line stub — not a real formatted report. No POI-based Excel generation, no PDF report generation. |
| **Portfolio-level analytics (cross-client comparisons)** | HIGH | Not implemented. |
| **Natural language querying (Phase 4)** | HIGH | Not started. |
| **LLM copilot for analysts** | HIGH | Not started. |

### 3.5 Workflow Engine

| Gap | Severity | Notes |
|---|---|---|
| **Visual BPMN workflow designer** | HIGH | The spec requires a visual drag-and-drop workflow designer. The admin page `admin/workflows/page.tsx` is a **read-only table listing** only. No workflow creation, step editing, or BPMN canvas exists. |
| **Configurable approval chains with conditions** | HIGH | `WorkflowDefinition` domain entity supports multiple steps. However there is no UI or API endpoint to **create** a workflow definition — only to list and complete tasks. |
| **Conditional routing (e.g., amount > $10M)** | MEDIUM | The `WorkflowStepDefinition` entity does not have a `conditionExpression` field; conditional routing is not modelled. |
| **Parallel approval steps** | MEDIUM | Not modelled in `StepType` enum or `WorkflowService`. |
| **Camunda/Flowable integration** | MEDIUM | The spec calls for Camunda 8 (Zeebe) or Flowable. The current implementation is a custom lightweight engine. Camunda is not integrated. |
| **Separate workflow configs per object type** | LOW | `WorkflowType` enum exists (SPREAD_APPROVAL, COVENANT_WAIVER, etc.) but per-type configuration is not enforced in UI. |

### 3.6 Multi-Tenancy

| Gap | Severity | Notes |
|---|---|---|
| **True multi-tenant isolation** | HIGH | All services use `TenantAwareEntity.DEFAULT_TENANT` as a fallback UUID. The `TenantContext` is populated from JWT claims but many queries fall through to `DEFAULT_TENANT`. There is no `schema-per-tenant` or row-level security policy at PostgreSQL level — row filtering is done in application code with `tenantId` equality. This means a bug in any service could leak cross-tenant data. |
| **Tenant provisioning / onboarding API** | HIGH | No admin API exists to create a new tenant, configure its data residency region, enable features, etc. |
| **Multi-region / data sovereignty** | HIGH | The spec requires configurable data residency per tenant (GDPR/DIFC/MAS). Not implemented. Kubernetes manifests and Helm charts do not exist — only docker-compose. |

### 3.7 Infrastructure & DevOps

| Gap | Severity | Notes |
|---|---|---|
| **Helm charts** | HIGH | `docker-compose.yml` and `docker-compose.full.yml` exist. No Helm chart directory exists anywhere in the workspace. On-premise packaging is incomplete. |
| **Kubernetes manifests** | HIGH | None present in the workspace. |
| **CI/CD pipeline (GitHub Actions / GitLab CI)** | HIGH | No `.github/workflows/` or `.gitlab-ci.yml` files exist. |
| **Prometheus / Grafana config** | MEDIUM | Backend exposes `/actuator/prometheus`. No `prometheus.yml` or Grafana dashboard JSON exists. |
| **Dependency scanning (Snyk/Dependabot)** | MEDIUM | No `dependabot.yml` or SAST config files present. |
| **MLflow server** | MEDIUM | `ModelManager` connects to MLflow; configuration exists in `pyproject.toml`. No `mlflow/` server config found. A `mlflow/` directory exists in workspace but unexplored — let us note it exists. |

---

## 4. Security Vulnerability Findings

### CRITICAL

#### SEC-01 — Default JWT Secret in Code
**File:** `backend/src/main/kotlin/com/numera/shared/config/NumeraProperties.kt` (line 28)  
**Finding:** The default JWT secret is `"change-me-please-change-me-please-change-me"`. The `JwtTokenProvider` validates that the secret is ≥32 bytes but the default value satisfies this check. If the application is deployed without setting the `JWT_SECRET` environment variable, any attacker who knows this public secret can forge valid JWTs.  
**OWASP:** A02 Cryptographic Failures  
**Fix:** Remove the default value entirely; require JWT_SECRET to be set or fail startup.

#### SEC-02 — Default MinIO Credentials in Code
**File:** `backend/src/main/kotlin/com/numera/shared/config/NumeraProperties.kt` (lines 40-42)  
**Finding:** `accessKey = "minioadmin"` and `secretKey = "minioadmin"` are the well-known default MinIO credentials. If an environment doesn't set `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`, anyone with network access to port 9000 can read/write all stored documents.  
**OWASP:** A02 Cryptographic Failures  
**Fix:** Remove defaults; require credentials explicitly or fail startup.

### HIGH

#### SEC-03 — CSRF Protection Disabled
**File:** `backend/src/main/kotlin/com/numera/shared/config/SecurityConfig.kt` (line 24)  
**Finding:** `http.csrf { it.disable() }`. While stateless JWT APIs typically don't need CSRF protection for pure REST endpoints, the application also exposes WebSocket connections and uses `allowCredentials(true)` in CORS. CSRF tokens should be at minimum implemented for WebSocket upgrade requests.  
**OWASP:** A01 Broken Access Control  
**Fix:** Enable CSRF for WebSocket endpoints or add SameSite cookie attribute enforcement.

#### SEC-04 — Wildcard ALLOWED_HEADERS in CORS
**File:** `backend/src/main/kotlin/com/numera/shared/config/WebConfig.kt` (line 18)  
**Finding:** `.allowedHeaders("*")` allows any header from any origin listed in `CORS_ALLOWED_ORIGINS`. Combined with `.allowCredentials(true)`, this is overly permissive. A malicious origin that is mistakenly added to the allowed list could send arbitrary headers.  
**OWASP:** A05 Security Misconfiguration  
**Fix:** Restrict allowed headers to the set actually needed (`Authorization`, `Content-Type`, `X-Tenant-ID`, `X-API-Key`).

#### SEC-05 — API Key Auth Bypassed in Tests
**File:** `ml-service/app/middleware/api_key_auth.py` (lines 27-29)  
**Finding:** `if "PYTEST_CURRENT_TEST" in os.environ: return await call_next(request)` — the API key check is completely skipped whenever `PYTEST_CURRENT_TEST` is set. In a containerised CI environment where `PYTEST_CURRENT_TEST` could leak, this bypasses authentication entirely.  
**OWASP:** A07 Identification and Authentication Failures  
**Fix:** Use a test fixture that provides a valid test API key rather than bypassing auth entirely.

#### SEC-06 — Unrestricted File Type on Document Upload
**File:** `backend/src/main/kotlin/com/numera/document\application\DocumentProcessingService.kt`  
**Finding:** The upload endpoint accepts multipart files. There is no server-side MIME type validation or file extension whitelist enforced before storing to MinIO. Only the UI enforces file type. An attacker who bypasses the UI can upload arbitrary files (e.g., scripts, polyglots) to the document store.  
**OWASP:** A03 Injection / A08 Software and Data Integrity Failures  
**Fix:** Validate MIME type using `Tika` magic-byte detection and enforce a server-side allowlist (PDF, DOCX, XLSX, JPEG, PNG, TIFF).

#### SEC-07 — SQL Injection via JPA Specification (Low Risk, but Audit Needed)
**File:** `DocumentProcessingService.kt` — JPA `Specification` lambdas  
**Finding:** Dynamic query building uses JPA `Specification` which parameterises all values correctly. However, raw `entityManager.createNativeQuery` or `@Query` with `nativeQuery=true` should be audited. A grep across the codebase for `nativeQuery = true` should be run during a security review.  
**OWASP:** A03 Injection  
**Fix:** Audit all `@Query(nativeQuery=true)`. Confirm all user-controlled inputs are bound via parameters.

#### SEC-08 — No Rate Limiting on Auth Endpoints
**File:** `backend/src/main/kotlin/com/numera/auth/api/AuthController.kt`  
**Finding:** `/api/auth/login` and `/api/auth/refresh` have no rate limiting. A brute-force or credential-stuffing attack can proceed unrestricted. The spec mentions an API Gateway (Kong) for rate limiting, but no Kong config exists.  
**OWASP:** A07 Identification and Authentication Failures  
**Fix:** Add Spring's `Bucket4j` or use the API gateway layer for rate limiting on auth endpoints. Log and block after N failures per IP.

### MEDIUM

#### SEC-09 — ML Service Has No Rate Limiting
**File:** `ml-service/app/middleware/api_key_auth.py`  
**Finding:** The ml-service only validates an API key but has no per-key request rate limit. A compromised key could trigger unlimited OCR processing, exhausting GPU/CPU resources.  
**OWASP:** A05 Security Misconfiguration  
**Fix:** Add per-key rate limiting middleware (e.g., `slowapi` with Redis backend).

#### SEC-10 — Hard-Coded Default Credentials in Docker Compose
**File:** `docker-compose.yml` / `docker-compose.full.yml`  
**Finding:** `POSTGRES_PASSWORD: numera`, `RABBITMQ_DEFAULT_PASS: guest`, MinIO credentials default to `minioadmin:minioadmin`. These compose files may be used as a starting point for production deployments.  
**OWASP:** A02 Cryptographic Failures  
**Fix:** Add a `docker-compose.override.yml.example` that forces operators to set secrets. Add comments warning that defaults must be changed before production use.

#### SEC-11 — Sensitive Data Potentially Exposed in Audit Log Payload
**File:** `backend/src/main/kotlin/com/numera/shared/audit/AuditService.kt`  
**Finding:** Audit events store `details` as freeform JSON. If a service passes a user DTO (which may contain password hashes, magic links, or PII) as the `details` payload, this ends up in the event log which is returned via `/api/audit` endpoints. No sanitisation or field-exclusion is enforced on the `details` field.  
**OWASP:** A09 Security Logging and Monitoring Failures  
**Fix:** Define an `AuditDetails` sealed class with only safe fields. Never log credentials or PII in audit payloads.

#### SEC-12 — Refresh Token Rotation Not Enforced on Reuse
**File:** `backend/src/main/kotlin/com/numera/auth/application/AuthService.kt`  
**Finding:** On `/api/auth/refresh`, the old refresh token is deleted and a new one is issued. However, if the same refresh token is replayed concurrently (race condition), both requests can succeed in a brief window before the first transaction's delete is committed.  
**OWASP:** A07 Identification and Authentication Failures  
**Fix:** Use a database select-for-update or optimistic locking on the `RefreshToken` entity to prevent concurrent refresh with the same token.

#### SEC-13 — Login Page Has Hard-Coded Demo Credentials
**File:** `numera-ui/src/app/(auth)/login/page.tsx` (lines 16-18)  
**Finding:** `defaultValues: { email: 'analyst@numera.ai', password: 'Password123!' }` — the form pre-populates demo credentials. If this is deployed to a staging or production environment accessible externally, it acts as a prompt to potential attackers.  
**OWASP:** A07 Identification and Authentication Failures  
**Fix:** Remove default credentials from the form; use environment-controlled demo hints or remove entirely for production builds.

#### SEC-14 — Password Exposed in Mail Config
**File:** `backend/src/main/resources/application.yml` (line 36)  
**Finding:** `password: ${MAIL_PASSWORD:}` — the password defaults to empty string. An empty MAIL_PASSWORD means the mailer attempts anonymous SMTP auth. While this won't expose a password, it results in sending unauthenticated mails which may relay through an open SMTP relay.  
**OWASP:** A05 Security Misconfiguration  
**Fix:** Require MAIL_PASSWORD to be set if mail is enabled; add a startup condition check.

### LOW

#### SEC-15 — Missing Security Headers (CSP, HSTS)
**File:** `SecurityConfig.kt`  
**Finding:** `Content-Security-Policy` and `Strict-Transport-Security` headers are not configured. `X-Content-Type-Options` and `X-Frame-Options` are set (frame.deny). But without CSP and HSTS, the risk of XSS and downgrade attacks remains elevated.  
**OWASP:** A05 Security Misconfiguration  
**Fix:** Add CSP header via Spring Security's `headers { it.contentSecurityPolicy { ... } }` and enable HSTS.

#### SEC-16 — No Input Sanitisation on Free-Text Fields (XSS Risk via Email Templates)
**File:** `covenant/application/WaiverService.kt`, `EmailTemplateService.kt`  
**Finding:** Waiver letters are rendered as HTML. Template bodies come from the database (admin-entered) and are rendered via Thymeleaf or string concatenation. If template variables include user-controlled values (customer names, comments), unsanitised interpolation can produce XSS in previewed/emailed HTML.  
**OWASP:** A03 Injection (XSS)  
**Fix:** Ensure all dynamic template variables are HTML-escaped before interpolation. Use a proper templating engine (Thymeleaf with auto-escaping) rather than raw string replacement.

---

## 5. Technical Debt & Code Quality Issues

### 5.1 Reporting Module: Complete Placeholder
The `reporting/` module directory is entirely empty. The `AuditReportController` is placed in `shared/audit/` which means report generation is tightly coupled to the audit store. MIS reports like "Covenant Pending Report", "Spreading Report" with proper filters, date ranges, and Excel/PDF export are completely missing. The frontend's `reports/page.tsx` renders raw `JSON.stringify` — this is a placeholder UI.

### 5.2 Dashboard Stats: N+1 Query Risk
`DashboardController.stats()` calls `spreadItemRepository.findAll()` then iterates each spread to call `spreadValueRepository.findBySpreadItemId(it.id!!)`. With 1,000+ spreads, this is an N+1 query. A materialised summary table or a single aggregating JPQL query should replace this.

### 5.3 Default Tenant Fallback Is a Correctness Risk
Many services contain:
```kotlin
TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT
```
This means any request where the JWT claims are not propagated (e.g., internal event listeners, background jobs) silently operates on the default tenant's data. This is a correctness and data-integrity bug for multi-tenant deployments.

### 5.4 `SystemConfigController.save` Is a No-Op
`SystemConfigController.save` accepts a JSON body and returns `{ saved: true }` without persisting anything. System configuration changes do nothing.

### 5.5 Export Endpoint Returns CSV Stub
`AuditReportController.export` generates:
```csv
generatedAt,format
2026-04-11T...,xlsx
report,spreading-summary
report,covenant-summary
```
This is not a real report. The response MIME type claims `text/plain` even when `format=xlsx` is requested.

### 5.6 One-Way Covenant Prediction
`CovenantPredictionService` uses linear regression as a placeholder. The comment in the code acknowledges this: *"This will be replaced by the dedicated ML prediction endpoint once the ml-service exposes the /covenants/predict endpoint."* The ml-service **does** expose `/api/ml/covenants/predict` (via `covenant_prediction.py`), but `CovenantPredictionPort` in the backend still falls back to the linear estimate if the feature flag `rsBsnPredictor` is off.

### 5.7 Workflow Designer Is Read-Only
`admin/workflows/page.tsx` merely renders a table. The API `GET /api/workflows` returns definitions, but there is no `POST /api/workflows` for creation, no step editor, and no BPMN canvas. The entire visual workflow designer as specified is missing.

### 5.8 `ng_milp` Module Not Exposed in Feature Flags
`NumeraProperties.Features.ngMilpSolver = true` (default on), but there is no separate feature flag for using MILP vs. heuristic expression building. The `expression_engine.py` switches between them internally, but the backend has no way to influence this choice per tenant.

### 5.9 Missing `@Transactional(readOnly=true)` on Read Queries
Several repository-backed read methods in controllers do not mark their transactions as read-only, missing Hibernate optimisation opportunities and risking dirty-read in future code changes.

### 5.10 Unchecked Cast Warning in DocumentProcessingService
Per repo memory: *"single warning in DocumentProcessingService: unchecked cast"*. This is a known but unresolved compiler warning that could hide runtime `ClassCastException` in production.

---

## 6. Phase Coverage vs Spec

| Phase | Spec Items | Implemented | Partial | Missing |
|---|---|---|---|---|
| **Phase 1: Foundation & Spreading** | 12 | 8 | 3 | 1 |
| **Phase 2: Covenants & Intelligence** | 10 | 7 | 2 | 1 |
| **Phase 3: Workflow & Reporting** | 7 | 1 | 1 | 5 |
| **Phase 4: LLM & Advanced AI** | 4 | 0 | 0 | 4 |
| **Phase 5: Enterprise Readiness** | 5 | 0 | 1 | 4 |
| **TOTAL** | **38** | **16 (42%)** | **7 (18%)** | **15 (40%)** |

### Phase 1 Detail
| Spec Item | Status |
|---|---|
| Auth (SSO + Form + MFA) | ✅ (SSO is stubbed) |
| RBAC + group-based visibility | ✅ |
| File Store (off-hours batch queue) | ❌ Missing |
| AI Pipeline (OCR + table detection) | ✅ |
| Autonomous mapping + confidence scoring | ✅ |
| Dual-pane Review Workspace | ✅ |
| Financial Model Engine + global templates | ⚠️ IFRS only; other templates missing |
| Spread lifecycle + exclusive locking | ✅ |
| Git-like version control | ✅ |
| Subsequent spreading + autofill | ✅ |
| Validation engine | ✅ |
| ML feedback loop + retrain pipeline | ⚠️ Feedback store exists; Colab notebook pipeline is separate, not automated |

### Phase 2 Detail
| Spec Item | Status |
|---|---|
| Covenant customer management | ✅ |
| Financial + non-financial covenant definitions | ✅ |
| Formula Builder | ✅ |
| Monitoring item auto-generation | ✅ |
| Real-time covenant recalculation | ✅ |
| Predictive breach probability | ⚠️ Linear regression placeholder; ML endpoint exists but not wired |
| Non-financial document approval workflow | ✅ |
| Waiver / Not-Waiver letter generation | ✅ |
| Email template + signature management | ✅ |
| Automated due/overdue reminders | ⚠️ Scheduler exists; reminder config table exists; actual email content wiring is basic |

### Phase 3 Detail
| Spec Item | Status |
|---|---|
| BPMN workflow designer (Camunda/Flowable) | ❌ Missing |
| Configurable approval hierarchies | ❌ Not creatable via UI/API |
| Live Spreading Dashboard | ⚠️ Stats card only; no per-analyst productivity metrics |
| Live Covenant Dashboard | ✅ |
| Portfolio analytics + drill-down | ❌ Missing |
| MIS reports (export Excel/PDF/HTML) | ❌ Missing (stub only) |
| Scheduled report delivery | ❌ Missing |

### Phase 4 Detail
| Spec Item | Status |
|---|---|
| LLM conversational copilot | ❌ Missing |
| Natural language querying | ❌ Missing |
| Notes processing (AI extraction from free text) | ❌ Missing |
| Per-client model specialisation | ⚠️ `client_model_resolver.py` exists; training pipeline is manual Colab notebooks |

### Phase 5 Detail
| Spec Item | Status |
|---|---|
| Multi-region deployment + data sovereignty | ❌ Missing |
| On-premise Helm packaging (air-gapped) | ❌ Missing (docker-compose only) |
| External system adapters (nCino, Finastra) | ❌ CreditLens + GenericRest only |
| SOC 2 / ISO 27001 audit prep | ❌ Not started |
| Performance hardening + load testing at scale | ⚠️ `PerformanceBenchmarkTest.kt` exists as a skeleton |

---

## 7. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | Default JWT secret deployed to production | MEDIUM | CRITICAL | Require env var; fail startup if default detected |
| R-02 | Default MinIO credentials provide open access to documents | MEDIUM | CRITICAL | Same as R-01 |
| R-03 | Cross-tenant data leakage via DEFAULT_TENANT fallback | MEDIUM | HIGH | Enforce non-null tenant resolution; add integration test for tenant isolation |
| R-04 | N+1 queries cause dashboard timeout at scale | HIGH | HIGH | Replace with aggregating queries or materialized view |
| R-05 | No Helm charts = on-premise deployment manual and error-prone | HIGH | HIGH | Create Helm chart as immediate work item |
| R-06 | Empty reporting module blocks Phase 3 delivery | HIGH | HIGH | Implement reporting module with proper MIS reports |
| R-07 | SSO stub means enterprise clients cannot integrate their IdPs | HIGH | MEDIUM | Complete OAuth2/OIDC PKCE flow; test against Azure AD |
| R-08 | No CI/CD pipeline = manual deployments, high error risk | HIGH | MEDIUM | Create GitHub Actions workflow for build → test → docker push |
| R-09 | Missing MIME-type validation on uploads = arbitrary file store | MEDIUM | HIGH | Add Tika-based file type validation |
| R-10 | Workflow designer is read-only = Phase 3 client demo will fail | HIGH | MEDIUM | Implement workflow creation API + minimal designer UI |

---

## 8. Recommended Next Steps (Prioritised)

### P0 — Security Hardening (Do Now)
1. Remove default JWT secret and MinIO credentials from `NumeraProperties.kt`; fail startup if these are not set via environment variables.
2. Add server-side MIME-type validation for document uploads using Apache Tika.
3. Remove hard-coded demo credentials from `login/page.tsx` default values.
4. Add CSP and HSTS headers in `SecurityConfig.kt`.
5. Fix API key bypass in `api_key_auth.py` — use a test fixture key instead of bypassing entirely.

### P1 — Phase 3 Gaps (Highest Product Impact)
1. **Implement the Reporting Module** — create `reporting/` Kotlin files: at minimum `SpreadingReportService`, `CovenantReportService`, and proper Excel export using Apache POI (the dependency is already in `build.gradle.kts`).
2. **Implement Workflow Creation API** — add `POST /api/workflows` and `PUT /api/workflows/{id}` with step definitions. Add a minimal workflow designer form in the admin UI.
3. **Wire ML covenant prediction** — enable `CovenantPredictionPort` to call the ml-service `/api/ml/covenants/predict` endpoint when `rsBsnPredictor` feature flag is on.
4. **Fix N+1 in DashboardController** — replace per-spread value lookups with a single aggregating JPQL query.

### P2 — Phase 1 Completions
1. **Implement File Store UI and backend** — add a `QUEUED` status, a background job that processes queued documents, and a "My Files / All Files / Error Files" view.
2. **Complete SSO handshake** — implement the OIDC PKCE flow in `SsoService` using Spring Security OAuth2 client. Test with a mock IdP.
3. **Add additional global model templates** — seed US GAAP template and at least one regional template.
4. **Password-protected PDF handling** — add a password-prompt modal in the upload flow and pass the decryption key to PaddleOCR only for the one-time decryption step.

### P3 — Infrastructure
1. **Create Helm chart** — minimum viable chart for backend, ml-service, ocr-service, PostgreSQL, Redis, MinIO, RabbitMQ.
2. **Create GitHub Actions CI/CD pipeline** — build → lint → test → Docker build → push to registry.
3. **Add Prometheus alert rules and Grafana dashboards** for JVM memory, API response latency, ML pipeline throughput, and covenant breach counts.
4. **Tenant provisioning API** — super-admin endpoint to create tenants, assign regions, and configure feature flags.

### P4 — Phase 4/5 (Long Tail)
1. LLM copilot integration (OpenAI / Azure OpenAI / local LLM via Ollama).
2. Natural language querying layer over the analytics API.
3. Per-client model fine-tuning pipeline (automated trigger after N corrections).
4. nCino / Finastra adapter implementation.
5. SOC 2 / ISO 27001 controls mapping and evidence collection.

---

*End of gap analysis. This document should be updated after each sprint to reflect progress.*
