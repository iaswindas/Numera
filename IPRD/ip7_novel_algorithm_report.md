# IP-7 Novel Algorithm Report: Financial Anomaly Detection

## 1. Critical Analysis of Attached Context

The attached IP-7 brief is directionally strong, but the real technical gap is sharper than the brief states. The current platform does not have an anomaly detection engine. It has a deterministic reconciliation engine with optional formula cells.

### Deconstruction of the Current Methodologies

The current backend path is structurally simple.

1. Template validations are persisted as name, expression, and severity tuples in the model layer.
2. The formula engine tokenizes and evaluates scalar arithmetic expressions over mapped line-item values.
3. The spread orchestration path and spread submission path execute each validation expression once and emit a PASS or FAIL result with a scalar difference.

Formally, let $x \in \mathbb{R}^n$ be the vector of mapped line-item values for one spread and let $f_k(x)$ be the $k$-th configured validation expression. The current system computes

$$
v_k(x) =
\begin{cases}
\text{PASS}, & \text{if } f_k(x) = 0, \\
\text{FAIL}, & \text{otherwise.}
\end{cases}
$$

This is not statistical inference. It is deterministic rule evaluation over a flat vector.

The attached FADE brief advances beyond that baseline by proposing three detector families:

1. Benford-style digital analysis.
2. Ratio consistency analysis across self-history, industry, and model expectation.
3. Cross-statement flow validation across income statement, balance sheet, and cash flow.

That is the correct problem decomposition at the business level. However, the brief still treats the three layers as mostly independent subsystems whose outputs are later combined by a weighted score. That leaves the central theoretical problem unsolved.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

#### 1.1 Representation mismatch: a spread is not a flat vector

Financial statements are articulated objects. Revenue, retained earnings, receivables, depreciation, debt, and cash are coupled across statement type and time. The current implementation stores values as independent cells with optional formulas. The attached brief adds more detectors, but it still does not define a single state representation that captures articulation, temporal continuity, and extraction provenance jointly.

Without that representation, the system can flag inconsistencies but cannot localize the minimal set of cells most likely responsible for the inconsistency.

#### 1.2 The proposed weighted-sum score is not statistically coherent

The brief proposes

$$
\text{AnomalyScore} = w_B S_B + w_R S_R + w_F S_F,
$$

with $w_B + w_R + w_F = 1$.

That expression is easy to communicate, but it is theoretically weak. Benford deviation, ratio outlierness, and articulation failure do not live on the same scale, are not conditionally independent, and do not have comparable noise models. A linear weighted sum therefore has no direct probabilistic interpretation and can be badly miscalibrated.

#### 1.3 Pure Benford analysis is unstable in the stated sample regime

The brief assumes one spread may contain 40 to 80 analyzable values. That is enough for weak directional digital evidence, but not enough for naive asymptotic testing to be reliable. Classical chi-squared Benford testing assumes expected cell counts large enough for asymptotic validity. With small samples, leading-digit deviation can be dominated by industry structure, statement formatting, or legitimate concentration in a narrow scale range.

A pure global Benford prior $\pi_B(d) = \log_{10}(1 + 1/d)$ is therefore insufficient. The missing theoretical component is a profile-conditioned prior that blends Benford, entity type, magnitude bucket, and client history.

#### 1.4 Ratio analysis in the brief is univariate where the problem is multivariate

The brief proposes many sensible ratios, but the flagging logic is still phrased as independent thresholding: swing greater than $20\%$, or $2$ standard deviations from industry, or disagreement with a model expectation. That is not enough.

Ratios are strongly dependent. Gross margin, operating margin, ROA, ROE, asset turnover, leverage, and accrual quality are coupled through shared numerators and denominators. Independent thresholding duplicates alerts, amplifies denominator noise, and cannot distinguish one wrong line item from a legitimate business shock that propagates coherently through several ratios.

#### 1.5 Flow validation without inverse attribution is underdetermined

Cross-statement flows are the strongest error signal in the brief, but rule firing alone does not identify cause. If

$$
\Delta \text{PP\&E} + \text{Depreciation} - \text{Capex} \neq 0,
$$

the violating residual could be explained by a bad depreciation line, a missing capex line, a scale-factor error, or a period mismatch. A binary rule engine reports the existence of inconsistency, but not the smallest explanatory set of cells.

That inverse problem is the main theoretical gap. It requires optimization, not just validation.

#### 1.6 Missingness, granularity mismatch, and partial statements are not formally modeled

