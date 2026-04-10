# Product Specification: AI-First Financial Spreading & Covenant Intelligence Platform

> **Codename**: TBD  
> **Version**: 1.0 — Draft Specification  
> **Date**: April 2026  
> **Status**: Awaiting Review

---

## 1. Product Vision & North Star

### 1.1 Vision Statement
Build a **fully autonomous financial spreading platform** that replaces the manual, time-intensive workflows of legacy tools (Spreadsmart, etc.) with an AI-first experience. An analyst uploads a PDF; the system returns a production-ready spread. The human reviews. The machine does the work.

### 1.2 North Star Metric
> *"What takes a skilled analyst 30-45 minutes on the competitor's platform should complete in under 3 minutes with 90%+ accuracy on ours — with the analyst only reviewing, not creating."*

### 1.3 Key Differentiators vs. Competitor (Spreadsmart)

| Dimension | Competitor (Spreadsmart) | Our Platform |
|---|---|---|
| **Spreading** | Semi-manual: analyst draws zones, clicks to map values, builds expressions by hand | **Fully autonomous**: AI extracts, identifies, maps, and builds expressions end-to-end |
| **Table Detection** | Rule-based auto-detect + manual draw for misses | **Vision-model powered**: zero manual zone drawing; manual override only as fallback |
| **Autofill** | Rule-based: match zone count → row name → taxonomy synonym fallback | **ML-driven**: model learns from corrections, predicts cell mappings with confidence scores |
| **Model Engine** | Locked to Moody's CreditLens; no independent model ownership | **Own model engine** with pluggable integrations to CreditLens, nCino, Finastra, etc. |
| **Covenants** | CRUD tracker with static status management | **Predictive intelligence**: probability-based breach forecasting, real-time triggers |
| **Workflow** | Hard-coded maker/checker pattern | **Configurable BPMN-style engine** with custom approval hierarchies per client |
| **Audit Trail** | Basic change logs | **Git-like version control** for every spread — full diff, rollback, branch history |
| **Analytics** | Export-only (Excel/PDF/HTML) | **Live dashboards**, portfolio analytics, **natural language querying** |
| **Deployment** | Single-tenant, on-prem dependent | **Hybrid**: cloud-native SaaS + private cloud/on-prem deployment options |
| **Collaboration** | No locking; conflict-prone | **Exclusive locking**: one analyst edits, all others see read-only with live status |

---

## 2. Target Market & User Roles

### 2.1 Primary Buyers
- **International Banks** (primary)
- Non-Banking Financial Companies (NBFCs)
- Private Equity / Venture Capital firms
- Credit Rating Agencies
- Audit & Advisory firms

### 2.2 User Roles & Permissions (RBAC)

| Role | Core Capabilities |
|---|---|
| **System Admin** | Tenant configuration, user management, SSO/Auth setup, global settings, AI model management, data residency config |
| **Analyst (Maker)** | Upload documents, trigger AI spreading, review & correct AI-generated spreads, submit spreads, upload covenant compliance documents, trigger covenant breach actions |
| **Manager (Checker)** | Approve/reject spreads, verify covenant compliance documents, waive/not-waive covenant breaches, configure formula libraries, manage email/letter templates, view portfolio dashboards |
| **Global Manager** | Cross-portfolio visibility, configure workflow rules, manage global taxonomy & model templates, access all MIS reports, manage signature & letter configurations |
| **Auditor (Read-Only)** | View all spread history, audit trails, covenant status, and generated reports — no edit capability |

> [!IMPORTANT]
> Roles must be **fully configurable per tenant**. A bank may want 7 approval levels; a PE firm may want 2. The system must not hard-code role hierarchies.

---

## 3. Core Modules — Detailed Functional Specification

---

### 3.1 Authentication & Access Control

#### 3.1.1 Authentication Methods
- **SSO Integration**: SAML 2.0 and OpenID Connect (OIDC) for enterprise identity providers (Azure AD, Okta, Ping Identity, ADFS).
- **Form-Based Authentication**: Username/password with configurable password policies (complexity, expiry, history) for organizations without SSO.
- **Multi-Factor Authentication (MFA)**: TOTP-based (Google Authenticator, Microsoft Authenticator) mandatory for admin roles, configurable for others.

#### 3.1.2 Access Control
- Configurable RBAC with permission granularity (module-level, action-level, data-level).
- **Group-based access** linking users to business portfolios/customer groups — users see only their assigned customers.
- **Is RM (Reporting Manager)** flag to activate Checker capabilities for Manager roles.
- Session management: configurable timeout, concurrent session limits, forced logout.

