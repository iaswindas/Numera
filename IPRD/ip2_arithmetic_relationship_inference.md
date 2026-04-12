# IP-2: Arithmetic Relationship Inference Engine (ARIE)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **novel algorithm** that automatically discovers arithmetic relationships between values in financial documents without being told what those relationships are. This is the single most patent-worthy innovation in the Numera platform.

**The problem**: A Balance Sheet shows values [100, 250, 50, 400]. A human analyst instantly recognizes 100 + 250 + 50 = 400 (the first three are sub-items of the total). Current systems require the analyst to manually build this expression. ARIE discovers it automatically.

**Why this is groundbreaking**: Every competing spreading tool does label-matching (match "Revenue" to "Revenue"). ZERO competitors do arithmetic relationship discovery. An analyst could receive a document in a language they don't read, and ARIE would still correctly map values by discovering the arithmetic structure.

---

## Context: What Exists Today

### Current Codebase
- **Expression Engine** (`ml-service/app/ml/expression_engine.py`):
  - Has `_try_sum_expression()` — checks if children (detected by indentation) sum to a total
  - Only works within a single table section (same indentation group)
  - Only checks direct SUM (no differences, no multi-level hierarchies)
  - No cross-section discovery (can't find that IS Net Income = BS Retained Earnings change)
  
- **Formula Engine** (`backend/.../FormulaEngine.kt`):
  - Evaluates given formulas (SUM, IF, ABS, arithmetic)
  - Does NOT discover formulas — it only computes expressions that are already defined
  
- **Semantic Matcher** (`ml-service/app/ml/semantic_matcher.py`):
  - SBERT cosine similarity
  - Label-level matching only — no value awareness

### The Gap
There is NO system that looks at extracted values and says: "I notice that 15,234 + 8,112 + 2,340 = 25,686, which matches the Total Assets field in the template. Therefore, these three values are the components of Total Assets."

---

## Research Directives

### R1: Core Algorithm — Subset Sum with Accounting Constraints

The fundamental problem is a variant of the **Subset Sum Problem** (NP-hard in general), but with domain-specific constraints that dramatically prune the search space.

**Formal problem definition:**
```
Given:
  - Source values S = {s₁, s₂, ..., sₙ} (extracted from PDF, n ≈ 30-200)
  - Target values T = {t₁, t₂, ..., tₘ} (from model template, m ≈ 50-100)
  - Accounting constraints C (identities like A = L + E)
  - Tolerance ε (for rounding, typically 0.001-0.01)

Find:
  - For each tⱼ ∈ T, find a subset Sⱼ ⊆ S and operators {+, -} such that:
    |tⱼ - Σ(±sᵢ for sᵢ ∈ Sⱼ)| ≤ ε × |tⱼ|
  - Subject to:
    - Each sᵢ used at most once across all expressions (exclusivity)
    - Accounting constraints C are satisfied globally
    - Minimize total number of unmapped source values
```

**Questions to answer:**
- What is the practical search space size? (n=100 values → 2¹⁰⁰ subsets theoretically, but with constraints?)
- What pruning strategies reduce this to tractable? (Zone-based: IS items only match with IS values. Hierarchy: children must be in same section as parent. Value-based: subset sum can't exceed target by more than ε.)
- What is the expected runtime for a typical financial document (100 source values, 60 target items)?
- Should we use branch-and-bound, dynamic programming, constraint propagation, or hybrid?
- How do we handle DIFFERENCES? (e.g., Gross Profit = Revenue - Cost of Sales. This requires a signed operator per element.)
- How do we handle RATIOS? (e.g., Debt/Equity = Total Debt ÷ Total Equity. This requires detecting division relationships.)

**Research areas:**
- Subset Sum Problem variants with tolerances
- Constraint Satisfaction Problems (CSPs) — arc consistency, backtracking with forward checking
- OR-Tools / Google CP-SAT solver for financial reconciliation
- Knapsack problem algorithms with accounting domain constraints
- Integer Linear Programming formulations for financial matching

### R2: Multi-Pass Architecture

Design the complete algorithm as a multi-pass pipeline where each pass adds intelligence:

**Pass 1 — Semantic Pre-filtering (existing)**
- Use SBERT to match labels when possible
- Mark high-confidence matches as "anchored" (reduce search space for subsequent passes)
- Zone-aware: penalize cross-zone matches (IS source → BS target gets -30% confidence)

**Pass 2 — Direct Value Matching**
- For each unmapped target item, search for a source value that is exactly equal (within ε)
- Prioritize by: same zone > adjacent zone > any zone
- Handle unit conversion: try multiplying source by 1000 or 1,000,000 before matching

**Pass 3 — Arithmetic Discovery (the novel part)**
- For each unmapped target item, search for COMBINATIONS of source values
- Start with the smallest subsets (pairs, then triples, then quads)
- Apply zone constraint: all sources in a combination must be from the same financial statement section
- Apply hierarchy constraint: sources should be at the same or lower indent level than their total
- Validate: if a combination is found, check it against the value's position in the document structure

**Pass 4 — Cross-Statement Flow Validation**
- Verify known accounting flows:
  - Net Income (IS) → Change in Retained Earnings (BS)
  - Depreciation (IS) → Add-back in Cash Flow from Operations
  - Capital Expenditure (CF Investing) → Change in Fixed Assets (BS) + Depreciation
  - Dividends Paid (CF Financing) → Reduction in Retained Earnings
  - Opening Cash + Net Cash Flow = Closing Cash
- Use these flows to CORRECT or CONFIRM earlier pass results
- If IS Net Income was mapped but BS Retained Earnings change doesn't match → flag discrepancy

**Pass 5 — Global Consistency Optimization**
- Solve for the assignment that minimizes total unexplained variance across ALL target items simultaneously
- This is a global optimization, not greedy per-item
- Use constraint propagation: once item A is mapped to sources {s₁, s₂}, those sources are removed from the candidate pool for item B

**Questions to answer:**
- What is the optimal ordering of passes? (Should arithmetic discovery come before or after cross-statement validation?)
- How do we handle conflicts? (Multiple arithmetic combinations could produce the same target value)
- How do we rank competing combinations? (Prefer fewer sources? Prefer same-page sources? Prefer sources with higher semantic similarity even if not exact match?)
- What confidence score should each pass assign? (Pass 1 semantic match = 0.9, Pass 3 arithmetic discovery = 0.75?)

### R3: Accounting Identity Database

Build a comprehensive database of accounting identities that ARIE uses for cross-validation.

**Core identities (must have):**
```
# Balance Sheet
Assets = Liabilities + Equity
Current Assets + Non-Current Assets = Total Assets
Current Liabilities + Non-Current Liabilities = Total Liabilities
Share Capital + Reserves + Retained Earnings = Total Equity

# Income Statement
Revenue - Cost of Sales = Gross Profit
Gross Profit - Operating Expenses = Operating Profit (EBIT)
EBIT - Interest + Other = Profit Before Tax
PBT - Tax = Net Income

# Cash Flow Statement
Operating + Investing + Financing = Net Change in Cash
Opening Cash + Net Change = Closing Cash

# Cross-Statement
Net Income (IS) = Change in Retained Earnings (BS) + Dividends
Depreciation (IS) = Depreciation add-back (CF)
Capex (CF) = Change in Gross Fixed Assets (BS)
```

**Extended identities (research required):**
- What are the IFRS-specific identities? (e.g., OCI treatment, IFRS 16 lease adjustments)
- What are the US GAAP-specific identities? (e.g., AOCI, ASC 842)
- What are banking-specific identities? (e.g., NIM = Interest Income - Interest Expense / Average Earning Assets)
- What are insurance-specific identities?
- How do consolidation adjustments affect these identities?
- What about seasonality in interim reports (Q1+Q2+Q3+Q4 = Annual)?

**Questions to answer:**
- How many distinct identities exist across IFRS + US GAAP + regional standards?
- How do we handle "soft" identities vs "hard" identities? (A=L+E is always true; Revenue-COGS=GP may not hold if "Other Income" is included)
- Can we learn NEW identities from data? (Symbolic regression approach: given historical spreads, discover arithmetic relationships that consistently hold)
- How do we weight identity violations in confidence scoring?

### R4: Tolerance & Rounding Handling

Financial documents have rounding at multiple levels. Research how to handle this robustly.

**Rounding scenarios:**
1. **Display rounding**: Values shown in thousands may round differently (15,234 + 8,112 = 23,346 but total shows 23,347 due to individual rounding)
2. **Currency conversion**: Multi-currency reports may have conversion rounding
3. **Restated figures**: Prior year values may differ slightly from what was originally reported
4. **Consolidation rounding**: Group totals ≠ exact sum of subsidiaries due to elimination adjustments
5. **Regulatory rounding**: Central bank reports may enforce specific rounding rules

**Questions to answer:**
- What tolerance ε should be used? (Fixed: ±1 unit? Relative: 0.1%? Adaptive based on document type?)
- How do we distinguish "rounding difference" from "wrong match"? (If the difference is exactly 1 in the last digit, it's likely rounding. If it's 5% off, it's likely wrong.)
- Should we have different tolerances for different types? (Balance sheet totals: strict ε=0.001. Cash flow reconciliation: relaxed ε=0.01.)
- How do we handle "amounts in thousands" where individual items are rounded to nearest thousand?

### R5: Performance & Scalability

The algorithm must be fast enough for interactive use.

**Questions to answer:**
- What is the target latency? (Entire ARIE pipeline should complete in <5 seconds for interactive use, <30 seconds for batch)
- What parallelization opportunities exist? (Each target item can be searched independently? Cross-validation must be sequential?)
- Can we use GPU acceleration? (Matrix operations for batch subset-sum checking?)
- What is the memory footprint for a large document (200 source values)?
- Can we pre-compute and cache the search results for incremental updates (analyst changes one value → only affected expressions need re-evaluation)?

### R6: Explainability & Analyst Trust

ARIE must explain its reasoning so analysts trust the results.

**Questions to answer:**
- How do we present discovered expressions to analysts? ("Total Assets = Cash (p.3) + Receivables (p.3) + Inventory (p.4) + PP&E (p.5)")
- How do we communicate confidence? ("Arithmetic match: 99.9% accuracy, 3 sources from same section, passes A=L+E check")
- How do we handle the case where ARIE finds a valid arithmetic match but the labels DON'T match? ("We found 100+250+50=400, but label says 'Miscellaneous'. Possible mislabeling?")
- What UI should the expression editor provide for accepting/modifying ARIE suggestions?
- How do we handle multiple valid combinations? (Show top-3 with confidence rankings?)

---

## Competitive Analysis Required

1. **Does any existing product do arithmetic relationship discovery?** (Search academic literature, patent databases, product documentation for Moody's/S&P/nCino/Finastra)
2. **Patent landscape**: Search USPTO/EPO for patents related to "automated arithmetic relationship detection in financial documents"
3. **Academic work**: FinQA benchmark, TAT-QA, HybridQA — any constraint-satisfaction approaches?
4. **Symbolic regression companies**: Are tools like TuringBot, Eureqa, or PySR applicable to this problem domain?
5. **Reconciliation software**: Products like ReconArt, Trintech, BlackLine — do they use automated matching algorithms we can learn from?

---

## Deliverables

1. **Algorithm Specification** — Complete pseudocode for all 5 passes with complexity analysis
2. **Accounting Identity Database** — Comprehensive list across IFRS/GAAP/Banking with classification (hard/soft)
3. **Tolerance Model** — Adaptive tolerance specification with justification
4. **Benchmark Design** — Test suite with known arithmetic relationships for validation
5. **Patent Claim Draft** — Core claims for the novel aspects of ARIE (constraint satisfaction + accounting identity validation)
6. **Risk Assessment** — False positive analysis, edge cases, failure modes
7. **Performance Budget** — Projected latency per pass, memory usage, optimization opportunities

---

## Success Criteria

ARIE is successful when:
- Given a 3-statement financial document (IS + BS + CF), it discovers >85% of arithmetic relationships automatically
- False positive rate (incorrect arithmetic match) is <5%
- Cross-statement flow validation catches >90% of mapping errors
- Full pipeline completes in <5 seconds for a typical document
- Discovered expressions are accepted by analysts >80% of the time without modification
- At least 2 novel patentable claims are identified