The brief raises indirect vs direct cash flow and granularity mismatches, but does not specify a mathematical treatment. In practice, financial spreading is full of partial observation:

- Some statements are missing.
- Some concepts appear aggregated in one statement and disaggregated in another.
- Some values are OCR-extracted with low confidence.
- Some values are manually overridden and therefore semantically stronger than raw AI outputs.

Those facts should directly affect anomaly attribution. The current architecture and the brief's layer design do not yet encode them into the scoring objective.

#### 1.7 Fraud signals and data-quality signals are conflated

The brief correctly lists round-number prevalence, receivables divergence, accrual quality, and smooth-growth patterns. However, those are not equally strong indicators and should not be collapsed into a single semantic label. In a regulated setting, the system should detect abnormality, not declare intent. The correct abstraction is evidence of inconsistency or manipulation-like patterning, then a separate escalation policy.

#### 1.8 The proposal lacks a calibrated root-cause model

The brief wants per-spread scores and flagged items. Standard detector ensembles can produce the score, and rule engines can produce the flags, but that still does not yield credible root cause. A production system needs a mechanism that says, in effect:

"If I were allowed to minimally repair this spread so that all detector families become jointly consistent, which cells would I change first?"

That question is absent from the attached design, yet it is exactly the question an analyst asks.

#### 1.9 Naive multi-layer implementations incur avoidable computational waste

If the brief is implemented literally, each layer would separately scan the spread, recompute overlapping derived quantities, and then perform per-flag attribution. A brute-force leave-one-out attribution strategy would require re-evaluating all constraints for each cell, which is at least

$$
O\big(n (|E_F| + q + d)\big),
$$

where $n$ is the number of cells, $|E_F|$ is the number of flow constraints, $q$ is the number of ratios, and $d$ is the number of digital tests. That is unnecessary. The structure of the problem is sparse and should be exploited directly.

### Summary of the Theoretical Gap

The missing SOTA component is not another detector. It is a unified inference procedure that:

1. Represents the spread as an articulated temporal object.
2. Couples flow, ratio, and digit evidence in one optimization problem.
3. Uses extraction confidence and manual overrides as reliability priors.
4. Returns both a calibrated spread-level score and a sparse line-item attribution.

## 2. The Novel Algorithmic Proposal

### Name

**SAPHIRE-F: Sparse Articulation-Prior Hypergraph Inference for Reconciled Financial Exceptions**

### Core Intuition

SAPHIRE-F is based on one central claim:

> A clean financial spread lies near a low-energy articulation manifold defined jointly by accounting flow constraints, contextual ratio geometry, and profile-conditioned digital regularity.

Instead of asking whether each detector independently fires, SAPHIRE-F asks a harder and more useful question:

> What is the minimum weighted correction to the observed spread that would project it back onto the manifold of plausible statements?

That correction vector is the anomaly explanation.

This differs fundamentally from the current backend and from the attached FADE design.

1. The current backend evaluates deterministic formulas only.
2. The attached FADE brief treats Benford, ratios, and flows as parallel detectors with a post-hoc weighted sum.
3. SAPHIRE-F turns those detector families into hyperedge energies over the same set of line-item nodes, then solves a sparse inverse repair problem to infer which cells most likely caused the joint inconsistency.

This gives four advantages simultaneously:

- Better calibration, because the score is derived from a unified energy rather than incompatible raw detector magnitudes.
- Better attribution, because the sparse repair vector identifies the minimal suspicious set of cells.
- Better robustness, because extraction confidence and manual override status directly enter the optimization as reliability weights.
- Better computational efficiency, because all evidence families share one sparse graph and one optimization loop.

### Mathematical Formulation

#### 2.1 Problem definition

Let a spread over the current period and $H-1$ historical periods be canonicalized into a vector

$$
x \in \mathbb{R}^n,
$$

where each coordinate $x_i$ corresponds to a tuple

$$
(\ell_i, s_i, t_i, m_i),
$$

with line-item code $\ell_i$, statement type $s_i \in \{\text{IS}, \text{BS}, \text{CF}, \text{SoCE}\}$, period $t_i$, and metadata $m_i$ containing source confidence, manual-override state, and entity profile.

Construct a typed temporal hypergraph

$$
\mathcal{H} = (V, E_F \cup E_R \cup E_D \cup E_T),
$$

where:

- $V = \{1, \dots, n\}$ are value nodes.
- $E_F$ are flow/articulation hyperedges.
- $E_R$ are ratio hyperedges.
- $E_D$ are digital-pattern hyperedges.
- $E_T$ are temporal continuity edges between the same concept across periods.