#### 3.1.3 User Lifecycle
- Self-registration with admin approval workflow.
- Bulk user provisioning via CSV/API.
- Account states: Pending Approval → Active → Inactive → Rejected.

---

### 3.2 Financial Spreading Module (Core Engine)

This is the heart of the platform. The fundamental paradigm shift is: **AI does the spreading; humans review and correct.**

#### 3.2.1 Document Ingestion Pipeline

```
Upload → Pre-processing → OCR → Table Extraction → Zone Classification → Value Mapping → Expression Building → Confidence Scoring → Analyst Review
```

**Step 1: Upload & Pre-Processing**
- Supported formats: PDF, Word (.docx), Images (JPEG/PNG/TIFF), Excel (.xlsx/.xls).
- Multi-file upload: Multiple documents for a single financial period are merged into one processing unit.
- Password-protected document handling with secure password entry.
- **File Store** for bulk pre-processing:
  - Users upload documents in off-hours.
  - System processes (OCR + AI classification) in the background.
  - Pre-processed files are ready when analysts start their workday.
  - File states: Uploaded → Processing → Ready → Mapped → Error.
  - Three views: My Files, All Files (group), Error Files.
- Language detection and multi-language support (English, Arabic RTL, French, etc.).
- **Clean File** post-processing: despeckle (low/high), watermark removal, deskew.

**Step 2: AI-Powered OCR**
- **PaddleOCR** as the primary engine for text and table extraction.
- Layout analysis: automatic detection of columns, rows, merged cells, headers, footnotes.
- **Visual cue correction**: if OCR misreads a value, the system highlights it in red; the analyst can correct it in-place. Corrections persist across subsequent periods.
- Support for handwritten annotations (future phase).

**Step 3: Autonomous Zone & Table Detection**
- **Vision ML model** automatically identifies all financial tables in the document and classifies them:
  - Main Tables: Income Statement, Balance Sheet, Cash Flow Statement.
  - Base Adjustments: Notes to accounts (e.g., Fixed Assets breakdown, Receivables aging).
  - Other Adjustments: Segment reporting, subsidiary data, off-balance sheet items.
- Each detected table is assigned:
  - Zone classification (with confidence score).
  - Account column identification.
  - Value column identification (with period/year detection).
  - Header row identification (for matrix zones).
- **Year/Period Detection**: The ML model reads column headers and independently identifies reporting periods (e.g., "FY2024", "Q3 2025", "6 months ended 30 June 2024").
- **Zero manual zone drawing required** — manual override provided only as a fallback:
  - Analyst can adjust zone boundaries if AI misses a table.
  - Analyst can draw a new zone for tables the AI entirely missed.
  - Analyst can split/merge tables, add/remove columns.

> [!TIP]
> The competitor requires analysts to manually draw bounding boxes, tag zones, set value columns, and set header rows for every table. Our system must do ALL of this autonomously.

**Step 4: Autonomous Value Mapping & Expression Building**
- The ML model maps each row in each detected table to the corresponding row in the target financial model.
- Mapping logic:
  1. **Semantic matching**: The model understands that "Turnover" = "Revenue" = "Net Sales" without needing a pre-configured taxonomy.
  2. **Structural matching**: Position in the table, proximity to subtotals, indentation level.
  3. **Historical learning**: If this customer was spread before, use previous mappings as strong priors.
  4. **Taxonomy fallback**: Global keyword/synonym library as the last-resort matcher.
- **Expression Building**: When a model cell requires combining multiple source values (e.g., "Property, Plant & Equipment" from the Balance Sheet adjusted by the Fixed Assets note), the system auto-constructs the expression with correct operators (+, -, ×, ÷).
- **Adjustment Factors** auto-applied:
  - Unit Scale conversion (actual → thousands → millions).
  - Absolute/Negative/Contra transformations.
  - Currency detection and notation.

**Step 5: Confidence Scoring & Analyst Review**
- Every mapping receives a **confidence score** (0-100%).
  - ≥90%: Auto-accepted (green highlight).
  - 70-89%: Suggested but flagged for review (amber highlight).
  - <70%: Unresolved, requires manual input (red highlight).
- The analyst opens the **Review Workspace** (dual-pane view):
  - Left pane: Document viewer with overlaid zone highlights and mapped value indicators.
  - Right pane: Financial model grid with confidence-coded cells.
