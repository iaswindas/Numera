# Bootstrapping Strategy: ML Costs, Product Name & Pricing

## 1. ML Cost Management — Zero to Revenue

This is the most critical section. Here's the honest breakdown: **you don't need to spend big money on ML until you have paying clients.** Here's how.

### 1.1 Why ML Costs Are Manageable for a Solo Founder

The competitor (Spreadsmart) spent years building custom ML pipelines. But in 2026, the landscape has fundamentally shifted:

| Component | Competitor's Approach (Expensive) | Your Approach (Near-Zero Cost) |
|---|---|---|
| **OCR** | Licensed OCR engine | **PaddleOCR** — free, pre-trained, runs on CPU |
| **Table Detection** | Custom-trained model, GPU clusters | **PaddlePaddle TableDetection** or **YOLO-based models** — pre-trained on financial documents, fine-tune with free GPUs |
| **Zone Classification** | Custom ML pipeline | **Pre-trained classifiers** (LayoutLM / PubLayNet) — already know what "Balance Sheet" vs "Income Statement" looks like |
| **Line-Item Matching** | Rule-based taxonomy + synonyms | **Sentence-BERT** (all-MiniLM-L6-v2) — pre-trained semantic similarity, runs on CPU, zero cost |
| **LLM Features** | N/A (they don't have this) | Free-tier APIs + local models (Ollama/Llama) for Phase 2 |

### 1.2 The Three Stages of ML Spend

#### Stage 1: Pre-Revenue (₹0/month — Months 1-6)
**Goal**: Build a working demo that can impress potential bank clients.

| Item | Cost | How |
|---|---|---|
| **PaddleOCR** | ₹0 | Pre-trained, runs on your development machine (CPU) |
| **Table Detection Model** | ₹0 | Use pre-trained PaddleDetection (PP-StructureV2) — already trained on documents, tables, figures |
| **Zone Classification** | ₹0 | Use LayoutLM-base (HuggingFace, free) or simple rule-based heuristics: "If table has Assets/Liabilities rows → Balance Sheet" |
| **Semantic Matching** | ₹0 | Sentence-BERT (all-MiniLM-L6-v2) — 80MB model, runs on CPU in milliseconds |
| **Fine-Tuning** | ₹0 | Use **Google Colab free tier** (T4 GPU, 12 hours/session) or **Kaggle Notebooks** (30 hours/week free GPU). Fine-tune on 200-500 SEC EDGAR annual reports |
| **Training Data** | ₹0 | SEC EDGAR XBRL filings = structured financial data with labels. Download via EDGAR API (free) |
| **Development Server** | ₹0-₹1,500/mo | Your local machine for development. For demos: a ₹1,500/month VPS (Hetzner/DigitalOcean) with 16GB RAM is enough for CPU inference |
| **Total** | **~₹0-₹1,500/month** | |

> [!TIP]
> **Key insight**: At pre-revenue stage, you only need to process **10-50 documents for demos**. CPU inference takes 30-60 seconds per document instead of 5 seconds on GPU. That delay is completely acceptable for demos — you can even show a progress bar and it looks professional.

#### Stage 2: First Client / Pilot (₹5,000-₹15,000/month — Months 7-12)
**Goal**: Serve one pilot client with acceptable performance.

| Item | Cost | How |
|---|---|---|
| **Cloud GPU for inference** | ₹5,000-₹10,000/mo | One small GPU instance (AWS g4dn.xlarge or equivalent) — handles ~500 docs/day |
| **Cloud VM for backend** | ₹3,000-₹5,000/mo | Standard VM for Kotlin/Spring Boot + PostgreSQL |
| **Object Storage** | ₹500-₹1,000/mo | S3/MinIO for document storage |
| **Total** | **~₹8,500-₹16,000/month** | |

This is covered easily when billing even one pilot client.

#### Stage 3: Growth (Client Revenue Funds Everything)
- Each client pays for their own compute via the pricing model.
- ML retraining on client-specific data happens on their dedicated infrastructure (on-prem) or their allocated cloud resources.
- GPU costs scale linearly with document volume — directly tied to revenue.

### 1.3 Cold-Start Training Plan (Zero Cost)

```
Step 1: Download 500+ annual reports from SEC EDGAR (free API)
                ↓
Step 2: Use PaddleDetection's pre-trained PP-StructureV2 to extract tables
                ↓
Step 3: Use XBRL tagged data as ground truth labels (SEC provides structured XBRL for every filing)
                ↓
Step 4: Fine-tune zone classifier on Google Colab (free T4 GPU)
        Input: Detected table image/layout → Output: Zone type (BS/IS/CF/Notes)
                ↓
Step 5: Fine-tune semantic matcher on Colab
        Input: PDF row text → Output: Target model line item
        Training pairs from XBRL concept → label mappings
                ↓
Step 6: Package models → Deploy on CPU for demos
```

**XBRL is your secret weapon**: SEC mandates that all public company filings include XBRL structured data. This means you get **free, perfectly labeled training data** — the PDF is the input, the XBRL is the ground truth output. No manual annotation needed.

### 1.4 Cost Danger Zones to Avoid

| Temptation | Why It's Dangerous | What To Do Instead |
|---|---|---|
| Renting GPU clusters for training | Burns ₹50K-₹2L/month with no revenue | Use Colab free/Kaggle free. Train in batches |
| Using OpenAI/Claude API at scale | ₹5-₹50 per document adds up to lakhs | Use local LLMs (Ollama + Llama 3.1 8B) for copilot features |
| Building a custom OCR engine | 6+ months of work, needs massive data | PaddleOCR is already 95%+ accurate on financial documents |
| Over-engineering the ML pipeline | Perfect is the enemy of shipped | Get 80% accuracy → ship → improve with client feedback |

---

## 2. Product Name Suggestions

Criteria: Premium, memorable, conveys AI + financial intelligence, available as .com domain (verify before committing).

| Name | Rationale | Vibe |
|---|---|---|
| **SpreadIQ** | "Spread" (industry term) + "IQ" (intelligence). Instantly communicates what it does and that it's smart. Short, punchy. | Modern, technical |
| **Numera** | From Latin "numerus" (number). Clean, elegant, one-word. Works globally across languages. | Premium, sophisticated |
| **AutoSpread** | Directly communicates the value proposition: automatic spreading. Zero ambiguity. | Clear, product-led |
| **FinForge** | "Finance" + "Forge" (to create/build). Implies precision-crafted financial outputs. | Strong, industrial |
| **CreditArc** | "Credit" + "Arc" (trajectory, journey). Implies tracking financial health over time. Pairs well with covenant forecasting. | Enterprise, trustworthy |

> [!IMPORTANT]
> **My recommendation**: **SpreadIQ** — it's the most immediately understandable to a banker. When you say "SpreadIQ automates financial spreading with AI", the value proposition is communicated in one sentence. But verify domain availability before committing.

---

## 3. Pricing Model

### 3.1 Model: Tiered Platform Fee + Per-Document Processing

Banks understand and prefer predictable costs with usage-based components.

#### Tier Structure

| Tier | Target Client | Monthly Platform Fee (USD) | Per-Document Fee (USD) | Included Documents | Users |
|---|---|---|---|---|---|
| **Starter** | Small banks, PE firms | $2,000/mo | $3/doc after included | 500/month | Up to 20 |
| **Professional** | Mid-size banks | $5,000/mo | $2/doc after included | 2,000/month | Up to 50 |
| **Enterprise** | Large international banks | $12,000/mo | $1.50/doc after included | 5,000/month | Up to 150 |
| **Sovereign** | Banks requiring on-prem / private cloud | Custom pricing | Custom | Unlimited | Unlimited |

#### Add-On Modules (Priced Separately)

| Module | Monthly Fee | Notes |
|---|---|---|
| **Covenant Intelligence** | +$1,500-$5,000/mo | Includes predictive breach forecasting |
| **LLM Copilot** | +$1,000-$3,000/mo | Natural language querying, conversational analysis |
| **Custom Adapter** (CreditLens, nCino, etc.) | +$2,000-$5,000/mo | Per-integration |
| **Dedicated ML Training** | One-time $5,000-$15,000 | Per-client model fine-tuning on their data |

### 3.2 Why This Model Works for Bootstrapping

1. **Platform fee = predictable MRR**: Even one Starter client = $2,000/month recurring.
2. **Per-document fee = scales with usage**: As the client processes more, you earn more — but your marginal cost per document is near-zero (CPU inference ~$0.01/doc).
3. **Gross margins are 85-95%**: Your costs are server hosting (~$150-500/month per client) versus $2,000-$12,000 revenue.
4. **On-prem/Sovereign tier = large upfront contracts**: Banks will pay $100K-$500K annually for on-prem deployments.

### 3.3 Go-To-Market Pricing for First Client

For the **first 2-3 pilot clients**, offer an aggressive deal to get reference logos:

> **Pilot Program**: $500/month for 6 months (Starter tier equivalent), unlimited documents. After 6 months, convert to standard pricing or walk away. No long-term commitment.

This gets you:
- A live client generating real training data.
- A reference logo for sales to larger banks.
- Proof that the system works at production scale.
- Revenue that covers your cloud infrastructure costs.

---

## 4. Revised Solo-Founder Implementation Plan

Since you're a solo founder, the original 18-month plan needs to be ruthlessly prioritized. Here's the revised plan optimized for **getting to a demo-ready MVP as fast as possible**.

### Phase 0: Demo MVP (Months 1-3) — GET TO DEMO ASAP

> [!CAUTION]
> This is the only phase that matters until you have a paying client. Cut every feature that doesn't contribute to a jaw-dropping demo.

**What the demo must show**:
1. Upload a PDF annual report.
2. AI automatically extracts all tables, classifies zones, identifies periods.
3. AI maps values to a financial model with confidence scores.
4. Analyst reviews in a polished dual-pane UI, makes 2-3 corrections.
5. Spread is submitted and version-controlled.

**What to build**:
- [ ] Simple form-based login (no SSO yet — just email/password with JWT)
- [ ] File upload → PaddleOCR processing → table extraction
- [ ] Pre-trained zone classification (LayoutLM or heuristic-based)
- [ ] Semantic line-item matching (Sentence-BERT)
- [ ] Dual-pane workspace: PDF.js left + grid right (basic grid, not full SpreadJS-level yet)
- [ ] Confidence-coded cell display (green/amber/red)
- [ ] Manual correction UI (click to remap)
- [ ] Basic model template (one IFRS model)
- [ ] Save/Submit lifecycle

**What to skip**:
- ❌ SSO integration
- ❌ Covenant module
- ❌ Workflow engine
- ❌ MIS reporting
- ❌ Multi-tenancy
- ❌ Email notifications
- ❌ Formula builder
- ❌ Admin panel

### Phase 1: Production-Ready Spreading (Months 4-8)
- [ ] SSO + form authentication
- [ ] RBAC & multi-tenancy
- [ ] Full File Store with bulk processing
- [ ] Exclusive spread locking
- [ ] Git-like version control
- [ ] Subsequent spreading with autofill
- [ ] Expression builder with full operators
- [ ] Validation engine
- [ ] ML feedback loop & retraining pipeline
- [ ] Customer management
- [ ] Basic admin panel

### Phase 2: Covenants & Intelligence (Months 9-13)
- [ ] Full Covenants module (financial + non-financial)
- [ ] Formula management
- [ ] Real-time covenant triggers
- [ ] Predictive breach forecasting
- [ ] Document verification workflow
- [ ] Waiver/letter generation
- [ ] Email notifications
- [ ] Live dashboards

### Phase 3: Workflow, LLM & Enterprise (Months 14-18)
- [ ] BPMN workflow engine
- [ ] LLM Copilot
- [ ] NL querying
- [ ] Portfolio analytics
- [ ] External system adapters
- [ ] On-prem deployment packaging
- [ ] Security hardening & compliance prep

### Key Principle for Solo Founder

```
Demo MVP → First pilot client → Revenue → Hire first engineer → Iterate
    3mo          6mo              7mo           8mo              forever
```

**Do NOT try to build the entire platform before showing it to anyone.** Get the autonomous spreading demo working, take it to 5 banks, sign one pilot, and use that revenue + validation to build everything else.

---

## 5. Updated Open Items

1. ~~Product Name~~ → Select from suggestions above
2. ~~Pricing Model~~ → Tiered platform + per-document 
3. ~~ML Costs~~ → Zero-cost cold start strategy defined
4. **Which demo region first?** Middle East (UAE banks), Europe, or Asia? This affects which financial statement formats to prioritize in training.
5. **Do you have banking industry contacts?** For pilot client outreach — even one warm introduction changes everything.
6. **Full-time or part-time on this?** The 3-month demo timeline assumes full-time dedication.
