# IP-7: Financial Anomaly Detection Engine (FADE)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **multi-layer anomaly detection system** that goes far beyond simple balance checks (A = L + E) to identify errors, data quality issues, unusual patterns, and potential fraud signals in financial spreads. The system produces an **Anomaly Score** (0-100) per spread with specific flagged items and explanations, giving managers a "Quality Badge" that provides pre-approval confidence.

**The problem**: Current spreading tools do ONE check: does the balance sheet balance? That catches <5% of errors. FADE catches errors across three independent detection layers, each using fundamentally different statistical approaches — making it extremely hard to fool and extremely thorough at finding genuine issues.

---

## Context: What Exists Today

### Current Codebase
- **Validation Rules** (`backend/.../model/domain/ValidationRule.kt`):
  - Simple formula-based validation: define an expression, if result ≠ 0, mark as FAIL
  - Only checks what's explicitly configured (A-L-E ≠ 0?)
  - No statistical analysis, no historical comparison, no anomaly scoring

- **Formula Engine** (`backend/.../model/application/FormulaEngine.kt`):
  - Can evaluate arbitrary expressions
  - Used by MappingOrchestrator to run validation rules
  - Capable of the arithmetic needed for ratio computation

- **MappingOrchestrator** (`backend/.../spreading/application/MappingOrchestrator.kt`):
  - Runs validation rules at end of `processSpread()`
  - Returns MappingValidation objects (name, status=PASS/FAIL, difference, severity)