- Analyst actions:
  - **Accept** a mapping (one-click).
  - **Reject & Remap** — click a different source value and the system rebuilds the expression.
  - **Edit Expression** — manually adjust operators or add constants.
  - **Accept All** — bulk accept all green-confidence mappings.
- **Every correction feeds back into the ML model** for continuous improvement.

#### 3.2.2 Financial Model Engine (Owned)

The platform owns its financial model layer, unlike the competitor which depends on CreditLens.

- **Global Model Templates**: Pre-configured for common international standards:
  - IFRS-based models.
  - US GAAP-based models.
  - Region-specific models (e.g., Central Bank of UAE reporting requirements).
- **Model Structure**:
  - Hierarchical line items with parent-child grouping.
  - Formula cells (computed), input cells (mapped), and validation cells.
  - Support for grouping, hiding, freezing rows.
  - Category navigation bubbles for quick jump between sections (Assets → Liabilities → Equity).
- **Customer Model Copies**: Global models are copied per customer; customer-specific modifications don't affect the global template.
- **Model Versioning**: Track changes to model structure over time.

#### 3.2.3 External System Integrations (Pluggable Adapters)

- **Integration Adapter Architecture**: The platform exposes a standardized output format. Adapters transform this into vendor-specific formats.
- Supported targets (build adapters incrementally per client demand):
  - Moody's CreditLens (2-way sync: push spreads, pull models/metadata/historical data).
  - nCino.
  - Finastra.
  - S&P Capital IQ.
  - Custom bank-internal systems via configurable API adapters.
- **2-Way Sync** (for CreditLens-type integrations):
  - Metadata sync: Statement Date, Audit Method, Frequency, Currency, Consolidation.
  - Value sync: Spread values pushed to external system; external changes pulled back.
  - Unit rounding: Values rounded to entity unit before push.
  - Retained Earnings fetch: Pull R/E values from external system before final submission.

#### 3.2.4 Spreading Workspace — UI Features

**Document Viewer (Left Pane)**
- High-fidelity PDF/image rendering with zoom, pan, rotate.
- Zone overlay visualization: color-coded bounding boxes per zone type.
- Page navigation, page merging, page splitting (book-view → single-page).
- Split view: Two document panes for mapping from different pages simultaneously.
- Document download via Links button.
- Detected value highlights: Clickable overlays on detected numbers.

**Model Grid (Right Pane)**
- Spreadsheet-style grid with formula support.
- Confidence-coded cell highlighting (green/amber/red).
- Expression editor with visual formula builder.
- Show/Hide unmapped rows.
- Variance column: Side-by-side comparison with base period, highlighting major variances.
- Currency & Unit display columns.
- Category bubble navigation.
- Load up to 20 historical spread periods for comparison.

**Shared Features**
- **Exclusive Locking**: When Analyst A opens a spread for editing, all other users see it as read-only with a banner: *"Currently being edited by [Analyst A]. View mode only."*
- **Validation Engine**: Real-time balance checks (Assets = Liabilities + Equity). Validation panel shows Passed/Failed with difference values.
- **Auto-Generated Comments**: For every mapped cell, the system records: source PDF name, page number, line-item name, extracted value, and a clickable URL that navigates directly to the source location in the document.
- **CL Notes**: Free-text notes per spread item, synced to external systems.

#### 3.2.5 Subsequent Spreading (Period-over-Period)

- System auto-selects the most recent same-frequency spread as the **base period**.
- AI autofill uses base period mappings as strong priors:
  - Same zone structure → auto-map with high confidence.
  - Changed zone structure (e.g., new line items) → semantic matching with lower confidence, flagged for review.
- **Duplicate cell handling**: Same line-item name appearing in different sections (e.g., "Other" in Current Assets vs. "Other" in Liabilities) — system uses zone context to disambiguate.
- Analyst can override base period selection.
- **Submit & Continue**: After submitting one period, immediately start spreading the next period from the same multi-period document.

#### 3.2.6 Spread Lifecycle & Version Control

**States**: Draft → Submitted → Approved → Pushed (to external system).

**Version Control (Git-like)**:
- Every save creates an immutable snapshot.
- Full diff view between any two versions.
- Rollback to any previous version.
- Audit trail: who changed what, when, and why (via mandatory comments on submission).
- **Override vs. Duplicate** for re-submitting:
  - Override: Update the same statement ID in the external system.
  - Duplicate: Create a new restated statement (Restated 1 … Restated 5).