The goal is not merely to classify $x$ as anomalous. The goal is to infer a sparse repair vector

$$
\delta \in \mathbb{R}^n
$$

such that $x - \delta$ is as consistent as possible with the hypergraph constraints while $\delta$ remains small and localized.

#### 2.2 Flow hyperedges

Each flow constraint $e \in E_F$ is represented by a sparse coefficient vector $a_e \in \mathbb{R}^n$, a target $b_e \in \mathbb{R}$, and a tolerance model $\tau_e(x) > 0$.

Examples include:

- $A - L - E = 0$
- $\Delta \text{Cash} - \text{NetCF} = 0$
- $\Delta \text{Receivables} + \text{CF adjustment for receivables} = 0$
- $\Delta \text{Retained Earnings} - \text{Net Income} + \text{Dividends} + \text{OCI} = 0$

Define the normalized flow residual

$$
u_e(x) = \frac{a_e^\top x - b_e}{\tau_e^{\text{abs}} + \tau_e^{\text{rel}} \lVert x_{\operatorname{supp}(e)} \rVert_1 + \varepsilon}.
$$

The flow energy is

$$
E_F(x) = \sum_{e \in E_F} \rho\big(u_e(x)\big),
$$

where $\rho$ is the pseudo-Huber loss

$$
\rho(z) = \delta_h^2 \left(\sqrt{1 + (z / \delta_h)^2} - 1\right).
$$

Pseudo-Huber is used instead of a hard threshold because it is quadratic near zero and linear in the tails, which stabilizes optimization under genuine outliers.

#### 2.3 Ratio hyperedges with contextual baselines

For ratio hyperedge $j \in E_R$, define

$$
r_j(x) = \frac{p_j^\top x}{q_j^\top x + \varepsilon},
$$

where $p_j$ and $q_j$ are sparse numerator and denominator selectors.

Each ratio is compared against three baselines:

1. Self-history: $(\mu_j^{\text{self}}, \sigma_j^{\text{self}})$
2. Industry/entity profile: $(\mu_j^{\text{ind}}, \sigma_j^{\text{ind}})$
3. Conditional model expectation: $(\hat{r}_j(x_{-j}), \sigma_j^{\text{mdl}})$

Define availability-aware weights $\omega_{j,c} \ge 0$ for $c \in \{\text{self}, \text{ind}, \text{mdl}\}$ with $\sum_c \omega_{j,c} = 1$.

The contextual standardized residual is

$$
z_j(x) = \sum_c \omega_{j,c} \frac{r_j(x) - \mu_{j,c}(x)}{\sigma_{j,c} + \varepsilon},
$$

where $\mu_{j,\text{mdl}}(x) = \hat{r}_j(x_{-j})$.

The ratio energy is

$$
E_R(x) = \sum_{j \in E_R} \rho\big(z_j(x)\big).
$$

This is already stronger than simple ratio thresholding because it respects the fact that the correct baseline depends on data availability and entity profile.

#### 2.4 Digital hyperedges with empirical-Bayes Benford conditioning

Pure Benford is too brittle for sparse, profile-specific financial spreads. SAPHIRE-F therefore uses a posterior digit prior rather than a fixed global prior.

For an entity profile $g$, magnitude bucket $b$, and client-history cache $h$, let the expected first-digit probabilities be

$$
p^{\text{post}}(d \mid g, b, h)
=
\frac{\alpha \pi_B(d) + \beta \hat{p}_{g,b}(d) + \gamma \hat{p}_{h}(d)}{\alpha + \beta + \gamma},
\qquad d \in \{1, \dots, 9\},
$$

where $\pi_B(d) = \log_{10}(1 + 1/d)$ is the Benford prior, $\hat{p}_{g,b}$ is the entity-type and scale-conditioned empirical distribution, and $\hat{p}_h$ is the client-specific historical distribution when enough history exists.

Let $I_D \subseteq V$ be the set of digit-eligible values after excluding zeros, values below a minimum magnitude, percentages, explicit ratios, and non-natural-number style fields. If $|I_D| < m_{\min}$, the digital layer is down-weighted automatically.

Let $\hat{p}_x$ be the empirical first-digit distribution of the current eligible values. Then the digital energy is

$$
E_D(x)
=
\lambda_{fd} D_{KL}(\hat{p}_x \Vert p^{\text{post}})
+
\lambda_{ld} \chi^2_{\text{last}}(x)
+
\lambda_{rn} \operatorname{RoundFrac}(x),
$$

