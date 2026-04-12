# Autonomous Missing-Work Implementation Plan (Enterprise Delivery)

Date: 2026-04-11  
Workspace: f:/Context

## 1. Objective

This plan defines the remaining work to move from current implementation to full enterprise delivery described in:
- application_specification.md
- IPRD/IPimplementation_plan.md

The plan is intentionally agent-executable:
- dependency ordered
- API-contract safe
- feature-flag guarded
- test-gated at each wave

## 2. Current Standing (Verified Snapshot)

## 2.1 Foundation already in place

- Backend domain modules exist: auth, admin, customer, document, spreading, covenant, reporting.
- Core spreading flow exists: process, values, submit, approve/reject, version history, diff, rollback.
- Subsequent spreading primitives exist: base-period discovery and autofill endpoints.
- Exclusive locking exists with Redis-backed lock service and lock endpoints.
- OAuth/OIDC + MFA structures exist (migrations, services, APIs).
- Group-based customer visibility structures exist (migrations, admin service/controller, repository filters).
- Taxonomy + exclusion management APIs/services exist (including import/export plumbing).
- OCR and ML services are production-shaped and wired from backend via MlServiceClient.
- IP partial implementation exists:
  - IP-2 NG-MILP: present (ml-service/app/ml/ng_milp)
  - IP-5 STGH: present (ocr-service/app/ml/stgh + ml-service fingerprint API)
  - Covenant prediction API exists (legacy statistical predictor, not RS-BSN).
- CI pipeline exists in .github/workflows/ci.yml for backend/frontend/ocr/ml build-test-image.

## 2.2 Major gaps still open (enterprise-critical)

1. Workflow engine is still in-memory CRUD (not BPMN/Camunda/Flowable runtime).
2. External integrations (CreditLens/nCino/Finastra adapters) are not implemented.
3. Event architecture is app-local events; no Kafka/RabbitMQ event backbone.
4. Audit cryptography still uses SHA-256 chain only (IP-3 ZK-RFA missing).
5. IP-1/IP-4/IP-6/IP-7 core modules not implemented:
   - H-SPAR, FSO, RS-BSN, OW-PGGR missing.
6. ML training system is not fully operationalized for enterprise lifecycle:
   - reproducible training/evaluation/promotion workflow needs hardening.
7. Frontend-backend real-time collaboration path is incomplete:
   - backend uses STOMP/SockJS; frontend websocket client currently uses raw ws semantics.
8. Covenant intelligence is still heuristic-heavy:
   - predictive engine and calibrated confidence/reporting need enterprise model.
9. Production deployment hardening still incomplete:
   - K8s/Helm/multi-region/data-sovereignty operationalization not done.

## 3. Delivery Principles (must be enforced)

- Preserve existing public API contracts unless explicitly versioned.
- Every new capability must be behind a feature flag with safe fallback.
- No cross-service schema drift: OpenAPI/Pydantic/Kotlin DTOs updated together.
- Each wave ends with:
  - green unit/integration tests
  - migration verification
  - smoke test in docker-compose.full.yml
- Use additive DB migrations only; no destructive migrations in active rollout.
- Keep legacy paths live until parity tests pass for 2 consecutive releases.

## 4. Autonomous Execution Graph

Wave A -> Wave B -> Wave C -> Wave D -> Wave E -> Wave F

- Wave A: Contract and platform hardening
- Wave B: Workflow and event backbone
- Wave C: IP completion (core missing IPs)
- Wave D: Covenant intelligence + anomaly + audit upgrades
- Wave E: External adapters and enterprise controls
- Wave F: Production scale, observability, and release readiness

## 5. Detailed Missing-Work Plan

## Wave A - Contract and Platform Hardening (2-3 weeks)

### A1. Canonical API Contract Freeze

Scope:
- Generate and freeze canonical schemas for backend + ml-service + ocr-service.

Tasks:
1. Export OpenAPI specs for backend, ml-service, ocr-service.
2. Create contract snapshots under docs/contracts/.
3. Add contract-diff CI step to fail breaking changes.

