# Topic 6 of 6: Covenant Breach Prediction

## 1. Critical Analysis of Attached Context

The attached IP-6 brief correctly identifies the business gap, but the more important technical observation is narrower: the current system has a covenant evaluation engine, not a covenant hazard model. The backend already computes covenant values from spread submissions and updates monitoring states, yet the prediction path is still a thin one-dimensional extrapolator over the covenant value itself. That is a representational mismatch with the true problem.

### Deconstruction of the Current Methodologies

The current covenant pipeline is a three-stage chain.

1. Spread data arrives as period-specific financial statements.
2. The covenant module evaluates a deterministic formula against spread values and marks the monitoring item as MET or BREACHED.
3. A separate prediction service takes up to the last eight covenant values, fits a simple ordinary least squares slope, projects one period forward, and converts the projected threshold crossing into a heuristic probability.

Formally, if the historical covenant values are $y_0, y_1, \dots, y_{n-1}$ with $n \le 8$, the current method computes

$$
\hat{\beta}
=
\frac{\sum_{t=0}^{n-1}(t-\bar{t})(y_t-\bar{y})}{\sum_{t=0}^{n-1}(t-\bar{t})^2},
\qquad
\hat{y}_n = y_{n-1} + \hat{\beta}.
$$

It then checks whether $\hat{y}_n$ crosses the covenant threshold and maps the magnitude of the slope to a score in $[0,1]$. This is not a trained probabilistic model, not a survival model, and not a calibrated estimator. It is a trend heuristic with a probabilistic veneer.

The monitoring subsystem is stronger operationally than predictively. It generates monitoring windows by covenant frequency, records manual overrides, and publishes breach events. The spread listener is also structurally important: it proves the platform already has access to the raw balance sheet, income statement, and cash flow inputs needed for multivariate prediction. However, those inputs are currently used only to compute the current covenant value, not to forecast the future state trajectory that drives the covenant.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

#### 1.1 The current probability is not statistically identifiable as a probability

The existing score is not learned from historical breach outcomes and is not optimized against a proper scoring rule such as log-loss or Brier loss. There is no likelihood model, no event-time objective, and no calibration layer. Consequently, two covenants with identical reported probabilities need not correspond to equal empirical breach frequencies.

#### 1.2 The model is single-variate while the underlying process is multivariate

A covenant breach is almost never caused by the covenant value in isolation. It is caused by the joint movement of revenue, EBITDA, debt, working capital, interest burden, and other latent stress drivers. Predicting $y_{t+1}$ from only $y_t$ discards the true state vector $x_t \in \mathbb{R}^d$ that generated $y_t = f_c(x_t)$.

That loss is critical because scenario simulation requires the inverse direction: users shock raw financial drivers and ask how the covenant distribution changes. A model that only observes the final covenant value cannot answer that counterfactual question without reconstructing the hidden state dynamics it never learned.

#### 1.3 The slope estimate is unstable in the low-history regime

The current service recalculates probabilities with as few as two historical points. For ordinary least squares on equally spaced times, the slope variance is

$$
\operatorname{Var}(\hat{\beta})
=
\frac{\sigma^2}{\sum_{t=0}^{n-1}(t-\bar{t})^2}.
$$

For $n=2$, the denominator is $0.5$. For $n=8$, it is $42$. The variance of the slope estimate at two points is therefore $84\times$ larger than at eight points, assuming equal observation noise. This is not a minor quality issue. It means the model is mathematically least trustworthy exactly in the sparse-history regime where relationship managers most need pooled cross-client intelligence.

#### 1.4 The existing formulation does not model first-passage risk

Managers do not ask, "What is the next covenant value?" They ask, "What is the probability this covenant breaches within the next 2 quarters?" That is a first-passage or time-to-event question. The proper object is a discrete hazard or survival distribution over horizons $h = 1, \dots, H$, not a one-step projected value.

#### 1.5 Covenant semantics are threshold-conditioned and heterogeneous

The platform already supports threshold operators such as GTE, LTE, EQ, and BETWEEN. These are not equivalent geometric objects. A value increasing toward a GTE threshold is good, while the same increase toward an LTE threshold is bad. A BETWEEN covenant has two absorbing boundaries. Any general model must map these heterogeneous operators into a common signed safety-margin space. The current implementation does not do that.

#### 1.6 Missing data, seasonality, and regime shifts are untreated

Real financial panels are sparse and irregular. Some ratios do not exist for some periods. Some industries are seasonal. Interest-rate shocks induce regime changes. The current method has no explicit missingness mask, no imputation strategy, no seasonal basis, and no mechanism for regime-conditioned uncertainty inflation.

#### 1.7 There is no cross-client trajectory memory