where:

- $D_{KL}$ measures first-digit surprise.
- $\chi^2_{\text{last}}$ measures abnormal last-digit clustering.
- $\operatorname{RoundFrac}(x)$ measures excessive round-number concentration.

For line-item attribution, define the digit surprise of value $i \in I_D$ by

$$
\psi_i(x)
=
-\log p^{\text{post}}\big(d_i(x) \mid g, b, h\big)
+
\eta_{rn} \mathbf{1}\{x_i \text{ is excessively round}\}.
$$

#### 2.5 Reliability-weighted sparse repair objective

Let $W = \operatorname{diag}(w_1, \dots, w_n)$ be a diagonal reliability matrix. High-confidence, manually overridden, or audited cells receive larger penalties; low-confidence AI-extracted cells receive smaller penalties.

One practical parameterization is

$$
w_i = c_0 + c_1 \cdot \operatorname{conf}_i + c_2 \cdot \mathbf{1}\{\text{manual}_i\} + c_3 \cdot \mathbf{1}\{\text{audited}_i\},
$$

with $c_0 > 0$.

Let $D_T$ be the temporal difference operator over $E_T$, encouraging coherent multi-period repairs when anomalies span adjacent periods rather than isolated cells.

SAPHIRE-F solves

$$
\delta^*
=
\arg\min_{\delta \in \mathbb{R}^n}
\Big[
\lambda_F E_F(x - \delta)
+
\lambda_R E_R(x - \delta)
+
\lambda_D E_D(x - \delta)
+
\lambda_1 \lVert W \delta \rVert_1
+
\lambda_2 \lVert D_T \delta \rVert_1
\Big].
$$

Interpretation:

- $E_F$, $E_R$, and $E_D$ force the repaired spread back toward structural, contextual, and digital plausibility.
- $\lVert W \delta \rVert_1$ ensures the explanation stays sparse and respects source reliability.
- $\lVert D_T \delta \rVert_1$ allows coherent temporal repairs when a whole series is shifted, scaled, or lagged.

This is the core novelty. The anomaly engine is not a detector ensemble. It is a sparse inverse reconciliation problem over a typed temporal hypergraph.

#### 2.6 Node-level and spread-level scores

Define corrected values

$$
x^* = x - \delta^*.
$$

For each node $i$, define its anomaly mass

$$
a_i
=
\alpha_1 \frac{|\delta_i^*|}{|x_i| + \varepsilon}
+
\alpha_2 \sum_{e \ni i} \frac{\rho_e(x^*)}{|\operatorname{supp}(e)|}
+
\alpha_3 \psi_i(x),
$$

where $\rho_e(x^*)$ is the post-repair residual contribution associated with edge $e$.

The line-item score is then

$$
s_i = 100 \cdot \sigma(a_i),
$$

with $\sigma(z) = 1 / (1 + e^{-z})$.

Layer scores are calibrated from repaired energies:

$$
\tilde{E}_\ell = \frac{E_\ell(x^*)}{Z_{\ell,g}}, \qquad \ell \in \{F, R, D\},
$$

where $Z_{\ell,g}$ is an entity-profile normalization constant learned from clean and reviewed spreads.

Instead of a naive linear weighted sum, the spread-level score uses interaction-aware aggregation:

$$
S(x)
=
100 \left[
1 - \exp\Big(
-\theta_F \tilde{E}_F
-\theta_R \tilde{E}_R
-\theta_D \tilde{E}_D
-\theta_{FR} \tilde{E}_F \tilde{E}_R
-\theta_{FD} \tilde{E}_F \tilde{E}_D
-\theta_{RD} \tilde{E}_R \tilde{E}_D
\Big)
\right].
$$

This matters because simultaneous flow and ratio disagreement is much stronger evidence than either in isolation.

## 3. Technical Architecture & Pseudocode

SAPHIRE-F is organized as a sparse inference pipeline rather than three isolated services.

1. Canonicalize the spread into a typed value vector.
2. Build one temporal articulation hypergraph.
3. Compute flow, ratio, and digital residuals on shared nodes.
4. Screen to the active residual set.
5. Solve a reliability-weighted sparse repair problem.
6. Diffuse repaired residual mass back to nodes for explanation.
7. Calibrate layer scores and aggregate them with interaction terms.