### The Gap
1. Only checks preconfigured balance rules — no automatic statistical analysis
2. No comparison against prior periods (can't flag "Revenue jumped 500% — is this real?")
3. No comparison against industry benchmarks
4. No Benford's Law or statistical distribution analysis
5. No cross-statement flow validation (IS → BS → CF flow checking)
6. No aggregated anomaly score

---

## Research Directives

### R1: Layer 1 — Benford's Law Analysis

Research and design a Benford's Law-based anomaly detector for financial spread values.

**Background**: Benford's Law states that in naturally occurring numerical datasets, the leading digit "1" appears ~30% of the time, "2" appears ~18%, etc. Financial statement values (revenue, expenses, assets) should follow this distribution. Deviation may indicate:
- Data fabrication (round numbers like 1,000,000 are over-represented)
- Manual entry errors (analyst typed values instead of mapping from document)
- Fraudulent manipulation (values adjusted to hit targets)

**First-Digit Distribution:**
| Digit | Expected % |
|---|---|
| 1 | 30.1% |
| 2 | 17.6% |
| 3 | 12.5% |
| 4 | 9.7% |
| 5 | 7.9% |
| 6 | 6.7% |
| 7 | 5.8% |
| 8 | 5.1% |
| 9 | 4.6% |

**Questions to answer:**
- What statistical test should we use to measure deviation from Benford's? (Chi-squared? Kolmogorov-Smirnov? Kuiper? MAD — Mean Absolute Deviation?)
- What is the minimum sample size for reliable Benford analysis? (One spread might have 40-80 values. Is that enough?)
- Should we analyze first digit only, or also first-two-digits (Benford extends to second digits)?
- How do we handle exceptions? (Percentages, small values <10, negative values)
- What is the threshold for flagging? (p-value < 0.05? Or use MAD > 0.015 as per Nigrini's criteria?)
- Can we identify SPECIFIC values that contribute most to the deviation? (Not just "the distribution is off" but "the value 50,000,000 on row 23 is suspicious")
- How do we handle recurring clients? (Client A's spread always deviates from Benford because their industry has concentrated values — personalized baseline?)
- What about second-order Benford analysis? (Check if the SECOND digit follows the expected distribution — harder to fake)

**Research areas:**
- Nigrini, M.J. "Benford's Law: Applications for Forensic Accounting, Auditing, and Fraud Detection" (2012) — definitive reference
- Kuiper's test vs Chi-squared for Benford analysis — which is more powerful for small samples?
- Benford's Law violations in real financial statements (published research on Enron, WorldCom)
- Generalized Benford's Law for financial ratios (not just raw values)
- Digital analysis for audit evidence (AICPA guidance, ISA 520)

### R2: Layer 2 — Financial Ratio Consistency Analysis

Design a comprehensive ratio analysis system that flags unusual patterns.

**Ratio categories to compute:**

| Category | Ratios | What Anomalies Indicate |
|---|---|---|
| **Liquidity** | Current Ratio, Quick Ratio, Cash Ratio, Working Capital / Revenue | Solvency risk, misclassified current vs non-current |
| **Leverage** | Debt/Equity, Debt/EBITDA, Equity Multiplier, Interest Coverage | Over-leverage, missing liabilities |
| **Profitability** | Gross Margin, Operating Margin, Net Margin, ROA, ROE, ROIC | Revenue/cost misclassification, missing expenses |
| **Efficiency** | Asset Turnover, Receivable Days, Payable Days, Inventory Days | Value magnitude errors, temporal mismatches |
| **Cash Flow** | Operating CF / Net Income, Free CF / Revenue, Capex / Depreciation | IS/CF disconnect, missing adjustments |
| **Coverage** | DSCR, Times Interest Earned, Fixed Charge Coverage | Debt schedule errors |
| **Growth** | Revenue Growth %, Asset Growth %, Equity Growth % | Period-over-period comparison errors |

**Three comparison dimensions:**

1. **Self-History**: Compare ratio against client's own prior 4-8 periods
   - Flag if swing > 20% (configurable) without explanation
   - Use Z-score within client's historical distribution
   
2. **Industry Benchmark**: Compare against industry median/quartile
   - Flag if ratio is >2 standard deviations from industry norm
   - Requires benchmark data (source: Damodaran, S&P, or aggregated from platform data)
   
3. **Model Expectations**: Compare against what the MODEL predicts given other values
   - "Given this Revenue and these Costs, the expected Gross Margin is 35-40%. The mapped value implies 72%. Something is wrong."

**Questions to answer:**
- How many ratios should we compute? (20 core ratios? 50 comprehensive? Or dynamic based on available values?)
- Where do we get industry benchmarks? (Aswath Damodaran's public data? Self-computed from platform data? External API?)
- What swing threshold should trigger a flag? (Fixed 20%? Adaptive based on client's historical volatility?)
- How do we handle first-time clients (no prior period)? (Only compare against industry benchmarks?)
- How do we present ratio anomalies to the analyst? (Table with red/amber/green? Or narrative? "Revenue grew 200% but Cost of Sales only grew 50% — this implies an unusual margin expansion. Please verify.")
- Should we weight some ratios higher than others? (Leverage ratios more important for banking covenant clients?)
- Can we do TREND analysis? (Not just current period swing, but 3-quarter declining trend in Coverage ratio?)

**Research areas:**
- Damodaran Online: industry average financial ratios (public data)
- DuPont Analysis decomposition (ROE = Margin × Turnover × Leverage)
- ISA 520: Analytical Procedures in auditing (international standard for ratio analysis)
- Outlier detection methods for multivariate financial data
- Mahalanobis distance for multi-ratio anomaly detection

### R3: Layer 3 — Cross-Statement Flow Validation

Design validation that checks whether the three financial statements (IS, BS, CF) are internally consistent.

**Critical flows to validate:**

```
┌──────────────────────────────────────────────────────┐
│                 CROSS-STATEMENT FLOWS                 │
│                                                       │
│  INCOME STATEMENT → BALANCE SHEET                     │
│  • Net Income → Change in Retained Earnings           │
│    (± Dividends, ± OCI, ± Other Adjustments)          │
│                                                       │
│  INCOME STATEMENT → CASH FLOW                         │
│  • Net Income = Starting point of Operating CF        │
│  • Depreciation (IS) = Depreciation add-back (CF)     │
│  • Interest Expense (IS) = Interest Paid (CF)         │
│  • Tax Expense (IS) ≈ Tax Paid (CF) ± Tax Payable Δ  │
│                                                       │
│  BALANCE SHEET → CASH FLOW                            │
│  • Change in Receivables (BS) = Receivables Δ (CF)    │
│  • Change in Inventory (BS) = Inventory Δ (CF)        │
│  • Change in Payables (BS) = Payables Δ (CF)          │
│  • Change in PP&E (BS) + Depreciation = Capex (CF)    │
│  • Change in Debt (BS) = Borrowings (CF)              │
│  • Opening Cash + Net CF = Closing Cash (BS)          │
│                                                       │
│  BALANCE SHEET SELF-CONSISTENCY                       │
│  • Prior Period Closing BS = Current Period Opening    │
│  • A = L + E (each period)                            │
└──────────────────────────────────────────────────────┘
```

**Questions to answer:**
- How do we handle mismatched line item granularity? (IS has "Depreciation & Amortization" but CF has separate "Depreciation" and "Amortization")
- What tolerance should flow validation use? (0.01%? 1%? Different for different flows?)
- How do we handle reclassifications? (Company reclassified an expense from Operating to Finance → IS total unchanged but CF breakdown different)
- How do we validate flows when the CF statement uses the INDIRECT method vs DIRECT method?
- What if only 2 of 3 statements are available? (Interim report with no CF statement)
- How do we explain violations to the analyst? ("Net Income per IS is 500K, but Retained Earnings change in BS is only 400K. Possible causes: undisclosed dividend of 100K, OCI adjustment of 100K, or mapping error.")
- Can we auto-detect the CAUSE of a flow violation? (If the difference exactly equals a mapped value in "Other" categories, suggest a reclassification)

**Research areas:**
- Financial statement articulation (accounting textbook fundamentals, but formalized for automation)
- Indirect method vs Direct method cash flow reconciliation
- IFRS vs US GAAP differences in statement articulation
- Statement of Changes in Equity as a reconciliation bridge
- EY, KPMG, Deloitte technical guidance on statement consistency checks

### R4: Anomaly Score Aggregation

Design the system that combines all three layers into a single, meaningful score.

**Scoring framework:**
```
Anomaly Score (0-100) = weighted sum of:
  • Benford Layer Score (0-100)        × weight_benford
  • Ratio Consistency Score (0-100)    × weight_ratio  
  • Cross-Statement Score (0-100)      × weight_flow
  
Where:
  weight_benford + weight_ratio + weight_flow = 1.0
  
Recommended defaults:
  weight_benford = 0.15  (lowest: Benford signals are subtle)
  weight_ratio = 0.35    (medium: ratio flags are common but contextual)
  weight_flow = 0.50     (highest: flow violations are serious errors)
```

**Score interpretation:**
| Score Range | Badge Color | Meaning |
|---|---|---|
| 0-15 | 🟢 Green | Clean — ready for approval |
| 16-30 | 🟢 Green | Minor items — review optional |
| 31-50 | 🟡 Amber | Attention needed — review before approval |
| 51-70 | 🟠 Orange | Significant issues — investigate before approval |
| 71-100 | 🔴 Red | Critical — DO NOT approve without investigation |

**Questions to answer:**
- Should the score be absolute or relative? (Absolute: always same scale. Relative: compared to client's historical scores.)
- How do we handle false positives? (Legitimate unusual events like M&A, IPO, restructuring will trigger flags legitimately)
- Should the analyst be able to "acknowledge" flags? (Mark as "reviewed — legitimate because of M&A" to suppress in future)
- How do we track anomaly score trends over time? (Is client X's data quality improving or degrading?)
- Can the anomaly score influence the workflow? (Score > 70 auto-escalates to senior reviewer?)
- Should the score decomposition be visible? (Manager sees: "Anomaly Score 45 = Benford 12 + Ratio 22 + Flow 11")

**Research areas:**
- Multi-criteria decision analysis (MCDA) for score aggregation
- Audit risk assessment models (AICPA audit risk model)
- Composite index construction (methodologies from World Bank indices, credit ratings)
- Human-interpretable anomaly scoring systems

### R5: Specific Fraud Signal Detection

Beyond general anomaly detection, research specific patterns that indicate potential financial fraud.

**Known fraud patterns:**

| Pattern | Detection Method | Examples |
|---|---|---|
| **Round number prevalence** | Excess of values ending in 000 | Revenue exactly 10,000,000 |
| **Last-digit analysis** | Non-uniform last digits | Last digits clustering on 0 and 5 |
| **Too-smooth trends** | Suspiciously consistent growth rates | Revenue grows exactly 8% every quarter |
| **Earnings management** | Small positive profits (just above zero) | NI clustering just above 0 |
| **Revenue/Receivables divergence** | Revenue growing but receivables growing faster | Channel stuffing indicator |
| **Accrual quality** | High accruals relative to cash flow | Aggressive accounting |
| **Big bath accounting** | Massive one-time charges in bad year | Restructuring charges after CEO change |
| **Cookie jar reserves** | Reserves building in good years, released in bad | Smoothing via provisions |

**Questions to answer:**
- Should we expose fraud signals differently from data quality signals? (Fraud = escalate to compliance. Data quality = send back to analyst.)
- What are the legal implications of labeling something as "potential fraud"? (Should we use softer language: "unusual pattern requiring review"?)
- How do we calibrate fraud detection to avoid excessive false positives? (Banks are already hyper-paranoid — too many false alarms = ignored)
- Can we combine multiple weak signals into a strong fraud indicator? (Any one pattern alone isn't suspicious, but 4/8 patterns present = very suspicious)
- What is the regulatory expectation? (Does anti-money laundering compliance require us to report certain patterns?)

**Research areas:**
- Beneish M-Score model (8 financial ratios that predict earnings manipulation)
- Dechow et al. (2011) — "Predicting Material Accounting Misstatements"
- University of Michigan's F-Score for predicting financial statement fraud
- Benford's Law applied to fraud detection (Nigrini's comprehensive framework)
- Machine learning for fraud detection in financial statements (survey papers)
- ACFE (Association of Certified Fraud Examiners) "Report to the Nations" methodology

### R6: Bank-Specific vs General Anomaly Detection

Financial statements look very different for banks vs corporates vs insurance companies. Research how to adapt anomaly detection per entity type.

**Banking-specific checks:**
- Net Interest Margin bounds (typically 1-5%, outside = suspicious)
- Provision coverage ratio (provisions / NPLs)
- Regulatory capital ratios (CET1, Tier 1, Total Capital — within regulatory bounds)
- Deposit-to-loan ratio
- IFRS 9 staging migration (Stage 1 → 2 → 3 movements — logical consistency)

**Corporate-specific checks:**
- Inventory days vs industry
- Capex as % of depreciation (< 50% sustained = underinvestment)
- Working capital cycle analysis

**Insurance-specific checks:**
- Combined ratio components
- Loss reserve adequacy
- Solvency ratios

**Questions to answer:**
- How many entity-type profiles do we need? (Banking, Corporate, Insurance, PE/Fund, Government — 5 profiles?)
- Should entity type be auto-detected or user-configured?
- How do we handle conglomerates? (Banking + insurance subsidiary — apply both profiles?)

---

## Competitive Analysis Required

1. **CaseWare IDEA (now Caseware Analytics)** — Their data analytics for audit. Benford's Law implementation.
2. **ACL Analytics (now Galvanize / Diligent)** — Audit analytics platform. Anomaly detection capabilities.
3. **Kensho (S&P)** — AI for financial analysis. Any anomaly detection?
4. **Palantir Foundry** — Used by some banks for financial crime detection. Relevant patterns?
5. **Bloomberg Terminal** — Anomaly flagging on financial data?
6. **Academic**: Beneish M-Score implementations, F-Score models. What's the state-of-the-art?
7. **Big 4 audit tools**: EY Helix, KPMG Clara, Deloitte Omnia, PwC Halo — what analytics do they include?

---

## Deliverables

1. **Benford's Law Module Specification** — Complete algorithm, test selection, thresholds, exceptions, per-value attribution
2. **Ratio Analysis Framework** — Full ratio library (20+ ratios), three comparison dimensions, flagging rules
3. **Cross-Statement Validator** — All IS→BS→CF flow checks with tolerance handling and cause inference
4. **Anomaly Score Model** — Aggregation weights, score interpretation guide, badge system
5. **Fraud Signal Catalog** — Known fraud patterns, detection methods, legal/language considerations
6. **Entity-Type Profiles** — Banking vs Corporate vs Insurance anomaly configurations
7. **UI Wireframes** — Anomaly panel in spread workspace, score badge, explanation cards
8. **Benchmark Dataset** — Synthetic test data with injected anomalies for validation
9. **Implementation Roadmap** — Phase 1 (basic), Phase 2 (advanced), Phase 3 (fraud signals)

---

## Success Criteria

FADE is successful when:
- Catches >80% of deliberately injected errors that a human auditor would catch
- False positive rate <15% (85% precision) — managers trust the system
- Benford's analysis runs in <200ms for a 100-value spread
- Ratio analysis covers ≥20 core financial ratios with 3-dimension comparison
- Cross-statement validation catches IS→BS→CF flow errors with >90% recall
- Anomaly Score correlates with actual error rate in production (calibrated)
- Managers report higher confidence in approved spreads (measured via survey)
- At least one fraud-relevant pattern is detected in pilot deployment (proves value beyond data quality)