The attached brief highlights the key unmet capability: recognizing that client A currently resembles client B two quarters before breach. No such library exists today. The system stores monitoring items and current values, but it does not store aligned multivariate pre-breach or post-recovery trajectories, nor does it compute any similarity measure over them.

#### 1.8 There is no causal counterfactual operator for what-if simulation

Scenario simulation requires more than a predictive classifier. It requires a fast operator that maps shocks on raw statement variables into updated ratio trajectories, covenant values, and breach probabilities. Generic gradient-boosted trees, recurrent networks, and ordinary regression do not natively provide this. The current implementation provides no counterfactual map at all.

#### 1.9 The current service has an avoidable systems bottleneck

At recalculation time, the prediction service iterates monitoring items and performs per-covenant history retrieval. In asymptotic terms, if there are $M$ active monitoring items and each history query scans $L$ records, the service behaves like an application-layer $O(M \cdot L)$ predictor wrapped around an $N+1$ repository access pattern. Even if $L$ is small, this scales poorly operationally and prevents richer feature assembly.

#### 1.10 Existing SOTA families each miss one of the required invariants

- Gradient-boosted trees handle tabular data well but do not natively express elastic trajectory similarity or multi-step counterfactual dynamics.
- LSTM/GRU/TFT models can capture sequence effects, but they are data-hungry relative to the available 4 to 20 periods per covenant and are difficult to calibrate under sparse breach labels.
- Classical credit-risk models such as Merton/KMV or EDF operate at the firm-default level, not at the covenant-formula level.
- Standard DTW, shapelets, or clustering methods provide similarity, but not calibrated breach probability or scenario sensitivity.

The theoretical gap is therefore precise: the platform lacks a data-efficient, threshold-aware, multi-horizon hazard model that can simultaneously pool strength across clients, exploit elastic trajectory similarity, and support instantaneous counterfactual shock propagation.

## 2. The Novel Algorithmic Proposal

### Name

**TACTIC-H: Threshold-Aware Causal Trajectory Inference for Covenant Hazards**

### Core Intuition

TACTIC-H is based on a specific claim: covenant breach prediction should be solved in signed safety-margin space, not in raw covenant-value space.

That single representational change unlocks the rest of the design.

1. Different covenant operators become comparable after normalization into a signed margin-to-threshold coordinate system.
2. Multivariate financial trajectories can be learned as drivers of future margin collapse rather than as generic feature vectors.
3. Cross-client pattern memory can be defined over aligned margin-conditioned trajectories instead of over raw, incomparable ratios.
4. Scenario simulation becomes a differentiable perturbation of margin dynamics through covenant formulas.

TACTIC-H is a hybrid algorithm with three coupled inference channels:

1. **Hierarchical causal dynamics** estimate how the multivariate client state evolves over the next $H$ horizons.
2. **Threshold-aware elastic prototype memory** compares the current trajectory against anonymized historical breach and recovery archetypes.
3. **Counterfactual Jacobian propagation** converts user-defined shocks on spread variables into rapid probability updates without retraining.

The final breach probability is not produced by any one channel alone. It is the output of a discrete-time hazard model that fuses local drift, cross-client similarity, and scenario sensitivity, then calibrates the result with split conformal prediction.

### Mathematical Formulation

#### 2.1 Problem definition

For client $i$, covenant $c$, and time period $t$, let

$$
s_{i,t} \in \mathbb{R}^m
$$

be the vector of raw spread variables, including income statement, balance sheet, and cash flow items. Let

$$
z_{i,t} = R(s_{i,t}) \in \mathbb{R}^q
$$

be the engineered ratio vector. Each covenant $c$ has a deterministic formula

$$
y_{i,c,t} = f_c(s_{i,t}),
$$

where $f_c$ is already supported by the formula engine.

We seek the probability of first breach within horizon $H$:

$$
P_{i,c,t}^{(H)}
=
\Pr\left(\tau_{i,c} - t \le H \mid \mathcal{F}_{i,t}\right),
$$

where $\tau_{i,c}$ is the first future breach time and $\mathcal{F}_{i,t}$ is the observed information set up to time $t$.

#### 2.2 Signed safety-margin transform

Define the operator-normalized signed margin $m_{i,c,t}$ as positive when safe and negative when breached:

$$
m_{i,c,t} =
\begin{cases}
\dfrac{y_{i,c,t} - \theta_c}{|\theta_c| + \varepsilon}, & \text{if } o_c = \mathrm{GTE}, \\
\dfrac{\theta_c - y_{i,c,t}}{|\theta_c| + \varepsilon}, & \text{if } o_c = \mathrm{LTE}, \\
\dfrac{\min\{y_{i,c,t} - \theta_{\min,c}, \theta_{\max,c} - y_{i,c,t}\}}{\max\{|\theta_{\min,c}|, |\theta_{\max,c}|\} + \varepsilon}, & \text{if } o_c = \mathrm{BETWEEN}, \\
-\dfrac{|y_{i,c,t} - \theta_c|}{|\theta_c| + \varepsilon}, & \text{if } o_c = \mathrm{EQ}.
\end{cases}
$$