```text
PROCEDURE SAPHIRE_F_SCORE(spread, history, benchmarks, profileConfig, runtimeConfig):
    # Step 1: Canonicalize current and historical statements into one sparse state vector.
    x, nodeMeta <- CANONICALIZE_SPREAD(spread, history)

    # Step 2: Resolve entity profile; do not rely on a single hard-coded profile if mixed evidence exists.
    profile <- RESOLVE_PROFILE(nodeMeta, profileConfig)

    # Step 3: Build a typed hypergraph with shared nodes.
    # Flow edges encode accounting identities and articulation bridges.
    # Ratio edges encode derived financial ratios.
    # Digital edges group Benford-eligible and digit-pattern-eligible values.
    # Temporal edges connect the same concept across adjacent periods.
    H <- BUILD_TEMPORAL_ARTICULATION_HYPERGRAPH(x, nodeMeta, profile, runtimeConfig)

    # Step 4: Estimate context-conditioned priors.
    # This produces profile-aware flow tolerances, ratio baselines, and posterior digit priors.
    priors <- ESTIMATE_PRIORS(H, history, benchmarks, profile, runtimeConfig)

    # Step 5: Compute residuals for every hyperedge family.
    flowResiduals <- EVALUATE_FLOW_RESIDUALS(H.flowEdges, x, priors.flowTolerance)
    ratioResiduals <- EVALUATE_RATIO_RESIDUALS(H.ratioEdges, x, priors.ratioBaselines)
    digitalResiduals <- EVALUATE_DIGITAL_RESIDUALS(H.digitalEdges, x, priors.digitPosterior, runtimeConfig)

    # Step 6: Activate only edges whose residual energy is materially non-zero.
    # This avoids solving a large optimization problem when the spread is already clean.
    activeEdges <- SCREEN_ACTIVE_EDGES(flowResiduals, ratioResiduals, digitalResiduals, runtimeConfig)

    IF activeEdges is empty THEN
        RETURN BUILD_CLEAN_RESULT(x, nodeMeta, profile)
    END IF

    # Step 7: Reliability weights penalize changing trusted cells more than weakly supported AI cells.
    W <- BUILD_RELIABILITY_DIAGONAL(nodeMeta, runtimeConfig)

    # Step 8: Temporal difference operator encourages coherent repairs across periods.
    D_T <- BUILD_TEMPORAL_DIFFERENCE_OPERATOR(H.temporalEdges)

    # Step 9: Solve the sparse inverse repair problem.
    delta <- SOLVE_SPARSE_REPAIR_ADMM(x, activeEdges, W, D_T, priors, runtimeConfig)

    corrected <- x - delta

    # Step 10: Recompute energies on the repaired spread; these drive the final calibrated score.
    repairedFlow <- EVALUATE_FLOW_RESIDUALS(H.flowEdges, corrected, priors.flowTolerance)
    repairedRatio <- EVALUATE_RATIO_RESIDUALS(H.ratioEdges, corrected, priors.ratioBaselines)
    repairedDigital <- EVALUATE_DIGITAL_RESIDUALS(H.digitalEdges, corrected, priors.digitPosterior, runtimeConfig)

    # Step 11: Diffuse edge energy back to nodes and combine it with sparse repair magnitude.
    nodeScores <- DIFFUSE_ANOMALY_MASS(H, corrected, delta, repairedFlow, repairedRatio, repairedDigital, priors)

    # Step 12: Build analyst-facing explanations from the dominant support of delta.
    explanations <- BUILD_EXPLANATION_CARDS(H, x, corrected, delta, nodeScores, profile)

    # Step 13: Calibrate each layer and combine them with interaction-aware aggregation.
    layerScores <- CALIBRATE_LAYER_SCORES(repairedFlow, repairedRatio, repairedDigital, profile, runtimeConfig)
    globalScore <- COMBINE_LAYER_SCORES(layerScores, runtimeConfig)

    RETURN BUILD_RESULT(globalScore, layerScores, nodeScores, explanations, corrected)
END PROCEDURE


PROCEDURE SOLVE_SPARSE_REPAIR_ADMM(x, activeEdges, W, D_T, priors, runtimeConfig):
    # We solve:
    #   min_delta  lambda_F E_F(x-delta) + lambda_R E_R(x-delta) + lambda_D E_D(x-delta)
    #             + lambda_1 ||W delta||_1 + lambda_2 ||D_T delta||_1
    # using a proximal ADMM / iteratively reweighted local linearization scheme.

    n <- LENGTH(x)
    delta <- ZERO_VECTOR(n)
    u <- ZERO_VECTOR(n)                  # split variable for weighted sparsity
    v <- ZERO_VECTOR(NUM_ROWS(D_T))      # split variable for temporal total variation
    y_u <- ZERO_VECTOR(n)                # dual variable for u
    y_v <- ZERO_VECTOR(NUM_ROWS(D_T))    # dual variable for v

    FOR iter FROM 1 TO runtimeConfig.maxIterations:
        corrected <- x - delta

        # Local linearization over the currently active residual set.
        # J is sparse because each edge touches only a small subset of cells.
        J, r <- BUILD_LOCAL_JACOBIAN_AND_RESIDUALS(activeEdges, corrected, priors)

        # Normal matrix for the quadratic proximal step.
        # Template sparsity pattern is stable, so symbolic factorization can be cached.
        M <- TRANSPOSE(J) * J
        M <- M + runtimeConfig.rho1 * TRANSPOSE(W) * W
        M <- M + runtimeConfig.rho2 * TRANSPOSE(D_T) * D_T
        M <- M + runtimeConfig.epsilon * IDENTITY(n)

        rhs <- TRANSPOSE(J) * r
        rhs <- rhs + runtimeConfig.rho1 * TRANSPOSE(W) * (u - y_u)
        rhs <- rhs + runtimeConfig.rho2 * TRANSPOSE(D_T) * (v - y_v)

        # Sparse solve; in production this should use a cached sparse Cholesky or preconditioned CG.
        delta <- SOLVE_SPARSE_SYSTEM(M, rhs)

        # Proximal shrinkage keeps the repair sparse and respects source reliability.
        u <- SOFT_THRESHOLD(W * delta + y_u, runtimeConfig.lambda1 / runtimeConfig.rho1)

        # Temporal shrinkage suppresses noisy period-to-period oscillations in the inferred repair.
        v <- SOFT_THRESHOLD(D_T * delta + y_v, runtimeConfig.lambda2 / runtimeConfig.rho2)

        y_u <- y_u + W * delta - u
        y_v <- y_v + D_T * delta - v

        IF CONVERGED(delta, u, v, y_u, y_v, runtimeConfig.tolerance) THEN
            BREAK
        END IF
    END FOR

    RETURN delta
END PROCEDURE


PROCEDURE DIFFUSE_ANOMALY_MASS(H, corrected, delta, repairedFlow, repairedRatio, repairedDigital, priors):
    nodeMass <- ZERO_VECTOR(NUM_NODES(H))

    FOR each flow edge e IN H.flowEdges:
        edgeMass <- FLOW_ENERGY(repairedFlow[e]) / EDGE_ARITY(e)
        FOR each node i IN SUPPORT(e):
            nodeMass[i] <- nodeMass[i] + edgeMass
        END FOR
    END FOR

    FOR each ratio edge j IN H.ratioEdges:
        edgeMass <- RATIO_ENERGY(repairedRatio[j]) / EDGE_ARITY(j)
        FOR each node i IN SUPPORT(j):
            nodeMass[i] <- nodeMass[i] + edgeMass
        END FOR
    END FOR

    FOR each digital edge d IN H.digitalEdges:
        edgeMass <- DIGITAL_ENERGY(repairedDigital[d]) / EDGE_ARITY(d)
        FOR each node i IN SUPPORT(d):
            nodeMass[i] <- nodeMass[i] + edgeMass
        END FOR
    END FOR

    FOR each node i IN H.nodes:
        # Final node score mixes graph-diffused residual energy with direct repair magnitude.
        normalizedRepair <- ABS(delta[i]) / (ABS(corrected[i]) + SMALL_EPSILON)
        surprise <- DIGITAL_SURPRISE_IF_AVAILABLE(i, priors)
        nodeMass[i] <- SIGMOID(priors.alpha1 * normalizedRepair + priors.alpha2 * nodeMass[i] + priors.alpha3 * surprise)
    END FOR

    RETURN 100 * nodeMass
END PROCEDURE
```

