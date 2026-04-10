# Numera Platform — Full Integration & Completion Plan

> **Goal**: Bridge the three independently-built layers (Backend, ML, Frontend) into a single working system, add test coverage, and prepare for pilot deployment.
>
> **Current state**: Each layer is 80–95% code-complete in isolation. Zero integration between layers. Zero test coverage on backend. All frontend pages render mock data.

---

## User Review Required

> [!IMPORTANT]
> **Phase ordering**: This plan is sequenced so each phase unlocks the next. Phase 1 (Backend Verification) must pass before Phase 2 (Integration) can begin. Please confirm this ordering works with your priorities, or if you'd like to skip to a specific phase (e.g., "just wire up the demo" first).

> [!WARNING]
> **Breaking scope decision**: The legacy ML training notebooks (01–10) for LayoutLM/SBERT are stubs. The VLM (Qwen3-VL) path is implemented and more modern. Should we:
> - **(A)** Complete the legacy notebooks too (for fallback training pipeline)?
> - **(B)** Focus only on VLM path and mark legacy notebooks as deprecated?
>
> This plan assumes **(A)** — complete both paths — but can be trimmed if you choose (B).

> [!CAUTION]
> **Database state**: The backend has 10 Flyway migrations but the PostgreSQL database may not exist yet locally. Phase 1 includes standing up local infra for the first time.

---

## Phase 1: Backend Verification & Testing (Foundation)

**Why first**: We can't integrate the frontend until we know the backend APIs actually work. The backend has ~70KB of Kotlin business logic across 6 modules but zero tests. This phase validates every API contract.

### 1.1 Local Infrastructure Bootstrap

#### [NEW] `backend/docker-compose.dev.yml`
A unified docker-compose that starts **everything** needed for local dev:
- PostgreSQL 17 (port 5432) — backend database
- Redis / Valkey 8 (port 6379) — session cache
- MinIO (ports 9000/9001) — object storage
- Uses the existing backend `docker-compose.yml` as base but with proper networking