---

### 3.3 Taxonomy & Learning System

#### 3.3.1 Global Taxonomy
- Keyword/synonym dictionary organized by **taxonomy groups** (e.g., "IFRS Banking Model", "US GAAP Corporate").
- Each keyword maps to one or more **Global Zones** (Balance Sheet, Income Statement, etc.).
- Bulk upload/download via Excel templates with pipe-separated synonyms.
- Multi-language support per taxonomy group.

#### 3.3.2 Exclusion List
- Configurable list of terms the AI should ignore when reading line items:
  - Categories: Prefix, Suffix, Superscript, Subscript, Punctuation, Spaces, Text Removal, Dots, Dates, Numbers, Brackets.
  - Example: "Note 5: Revenue from operations (continued)" → cleaned to "Revenue from operations".
- Global exclusion lists managed by admins; customer-specific overrides allowed.

#### 3.3.3 Continuous Learning Pipeline
- Every analyst correction (remapping, expression edit, zone adjustment) is captured as a training signal.
- **Feedback loop**: Corrections are batched and periodically used to retrain/fine-tune the ML models.
- **Cold-start strategy**: Initial models trained on publicly available financial statements (SEC EDGAR filings, annual reports from public companies). As clients onboard and use the system, their corrections progressively improve model accuracy for their specific document styles.
- **Per-client model specialization**: After sufficient client-specific training data accumulates, the system can fine-tune a client-specific model variant while retaining the global model as a fallback.

#### 3.3.4 AI Model Training Interface (Admin/Manager)
- **Online Training**: Tag tables in financial reports as Global Zones; system enriches to predict similar tables for all customers.
- **Offline Training**: Upload tagged data via Excel for batch enrichment.
- **Trained Data Dashboard**: View active Global Zones, ML engine status, accuracy metrics, and retraining history.

---

### 3.4 Covenants Module

#### 3.4.1 Covenant Customer Management
- Create covenant customers with:
  - Basic Info: Customer Name, RIM ID, CL Entity ID, Financial Year End.
  - Additional Info: Internal users (system-registered) and External users (email contacts) for notifications.
- Customer listing with search (by RIM ID, Customer Name), edit, activate/deactivate.

#### 3.4.2 Covenant Definition

**Financial Covenants**:
- Link to model line items via formula builder.
- Define threshold conditions (≥, ≤, =, range).
- Configurable monitoring frequency: Monthly, Quarterly, Semi-annually, Annually, FY-To-Date.
- Auto-generation of monitoring items based on frequency and year-end.
- Skip-overlap logic: If annual covenant exists, Q4 quarterly is auto-skipped for same audit method.

**Non-Financial Covenants**:
- Document-type obligations (e.g., "Submit audited financial statements").
- Custom item types: Select from predefined list or "Other" with free description.
- Same frequency options as financial covenants.
- **Financial Statement Auto-Approval**: Non-financial items of type "Financial Statement" are automatically marked Approved when the corresponding spread is submitted.

#### 3.4.3 Formula Management
- Visual **Formula Builder** using model line items.
- Standard formulas applicable across multiple customers/groups.
- Operators: +, -, ×, ÷, with parenthetical grouping.
- Formulas are per-period (cross-period formulas not supported).
- Formula audit trail: track all changes since creation.
- Active/Inactive toggle; soft delete with audit log.

#### 3.4.4 Monitoring & Status Engine

**Financial Covenant Statuses**:
| Status | Definition |
|---|---|
| Due | System-generated item; calculated value not yet available |
| Overdue | Due date passed; value still unavailable |
| Met | Value available and within threshold |
| Breached | Value violates threshold |
| Trigger Action | Analyst triggers breach for Manager action |
| Closed | Waived or Not-Waived letter sent; no further action |

**Non-Financial Covenant Statuses**:
| Status | Definition |
|---|---|
| Due | Documents not yet submitted |
| Submitted | Maker submitted documents for Checker verification |
| Approved | Checker approved documents |
| Rejected | Checker rejected; maker must resubmit |
| Overdue | Due date passed; documents not approved |
| Breached | Checker marked as breach |
| Closed | Waived/Not-Waived; no further action |

**Views**:
- All Covenants tab (every item regardless of status).
- Pending Covenants tab (Due, Overdue, Rejected items requiring action).
- Violated/Breached Covenants tab.