This transform places all covenant types into one unified geometric space. Breach occurs when $m_{i,c,t} < 0$.

#### 2.3 State representation with missingness awareness

Let the period state be

$$
x_{i,c,t} =
\Big[
z_{i,t},
\Delta z_{i,t},
\Delta^2 z_{i,t},
u_t,
q_{i,t},
b_{i,t},
m_{i,c,t},
g_{i,c,t}
\Big] \in \mathbb{R}^d,
$$

where:

- $u_t$ are macro variables,
- $q_{i,t}$ are qualitative metadata signals,
- $b_{i,t}$ is a binary missingness mask,
- $g_{i,c,t} = \nabla_{s} f_c(s_{i,t})$ is the covenant-formula gradient with respect to raw spread items.

Missing values are imputed with model-based conditional forecasts while preserving the mask explicitly:

$$
x^{\star}_{i,c,t}
=
b_{i,t} \odot x_{i,c,t}
+
(1-b_{i,t}) \odot \hat{x}^{\text{dyn}}_{i,c,t}.
$$

This prevents the common failure mode where the model treats imputed values as observed certainty.

#### 2.4 Hierarchical causal dynamics

TACTIC-H uses a sparse plus low-rank transition operator:

$$
x_{t+1}
=
\Big(S + U V^\top + A_{\mathrm{type}(c)} + A_{\mathrm{industry}(i)}\Big)x_t
+
B u_{t+1}
+
\eta_t.
$$

Here:

- $S$ is a sparse lag operator capturing interpretable pairwise dependencies,
- $U V^\top$ is a low-rank global operator that pools broad cross-client structure,
- $A_{\mathrm{type}(c)}$ and $A_{\mathrm{industry}(i)}$ are covenant-type and industry residual operators,
- $\eta_t$ is zero-mean noise.

This decomposition is essential under limited history. A fully client-specific model would overfit. A fully global model would miss covenant semantics. The sparse plus low-rank hierarchy shares strength without forcing identical dynamics.

The $h$-step baseline forecast is

$$
\hat{x}_{t+h}
=
A^h x_t
+
\sum_{j=0}^{h-1} A^j B u_{t+h-j},
$$

with $A = S + U V^\top + A_{\mathrm{type}(c)} + A_{\mathrm{industry}(i)}$.

#### 2.5 Threshold-aware elastic prototype memory

Let the lookback window of length $L$ be

$$
W_{i,c,t} = (x_{i,c,t-L+1}, \dots, x_{i,c,t}).
$$

Construct two anonymized prototype libraries:

- $\mathcal{P}^- = \{p_1^-, \dots, p_{K^-}^-\}$ for pre-breach trajectories,
- $\mathcal{P}^+ = \{p_1^+, \dots, p_{K^+}^+\}$ for near-breach recoveries and persistent safe trajectories.

For aligned positions $(a,b)$ between a live window and prototype, define the local cost

$$
\ell(a,b)
=
\sum_{j=1}^{d} \omega_j \big(W_a^{(j)} - p_b^{(j)}\big)^2
+
\lambda_m \big(m_a - m_b\big)^2
+
\lambda_s \|\Delta W_a - \Delta p_b\|_2^2.
$$

The novelty is that the alignment is weighted by both feature stability and threshold relevance. Variables that are noisy but weakly related to breach are down-weighted; variables that reliably precede margin collapse are amplified.

Define the threshold-aware elastic distance

$$
D_{\mathrm{TAE}}(W,p)
=
\min_{\pi \in \mathcal{A}_w(L)} \sum_{(a,b) \in \pi} \ell(a,b),
$$

where $\mathcal{A}_w(L)$ is the set of admissible band-limited alignment paths with warping width $w$.

The prototype memory risk ratio is then

$$
r(W)
=
\log
\frac{\sum_{k=1}^{K^-} \exp\big(-D_{\mathrm{TAE}}(W,p_k^-)/\tau\big) + \epsilon}
{\sum_{k=1}^{K^+} \exp\big(-D_{\mathrm{TAE}}(W,p_k^+)/\tau\big) + \epsilon}.
$$

If $r(W)$ is large, the current client more closely resembles historical breach trajectories than recovery trajectories.

#### 2.6 Counterfactual scenario operator

Let a user-defined scenario be a shock sequence

$$
\delta_{0:H-1} = (\delta_0, \delta_1, \dots, \delta_{H-1}),
$$

where each $\delta_h$ perturbs a subset of raw spread variables. TACTIC-H propagates this through the state dynamics and covenant formula using Jacobian reuse.

For each horizon $h$, the scenario-adjusted margin is approximated by