The implementation detail that makes the pseudocode production-oriented is the reuse of sparsity:

- The hypergraph pattern is template-stable.
- Most flow and ratio edges have small fixed arity.
- The symbolic factorization of the normal matrix can therefore be cached per template profile.

That is what prevents the inverse attribution step from collapsing into brute-force leave-one-out recomputation.

## 4. Rigorous Complexity Analysis

Let:

- $n$ be the number of unfolded value nodes across current and historical periods.
- $e_F = |E_F|$ be the number of flow hyperedges.
- $e_R = |E_R| = q$ be the number of ratio hyperedges.
- $e_D = |E_D|$ be the number of digital hyperedges.
- $d$ be the number of Benford-eligible values.
- $H$ be the number of historical periods used for self-history baselines.
- $\bar{k}$ be the average hyperedge arity.
- $s$ be the number of active hyperedges after screening, with $s \le e_F + e_R + e_D$.
- $T$ be the number of ADMM iterations until convergence.

### Time Complexity

#### Step 1: Canonicalization

Each observed value is visited once to build the canonical vector and node metadata.

$$
T_1 = O(n).
$$

#### Step 2: Hypergraph construction

Each hyperedge stores references only to the nodes it touches. Because each accounting identity, ratio, or digital group references a small subset of nodes, construction cost is proportional to the number of incident pairs.