**Manual Value Override**: If the system-calculated value needs adjustment, analysts can input a manual value with mandatory justification.

#### 3.4.5 Predictive Covenant Intelligence

> [!IMPORTANT]
> This is a major differentiator. The competitor only tracks current status. Our system **predicts future breaches**.

- **Breach Probability Forecasting**: Based on historical financial trends, the system calculates the probability of each covenant breaching in future periods.
  - Example output: *"Debt/EBITDA covenant has a 78% probability of breaching in Q4 2026 based on trailing 4-quarter revenue decline and increasing leverage."*
- **Early Warning Dashboard**: Visual risk heatmap showing all covenants color-coded by breach probability.
- **Trend Analysis**: Track covenant metrics over time with trendline charts and threshold bands.
- **Real-Time Triggers**: The moment a spread is submitted, the system immediately:
  1. Recalculates all affected covenant values.
  2. Updates statuses (Met/Breached/Due).
  3. Re-runs breach probability forecasts.
  4. Sends notifications if status changed or risk increased.

#### 3.4.6 Document Verification Workflow (Non-Financial)
- Maker uploads compliance documents against a covenant item.
- Checker receives notification and navigates to verification screen:
  - View item metadata (read-only).
  - Preview, download, upload additional documents.
  - Add comments.
  - **Approve** or **Reject** with mandatory comments.
- Rejected items return to Maker with Checker's comments for resubmission.

#### 3.4.7 Waiver / Not-Waiver Workflow

- Triggered on Breached or Overdue items by Manager users.
- **Waive**:
  - Select instance-only (single period) or permanent waiver.
  - Provide comments/justification.
  - View history of previously generated documents for this covenant.
  - Select a pre-configured waiver letter template.
  - Select/add recipient contacts (internal + external).
  - **Generate Document**: System auto-populates letter template with covenant data, customer info, and selected dynamic fields.
  - Preview, Edit, Print.
  - **Send**: Via system email or download for manual Outlook delivery.
- **Not-Waive**: Same flow without instance/permanent selection.
- Item moves to "Closed" status after letter is sent.

#### 3.4.8 Email Template & Signature Management
- Rich text **letter template editor** with drag-and-drop dynamic field tags.
- Field list: Auto-populated tags for customer name, covenant details, values, dates, etc.
- Template categorization by covenant type (Financial / Non-Financial).
- Active/Inactive toggle, audit trail, delete.
- **Signature Management**: Standard signatures created by Managers, auto-appended to generated letters.

#### 3.4.9 Automated Email Notifications
- Configurable reminder schedule: X days before due date, Y days after (overdue).
- Recipients: Configured RMs and customer-mapped email addresses.
- Templates for due, overdue, breach, and approval notifications.

---

### 3.5 Configurable Workflow Engine

> [!IMPORTANT]
> The competitor hard-codes a Maker → Checker flow. Our platform must support **arbitrary approval hierarchies** configured per client.

- Visual **workflow designer** (BPMN-inspired) for defining:
  - Approval chains: Analyst → Senior Analyst → Manager → Head of Credit.
  - Conditional routing: If spread value > $10M, require additional VP approval.
  - Parallel approvals: Two department heads must approve simultaneously.
  - Escalation rules: Auto-escalate if not actioned within N hours.
  - SLA timers: Track time-to-completion per workflow step.
- Separate workflow configurations for:
  - Spread submission & approval.
  - Non-financial covenant document verification.
  - Covenant waiver processing.
  - User account approval.
- Workflow audit trail: Full log of each step's actor, action, timestamp, and comments.

---

### 3.6 Reporting, Analytics & Dashboards

#### 3.6.1 Live Dashboards

**Spreading Dashboard**:
- Overall project overview: Total spreads by status (Draft/Submitted/Approved), by analyst, by time period.
- Analyst productivity: Average time per spread, spreads per day, AI accuracy rate (% of auto-accepted mappings).
- AI Performance: Average confidence score trending over time; retrain impact analysis.

**Covenant Dashboard**:
- Covenant health overview: Count by status (Due/Overdue/Met/Breached/Closed).
- **Breach Risk Heatmap**: All covenants plotted by customer and breach probability.
- Trend charts per covenant per customer over time.
- Upcoming covenant calendar: Timeline view of due dates with risk coloring.

