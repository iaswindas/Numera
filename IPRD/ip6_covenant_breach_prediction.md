# IP-6: Covenant Breach Prediction Network (CBPN)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **predictive intelligence engine** that forecasts covenant breaches before they happen, simulates "what-if" scenarios, and detects early warning patterns by learning from cross-client financial trajectories. This transforms the covenant module from a backward-looking tracking tool into a **forward-looking risk intelligence platform**.

**The problem**: Current covenant systems tell you "this covenant IS breached." Relationship managers need to know: "This covenant WILL breach in 2 quarters if current trends continue" and "What happens if the client's revenue drops 15%?" No competitor provides this.

---

## Context: What Exists Today

### Current Codebase
- **CovenantPredictionService** (`backend/.../covenant/application/CovenantPredictionService.kt`):
  - Uses **simple linear regression** on last 8 data points
  - Single-variate: only looks at the covenant's own values
  - Extrapolates 1 period ahead
  - Maps trend strength to probability (0.0-1.0)
  - No cross-client patterns, no macro indicators, no multi-variate analysis

- **Covenant Monitoring** (`backend/.../covenant/application/CovenantMonitoringService.kt`):
  - Auto-generates monitoring items based on frequency (quarterly, semi-annual, annual)
  - Calculates current status: MET / BREACHED / OVERDUE
  - Links to spread values for financial covenant types
  - No predictive capability beyond the PredictionService

- **Spread Values**: Historical spread data is available per customer per period (IS + BS + CF values). This is the core data source for prediction features.