$$
m_{t+h}^{(\delta)}
\approx
\hat{m}_{t+h}
+
J_{c,h} \delta
+
\frac{1}{2} \delta^\top H_{c,h} \delta,
$$

where $J_{c,h}$ and $H_{c,h}$ are the first- and second-order derivatives of the future margin with respect to the shocked base variables.

The first-order term expands by chain rule as

$$
J_{c,h}
=
\frac{\partial m_{t+h}}{\partial y_{t+h}}
\cdot
\frac{\partial f_c}{\partial s_{t+h}}
\cdot
\frac{\partial s_{t+h}}{\partial s_t}.
$$

This is the mechanism that makes the simulator interactive. The model does not need to rerun a full nonlinear training loop for each slider movement. It updates the hazard inputs through precomputed derivative objects.

#### 2.7 Discrete-time hazard fusion

For each horizon $h \in \{1, \dots, H\}$, define the hazard

$$
\lambda_h
=
\sigma\Big(
\alpha_h
+
\beta_h^\top \phi_t
+
\gamma_h r(W_t)
+
\delta_h \hat{m}_{t+h}
+
\xi_h \kappa_{t,h}
\Big),
$$

where:

- $\phi_t$ contains current engineered features,
- $r(W_t)$ is the prototype memory score,
- $\hat{m}_{t+h}$ is the forecast margin,
- $\kappa_{t,h}$ is scenario sensitivity or expected stress exposure,
- $\sigma(\cdot)$ is the logistic function.

The cumulative breach probability within horizon $H$ is

$$
P_t^{(H)}
=
1 - \prod_{h=1}^{H}(1-\lambda_h).
$$

This directly answers the business question that the current implementation cannot: not just whether the next point crosses the threshold, but the probability that breach happens at any point within the requested forward horizon.

#### 2.8 Leading indicator score

TACTIC-H does not treat leading indicators as a post hoc dashboard feature. It computes them from the hazard model itself.

For feature $j$, define

$$
L_j
=
\sum_{h=1}^{H} \rho_h
\Bigg(
\left|\frac{\partial \lambda_h}{\partial x_j}\right|
+
\alpha \cdot \operatorname{MI}(x_{j,t-h}; B_t)
+
\beta \cdot G_{j \rightarrow c}(h)
\Bigg)
\cdot \operatorname{Stab}_j,
$$

where:

- $\operatorname{MI}$ is lagged mutual information with future breach,
- $G_{j \rightarrow c}(h)$ is a Granger-style or sparse-lag dependency score,
- $\operatorname{Stab}_j$ measures cross-fold and cross-regime stability.

Variables with high $L_j$ are surfaced as early-warning drivers. This is more reliable than reporting raw feature importance because it penalizes unstable indicators.

#### 2.9 Conformal confidence intervals

Let $\hat{P}_n$ be predicted breach probabilities on a calibration set and $y_n \in \{0,1\}$ the observed outcomes. Define absolute calibration residuals

$$
r_n = |y_n - \hat{P}_n|.
$$

For each covenant-type and horizon bucket, compute the $(1-\alpha)$ quantile $q_{1-\alpha}$. The final prediction interval is

$$
\mathcal{I}_{1-\alpha}(x)
=
\Big[
\max(0, \hat{P}(x) - q_{1-\alpha}),
\min(1, \hat{P}(x) + q_{1-\alpha})
\Big].
$$

This produces finite-sample uncertainty bands without assuming Gaussian residuals, which is important in low-sample covenant regimes.

#### 2.10 Data-efficient bootstrapping regime

TACTIC-H is explicitly designed for sparse breach history. The training strategy has three layers.

1. Fit the global sparse plus low-rank dynamics on all financial covenants, not only breached ones.
2. Build prototype libraries from real breach and recovery windows, clustered by covenant type and industry.
3. Generate synthetic near-threshold windows by perturbing raw spread variables subject to accounting-identity constraints and historical covariance structure, but use those synthetic windows only for representation smoothing and stress-surface densification, never for final calibration.

That last restriction is deliberate. Synthetic data can improve geometric coverage of the state space, but if it is allowed to dominate calibration, the reported probabilities will become overconfident.

## 3. Technical Architecture & Pseudocode

TACTIC-H has four operational phases: window assembly, dynamic-operator fitting, prototype-library construction, and online prediction with optional scenario simulation.

### 3.1 System architecture

1. **Feature assembler**
   - Reads historical spread values, covenant definitions, macro series, and qualitative signals.
   - Computes ratios, derivatives, missingness masks, formula gradients, and signed safety margins.

2. **Dynamics learner**
   - Fits the sparse plus low-rank operator with covenant-type and industry residuals.
   - Produces multi-horizon baseline forecasts for each active covenant.