#### 3.6.2 Portfolio-Level Analytics
- Cross-client financial ratio comparisons.
- Custom queries: *"Show me all clients whose current ratio dropped >15% vs last quarter."*
- Sector/geography/portfolio group filtering.
- Drill-down from portfolio → client → specific spread → specific cell.

#### 3.6.3 Natural Language Querying (Phase 2)
- LLM-powered query interface for ad-hoc analysis:
  - *"Which covenants are at risk of breaching this month?"*
  - *"What is the average DSCR across my portfolio?"*
  - *"Show me all clients where the AI accuracy was below 80%."*
- Results rendered as tables, charts, or narrative summaries.

#### 3.6.4 MIS Reports (Export)
- **Spreading Reports**: Spread details, customer details, user activity, OCR accuracy.
- **Covenant Reports**:
  - Covenant Pending Report.
  - Covenant Default/Breach Report.
  - Covenant History Report.
  - Covenant Change History Report.
  - Non-Financial Covenant Item Report.
- Filters: Status, date range, RM, customer, group. Default: last 3 months.
- Export formats: Excel, PDF, HTML.
- Scheduled report delivery via email (configurable frequency).

---

### 3.7 Customer Management

- **Search**: By Long Name, Entity ID, or custom attributes.
- **Modify**: Financial Year End, group assignment, custom metadata.
- **Customer Data Source**: Customers can be synced from external systems (CreditLens, core banking) or created natively.
- **Group-Based Visibility**: Users see only customers within their assigned business portfolio/group.

---

### 3.8 Administration Module

#### 3.8.1 User Management
- Approve/reject/activate/deactivate user accounts.
- Assign roles, groups, and portfolio access.
- Bulk operations via CSV upload.

#### 3.8.2 Global Taxonomy Management
- CRUD operations for keywords, synonyms, zone mappings.
- Bulk upload/download via Excel.
- Per-language taxonomy support.

#### 3.8.3 Global Zone Management
- Maintain the master list of zone names (Income Statement, Balance Sheet, Cash Flow, etc.).
- Add custom zones applicable across all customers.

#### 3.8.4 Language Management
- Enable/disable supported languages for OCR and taxonomy.

#### 3.8.5 Exclusion List Management
- 12-category exclusion configuration (as detailed in §3.3.2).

#### 3.8.6 AI Model Management
- View current model accuracy, retraining status, training data volume.
- Trigger manual retraining.
- A/B test new model versions before promoting to production.

---

## 4. Technical Architecture

### 4.1 Architecture Principles
- **Hybrid-First**: Cloud-native design that can deploy identically to public cloud (AWS/Azure/GCP) or private cloud / on-premises (via Kubernetes + Helm).
- **Multi-Tenant**: Single codebase, isolated data per tenant via schema-per-tenant or row-level security.
- **API-First**: All UI interactions go through well-documented REST APIs; enables third-party integrations.
- **Event-Driven**: Key actions (spread submitted, covenant breached, document uploaded) emit events for real-time processing.
- **Data Sovereignty**: Configurable data residency per tenant — data stored in region-specific clusters for GDPR (EU), DIFC (Middle East), MAS (Singapore) compliance.

### 4.2 System Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────────┐    │
│  │  React SPA  │  │  Admin Panel │  │  Public API (REST/gRPC)  │    │
│  └──────┬──────┘  └──────┬───────┘  └────────────┬─────────────┘    │
└─────────┼────────────────┼───────────────────────┼──────────────────┘
          │                │                       │
          ▼                ▼                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY (Kong / Spring Cloud Gateway)      │
│  Auth · Rate Limiting · Routing · TLS Termination                    │
└──────────────────────────┬───────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
┌──────────────┐  ┌───────────────┐  ┌──────────────────┐
│  CORE API    │  │  ML SERVICE   │  │  WORKFLOW ENGINE  │
│  (Kotlin /   │  │  (Python /    │  │  (Camunda / BPMN) │
│  Spring Boot)│  │  FastAPI)     │  │                    │
│              │  │               │  │  Approval chains   │
│  Spreading   │  │  PaddleOCR    │  │  Escalation rules  │
│  Covenants   │  │  Table ML     │  │  SLA tracking      │
│  Models      │  │  Mapping ML   │  │                    │
│  Users/Auth  │  │  NLP/LLM      │  └────────┬───────────┘
│  Reporting   │  │  Retraining   │           │
└──────┬───────┘  └───────┬───────┘           │
       │                  │                   │
       ▼                  ▼                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                   │
