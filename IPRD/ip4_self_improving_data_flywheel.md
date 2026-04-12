# IP-4: Self-Improving Data Flywheel (SIDF)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **self-improving machine learning system** where every analyst interaction makes the platform smarter, creating a compound data advantage that grows with each client and each document processed. The flywheel is the primary long-term moat — after 12 months of production use, a competitor would need years to replicate the accumulated intelligence.

**The insight**: Most AI products are "static" — they ship a model, users consume it. Numera should be a "living" system where the model gets better every day, per-client accuracy diverges from the global average (in a good way), and switching to a competitor means losing months/years of accumulated learning.

---

## Context: What Exists Today

### Current Codebase
- **Feedback Store** (`ml-service/app/services/feedback_store.py`):
  - Captures analyst corrections: what SBERT suggested vs what analyst chose
  - Stores correction type: REMAPPED, ADDED, REMOVED, VALUE_CHANGED
  - PostgreSQL + in-memory fallback
  - Export endpoint for Colab retraining notebooks
  - Statistics endpoint (total corrections, by type, by tenant)

- **Client Model Resolver** (`ml-service/app/services/client_model_resolver.py`):
  - Per-tenant model resolution with LRU caching
  - Checks MLflow for `sbert-ifrs-matcher-{tenant_id}` model
  - Threshold-based: only loads client model if tenant has ≥N corrections
  - Lazy loading with cache eviction

- **A/B Testing** (`ml-service/app/ml/semantic_matcher.py`):
  - Route configurable % of requests to staging model
  - Compare production vs staging accuracy
  - Already integrated into semantic matching pipeline

- **Training Notebooks** (`ml-training/notebooks/`):
  - `20_feedback_retraining.ipynb` — processes feedback into training pairs
  - `21_client_model_specialization.ipynb` — fine-tunes per-tenant model
  - `09_sbert_finetuning.ipynb` — base SBERT fine-tuning

### The Gap
1. **No automated trigger** — Retraining requires manually running Colab notebooks
2. **No confidence calibration** — When system says "85% confident", it might actually be right 60% of the time
3. **Full model per client** — Each client model is a full 400MB SBERT clone; should be 4MB LoRA adapter
4. **No promotion pipeline** — No automated A/B test → evaluation → promote-to-production lifecycle
5. **No cross-tenant learning** — Corrections from Tenant A don't benefit Tenant B (even anonymized)
6. **Expression Memory is in-memory only** — `ExpressionMemory` class uses dict, doesn't persist to DB

---

## Research Directives

### R1: Feedback Signal Architecture

Design a comprehensive feedback capture system that extracts maximal learning signal from analyst behavior.

**Three feedback channels:**

| Channel | Signal | ML Training Pair | Current Status |
|---|---|---|---|
| **Explicit Correction** | Analyst remaps value from Item A → Item B | (source_text, wrong_item, correct_item) → contrastive pair | ✅ Captured |
| **Expression Override** | Analyst changes SUM(a,b,c) → SUM(a,b) | (source_values, wrong_expression, correct_expression) → ARIE training | ❌ Not captured |
| **Implicit Acceptance** | Analyst bulk-accepts all HIGH confidence values | (source_text, item) → positive confirmation pair | ❌ Not captured |
| **Value Edit** | Analyst changes mapped value from 15,234 → 15,234,000 | (source_text, scale_factor) → unit detection training | ❌ Not captured |
| **Zone Correction** | Analyst moves a value from IS zone to BS zone | (page_region, wrong_zone, correct_zone) → zone classifier training | ❌ Not captured |
| **Cross-Reference Add** | Analyst manually links a Note to a BS line | (note_reference_text, linked_section) → cross-ref training | ❌ Not captured |

**Questions to answer:**
- What is the minimum viable feedback schema that captures all 6 channels?
- How do we handle "noisy" feedback? (Analyst makes a mistake, then corrects again — which correction is authoritative?)
- How do we handle "lazy" acceptance? (Analyst bulk-accepts without reviewing — this is a false positive signal)
- What is the latency from feedback capture → available for training? (Real-time? Daily batch?)
- How do we weight different feedback channels? (Explicit correction > implicit acceptance, but what ratio?)
- What meta-data should we capture alongside feedback? (Document type, auditor, jurisdiction, section, page — all useful for conditioning)

**Research areas:**
- Implicit feedback vs explicit feedback in recommendation systems
- Human-in-the-loop ML feedback loops (Clean Lab, Snorkel, Prodigy)
- Feedback loop delay and its effect on model quality
- Attention-based feedback weighting (user attention time as signal)

### R2: Per-Client Model Specialization (LoRA Approach)

Research efficient per-client model customization that doesn't require storing a full model per tenant.

**The problem with the current approach:**
- Full SBERT model = ~400MB per client
- 100 clients = 40GB just for models
- Loading a 400MB model takes 5-10 seconds (unacceptable latency for first request)