3. **Prototype memory builder**
   - Extracts pre-breach, recovery, and stable-safe windows.
   - Builds anonymized barycentric prototypes under threshold-aware elastic alignment.

4. **Hazard fusion and calibration layer**
   - Trains horizon-specific hazard heads.
   - Calibrates outputs with split conformal residuals.

5. **Scenario engine**
   - Precomputes Jacobians for each covenant formula and forecast horizon.
   - Applies shocks and re-scores probabilities in interactive time.

### 3.2 Production-grade pseudocode

```text
ALGORITHM TrainTACTIC_H(dataset D, lookback L, horizon H, prototype_count K, warp_band w)
    INPUT:
        D = all historical clients, spread items, covenant definitions, monitoring outcomes
        L = number of trailing periods used in each live trajectory window
        H = forward breach horizon in periods
        K = number of breach and recovery prototypes retained per bucket
        w = maximum alignment warp width for elastic matching
    OUTPUT:
        model M containing dynamics, prototype library, hazard heads, and calibration tables

    windows <- empty list
    transitions <- empty list
    breach_windows <- empty list
    recovery_windows <- empty list
    safe_windows <- empty list

    FOR each client i in D.clients DO
        ordered_periods <- SortChronologically(i.periods)

        FOR each covenant c attached to client i DO
            state_series <- empty list

            FOR each period t in ordered_periods DO
                s_t <- LoadRawSpreadVector(i, t)
                z_t <- ComputeFinancialRatios(s_t)
                dz_t <- FirstDifference(z_t)
                ddz_t <- SecondDifference(z_t)
                u_t <- LoadMacroVariables(t)
                q_t <- LoadQualitativeSignals(i, t)
                b_t <- MissingnessMask(z_t, q_t)

                # Compute the current covenant value using the existing formula semantics.
                y_t <- EvaluateCovenantFormula(c, s_t)

                # Map heterogeneous covenant operators into one signed safety margin.
                m_t <- SignedMargin(y_t, c.operator, c.thresholds)

                # Gradient of the covenant formula with respect to raw spread items.
                # This is later reused by the scenario engine for fast shock propagation.
                g_t <- FormulaGradient(c, s_t)

                x_t <- Concatenate(z_t, dz_t, ddz_t, u_t, q_t, b_t, m_t, g_t)
                Append(state_series, x_t)
            END FOR

            state_series <- ImputeMissingWithMaskAwareForecasts(state_series)

            FOR t from 1 to Length(state_series) - 1 DO
                Append(transitions, (state_series[t], state_series[t + 1], c.type, i.industry))
            END FOR

            FOR t from L to Length(state_series) - H DO
                W_t <- Slice(state_series, t - L + 1, t)

                # Label is 1 if the covenant first breaches anywhere in the next H periods.
                y_future <- FirstBreachWithinHorizon(c, ordered_periods, t, H)

                Append(windows, (W_t, y_future, c.type, i.industry, c.id))

                IF IsPreBreachWindow(c, ordered_periods, t, H) THEN
                    Append(breach_windows, (W_t, c.type, i.industry))
                ELSE IF IsRecoveryWindow(c, ordered_periods, t, H) THEN
                    Append(recovery_windows, (W_t, c.type, i.industry))
                ELSE
                    Append(safe_windows, (W_t, c.type, i.industry))
                END IF
            END FOR
        END FOR
    END FOR

    # Fit sparse plus low-rank dynamics with hierarchical residual structure.
    A_global_sparse, U, V, A_type, A_industry, B <- FitHierarchicalDynamics(transitions)

    # Forecast each stored window forward once so the hazard head can learn on forecasted margins.
    enriched_windows <- empty list
    FOR each item in windows DO
        W_t, y_future, covenant_type, industry, covenant_id <- item
        x_last <- LastState(W_t)
        baseline_rollout <- RolloutDynamics(x_last, H, A_global_sparse, U, V, A_type[covenant_type], A_industry[industry], B)
        Append(enriched_windows, (W_t, baseline_rollout, y_future, covenant_type, industry, covenant_id))
    END FOR

    # Build anonymized breach and recovery prototypes with elastic barycenters.
    breach_library <- BuildThresholdAwarePrototypes(breach_windows, K, w)
    recovery_library <- BuildThresholdAwarePrototypes(recovery_windows + safe_windows, K, w)

    hazard_training_rows <- empty list
    leading_indicator_cache <- empty list

    FOR each item in enriched_windows DO
        W_t, baseline_rollout, y_future, covenant_type, industry, covenant_id <- item

        # Compare the live window only to prototypes in the matching covenant and industry bucket.
        breach_candidates <- RetrieveCandidatePrototypes(breach_library, covenant_type, industry)
        recovery_candidates <- RetrieveCandidatePrototypes(recovery_library, covenant_type, industry)

        breach_score <- PrototypeRiskScore(W_t, breach_candidates, recovery_candidates, w)
        margin_path <- ExtractForecastMargins(baseline_rollout)

        # Local stress sensitivity is estimated once from the forecast path and formula gradients.
        scenario_sensitivity <- EstimateLocalStressSensitivity(W_t, baseline_rollout, covenant_id)

        features <- FuseHazardFeatures(W_t, margin_path, breach_score, scenario_sensitivity)
        Append(hazard_training_rows, (features, y_future, covenant_type, industry))

        Append(leading_indicator_cache, (W_t, features, y_future, covenant_type, industry))
    END FOR

    hazard_heads <- FitDiscreteHazardHeads(hazard_training_rows)
    calibration_tables <- FitMondrianConformalCalibrators(hazard_heads, hazard_training_rows)
    stability_table <- EstimateLeadingIndicatorStability(hazard_heads, leading_indicator_cache)

    RETURN Model(
        dynamics = (A_global_sparse, U, V, A_type, A_industry, B),
        breach_library = breach_library,
        recovery_library = recovery_library,
        hazard_heads = hazard_heads,
        calibration_tables = calibration_tables,
        stability_table = stability_table,
        lookback = L,
        horizon = H,
        warp_band = w
    )
END ALGORITHM


ALGORITHM PredictTACTIC_H(model M, live_client_state S_live, covenant c, optional scenario Delta)
    INPUT:
        M = trained TACTIC-H model
        S_live = latest historical spreads, ratios, macro values, and qualitative signals for one client
        c = covenant definition to score
        Delta = optional user-defined shock sequence over future periods
    OUTPUT:
        baseline breach probability, scenario probability, confidence interval, and leading indicators

    W_live <- BuildLatestWindow(S_live, c, M.lookback)
    x_last <- LastState(W_live)

    baseline_rollout <- RolloutDynamics(
        x_last,
        M.horizon,
        M.dynamics.global_sparse,
        M.dynamics.U,
        M.dynamics.V,
        M.dynamics.type[c.type],
        M.dynamics.industry[S_live.industry],
        M.dynamics.B
    )

    breach_candidates <- RetrieveCandidatePrototypes(M.breach_library, c.type, S_live.industry)
    recovery_candidates <- RetrieveCandidatePrototypes(M.recovery_library, c.type, S_live.industry)
    breach_score <- PrototypeRiskScore(W_live, breach_candidates, recovery_candidates, M.warp_band)

    baseline_margin_path <- ExtractForecastMargins(baseline_rollout)
    baseline_sensitivity <- EstimateLocalStressSensitivity(W_live, baseline_rollout, c.id)
    baseline_features <- FuseHazardFeatures(W_live, baseline_margin_path, breach_score, baseline_sensitivity)
    baseline_hazards <- EvaluateHazardHeads(M.hazard_heads, baseline_features)
    baseline_probability <- AggregateHazards(baseline_hazards)
    baseline_interval <- ConformalInterval(M.calibration_tables, baseline_probability, c.type)

    IF Delta is provided THEN
        # Fast counterfactual update: reuse precomputed Jacobians rather than retraining or
        # re-running a full nonlinear optimization for every slider movement.
        scenario_rollout <- ApplyScenarioByJacobianReuse(baseline_rollout, W_live, c, Delta)
        scenario_margin_path <- ExtractForecastMargins(scenario_rollout)
        scenario_sensitivity <- EstimateScenarioStressSensitivity(W_live, scenario_rollout, c.id, Delta)
        scenario_features <- FuseHazardFeatures(W_live, scenario_margin_path, breach_score, scenario_sensitivity)
        scenario_hazards <- EvaluateHazardHeads(M.hazard_heads, scenario_features)
        scenario_probability <- AggregateHazards(scenario_hazards)
    ELSE
        scenario_probability <- baseline_probability
    END IF

    leading_indicators <- RankLeadingIndicators(
        M.hazard_heads,
        W_live,
        baseline_rollout,
        M.stability_table,
        top_k = 5
    )

    RETURN {
        baseline_probability,
        scenario_probability,
        delta_probability = scenario_probability - baseline_probability,
        confidence_interval = baseline_interval,
        leading_indicators = leading_indicators,
        forecast_margin_path = baseline_margin_path,
        scenario_margin_path = scenario_margin_path if Delta is provided else baseline_margin_path
    }
END ALGORITHM
```