│  ┌────────────┐  ┌──────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │ PostgreSQL │  │ Object Store │  │ Redis/Valkey│  │ Event Bus  │ │
│  │ (Primary)  │  │ (S3/MinIO)   │  │ (Cache +    │  │ (Kafka /   │ │
│  │            │  │ Documents,   │  │  Locks +    │  │  RabbitMQ) │ │
│  │ Tenants    │  │ OCR outputs, │  │  Sessions)  │  │            │ │
│  │ Models     │  │ ML artifacts │  │             │  │ Spread     │ │
│  │ Spreads    │  │              │  │             │  │ events,    │ │
│  │ Covenants  │  │              │  │             │  │ covenant   │ │
│  │ Audit logs │  │              │  │             │  │ triggers   │ │
│  └────────────┘  └──────────────┘  └─────────────┘  └────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.3 Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| **Frontend** | React 19 + TypeScript | Component-based, massive ecosystem, enterprise-proven |
| **UI Components** | Tailwind CSS + Shadcn UI (Radix primitives) | Modern design system, accessible, customizable |
| **Spreadsheet Grid** | Jspreadsheet CE or Luckysheet (open-source) | Free, SpreadJS-like Excel functionality without licensing costs |
| **PDF Viewer** | PDF.js + custom overlay layer | Open-source, coordinate-aware rendering for zone visualization |
| **Charts** | Recharts or Apache ECharts | Free, performant, interactive drill-down support |
| **State Management** | Zustand | Lightweight, performant, minimal boilerplate vs Redux |
| **Backend (Core)** | Kotlin + Spring Boot 3 | Enterprise-grade, strong typing, JVM ecosystem, battle-tested in banking |
| **Backend (ML/AI)** | Python + FastAPI | Best ecosystem for ML/AI libraries and model serving |
| **OCR** | PaddleOCR | Open-source, high accuracy, multi-language (including Arabic) |
| **Table Extraction ML** | PaddlePaddle / Custom CNN-Transformer model | Fine-tuned on financial document layouts |
| **Mapping ML** | Sentence-BERT + custom classification head | Semantic similarity for line-item matching |
| **Workflow Engine** | Camunda 8 (Zeebe) or Flowable | BPMN 2.0 compliant, embeddable, scalable |
| **Database** | PostgreSQL 16 | ACID, JSONB for flexible metadata, row-level security for multi-tenancy |
| **Cache / Locks** | Redis (Valkey) | Session management, exclusive spread locking, query caching |
| **Message Broker** | Apache Kafka or RabbitMQ | Event-driven: spread submitted → recalculate covenants → send notifications |
| **Object Storage** | S3 (cloud) / MinIO (on-prem) | Documents, processed files, ML model artifacts |
| **Container Orchestration** | Kubernetes + Helm | Hybrid deployment: identical packaging for cloud and on-prem |
| **CI/CD** | GitHub Actions / GitLab CI | Automated testing, build, deploy |
| **Monitoring** | Prometheus + Grafana | Infra and ML model performance monitoring |

### 4.4 Hybrid Deployment Strategy

**Cloud Deployment (Default)**:
- Kubernetes clusters on AWS EKS / Azure AKS / GCP GKE.
- Managed PostgreSQL (RDS / Cloud SQL).
- S3 / Azure Blob for document storage.
- Auto-scaling based on document processing load.

**On-Premise / Private Cloud Deployment**:
- Same Kubernetes manifests deployed on client's infrastructure.
- MinIO replaces S3; self-hosted PostgreSQL.
- Air-gapped option for clients with zero internet connectivity.
- Helm charts with configurable values for resource allocation.

**Multi-Region Data Sovereignty**:
- Per-tenant configuration of data residency region.
- Document storage and database instances can be pinned to specific regions.
- API gateway routes requests to the nearest regional cluster.

### 4.5 Security Architecture

- **Encryption**: TLS 1.3 in transit; AES-256 at rest for databases and object storage.
- **Authentication**: JWT with short-lived access tokens + refresh token rotation.
- **Authorization**: Spring Security with RBAC + attribute-based access control (ABAC) for data-level permissions.
- **Audit Logging**: Every API call, data modification, and user action logged with actor, timestamp, IP, and action details. Immutable append-only audit store.
- **Document Security**: Secure handling of password-protected files; passwords never stored, used only for one-time decryption.
- **Vulnerability Management**: Dependency scanning (Snyk/Dependabot), SAST/DAST in CI pipeline.
- **Compliance**: SOC 2 Type II, ISO 27001 readiness. GDPR data subject rights (export, deletion).