$$
T_2 = O\big(n + (e_F + e_R + e_D)\bar{k}\big).
$$

#### Step 3: Prior estimation

There are three dominant pieces.

1. Self-history ratio statistics over $q$ ratios and $H$ periods:

$$
O(qH).
$$

2. Posterior digit prior estimation over the $d$ digit-eligible values:

$$
O(d).
$$

3. Industry and entity-profile lookup over ratios and tolerance tables:

$$
O(q + e_F).
$$

Therefore,

$$
T_3 = O(qH + d + q + e_F).
$$

#### Step 4: Residual evaluation before optimization

Flow residuals touch $e_F \bar{k}$ node-edge incidences.

$$
O(e_F \bar{k}).
$$

Ratio residuals touch $e_R \bar{k}$ incidences.

$$
O(e_R \bar{k}).
$$

Digital residuals require scanning eligible values and updating a small fixed-size histogram.

$$
O(d).
$$

Hence,

$$
T_4 = O\big((e_F + e_R)\bar{k} + d\big).
$$

#### Step 5: Active-edge screening

Each precomputed residual is checked once against a screening threshold or quantile rule.

$$
T_5 = O(e_F + e_R + e_D).
$$

#### Step 6: Sparse repair optimization

This is the dominant online term.

For each ADMM iteration:

1. Build local Jacobian and residuals over the active set:

$$
O(s \bar{k}).
$$

2. Solve the sparse linear system. Under the intended sparse-template regime with cached symbolic factorization, the numeric solve is approximately linear in the number of nonzeros:

$$
O(n + s \bar{k}).
$$

3. Apply shrinkage and dual updates:

$$
O(n).
$$

So one iteration costs

$$
O(n + s \bar{k}).
$$

Over $T$ iterations,

$$
T_6 = O\big(T(n + s \bar{k})\big).
$$

#### Step 7: Diffusion and explanation generation

Each active edge distributes mass back to its participating nodes, and each node is scored once.

$$
T_7 = O(s \bar{k} + n).
$$

### Total Time Complexity

Combining the stages:

$$
T_{\text{total}}
=
O\Big(
n
+
qH
+
d
+
(e_F + e_R + e_D)\bar{k}
+
T(n + s \bar{k})
\Big).
$$

Since $\bar{k}$ is small and effectively constant for financial templates, this simplifies to

$$
T_{\text{total}} = O\big(n + qH + d + e_F + e_R + e_D + T(n + s)\big).
$$

#### Best case

If screening finds no materially active edges, the optimization stage is skipped entirely. Then

$$
T_{\text{best}} = O\big(n + qH + d + e_F + e_R + e_D\big).
$$

#### Average case

In normal use, only a small active subset survives screening, so $s \ll e_F + e_R + e_D$, and ADMM converges in a moderate number of iterations $T_{avg}$. Then

$$
T_{\text{avg}} = O\big(n + qH + d + e_F + e_R + e_D + T_{avg}(n + s)\big).
$$

Because $s$ is sparse, the online runtime is close to linear in spread size.

#### Worst case

Under the intended sparse-template assumption, every edge can become active, giving

$$
T_{\text{worst,sparse}} = O\big(n + qH + d + e_F + e_R + e_D + T(n + e_F + e_R + e_D)\big).
$$

Under a pathological dense fallback where the sparse solve degenerates to dense linear algebra, the repair step can degrade to

$$
T_{\text{worst,dense}} = O(T n^3).
$$

That is the formal upper bound, but it is not the intended operating regime because the financial articulation graph is structurally sparse.

### Space Complexity

#### Core state and metadata

The canonical vector, corrected vector, and metadata require

$$
S_1 = O(n).
$$

#### Hypergraph storage

Adjacency lists for all hyperedges require storage proportional to total incidences:

$$
S_2 = O\big((e_F + e_R + e_D)\bar{k}\big).
$$

#### Priors and residual tables

Ratio baselines, flow tolerances, digit priors, and residual caches require

$$
S_3 = O(q + e_F + e_D + d).
$$

#### Optimization workspace

The ADMM state stores $\delta$, split variables, dual variables, and the active sparse system:

$$
S_4 = O(n + s \bar{k}).
$$

Therefore the normal sparse operating-space complexity is

$$
S_{\text{total}} = O\Big(n + d + q + (e_F + e_R + e_D)\bar{k}\Big).
$$

With constant $\bar{k}$ this is effectively linear.

#### Worst-case dense space

If the solver falls back to a dense normal matrix, memory can degrade to

$$
S_{\text{worst,dense}} = O(n^2).
$$

Again, that bound is formal rather than operationally expected.

### Offline Precomputation Complexity

The online scoring complexity above assumes three offline artifacts are precomputed:

1. Entity-profile ratio baselines.
2. Digit posterior tables by entity type and magnitude bucket.
3. Symbolic factorization pattern per template family.

If $N$ historical spreads are used to fit ratio baselines over $q$ ratios, the dominant offline term is typically

$$
O(Nq + q^3),
$$

where $q^3$ comes from covariance shrinkage or robust precision-matrix estimation if used. That cost is amortized and does not affect per-spread latency.

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

SAPHIRE-F is not a small extension of existing anomaly detection patterns.

**Versus the current backend validation engine**

The current platform evaluates configured arithmetic formulas and emits PASS or FAIL. SAPHIRE-F instead builds a typed temporal hypergraph, scores structural, contextual, and digital inconsistency jointly, and solves a sparse inverse repair problem to localize the likely offending cells. The representational object and the inference objective are both different.

**Versus classic Benford-plus-dashboard audit analytics**

Publicly described audit analytics suites commonly expose Benford tests, last-digit analysis, gap tests, duplicate tests, and ratio dashboards. Those are useful detector modules, but they generally operate as parallel analytics. SAPHIRE-F differs by using context-conditioned digit priors and embedding digital evidence inside a shared optimization with cross-statement and ratio constraints.

**Versus Beneish M-Score, Dechow F-Score, and similar fraud formulas**

Beneish- and F-Score-style models are fixed firm-level screening formulas over a limited ratio set. They are valuable as coarse fraud-risk indicators, but they are not line-item attribution systems and they do not exploit exact accounting articulation constraints. SAPHIRE-F operates at spread-cell resolution, not only at firm-level classification resolution.

**Versus Mahalanobis distance, Isolation Forest, and autoencoder anomaly detection**

Generic multivariate anomaly detectors view the spread as a feature vector and estimate outlierness in that ambient space. They do not natively encode exact conservation laws such as cash reconciliation or retained-earnings bridges, and they do not return a sparse repair vector saying which values should change to restore plausibility. SAPHIRE-F is explanation-first rather than embedding-first.

**Versus graph reconciliation and rule-based articulation systems**

Graph-based reconciliation systems can encode accounting dependencies, but they typically stop at consistency checking or deterministic balancing. SAPHIRE-F goes further by combining graph articulation with empirical-Bayes digital priors, contextual ratio geometry, reliability weighting from extraction provenance, and sparse inverse repair. That combination is the novelty center.

### Specific Claims

- A method for constructing a typed temporal articulation hypergraph in which accounting identities, ratio relationships, digit-pattern groups, and inter-period continuity are encoded as distinct hyperedge families over shared financial-statement value nodes.
- A method for computing profile-conditioned digital anomaly priors by fusing a Benford prior with entity-type, magnitude-bucket, and client-history empirical distributions, and injecting the resulting posterior surprise directly into a unified financial-anomaly objective.
- A method for localizing financial-statement anomalies by solving a reliability-weighted sparse repair optimization that penalizes changes to high-confidence or manually overridden cells more heavily than changes to weakly supported extracted cells.
- A method for generating calibrated spread-level and line-item anomaly scores by combining repaired flow, ratio, and digital energies with explicit interaction terms, rather than by a simple additive weighted detector ensemble.

The most defensible patentable core is the three-part mechanism formed by:

1. Shared hypergraph representation.
2. Context-conditioned digital priors.
3. Reliability-weighted sparse inverse repair for root-cause localization.

That triplet is materially different from standard Benford tooling, standard ratio-outlier models, standard graph reconciliation, and standard black-box anomaly detectors.