Changes:
- Update existing [backend/docker-compose.yml](file:///f:/Context/backend/docker-compose.yml) to include Redis + MinIO alongside PostgreSQL
- Add health checks and `depends_on` conditions
- Add a `minio-init` service to auto-create the `numera-docs` bucket on startup

#### [MODIFY] [application.yml](file:///f:/Context/backend/src/main/resources/application.yml)
- Add environment variable fallbacks with `${...}` syntax (matching the spec in `Updatedbackend_implementation_plan.md`)
- Add virtual threads config (`spring.threads.virtual.enabled: true`)
- Add Hikari pool sizing
- Add multipart upload config (`50MB`)
- Tighten `flyway.baseline-on-migrate` to `false`

#### [MODIFY] [application-dev.yml](file:///f:/Context/backend/src/main/resources/application-dev.yml)  
- Expand from 2 lines to a proper dev profile with debug logging, show-sql, faster JWT expiry for testing

---

### 1.2 Backend Test Suite

#### [NEW] `backend/src/test/kotlin/com/numera/ModuleBoundaryTest.kt`
- Spring Modulith `ApplicationModules.verify()` test
- Ensures no cross-module internal package leaks

#### [NEW] `backend/src/test/kotlin/com/numera/auth/AuthServiceTest.kt`
- Login with valid credentials → returns JWT tokens
- Login with invalid password → 401
- Refresh token → new access token
- Expired refresh token → 401
- `/api/auth/me` with valid JWT → returns user profile
- Password hash verification with BCrypt

#### [NEW] `backend/src/test/kotlin/com/numera/customer/CustomerServiceTest.kt`
- CRUD: create, read, update customers
- Search by name (trigram index)
- Tenant isolation (customer from tenant A not visible to tenant B)

#### [NEW] `backend/src/test/kotlin/com/numera/document/DocumentProcessingTest.kt`
- Upload document → creates record in DB with status `UPLOADED`
- Process document → calls ML service client → status transitions
- Zone detection → stores `DetectedZone` records
- Error handling → status transitions to `ERROR` with message

#### [NEW] `backend/src/test/kotlin/com/numera/model/FormulaEngineTest.kt`
- Evaluate `SUM(IS001, IS002)` → correct result
- Evaluate `IF(BS_TOTAL = 0, "ERROR", "OK")` → correct result
- Validation rules: `TOTAL_ASSETS == TOTAL_LIABILITIES_EQUITY` tolerance check
- Invalid formula → graceful error

#### [NEW] `backend/src/test/kotlin/com/numera/spreading/MappingOrchestratorTest.kt`
- Process spread item → calls ML pipeline → stores mapping results
- Version creation on submit
- Diff between versions
- Rollback to previous version
- Bulk accept by confidence threshold

#### [NEW] `backend/src/test/kotlin/com/numera/spreading/SpreadServiceTest.kt`
- Create spread item → links to customer + template + document
- Status transitions: DRAFT → SUBMITTED → APPROVED
- Lock/unlock semantics

#### [NEW] `backend/src/test/kotlin/com/numera/covenant/CovenantServiceTest.kt`
- Create covenant customer → link covenants
- Monitoring item calculation
- Breach detection
- Waiver workflow
- Email template rendering

#### [NEW] `backend/src/test/kotlin/com/numera/shared/AuditServiceTest.kt`
- Audit event creation → hash chain integrity
- Hash chain verification
- Audit log query by entity, action, date range

**Test infrastructure**: Use `@DataJpaTest` + H2 for repo tests, `@SpringBootTest` + Testcontainers PostgreSQL for integration tests, MockK for unit tests.

---

### 1.3 Backend API Verification (Manual)

After tests pass, manually verify each controller endpoint using the Swagger UI (`/swagger-ui.html`):

| Module | Endpoint | Method | Verify |
|---|---|---|---|
| Auth | `/api/auth/login` | POST | Returns JWT |
| Auth | `/api/auth/refresh` | POST | Refreshes token |
| Auth | `/api/auth/me` | GET | Returns user profile |
| Customer | `/api/customers` | GET/POST | CRUD works |
| Customer | `/api/customers/{id}` | GET/PUT | Single entity |
| Document | `/api/documents` | POST (multipart) | File upload to MinIO |
| Document | `/api/documents/{id}/process` | POST | Triggers ML pipeline |
| Document | `/api/documents/{id}/zones` | GET | Returns detected zones |
| Model | `/api/templates` | GET | Lists templates |
| Model | `/api/templates/{id}/line-items` | GET | Returns line items |
| Spreading | `/api/customers/{cid}/spread-items` | POST/GET | Create & list |
| Spreading | `/api/spread-items/{id}/process` | POST | ML pipeline trigger |
| Spreading | `/api/spread-items/{id}/submit` | POST | Status transition |
| Spreading | `/api/spread-items/{id}/history` | GET | Version history |
| Spreading | `/api/spread-items/{id}/diff/{v1}/{v2}` | GET | Version diff |
| Covenant | `/api/covenants/customers` | GET/POST | Covenant customers |
| Covenant | `/api/covenants/definitions` | GET/POST | Covenant CRUD |
| Covenant | `/api/covenants/monitoring` | GET | Monitoring dashboard |
| Dashboard | `/api/dashboard/stats` | GET | Aggregate stats |
| Audit | `/api/audit/events` | GET | Event log |

---

## Phase 2: Frontend ↔ Backend Integration

**Why second**: Now that APIs are verified, we wire the frontend to real data. The Next.js rewrites proxy (`/api/:path*` → `localhost:8080`) is already configured in [next.config.ts](file:///f:/Context/numera-ui/next.config.ts), so the API layer in [api.ts](file:///f:/Context/numera-ui/src/services/api.ts) should work out of the box once the backend is running.

### 2.1 Auth Flow (End-to-End)

#### [MODIFY] [login/page.tsx](file:///f:/Context/numera-ui/src/app/(auth)/login/page.tsx)
- Replace mock login with actual `useLogin()` mutation from [authApi.ts](file:///f:/Context/numera-ui/src/services/authApi.ts)
- On success → redirect to `/dashboard`
- On error → show error message toast
- Wire form to `react-hook-form` + `zod` validation

#### [NEW] `numera-ui/src/middleware.ts`
- Next.js middleware for route protection
- Check `numera-auth` cookie/localStorage for valid token
- Redirect unauthenticated users to `/login`
- Redirect authenticated users away from `/login` to `/dashboard`

#### [NEW] `numera-ui/src/components/providers/AuthGuard.tsx`
- Client-side auth guard component wrapping dashboard layout
- Auto-refresh token before expiry using `useRefreshToken()` hook
- Logout on 401 responses (add interceptor to `fetchApi`)

#### [MODIFY] [api.ts](file:///f:/Context/numera-ui/src/services/api.ts)
- Add 401 interceptor → auto-refresh or redirect to login
- Add `X-Tenant-ID` header from auth store
- Add request/response logging in dev mode

---

### 2.2 Dashboard Page Integration

#### [MODIFY] [dashboard/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/dashboard/page.tsx)
- Replace all hardcoded `const` data with API calls:
  - `GET /api/dashboard/stats` → stat cards (Total Spreads, AI Accuracy, Avg Time, etc.)
  - `GET /api/customers/{cid}/spread-items?limit=5&sort=updatedAt` → recent spreads table
  - `GET /api/covenants/monitoring/summary` → covenant status pie chart
- Add loading skeletons (shimmer effect) during data fetch
- Add error states

#### [NEW] Backend: Expand `DashboardController.kt`
- The current [DashboardController](file:///f:/Context/backend/src/main/kotlin/com/numera/spreading/api/DashboardController.kt) only returns counts. Extend with:
  - Recent spreads (last 5, with customer name, status, accuracy, processing time)
  - AI accuracy aggregate (from spread values confidence)
  - Average processing time
  - Covenant risk count (from monitoring items)
  - Spread trend data (monthly counts for last 7 months)
  - Covenant status distribution

---

### 2.3 Customer Pages Integration

#### [MODIFY] [customers/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/customers/page.tsx)
- Replace `const customers = [...]` with `useQuery` calling `GET /api/customers`
- Wire search input to query parameter
- Wire "Add Customer" button to `POST /api/customers` mutation

#### [NEW] `numera-ui/src/services/customerApi.ts` — expand
- Add `useCustomers(query?)` hook (list with search)
- Add `useCustomer(id)` hook (single)
- Add `useCreateCustomer()` mutation
- Add `useUpdateCustomer()` mutation

#### [MODIFY] Customer detail page
- Load customer data from API
- Load customer's spread items from `GET /api/customers/{id}/spread-items`

---

### 2.4 Document / File Store Integration

#### [MODIFY] [documents/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/documents/page.tsx)
- Replace `const files = [...]` with `useQuery` calling `GET /api/documents`
- Wire drag-and-drop upload zone to `POST /api/documents` (multipart/form-data)
- Add upload progress bar
- Wire "Process" button to `POST /api/documents/{id}/process`
- Show real-time status updates (polling or WebSocket)
- Wire delete button to `DELETE /api/documents/{id}`

#### [MODIFY] [documentApi.ts](file:///f:/Context/numera-ui/src/services/documentApi.ts) — expand
- Add `useDocuments()` query hook
- Add `useUploadDocument()` mutation (multipart FormData)
- Add `useProcessDocument()` mutation
- Add `useDeleteDocument()` mutation
- Add polling for document processing status

---

### 2.5 Spreading Workspace Integration

#### [MODIFY] [spreading/[spreadId]/page.tsx](file:///f:/Context/numera-ui/src/app/spreading/[spreadId]/page.tsx)
This is the largest page (22KB). Replace mock data with:
- `useSpreadItem(spreadId)` → load spread item with values
- `useProcessSpread()` → trigger ML pipeline
- `useUpdateSpreadValue()` → inline cell editing
- `useAcceptAll()` → bulk accept by confidence
- `useSubmitSpread()` → workflow transition
- `useSpreadHistory()` → version history panel
- `useRollbackSpread()` → rollback action
- Wire PDF viewer panel to actual document rendering (via MinIO URL or backend proxy)
- Wire mapping confidence indicators to real ML confidence scores
- Wire zone tags to actual detected zones

---

### 2.6 Covenant Pages Integration

#### [MODIFY] [covenants/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/covenants/page.tsx)
- Replace mock covenant data with API calls to `GET /api/covenants/customers` and `GET /api/covenants/definitions`

#### [NEW] `numera-ui/src/services/covenantApi.ts`
- `useCovenantCustomers()` 
- `useCovenantDefinitions(customerId)`
- `useCreateCovenant()` mutation
- `useMonitoringItems()` for the monitoring dashboard
- `useWaiverRequest()` mutation

#### [MODIFY] [covenant-intelligence/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/covenant-intelligence/page.tsx)
- Wire heatmap to real monitoring data
- Wire risk predictions to `CovenantPredictionService` output

#### [MODIFY] [email-templates/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/email-templates/page.tsx)
- CRUD for email templates via `GET/POST/PUT/DELETE /api/covenants/email-templates`

---

### 2.7 Admin Pages Integration

#### [NEW] Backend: Admin module
Create `backend/src/main/kotlin/com/numera/admin/` with:
- `UserManagementController.kt` — CRUD users, assign roles
- `SystemConfigController.kt` — tenant settings, ML config
- Uses existing `auth` module's User/Role repos

#### [MODIFY] Admin frontend pages
- [admin/users/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/admin/users/page.tsx) → wire to user management API
- [admin/formulas/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/admin/formulas/page.tsx) → wire to model template formula API
- [admin/taxonomy/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/admin/taxonomy/page.tsx) → wire to IFRS taxonomy API
- [admin/workflows/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/admin/workflows/page.tsx) → wire to workflow config API

---

### 2.8 Reports Page Integration

#### [NEW] Backend: Reporting endpoints
- `GET /api/reports/spreading-summary` — aggregate spread stats with filters
- `GET /api/reports/covenant-summary` — covenant health report
- `GET /api/reports/audit-trail` — exportable audit log
- `GET /api/reports/export?format=xlsx` — Excel export (Apache POI)

#### [MODIFY] [reports/page.tsx](file:///f:/Context/numera-ui/src/app/(dashboard)/reports/page.tsx)
- Wire to reporting API
- Add export buttons (CSV, Excel)

---

### 2.9 Shared UI Components

#### [NEW] `numera-ui/src/components/ui/Toast.tsx`
- Global toast notification system for success/error/info messages
- Integrate with Radix UI Toast primitive

#### [NEW] `numera-ui/src/components/ui/LoadingSkeleton.tsx`
- Reusable shimmer loading skeletons for cards, tables, charts

#### [NEW] `numera-ui/src/components/ui/ErrorBoundary.tsx`
- Global error boundary with retry functionality
- Graceful fallback UI

#### [NEW] `numera-ui/src/components/ui/ConfirmDialog.tsx`
- Reusable confirmation dialog for destructive actions (delete, rollback)

---

## Phase 3: Backend ↔ ML Integration Validation

**Why third**: The backend already has `MlServiceClient.kt` (5.3KB) that calls the ML services. This phase ensures the integration actually works end-to-end.

### 3.1 Unified Docker Compose

#### [NEW] `docker-compose.full.yml` (root level)
A single compose file that orchestrates **all** services:
```
services:
  postgres         (backend DB)
  redis            (cache)
  minio            (storage)
  postgres-ml      (ML feedback DB, or reuse same)
  mlflow           (model registry)
  ocr-service      (Qwen3-VL / PaddleOCR)
  ml-service       (LayoutLM + SBERT)
  backend          (Spring Boot — built from ./backend)
  frontend         (Next.js — built from ./numera-ui)
```
- Shared network
- Proper `depends_on` with health checks
- Volume mounts for data persistence

### 3.2 ML Pipeline End-to-End Test

#### [NEW] `backend/src/test/kotlin/com/numera/integration/MlPipelineIntegrationTest.kt`
- Uses Testcontainers to spin up OCR + ML services
- Upload a sample PDF → trigger processing → verify zones detected → verify mappings returned
- Verify feedback submission → stored in ML feedback table

### 3.3 Verify `MlServiceClient.kt`

#### [MODIFY] [MlServiceClient.kt](file:///f:/Context/backend/src/main/kotlin/com/numera/document/infrastructure/MlServiceClient.kt)
- Verify request/response DTOs match the ML service's Pydantic models
- Add retry logic with exponential backoff (WebClient retries)
- Add circuit breaker pattern for ML service unavailability
- Add timeout configuration from `application.yml`

---

## Phase 4: ML Training Pipeline Completion

**Why fourth**: The inference services work, but models aren't trained yet. The VLM notebooks are implemented; the legacy LayoutLM/SBERT notebooks are stubs.

### 4.1 Complete Legacy Training Notebooks

#### [MODIFY] `ml-training/notebooks/01_edgar_data_collection.ipynb`
- Implement SEC EDGAR 10-K/20-F download using [edgar_downloader.py](file:///f:/Context/ml-training/scripts/edgar_downloader.py)
- Download 200+ annual reports across sectors (banking, energy, consumer goods)
- Save to Google Drive structure

#### [MODIFY] `ml-training/notebooks/02_lse_gcc_data_collection.ipynb`
- Implement London Stock Exchange annual report scraping
- Gulf Cooperation Council company filings
- Focus on IFRS-standard companies

#### [MODIFY] `ml-training/notebooks/03_xbrl_parsing_autolabeling.ipynb`
- Parse XBRL data using [xbrl_parser.py](file:///f:/Context/ml-training/scripts/xbrl_parser.py)
- Auto-generate zone labels from XBRL concept hierarchy
- Output `zone_labels.json` + `mapping_pairs.json`

#### [MODIFY] `ml-training/notebooks/04_ocr_batch_processing.ipynb`
- Batch-process collected PDFs through OCR service
- Store OCR results to Google Drive

#### [MODIFY] `ml-training/notebooks/06_zone_annotation_tool.ipynb`
- Interactive annotation tool (Colab widgets)
- Manual verification/correction of auto-labeled zones
- Export corrected labels

#### [MODIFY] `ml-training/notebooks/07_layoutlm_zone_training.ipynb`
- Fine-tune `microsoft/layoutlm-base-uncased` on zone classification
- 8 classes: INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW, etc.
- Use annotated data from notebooks 03+06
- Checkpoint to Google Drive every 30 min
- Log metrics to MLflow (DagsHub)
- Export model to MLflow registry

#### [MODIFY] `ml-training/notebooks/08_sbert_baseline_eval.ipynb`
- Evaluate base `all-MiniLM-L6-v2` on mapping task
- Compute precision/recall/F1 for line-item matching
- Establish baseline metrics

#### [MODIFY] `ml-training/notebooks/09_sbert_finetuning.ipynb`
- Fine-tune Sentence-BERT on IFRS mapping pairs
- Use XBRL-derived training data
- Contrastive learning with hard negatives
- Export to MLflow

#### [MODIFY] `ml-training/notebooks/10_ifrs_taxonomy_builder.ipynb`
- Expand [ifrs_taxonomy.json](file:///f:/Context/data/ifrs_taxonomy.json) from current 3.7KB to comprehensive ~500 terms
- Add synonyms per line item
- Cross-reference with XBRL concept map

#### [MODIFY] `ml-training/notebooks/12_export_to_mlflow.ipynb`
- Export both LayoutLM and SBERT models to MLflow
- Register as "Production" stage
- Verify models can be loaded by `ml-service`

### 4.2 Verify Training → Inference Pipeline

- Train models on Colab → export to MLflow
- ML service downloads from MLflow on startup
- Verify zone classification accuracy ≥ 85%
- Verify mapping suggestion accuracy ≥ 80%

---

## Phase 5: Production Hardening & DevOps

### 5.1 CI/CD Pipeline

#### [NEW] `.github/workflows/backend.yml`
- Trigger on push to `main` or PR
- Steps: checkout → Gradle build → run tests → Docker build → push image
- Cache Gradle dependencies

#### [NEW] `.github/workflows/frontend.yml`
- Trigger on push to `main` or PR
- Steps: checkout → npm install → lint → build → (optional) Playwright E2E
- Cache node_modules

#### [NEW] `.github/workflows/ml.yml`
- Trigger on push to `main` affecting `ml-service/` or `ocr-service/`
- Steps: checkout → pip install → pytest → Docker build

### 5.2 Backend Production Config

#### [NEW] `backend/src/main/resources/application-prod.yml`
- Production database URL (env vars)
- Redis cluster config
- JWT secret from env
- ML service URLs (internal Docker network)
- Actuator security (only health public)

### 5.3 Observability

#### [NEW] `docker-compose.monitoring.yml`
- Prometheus (scrapes backend `/actuator/prometheus`)
- Grafana dashboard (pre-built Spring Boot dashboard)
- Loki for log aggregation

### 5.4 Security Hardening

- Enable HTTPS via reverse proxy (nginx/traefik)
- Add rate limiting to auth endpoints
- Add CORS whitelist for production domains
- Rotate JWT secrets
- Add Content Security Policy headers

---

## Phase 6: Redis Caching & WebSocket

### 6.1 Redis Cache Integration

#### [MODIFY] Backend services
- Cache model template line items (rarely change)
- Cache IFRS taxonomy
- Cache dashboard stats (TTL: 5 min)
- Add `@Cacheable` annotations to `TemplateService`, `DashboardController`

### 6.2 WebSocket Real-Time

#### [NEW] `backend/src/main/kotlin/com/numera/shared/config/WebSocketConfig.kt`
- STOMP WebSocket configuration
- Topic: `/topic/spreads/{spreadId}` — real-time value updates
- Topic: `/topic/documents/{docId}/status` — processing progress

#### [MODIFY] Frontend: Add WebSocket client
- Subscribe to spread updates during collaborative editing
- Subscribe to document processing status for live progress

---

## Effort Estimates

| Phase | Scope | Estimated Effort | Dependencies |
|---|---|---|---|
| **Phase 1** | Backend tests + infra | ~3–4 days | None |
| **Phase 2** | Frontend ↔ Backend integration | ~5–7 days | Phase 1 |
| **Phase 3** | Backend ↔ ML validation | ~2–3 days | Phase 1 |
| **Phase 4** | ML training notebooks | ~5–7 days (Colab time) | Phase 3 |
| **Phase 5** | CI/CD + production | ~2–3 days | Phases 1–3 |
| **Phase 6** | Redis + WebSocket | ~2–3 days | Phase 2 |
| **Total** | | **~20–27 days** | |

---

## Verification Plan

### Automated Tests
- `./gradlew test` — all backend unit + integration tests pass
- `cd ml-service && pytest` — all ML service tests pass
- `cd ocr-service && pytest` — all OCR service tests pass
- `cd numera-ui && npm run build` — frontend builds without errors

### Integration Verification
- `docker compose -f docker-compose.full.yml up` — all 9 services start and become healthy
- Login flow: browser → frontend → backend auth → JWT → dashboard with real data
- Document upload: frontend upload → MinIO storage → ML processing → zones displayed
- Full spread: create customer → upload PDF → process → spreading workspace with ML suggestions → accept/edit → submit → version history
- Covenant monitoring: create covenant → link to spread → monitoring calculation → breach detection

### Manual Verification
- Navigate every frontend page — no mock data, all real API responses
- Swagger UI (`/swagger-ui.html`) — all endpoints documented and testable
- MLflow UI (port 5000) — trained models visible after Phase 4

---

## Open Questions

> [!IMPORTANT]
> 1. **Which phases to tackle first?** Should we start with Phase 1 (backend tests) or jump to Phase 2 (integration) for a faster demo?
> 2. **Legacy vs VLM training**: Invest in completing the 8 stub notebooks, or focus solely on Qwen3-VL path?
> 3. **Deployment target**: Where will this be deployed (cloud VPS, Azure, AWS, self-hosted)? This affects Phase 5 configuration.
> 4. **Seed data**: Should we create realistic demo seed data (sample customers, documents, spreads) for Phase 2 integration testing?