**The LoRA/Adapter approach:**
- Keep one global model (400MB, loaded once)
- Per client: train a small LoRA adapter (4-16MB) that modifies attention weights
- At inference time: global model + client adapter = personalized prediction
- Loading a 4MB adapter takes <100ms
- 100 clients = 400MB global + 1.6GB adapters = 2GB total

**Questions to answer:**
- What is the minimum number of corrections needed to train a useful adapter? (Current threshold is 100. Can we go lower with few-shot techniques?)
- What rank should the LoRA adapters use? (r=4? r=16? Higher rank = more capacity but more storage)
- How do we evaluate whether a client adapter is BETTER than the global model? (Must not regress on any subset)
- Can we use more advanced PEFT techniques? (LoRA, QLoRA, Prefix Tuning, P-Tuning v2 — which works best for SBERT?)
- How do we handle adapter staleness? (Client doesn't submit documents for 6 months, then submits with new format — is the old adapter still valid?)
- Can we do "hierarchical adapters"? (Industry adapter → Client adapter → the client benefits from both its own data AND industry-level learning)

**Research areas:**
- LoRA: Low-Rank Adaptation of Large Language Models (Hu et al., 2021)
- QLoRA: Efficient Finetuning of Quantized LLMs (Dettmers et al., 2023)
- PEFT library (Hugging Face) — adapter management, merging, composition
- Multi-tenant model serving architectures (Mosaic ML, Anyscale)
- Adapter composition: can we combine industry + client adapters at inference time?

### R3: Bayesian Confidence Calibration

Research how to make confidence scores MEANINGFUL — when the system says "85% confident", it should actually be right 85% of the time.

**The problem:**
- Current SBERT cosine similarity of 0.85 does NOT mean 85% probability of correct match
- Calibration varies by: document type, section, client, model version
- Miscalibrated confidence = analysts can't trust bulk-accept thresholds

**Calibration approach:**
```
1. Collect {confidence_score, actual_correct} pairs from feedback
2. Fit a calibration function: P(correct | score) = f(score)
3. Options for f: isotonic regression, Platt scaling, temperature scaling
4. Per-client calibration: each client has their own f function
5. Output: calibrated_confidence that is a true probability
```

**Questions to answer:**
- What is the minimum sample size for reliable calibration? (50 samples? 200? 500?)
- How do we calibrate before we have enough data? (Use global calibration as prior, update with Bayesian online learning)
- Should calibration be per-model, per-client, per-zone, or per-item-type?
- How do we detect calibration drift? (Model is retrained, old calibration no longer valid)
- Can we use calibration to AUTOMATICALLY adjust bulk-accept thresholds? (If calibrated confidence ≥ 0.95, auto-accept without analyst review)
- What is the expected accuracy improvement from calibration? (Literature suggests 5-15% improvement in expected calibration error)

**Research areas:**
- Platt scaling, temperature scaling, isotonic regression for neural network calibration
- "On Calibration of Modern Neural Networks" (Guo et al., 2017)
- Bayesian online calibration with prior updates
- Calibration-aware loss functions (focal loss, label smoothing)
- Expected Calibration Error (ECE) as a metric

### R4: Automated Retraining Pipeline

Design the automated pipeline from feedback collection → model improvement → deployment.

**Pipeline stages:**
```
Feedback Capture → Kafka → Flywheel Orchestrator → Threshold Check
     │
     ▼ (threshold met: e.g., 50 new corrections)
Training Job Trigger → Export feedback → Generate training pairs
     │
     ▼
Fine-tuning → LoRA adapter training on exported pairs
     │
     ▼
Evaluation → Compare new adapter vs current on held-out test set
     │
     ▼ (new adapter is better)
A/B Test Deployment → Deploy as "staging" model → Route 10% traffic
     │
     ▼ (7-day A/B test period)
Metric Comparison → Compare production vs staging on: accuracy, analyst corrections, processing time
     │
     ▼ (staging is ≥2% better)
Promotion → staging → production, old production → archived
     │
     ▼
Notification → "Model updated for Tenant X: accuracy improved from 87.2% → 91.4%"
```

**Questions to answer:**
- What infrastructure runs the training? (Colab? Cloud GPU? Local GPU? Can we use CPU-only for small LoRA adapters?)
- What is the retraining latency? (From threshold met → new model in production. Target: <24 hours)
- How do we handle training failures? (Bad data, convergence issues, regression. Rollback plan?)
- What metrics determine "better"? (Just accuracy? Or also: correction rate reduction, analyst time reduction, confidence calibration improvement)
- How do we handle A/B test statistical significance? (What sample size for reliable comparison? Sequential testing vs fixed-horizon?)
- Can we do "continuous learning" (update model with each correction) or must it be batch retraining? (Online vs batch tradeoffs)
- How do we version models? (Model versioning scheme: global_v2.1 + client_tenant123_v1.3)

**Research areas:**
- MLOps best practices for model retraining (MLflow, Weights & Biases, DVC)
- Online learning vs batch retraining for SBERT-style models
- Continual learning without catastrophic forgetting (EWC, PackNet, progressive nets)
- A/B testing statistical methods for ML models (Bayesian A/B testing, multi-armed bandits)
- Feature store architectures for feedback data (Feast, Tecton)

### R5: Cross-Tenant Learning (Privacy-Preserving)

Research how to transfer learning across tenants without revealing proprietary data.

**The opportunity**: When Tenant A corrects "Provisions for loan losses" → mapped to "Provision for Credit Losses", this correction should benefit ALL tenants in the banking sector, not just Tenant A.

**Privacy challenges:**
- Source text may contain client names, amounts, dates
- Corrections may reveal the structure of a bank's internal model
- GDPR / data processing agreements may prohibit cross-tenant data usage

**Approaches to evaluate:**

| Approach | Privacy Level | Accuracy Benefit | Complexity |
|---|---|---|---|
| **Federated Averaging** | High (only gradients shared) | Medium | High |
| **Differential Privacy** | Very high (noise added) | Low-Medium | High |
| **Synthetic pair generation** | High (generate similar pairs, not exact) | Medium | Medium |
| **Embedding-only sharing** | Medium (share label embeddings, not text) | Medium-High | Low |
| **Industry-level aggregation** | Medium (aggregate corrections per industry) | Medium | Low |

**Questions to answer:**
- What level of privacy is legally required? (Data processing agreement review)
- What is the accuracy improvement from cross-tenant learning? (Expected: 10-20% for new tenants, diminishing for established tenants)
- Can we use synthetic data generation? (Given real correction pair, generate N similar synthetic pairs that don't identify the source)
- What is the opt-in/opt-out model? (Tenant can opt out of cross-learning — how does this affect their model?)
- How do we handle competing tenants? (Two banks that are actual competitors — should their corrections benefit each other?)

**Research areas:**
- McMahan et al., "Communication-Efficient Learning of Deep Networks from Decentralized Data" (FedAvg)
- Differential Privacy with Gaussian noise for deep learning (DP-SGD)
- Synthetic data generation for NLP (methods like VAE-generated text pairs)
- Privacy-preserving machine learning in financial services (regulatory guidance)

### R6: Flywheel Economics & Metrics Dashboard

Design the metrics and monitoring system that demonstrates flywheel value to stakeholders.

**Key metrics to track:**
1. **Accuracy Trajectory** — per tenant, per model version, over time
2. **Correction Velocity** — corrections per 100 mapped values (should decrease over time)
3. **Auto-Accept Rate** — % of values auto-accepted without analyst review (should increase)
4. **Time-to-Spread** — average analyst time per spread (should decrease)
5. **Flywheel Latency** — time from correction → model improvement
6. **Model Divergence** — how different client models are from global (high = specialized)
7. **Cross-Tenant Lift** — accuracy improvement from industry-level shared learning

**Questions to answer:**
- What visualization best demonstrates flywheel value to a CTO/CFO?
- What is the expected ROI narrative? ("Each correction saves 30 seconds of future analyst time. After 1000 corrections, the system saves 8.3 hours per week.")
- How do we benchmark against "day 1 accuracy" to show compound improvement?
- What alerting thresholds indicate the flywheel is stalling? (No improvement in 30 days? Accuracy regression?)

---

## Competitive Analysis Required

1. **Does any financial spreading tool have a self-improving accuracy system?** Document specific claims from Moody's/S&P/nCino
2. **Grammarly's adaptive model** — How do they do per-user style adaptation while maintaining a global model?
3. **Spotify/Netflix recommendation flywheels** — What architectural patterns from RecSys can we adapt?
4. **GitHub Copilot's telemetry-based improvement** — How do they use acceptance/rejection signals?
5. **Snorkel AI / Prodigy** — Their approaches to programmatic labeling from human feedback
6. **Financial-specific**: Do Bloomberg Terminal or Refinitiv have self-improving extraction?

---

## Deliverables

1. **Feedback Schema Specification** — Complete schema for all 6 feedback channels with examples
2. **LoRA Adapter Architecture** — Per-client adapter design with memory/latency analysis
3. **Calibration Algorithm** — Bayesian confidence calibration with per-client curves
4. **Retraining Pipeline Design** — End-to-end automated pipeline with rollback procedures
5. **Cross-Tenant Protocol** — Privacy-preserving knowledge transfer specification
6. **Metrics Dashboard Wireframe** — Admin UI showing flywheel health and ROI
7. **Economic Model** — Projected accuracy improvement curves per client over time (month 1 → month 12)
8. **Risk Assessment** — Feedback loop failure modes, data poisoning attacks, model degradation scenarios

---

## Success Criteria

The flywheel is successful when:
- Client accuracy improves measurably month-over-month for the first 12 months
- Per-client LoRA adapters use <16MB storage and load in <200ms
- Auto-accept rate reaches >60% for clients with 6+ months of history
- Calibrated confidence ECE (Expected Calibration Error) is <0.05
- New tenants in a known industry achieve 15% higher day-1 accuracy from cross-tenant transfer
- Time-to-spread decreases >30% over 6 months for active clients