### The Gap
1. Single-variate prediction (only covenant's own value) — ignores correlated financial ratios
2. No "what-if" scenario simulation
3. No cross-client pattern learning (client A trending like client B did before breach)
4. No leading indicator detection
5. No macro-economic factor incorporation
6. No ensemble methods or confidence intervals on predictions

---

## Research Directives

### R1: Feature Engineering for Covenant Prediction

Design the feature set that powers breach prediction models.

**Feature categories:**

| Category | Examples | Source |
|---|---|---|
| **Covenant History** | Trailing 4-8 values, trend direction, volatility, distance-from-threshold | Monitoring items |
| **Financial Ratios** | Current Ratio, Debt/EBITDA, Interest Coverage, DSCR, Working Capital, etc. (20+) | Historical spreads |
| **Ratio Trajectory** | Slope, acceleration, curvature of each ratio over 4-8 periods | Computed from spreads |
| **Cross-Ratio Correlations** | Which ratios are moving together? (Multicollinearity detection) | Computed |
| **Industry Benchmarks** | Client's ratios vs industry median/quartile | External or aggregated |
| **Macro Indicators** | Interest rates, GDP growth, sector indices | External APIs |
| **Qualitative Signals** | Auditor change, restated figures, going concern opinion | Document metadata |
| **Seasonal Patterns** | Q1 vs Q4 performance variation | Historical analysis |

**Questions to answer:**
- What is the minimum number of historical periods needed for reliable prediction? (2? 4? 8?)
- What is the optimal look-forward horizon? (1 quarter? 2 quarters? 1 year?)
- How do we handle missing data? (Not all periods have all ratios. Imputation strategy?)
- Which features are most predictive of breach? (Feature importance analysis needed)
- How do we handle different covenant types? (Financial vs non-financial — different features)
- Should features be normalized per-industry or per-client? (Z-score within industry? Percentile rank?)
- Can we compute "early warning" features? (Rate of change accelerating, crossing moving averages)

**Research areas:**
- Credit risk feature engineering (Basel II/III PD models)
- Altman Z-Score components and extensions
- Merton model: distance-to-default concept
- Time-series feature engineering libraries (tsfresh, tsfel)
- Financial distress prediction literature (academic)

### R2: Model Architecture Selection

Research and recommend the optimal model architecture for breach prediction.

**Candidate models:**

| Model | Strengths | Weaknesses | Interpretability |
|---|---|---|---|
| **Gradient Boosted Trees** (XGBoost/LightGBM) | Works with tabular data, handles missing values, feature importance | Not natively sequential | High (SHAP values) |
| **LSTM / GRU** | Captures temporal patterns, sequential data | Needs more data, harder to interpret | Medium |
| **Temporal Fusion Transformer** (TFT) | Multi-horizon forecasting, attention over past values, interpretable | Complex, needs moderate data | High (attention weights) |
| **Prophet** (Facebook) | Time-series with seasonality, changepoint detection | Univariate only | High |
| **Ensemble** (XGBoost features + LSTM sequence) | Best of both worlds | Most complex to implement | Medium |
| **Bayesian Structural Time-Series** | Uncertainty quantification, counterfactual estimates | Slower, requires stats expertise | Very High |

**Questions to answer:**
- Given typical data volume (4-20 periods per covenant), which model family is most appropriate? (Deep learning may need more data than we have)
- Should the model predict a continuous probability or a binary (breach/no-breach)?
- Should we have separate models per covenant TYPE (DSCR covenant vs Current Ratio covenant) or a single model?
- How do we produce **confidence intervals** on predictions? (Not just "78% breach probability" but "78% ± 12%" with 90% CI)
- Can we use transfer learning? (Pre-train on public financial data, fine-tune on client data)
- What is the minimum viable model that still beats linear regression significantly?

**Research areas:**
- Temporal Fusion Transformer (Lim et al., 2021) — multi-horizon probabilistic forecasting
- CreditRisk+ and KMV-Merton models — structural approaches
- XGBoost for credit rating prediction (industry standard)
- Bayesian approaches to small-sample financial forecasting
- Ensemble methods for time-series credit risk

### R3: Scenario Simulation Engine

Design a "what-if" simulator that lets managers stress-test covenant compliance under hypothetical scenarios.

**Scenario types:**
1. **Single-variable shock**: "What if revenue drops 15%?"
2. **Multi-variable scenario**: "What if revenue drops 15% AND costs increase 10%?"
3. **Named scenarios**: "Apply the 2020 COVID shock scenario" (pre-defined stress patterns)
4. **Macro-linked scenarios**: "What if interest rates rise 200bps?" → cascaded impact on all ratios
5. **Custom timeline**: "Revenue drops 15% in Q1, recovers 5% in Q2, flat Q3-Q4"

**How it works conceptually:**
```
1. User selects scenario parameters (variable, shock %, timeline)
2. System takes current financial state (latest spread values)
3. Apply shock to affected variables:
   - Direct: Revenue × 0.85
   - Cascaded: Lower revenue → lower profit → lower retained earnings → higher leverage
4. Recompute ALL financial ratios with shocked values
5. Recompute ALL covenant values (DSCR, current ratio, etc.)
6. Output: per-covenant breach probability under scenario vs baseline
7. Visualization: slider controls → live probability update
```

**Questions to answer:**
- How do we model cascaded effects? (Revenue shock → how much does Gross Profit change? Depends on cost structure — how do we know the cost structure?)
- Should cascading use fixed rules (Revenue↓ → GP = Revenue × GP_margin) or learned relationships (from historical data, Revenue↓15% → GP typically ↓20-25% for this client)?
- How do we present scenario results? (Table: covenant → baseline probability → scenario probability → delta? Waterfall chart?)
- Can we auto-generate "worst case" scenarios? (Optimize: what combination of shocks causes the MOST breaches?)
- How do we handle non-linear effects? (Small revenue drop = no breach. Large drop = multiple covenant cascade)
- Should we pre-compute scenario results for common shocks (5%, 10%, 15%, 20%) for instant display?

**Research areas:**
- Basel III ICAAP stress testing methodology
- Scenario analysis in risk management (reverse stress testing)
- Sensitivity analysis techniques (Monte Carlo, tornado diagrams)
- Financial simulators (Moody's Analytics CreditEdge, S&P Risk Solutions)
- Interactive dashboard design for scenario what-if analysis

### R4: Cross-Client Pattern Recognition

Research how to detect early warning patterns by comparing a client's trajectory against historical breach cases across ALL clients.

**The insight**: "Client A's Debt/EBITDA trajectory over the last 4 quarters looks remarkably similar to what Client B experienced 2 quarters before they breached their DSCR covenant."

**Approach:**
1. For each historical breach event, extract the 4-8 period trajectory of ALL financial ratios leading up to the breach
2. Build a library of "breach trajectory patterns" (anonymized — no client names, just ratio trajectories)
3. For each active covenant, compare current trajectory against the pattern library
4. If current trajectory matches a historical breach pattern with >80% similarity → raise early warning

**Questions to answer:**
- What similarity metric works best for financial trajectory comparison? (DTW — Dynamic Time Warping? Euclidean? Cosine on trajectory embeddings?)
- How many breach events are needed to build a reliable pattern library? (50? 200? 1000?)
- How do we handle industry differences? (Banking breach patterns ≠ manufacturing breach patterns. Cluster by industry?)
- What is the optimal trajectory length for matching? (Last 4 quarters? Last 8 quarters? Adaptive?)
- How do we avoid false alarms? (Many trajectories look "concerning" but never breach. What's the precision/recall tradeoff?)
- Can we use this for POSITIVE patterns too? ("Client A's trajectory matches companies that recovered from near-breach")

**Research areas:**
- Dynamic Time Warping for financial time series comparison
- Shape-based clustering of time series (k-Shape algorithm)
- Matrix Profile for anomaly detection in financial sequences
- Early warning systems in banking (Kaminsky-Reinhart indicators)
- Credit migration forecasting

### R5: Leading Indicator Detection

Research automated detection of which financial ratios PREDICT breach for each covenant type.

**The concept**: For a DSCR covenant, the most predictive leading indicator might not be DSCR itself. It might be "Working Capital / Revenue" which starts declining 3 quarters before DSCR breaches. The system should discover these leading indicators automatically.

**Approach:**
- Granger causality tests: does variable X predict variable Y?
- Cross-correlation analysis with lag: at what lag does the correlation peak?
- Feature importance from the prediction model (SHAP values over time)
- Mutual information between lagged features and breach outcome

**Questions to answer:**
- Are leading indicators universal or client-specific? (Working Capital leads DSCR for ALL banking clients? Or only for some?)
- How stable are leading indicators over time? (Do they change with economic cycles?)
- Can we present leading indicators to the manager? ("We're watching Working Capital/Revenue — it's the best early warning for DSCR covenant compliance for this client based on historical patterns.")
- What is the expected lead time? (How many periods of advance warning do leading indicators provide?)

**Research areas:**
- Granger causality in financial time series
- Transfer entropy for financial leading indicator detection
- SHAP time-series feature importance
- Econometric leading indicator models (Conference Board methodology)

---

## Competitive Analysis Required

1. **Moody's CreditEdge** — Their Expected Default Frequency (EDF) model. How does it work? Can we do something similar at the covenant level?
2. **S&P Capital IQ Pro Probability of Default** — Their PD model architecture
3. **Bloomberg DRC (Default Risk Calculator)** — Methodology
4. **nCino Covenant Monitoring** — Any predictive capabilities?
5. **Academic**: Beaver (1966) financial ratio failure prediction → Altman (1968) Z-Score → modern ML extensions. What's the state of the art?
6. **Regulatory**: Basel II/III IRB models — how do they compute PD/LGD/EAD? Can we adapt for covenant breach probability?

---

## Deliverables

1. **Feature Engineering Specification** — Complete feature list with computation logic, data sources, handling of missing data
2. **Model Architecture Recommendation** — Selected model with justification, alternatives considered
3. **Scenario Simulator Design** — Input/output specification, cascade model, UI wireframes
4. **Cross-Client Pattern Library** — Anonymization protocol, trajectory matching algorithm, pattern storage
5. **Leading Indicator Framework** — Detection algorithm, stability analysis, presentation design
6. **Training Data Strategy** — How to bootstrap with limited breach history, synthetic data generation
7. **Backtesting Framework** — How to validate predictions against historical data
8. **Risk Assessment** — Model risk, overfitting mitigation, regulatory model risk management compliance

---

## Success Criteria

CBPN is successful when:
- Breach prediction accuracy (AUROC) exceeds 0.80 for covenants with ≥4 periods of history
- Predictions provide ≥2 quarter advance warning for >70% of breaches
- Scenario simulation responds in <2 seconds for interactive use
- Cross-client patterns identify >50% of breach cases that the client's own history alone would miss
- Leading indicators provide actionable early warnings that managers can understand and act on
- False alarm rate is <20% (80% precision at the chosen recall threshold)