### 3.3 Why this architecture is production-viable

The core engineering virtue of TACTIC-H is separation of concerns.

- The dynamics learner handles temporal pooling under sparse histories.
- The prototype library handles cross-client analog retrieval.
- The hazard head converts both into calibrated event probabilities.
- The scenario engine reuses derivative structure instead of invoking slow Monte Carlo for every UI interaction.

This decomposition is materially better suited to the workspace than a monolithic deep model because it aligns with the data sources that already exist: spread values, covenant formulas, monitoring outcomes, and periodized histories.

## 4. Rigorous Complexity Analysis

Let:

- $N$ be the number of training windows,
- $d$ be the feature dimension of one period state,
- $L$ be the lookback length,
- $H$ be the forecast horizon,
- $E$ be the number of nonzero entries in the sparse transition operator $S$,
- $r$ be the low-rank dimension of $U V^\top$,
- $K_0$ be the total number of stored prototypes,
- $K$ be the number of prototypes retrieved for exact alignment after bucket filtering or ANN preselection,
- $w$ be the DTW warping band width,
- $I_d$ be the number of optimization iterations for the dynamics fit,
- $I_h$ be the number of optimization iterations for hazard-head training,
- $I_p$ be the number of barycenter updates when constructing prototype libraries.

### 4.1 Training time complexity