Files:
- backend OpenAPI config files
- ml-service/app/main.py, ocr-service/app/main.py
- .github/workflows/ci.yml
- docs/contracts/*.json

Done when:
- Contract snapshots committed.
- CI fails on breaking response/request changes.

### A2. Frontend Realtime Protocol Alignment

Problem:
- Backend: STOMP/SockJS topic model.
- Frontend: native WebSocket JSON client.

Tasks:
1. Replace frontend ws client with STOMP-over-SockJS client.
2. Subscribe to:
   - /topic/spread/{id}/lock
   - /topic/spread/{id}/values
   - /topic/tenant/{tenantId}/documents
   - /topic/tenant/{tenantId}/covenants
3. Add reconnect/auth handshake logic and message normalization.

Files:
- numera-ui/src/services/websocket.ts
- numera-ui/src/hooks/useWebSocket.ts
- spreading/covenant/document pages using live updates

Done when:
- Lock changes and covenant updates render in UI in real time.

### A3. End-to-End Test Harness Completion

Tasks:
1. Add API integration suites for locking, submit->covenant trigger, waiver, reminders.
2. Add frontend e2e smoke flows (login, upload, process, submit, approve).
3. Add compose-based CI nightly e2e stage.

Files:
- backend/src/test/kotlin/**
- numera-ui e2e test dir (new)
- .github/workflows/ci.yml (nightly job)

Done when:
- CI has deterministic end-to-end smoke gates.

---

## Wave B - Workflow Engine + Event Backbone (3-4 weeks)

### B1. Replace In-Memory Workflow with BPMN Runtime

Current gap:
- WorkflowConfigController is in-memory list; no executable workflow runtime.

Tasks:
1. Introduce workflow-runtime module with Camunda/Flowable adapter.
2. Persist workflow definitions, versions, states, tokens.
3. Replace hardcoded spread approval transitions with runtime-driven transitions.
4. Add SLA and escalation job handlers.

Files:
- backend/src/main/kotlin/com/numera/admin/WorkflowConfigController.kt (refactor)
- new workflow runtime package under backend/src/main/kotlin/com/numera/workflow/**
- DB migrations: workflow_definitions, workflow_instances, workflow_tasks, workflow_events

Done when:
- Spread and covenant workflows execute through BPMN runtime with audit trail per step.

### B2. Event Backbone (Kafka or RabbitMQ)

Current gap:
- Spring application events only.

Tasks:
1. Introduce event publisher abstraction.
2. Emit events on:
   - spread submitted
   - spread approved/rejected
   - covenant status changes
   - document processed
3. Add consumer workers:
   - covenant recalculation
   - notification dispatch
   - analytics materialization
4. Keep existing in-process listeners as fallback under flag.

Files:
- backend shared events + listeners
- infra compose additions for broker
- application.yml event config

Done when:
- Broker-backed events drive recalculation and notifications asynchronously.

---

## Wave C - Missing IP Core Modules (4-6 weeks)

### C1. IP-1 H-SPAR Knowledge Graph

Missing:
- hspar package and KG API.

Tasks:
1. Implement ml-service/app/ml/hspar/{graph_builder.py,knowledge_graph.py,models.py}.
2. Add /ml/knowledge-graph/build and /ml/knowledge-graph/{entity} APIs.
3. Wire pipeline endpoint to optional graph build step.
4. Add backend migration for knowledge_graph storage and repository/service.

Dependencies:
- NG-MILP expression outputs (existing).

Done when:
- Graph builds from extraction, persists per entity, and can be retrieved.

### C2. IP-7 OW-PGGR Anomaly Detection

Missing:
- owpggr package and backend storage pipeline.

Tasks:
1. Implement ml-service/app/ml/owpggr/{detector.py,materiality.py,models.py}.
2. Add /ml/anomaly/detect endpoint.
3. Extend backend spread completion path to request anomaly report and persist.
4. Add migration for anomaly fields in spreads.

Done when:
- Spread submissions produce materiality-aware anomaly reports.

### C3. IP-4 FSO Federated Learning

Missing:
- fso package and training orchestration.

Tasks:
1. Implement ml-service/app/ml/fso/{aggregator.py,trainer.py,models.py}.
2. Add /ml/federated/train-round and /ml/federated/status.
3. Integrate resolver/model manager with global update application.
4. Add tenant privacy audit metrics and guardrails.

Done when:
- Multi-tenant federated rounds run with orthogonality/privacy checks.

### C4. IP-6 RS-BSN Covenant Predictor

Missing:
- rsbsn package and full backend switch-over.

Tasks:
1. Implement ml-service/app/ml/rsbsn/{model.py,regime_hmm.py,models.py}.
2. Add /ml/covenants/predict and /ml/covenants/predict/batch.
3. Update CovenantPredictionService to use RS-BSN response model under feature flag.
4. Add calibration and regime-detection tests.

Done when:
- Backend covenant probability comes from RS-BSN with confidence intervals.

---

## Wave D - Audit, Compliance, and Intelligence Upgrade (3-4 weeks)

### D1. IP-3 ZK-RFA Audit Upgrade

Current gap:
- HashChainService remains plain SHA-256.

Tasks:
1. Add crypto package:
   - ChameleonHash
   - MerkleAccumulator (MMR)
2. Expand AuditEvent model and migration fields for chameleon randomness/MMR proof metadata.
3. Add redaction workflow endpoint and inclusion-proof verification endpoint.
4. Integrate key provider (Vault/KMS) abstraction.

Files:
- backend/src/main/kotlin/com/numera/shared/audit/**
- backend/src/main/resources/db/migration/V0xx__zkrfa_audit.sql
- backend build.gradle.kts (crypto deps)

Done when:
- Redaction preserves chain validity and inclusion proofs verify in O(log n).

### D2. Covenant Intelligence Full Loop

Tasks:
1. Trigger covenant recompute + RS-BSN prediction from broker event on spread submit.
2. Materialize risk heatmap model for dashboard queries.
3. Add threshold-band trendline API.
4. Add overdue/breach auto-actions with workflow state transitions.

Done when:
- Dashboard reflects near-real-time risk and status transitions after submission.

---

## Wave E - External Integrations + Enterprise Controls (4-5 weeks)

### E1. Adapter Framework

Tasks:
1. Create adapter SPI in backend integration module.
2. Define canonical spread payload contract and mapper layer.
3. Implement adapters:
   - CreditLens (priority)
   - one generic REST adapter template
4. Implement outbound idempotency keys + retry policy + dead-letter queue.

Done when:
- One production-grade external adapter can push/pull metadata and values.

### E2. Two-Way Sync and Restatement Governance

Tasks:
1. Map override/duplicate restatement flows to external statement identifiers.
2. Add sync conflict resolver strategy.
3. Add UI reconciliation queue for mismatches.

Done when:
- External sync is auditable, retryable, and restatement-safe.

### E3. Admin Enterprise Controls

Tasks:
1. Complete policy UX/API for:
   - password policy
   - session concurrency limits
   - language enable/disable
   - role/action/data-level permissions matrix
2. Add tenant-level feature flag management UI/API.

Done when:
- Tenant admins can manage controls without code changes.

---

## Wave F - Production Readiness and Scale (3-4 weeks)

### F1. Kubernetes/Helm and Multi-Env Promotion

Tasks:
1. Create helm charts for backend, ui, ml-service, ocr-service, broker, redis, postgres dependencies.
2. Add environment overlays: dev/staging/prod/on-prem baseline.
3. Add blue/green or canary for model-serving services.

Done when:
- One-command deploy to staging and reproducible release promotion.

### F2. Observability and SLOs

Tasks:
1. Instrument end-to-end traces across UI->backend->ml/ocr.
2. Add Prometheus dashboards and alerts for:
   - spread processing latency
   - mapping coverage and confidence
   - covenant prediction latency/error
   - queue lag and consumer failures
3. Define SLO error budgets and runbooks.

Done when:
- Operational dashboards and alerts are actionable for on-call.

### F3. Security and Compliance Readiness

Tasks:
1. Secrets management hardening (no default secrets in runtime config).
2. Dependency and container scanning gates in CI.
3. Data retention/deletion/export workflows validation.
4. Evidence bundle generation for SOC2/ISO pre-audit.

Done when:
- Security/compliance checklist is green for pilot onboarding.

## 6. Cross-Cutting Change Control (for autonomous agents)

Each task execution must include:

1. Preconditions
- Related feature flag exists and defaults to false.
- DB migration id reserved and ordered.
- Contract snapshot updated if any payload changes.

2. Implementation sequence
- Domain model -> repository -> service -> API -> tests -> docs.
- For frontend-dependent changes: backend first, then frontend wiring.

3. Validation gates
- Unit tests pass.
- Integration tests for modified module pass.
- docker-compose.full.yml smoke flow passes.
- No new lint/type errors.

4. Rollback readiness
- Feature can be disabled by config.
- Data migrations are backward compatible.

## 7. Agent Work Package Template (copy for every ticket)

Use this exact template per implementation ticket:

- Goal:
- Inputs (spec refs, files):
- Feature flag:
- API contracts touched:
- DB migrations:
- Files to create/modify:
- Step-by-step implementation:
- Automated tests to add/update:
- Manual verification:
- Rollback plan:
- Completion evidence:

## 8. Priority Backlog (Missing Work Only)

P0 (start immediately)
1. Workflow runtime replacement (Wave B1)
2. Event backbone introduction (Wave B2)
3. RS-BSN implementation and covenant integration (Wave C4)
4. OW-PGGR anomaly detection path (Wave C2)
5. Frontend STOMP/SockJS alignment (Wave A2)

P1
1. H-SPAR knowledge graph (Wave C1)
2. ZK-RFA audit upgrade (Wave D1)
3. CreditLens adapter MVP (Wave E1)
4. Observability/SLO dashboards (Wave F2)

P2
1. FSO federated learning (Wave C3)
2. Full multi-region/on-prem packaging (Wave F1)
3. Compliance evidence automation (Wave F3)

## 9. Suggested Autonomous Execution Order (No-Stop Chain)

Execute in this exact order to avoid mismatch:

1. A1 -> A2 -> A3
2. B1 -> B2
3. C4 -> C2 -> C1 -> C3
4. D1 -> D2
5. E1 -> E2 -> E3
6. F1 -> F2 -> F3

Reasoning:
- Contract/test stability first.
- Runtime orchestration and async backbone before heavy ML capability rollout.
- Predictive + anomaly features before external adapter commitments.
- Deployment and compliance last for stabilization.

## 10. Acceptance Criteria for Enterprise Delivery Completion

Enterprise delivery is complete only when all are true:

- All Waves A-F marked done with evidence.
- IP modules complete: 1 through 7 behind controllable flags.
- Workflow engine is BPMN runtime backed, not in-memory.
- Event-driven recalculation and notification path runs on broker.
- One external system adapter in production-grade operation.
- SLO dashboards and on-call alerts active.
- Security/compliance pre-audit checklist closed.
- Pilot UAT script passes end-to-end with <3 minute spread cycle and traceable audit/version history.