---

## 5. Non-Functional Requirements

| Requirement | Target |
|---|---|
| **Concurrent Users** | 100+ per tenant |
| **Document Throughput** | 1,000+ documents/day per tenant (processing pipeline) |
| **Spread Processing Time** | < 3 minutes end-to-end (upload → AI-mapped spread ready for review) |
| **AI Mapping Accuracy** | ≥ 90% auto-accepted (green confidence) within 6 months of client onboarding |
| **System Availability** | 99.9% uptime SLA |
| **Response Time** | < 500ms for UI interactions; < 2s for search/filter operations |
| **Data Retention** | Configurable per tenant (regulatory: 7-10 years typical for banking) |
| **Backup & Recovery** | RPO < 1 hour; RTO < 4 hours |
| **Browser Support** | Chrome (latest 2), Edge (latest 2), Firefox (latest 2) |

---

## 6. Phased Implementation Plan

### Phase 1: Foundation & Autonomous Spreading (Months 1-6)
- [ ] Authentication (SSO + Form + MFA)
- [ ] RBAC with configurable roles and group-based customer visibility
- [ ] File Store: Upload, bulk pre-processing, PaddleOCR integration
- [ ] AI Pipeline: Table detection, zone classification, value extraction
- [ ] Autonomous mapping engine with confidence scoring
- [ ] Dual-pane Review Workspace (React + PDF.js + Grid)
- [ ] Financial Model Engine with global templates
- [ ] Spread lifecycle: Draft → Submit → Approve with exclusive locking
- [ ] Git-like version control for spreads
- [ ] Subsequent spreading with AI autofill
- [ ] Basic validation engine (balance checks)
- [ ] ML feedback loop: capture corrections, batch retrain pipeline

### Phase 2: Covenants & Intelligence (Months 7-10)
- [ ] Covenant customer management
- [ ] Financial & non-financial covenant definitions
- [ ] Formula Builder (visual)
- [ ] Monitoring item auto-generation
- [ ] Real-time covenant recalculation on spread submission
- [ ] Predictive breach probability forecasting
- [ ] Non-financial document approval workflow
- [ ] Waiver / Not-Waiver letter generation and delivery
- [ ] Email template & signature management
- [ ] Automated due/overdue email reminders

### Phase 3: Workflow Engine & Reporting (Months 11-13)
- [ ] BPMN workflow designer integration (Camunda/Flowable)
- [ ] Configurable approval hierarchies for spreads, covenants, waivers
- [ ] Live Spreading Dashboard (analyst productivity, AI accuracy)
- [ ] Live Covenant Dashboard (health overview, breach risk heatmap)
- [ ] Portfolio-level analytics with drill-down
- [ ] MIS report generation with export (Excel/PDF/HTML)
- [ ] Scheduled report delivery

### Phase 4: LLM Copilot & Advanced AI (Months 14-16)
- [ ] LLM-powered conversational copilot for analysts
- [ ] Natural language querying for dashboards and reports
- [ ] Notes processing: AI extraction of values from free-text paragraphs
- [ ] Per-client model specialization (dedicated fine-tuned models)
- [ ] AI model A/B testing and version management

### Phase 5: Enterprise Readiness & Scale (Months 17-18)
- [ ] Multi-region deployment with data sovereignty controls
- [ ] On-premise deployment packaging (Helm + air-gapped support)
- [ ] External system adapters (CreditLens, nCino, Finastra)
- [ ] SOC 2 / ISO 27001 audit preparation
- [ ] Performance hardening and load testing at scale
- [ ] Client onboarding toolkit and documentation

---

## 7. Open Items for Further Discussion

1. **Product Name / Brand**: The platform needs a codename and eventual brand identity.
2. **Pricing Model**: Per-user? Per-document? Per-tenant flat fee? Usage tiers?
3. **Initial Pilot Client**: Is there a specific bank lined up for alpha/beta?
4. **Team Composition**: What engineering resources are available (frontend, backend, ML/AI, DevOps)?
5. **Regulatory Requirements**: Any specific banking regulator certifications required beyond SOC 2 (e.g., CBUAE, MAS, PRA)?
6. **Data Licensing**: For cold-start training data — confirm use of SEC EDGAR and publicly available annual reports is acceptable.