#### Step 1: Window and feature assembly

For each of the $N$ windows, we compute up to $L$ time steps of $d$-dimensional features.

$$
T_1 = O(NLd).
$$

#### Step 2: Hierarchical dynamics fitting

Each transition update applies a sparse matrix-vector product and a low-rank matrix-vector product.

- Sparse term cost per transition: $O(E)$.
- Low-rank term cost per transition: $O(rd)$, because $V^\top x$ costs $O(rd)$ and $U(\cdot)$ costs $O(rd)$.

Across roughly $N L$ transitions and $I_d$ optimization iterations:

$$
T_2 = O\big(I_d N L (E + rd)\big).
$$

If the sparse matrix were replaced by a dense $d \times d$ matrix, this would inflate to $O(I_d N L d^2)$, which is exactly why the sparse plus low-rank factorization matters.

#### Step 3: Prototype construction

Computing one band-limited elastic alignment between a live window and a prototype costs

$$
O(Lwd)
$$

instead of $O(L^2 d)$ under unrestricted warping. If prototype barycenter refinement runs for $I_p$ iterations over $K$ prototypes and a subset of windows proportional to $N$, then

$$
T_3 = O\big(I_p N K L w d\big).
$$

Worst-case, if $w=L$, this becomes

$$
T_3^{\mathrm{worst}} = O\big(I_p N K L^2 d\big).
$$

#### Step 4: Hazard-head fitting

Let $p$ denote the fused hazard-feature dimension. Training horizon-specific discrete hazard heads over $H$ horizons for $N$ windows costs

$$
T_4 = O(I_h N H p).
$$

#### Step 5: Conformal calibration and indicator stability

Calibration is linear in the number of scored windows and horizons:

$$
T_5 = O(NH).
$$

If leading-indicator stability is estimated over $F$ rolling folds, the added cost is

$$
O(F N H d),
$$

which is still lower-order than prototype alignment when $K$ and $w$ are nontrivial.

#### Total training complexity

Average-case training complexity is therefore

$$
T_{\mathrm{train}}
=
O\Big(
NLd
+
I_d N L (E + rd)
+
I_p N K L w d
+
I_h N H p
+
F N H d
\Big).
$$

Worst-case training complexity is

$$
T_{\mathrm{train}}^{\mathrm{worst}}
=
O\Big(
NLd
+
I_d N L d^2
+
I_p N K L^2 d
+
I_h N H p
\Big).
$$

Best-case training occurs when prototype construction is skipped for clients with insufficient history and the sparse operator remains very sparse:

$$
T_{\mathrm{train}}^{\mathrm{best}}
=
O\big(NLd + I_d N L (E + rd) + I_h N H p\big).
$$

### 4.2 Online inference time complexity

#### Step 1: Live window assembly

Building the current lookback state costs

$$
O(Ld).
$$

#### Step 2: Multi-horizon rollout

Rolling out $H$ steps through the sparse plus low-rank dynamics costs

$$
O\big(H(E + rd)\big).
$$

#### Step 3: Prototype retrieval and exact alignment

If an ANN or bucket prefilter is used, coarse retrieval over the full library costs approximately

$$
O(\log K_0)
$$

on average, after which exact alignment is performed on only $K$ candidates:

$$
O(KLwd).
$$

Without prefiltering and without banding, worst-case exact matching becomes

$$
O(K_0 L^2 d).
$$

#### Step 4: Hazard evaluation and interval construction

Evaluating horizon-specific hazard heads and constructing the conformal interval costs

$$
O(Hp).
$$

#### Total baseline inference complexity

Average-case baseline scoring per covenant is

$$
T_{\mathrm{infer}}
=
O\Big(
Ld
+
H(E + rd)
+
\log K_0
+
KLwd
+
Hp
\Big).
$$

Worst-case baseline scoring is

$$
T_{\mathrm{infer}}^{\mathrm{worst}}
=
O\Big(
Ld
+
H(E + rd)
+
K_0 L^2 d
+
Hp
\Big).
$$

Best-case baseline scoring, when the prototype channel is disabled because history is too short or a cached score exists, is

$$
T_{\mathrm{infer}}^{\mathrm{best}}
=
O\big(Ld + H(E + rd) + Hp\big).
$$

### 4.3 Interactive scenario-update complexity

This is where TACTIC-H materially improves over naive simulation.

Suppose a user shocks $q$ raw variables across $H$ horizons. Because Jacobians are precomputed, a slider update only applies derivative-based corrections plus hazard reevaluation:

$$
T_{\mathrm{scenario}}
=
O(Hq + Hp).
$$

Without Jacobian reuse, the system would need either full Monte Carlo or repeated nonlinear rollouts, which would raise the update cost back toward the baseline inference path or worse. The derivative cache is therefore not an implementation detail. It is a complexity-reducing algorithmic mechanism.

### 4.4 Space complexity

The dominant memory terms are:

1. Sparse transition operator: $O(E)$
2. Low-rank factors: $O(rd)$ for $U$ and $O(rd)$ for $V$, hence $O(rd)$ overall in big-O form
3. Type and industry residual operators: absorbed into sparse or low-rank storage, or bounded separately by a small constant number of buckets
4. Prototype library: $O(K_0 L d)$
5. Hazard heads: $O(Hp)$
6. Calibration tables and stability statistics: $O(HC + Hd)$, where $C$ is the number of covenant buckets

Thus the model storage complexity is

$$
S_{\mathrm{model}}
=
O\big(E + rd + K_0 L d + Hp + HC + Hd\big).
$$

Worst-case, if sparse structure is abandoned and prototype count explodes, storage becomes

$$
S_{\mathrm{worst}} = O(d^2 + K_0 L d).
$$

In the intended operating regime, the prototype library dominates storage, while inference latency is dominated by exact elastic alignment on a small shortlist.

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

TACTIC-H is not a minor variation on existing baseline families.

**Versus standard linear regression and heuristic trend scoring**

The current backend method projects a one-dimensional slope and converts it into a heuristic score. TACTIC-H instead models multi-horizon first-passage risk in signed margin space, integrates cross-client analog memory, and supports counterfactual shocks. The representational object is different, the learning objective is different, and the output semantics are different.

**Versus gradient-boosted trees and generic probability-of-default models**

Standard PD models and gradient-boosted tabular classifiers are strong on static credit snapshots, but they do not natively encode elastic trajectory matching against breach archetypes or formula-level counterfactual propagation. TACTIC-H explicitly exploits the deterministic covenant formula and treats breach as a threshold-hitting event, not merely as a generic binary label.

**Versus LSTM, GRU, and Temporal Fusion Transformer models**

Deep sequence models can learn temporal structure, but they are poorly matched to the small-history, sparse-label regime described in the brief. They also do not inherently produce fast scenario re-scoring unless an additional simulator is built around them. TACTIC-H is deliberately hybrid because the data regime is hybrid: short sequences, structured formulas, heterogeneous thresholds, and scarce breaches.

**Versus DTW, shapelets, and time-series prototype methods**

DTW-style methods measure similarity, but similarity alone does not yield calibrated breach probability. TACTIC-H does not stop at alignment. It transforms aligned prototype evidence into horizon-specific hazard contributions, fuses them with dynamic drift and scenario sensitivity, and calibrates the result with conformal prediction.

**Versus Merton/KMV/EDF-style structural credit-risk models**

Structural default models estimate firm-level default probability from asset-value dynamics, typically relying on market-value proxies and capital-structure assumptions. They do not operate at the covenant-formula level, do not use client-specific accounting trajectories as the primary state, and do not expose covenant-specific what-if sensitivities. TACTIC-H is therefore complementary to default-risk models, not derivative of them.

### Specific Claims

- A method for transforming heterogeneous covenant thresholds and operators into a unified signed safety-margin state space, and training a discrete-time multi-horizon hazard model directly on that normalized margin process.
- A method for computing covenant breach probability by fusing sparse plus low-rank client-state dynamics with threshold-aware elastic similarity to anonymized breach and recovery prototype libraries.
- A method for interactive covenant scenario simulation that precomputes covenant-formula Jacobians and Hessian approximations, then updates multi-horizon breach probabilities under user-defined shocks without retraining the predictive model.
- A method for ranking covenant leading indicators by combining hazard gradients, lagged dependency scores, and cross-regime stability penalties, thereby surfacing only indicators that are both predictive and stable.

The strongest patentable core is the combination of three mechanisms into one inference process: operator-normalized margin dynamics, threshold-aware elastic prototype memory, and Jacobian-reused counterfactual hazard updates. That triplet is the part least likely to be replicated by off-the-shelf credit-risk or time-series tooling and most likely to withstand a novelty challenge.