# Topic 4 of 6: Self-Improving Data Flywheel

## 1. Critical Analysis of Attached Context

The attached IP-4 brief identifies the correct strategic target: the long-term moat is not a static extraction model, but a closed learning system whose predictive accuracy, confidence calibration, and tenant-specific specialization improve as analysts use the platform. The current codebase already contains the seeds of such a flywheel, but the present design is still a collection of loosely connected features rather than a formally optimized learning system.

### Deconstruction of the Current Methodologies

**1. Feedback capture is correction-centric rather than signal-centric.**

- `ml-service/app/services/feedback_store.py` persists only a narrow correction record: source text, suggested item, corrected item, correction type, tenant, analyst, and model version.
- This is sufficient for explicit remapping supervision, but it does not represent the full feedback state space defined in the IP-4 brief: expression overrides, implicit acceptance, value-scale edits, zone corrections, and cross-reference additions are absent as first-class learning signals.
- The current schema therefore collapses a heterogeneous supervision problem into a single-table remapping log. That is a theoretical bottleneck because different feedback channels correspond to different loss surfaces. A zone correction should update a zone classifier, not be forced into the same representation as a semantic remap.

**2. The system has no notion of feedback authority, stability, or review depth.**

- Today, each correction is treated as if it were equally trustworthy.
- There is no revision-chain model for handling analyst mistakes followed by later corrections.
- There is no attention proxy for detecting low-authority implicit feedback, such as bulk acceptance executed with negligible review time.
- This means the current data pipeline assumes i.i.d. high-quality labels when the actual supervision process is sequential, noisy, and role-dependent.

**3. Per-tenant specialization is implemented as full-model duplication gated by raw count thresholds.**

- `ml-service/app/services/client_model_resolver.py` checks whether a tenant has at least `client_model_min_corrections` corrections and, if so, attempts to load a full tenant-specific SBERT model from MLflow.
- Let `|theta|` denote the full model parameter count and `T` the number of active tenants. The storage cost is `O(T |theta|)`. With hundreds of tenants, this scales poorly in both memory and cold-start latency.
- The threshold itself is count-based, not information-based. One hundred weak implicit accepts are not equivalent to one hundred explicit high-confidence remaps, yet the current gate treats them as interchangeable.

**4. The semantic matcher uses hard thresholds on uncalibrated cosine scores.**

- `ml-service/app/ml/semantic_matcher.py` computes source-target similarities with Sentence-BERT embeddings, applies a fixed cross-zone penalty, and then classifies confidence by two static thresholds.
- If there are `n` source rows, `m` target items, and embedding dimension `d`, the matching step is roughly `O((n + m) d + n m)` once embeddings are available.
- That is computationally reasonable, but the confidence semantics are mathematically weak. A cosine score of `0.85` is not a probability, and the current implementation does not estimate `P(correct | score, tenant, zone, model_version)`.
- As a result, any bulk-accept rule built on the present thresholds is structurally unsafe.

**5. A/B testing exists, but it is random routing rather than statistically grounded promotion control.**

- The semantic matcher already supports staging-model routing via a configurable ratio.
- However, the system does not optimize an explicit utility objective, does not control posterior regret, and does not include an automatic rollback condition beyond manual interpretation.
- In effect, staging traffic exists, but there is no closed-loop decision rule that turns that traffic into mathematically justified promotion decisions.

**6. Expression memory is ephemeral and label-based.**

- `ml-service/app/ml/expression_engine.py` includes `ExpressionMemory`, but it stores patterns in an in-memory dictionary keyed by `(tenant_id, customer_id)`.
- The current lookup cost is cheap, but the representation is too weak for a durable flywheel: patterns are not versioned, not persisted, not reconciled across conflicting edits, and not connected to downstream retraining.
- This means a major source of recurring analyst effort is observed but not converted into a lasting learning asset.

**7. The current retraining path is notebook-driven and externally orchestrated.**

- The training notebooks in `ml-training/notebooks/` are useful research assets, but operationally they imply a human-in-the-loop trigger path.
- There is no automated event-to-training-to-evaluation-to-promotion pipeline, so the learning latency is governed by human availability rather than information arrival.
- A self-improving system with delayed updates behaves like a sampled-data controller with excessive lag; once lag becomes large, the control loop becomes sluggish and its realized gain collapses.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

1. **The current supervision model is under-specified.** The platform receives at least six qualitatively different forms of feedback, but the implemented feedback representation captures only one channel robustly.

2. **Noise is modeled as if it did not exist.** There is no mechanism for debouncing repeated edits, suppressing low-authority acceptances, or down-weighting contradictory analyst behavior.

3. **The specialization trigger is mis-specified.** Using raw correction count as a retraining trigger ignores effective information content, analyst authority, class balance, and document diversity.

4. **Model storage scales linearly with full model size.** Full-clone tenant specialization has storage complexity `O(T |theta|)` and cold-start load cost proportional to the full checkpoint size. The intended moat should scale with signal, not with model duplication.

5. **Confidence is not calibrated.** The current matcher provides ranking scores, not trustworthy probabilities. That blocks principled bulk acceptance, ranking-aware analyst queues, and risk-sensitive automation.

6. **Cross-tenant transfer is absent at the algorithmic level.** There is no representation for privacy-preserving sector-level knowledge that can help new tenants before they have much local history.

7. **Promotion is not risk-bounded.** A/B routing exists, but the system lacks a formal policy such as `promote only if posterior utility exceeds epsilon with confidence 1 - delta`.

8. **The current design separates learning problems that should be coupled.** Mapping, expression inference, zone correction, scale detection, and cross-reference learning are all downstream of the same analyst workflow, yet the present system treats them as isolated or entirely unmodeled tasks.

The core research gap is therefore not merely "add LoRA" or "add calibration." The missing theoretical object is a unified learning algorithm that converts heterogeneous analyst interactions into weighted evidence, updates a hierarchical personalized model under strict memory limits, calibrates its uncertainty, and decides when a candidate update is safe to promote.

## 2. The Novel Algorithmic Proposal

### Name

**Evidence-Weighted Hierarchical Adapter-Calibrator (EWHAC)**

### Core Intuition

EWHAC treats the self-improving flywheel as a single constrained optimization problem rather than four disconnected features.

The key idea is to map every analyst interaction into a **typed evidence event** with an explicit authority weight, then use that weighted evidence to drive three coupled updates:

1. **Hierarchical low-rank personalization**: a tenant model is not a full checkpoint clone. It is a composition of a global backbone, an industry adapter, and a tenant adapter whose strength grows with tenant-specific effective evidence.
2. **Bayesian confidence calibration**: raw model scores are converted into context-aware probabilities using hierarchical priors so that sparse tenants inherit global and industry knowledge before enough local data exists.
3. **Risk-bounded promotion control**: candidate adapters are promoted only when the posterior probability of positive utility exceeds a predefined threshold under online A/B evidence.

The mechanism is fundamentally different from existing SOTA fragments.

- Standard PEFT methods such as LoRA solve the storage problem, but they do not solve heterogeneous feedback weighting, calibration, or promotion.
- Standard calibration methods such as isotonic regression or Platt scaling solve post-hoc probability estimation, but they do not solve tenant specialization under sparse data.
- Standard recommendation-system feedback loops use acceptance logs, but they rarely couple those logs to a hierarchical low-rank adapter stack with multi-task supervision and formal rollback control.

EWHAC is novel because it unifies these components around **effective information** rather than raw event count. The system retrains when weighted evidence is sufficient, not merely when enough rows have been logged.

### Mathematical Formulation

Let each analyst interaction be an event

$$
e_i = (x_i, \hat{y}_i, y_i, c_i, m_i, s_i, t_i, \tau_i),
$$

where:

- `x_i` is the document-context input,
- `hat{y}_i` is the model output before analyst intervention,
- `y_i` is the analyst-confirmed outcome,
- `c_i` is the feedback channel in `{explicit, implicit, expr, value, zone, xref}`,
- `m_i` is metadata such as tenant, industry, document type, page, section, analyst role, dwell time, and review depth,
- `s_i` is the raw model score,
- `t_i` is the model version identifier,
- `tau_i` is the event timestamp.

Because analyst actions may be revised, EWHAC first applies a revision-collapse operator `R` over event chains keyed by `(tenant, document, object_id, channel)`:

$$
e_i^* = R(e_{i,1}, e_{i,2}, \ldots, e_{i,k}),
$$

where only the final stable event in the chain is used for supervision. Stability can be defined by either document finalization or by a no-edit interval exceeding a debounce horizon `Delta`.

For each stable event, define an authority weight

$$
w_i = \lambda_{c_i}
      \cdot \sigma(u^T z_i)
      \cdot \exp(-\eta r_i)
      \cdot I[\mathrm{stable}(e_i^*) = 1],
$$

where:

- `lambda_{c_i}` is a base channel weight,
- `z_i` is a feature vector derived from metadata `m_i` (dwell time, bulk size, analyst role, review depth, document completion state, disagreement history),
- `r_i` is the number of reversals observed before stabilization,
- `sigma` is the logistic function.

This immediately solves two failure modes in the brief:

- noisy analyst corrections are collapsed into one authoritative signal,
- lazy bulk acceptance receives low `w_i` because the attention proxy in `z_i` is weak.

Now define a multi-task output space

$$
\mathcal{Y} = \mathcal{Y}_{map} \times \mathcal{Y}_{expr} \times \mathcal{Y}_{scale} \times \mathcal{Y}_{zone} \times \mathcal{Y}_{xref}.
$$

Each event activates only the tasks relevant to its channel. The weighted training objective for tenant `a` in industry `g` is

$$
\mathcal{L}_{a} = \sum_{i \in E_a} w_i \sum_{h \in H_i} \mu_h \, \ell_h(f_{\Theta, g, a}(x_i), y_i^{(h)}) + \Omega_{rank} + \Omega_{drift},
$$

where:

- `E_a` is the stable event set for tenant `a`,
- `H_i` is the subset of active tasks for event `i`,
- `mu_h` is the task weight,
- `ell_h` is the task-specific loss,
- `Omega_rank` regularizes adapter complexity,
- `Omega_drift` penalizes excessive deviation from the global model.

The personalized model uses a hierarchical adapter composition. For each adapted layer `l`, let the frozen backbone weight be `W_l`. EWHAC uses

$$
W_l' = W_l + A_{g,l} B_{g,l} + \gamma_a A_{a,l} B_{a,l},
$$

with:

- `A_{g,l} in R^{d x r_g}`, `B_{g,l} in R^{r_g x d}` for the industry adapter,
- `A_{a,l} in R^{d x r_a}`, `B_{a,l} in R^{r_a x d}` for the tenant adapter,
- `gamma_a in [0,1]` a tenant-specific gate.

The tenant gate is driven by effective evidence rather than a hard count threshold:

$$
N_{eff}(a) = \frac{(\sum_{i \in E_a} w_i)^2}{\sum_{i \in E_a} w_i^2},
$$

$$
\gamma_a = \frac{N_{eff}(a)}{N_{eff}(a) + \kappa}.
$$

This is critical. A tenant with sparse but high-authority corrections can activate specialization earlier than a tenant with many low-information acceptances.

EWHAC also uses a dynamic tenant rank schedule:

$$
r_a = \min\left(r_{max}, \, r_0 + \left\lfloor \log_2\left(1 + \frac{N_{eff}(a)}{\tau_0}\right) \right\rfloor \right).
$$

Therefore, tenants with little evidence receive a tiny adapter, while high-activity tenants earn more capacity. This makes storage grow with information, not with client count alone.

For cross-tenant transfer, EWHAC does not share raw source text across tenants. Instead, the industry adapter is updated from clipped low-rank gradient sketches:

$$
\tilde{g}_i = \mathrm{clip}(\Pi_r(\nabla \ell_i), C) + \mathcal{N}(0, \sigma^2 C^2 I),
$$

where `Pi_r` is a low-rank projection, `C` is a clipping norm, and Gaussian noise provides differential privacy at the sketch level. These sketches are aggregated per industry to maintain `A_{g,l}, B_{g,l}` without exporting raw tenant content.

Confidence calibration is hierarchical and Bayesian. For a context

$$
u = (tenant, industry, zone, item\_class, model\_version),
$$

partition the raw score range into `B` bins. For each bin `b`, maintain a Beta posterior:

$$
\theta_{u,b} \sim \mathrm{Beta}(\alpha_{u,b}^{(0)}, \beta_{u,b}^{(0)}),
$$

$$
\theta_{u,b} \mid D \sim \mathrm{Beta}\left(\alpha_{u,b}^{(0)} + \sum_{i \in D_{u,b}} w_i r_i, \, \beta_{u,b}^{(0)} + \sum_{i \in D_{u,b}} w_i (1-r_i)\right),
$$

where `r_i in {0,1}` indicates whether the model suggestion was ultimately confirmed correct. The prior parameters `(alpha_{u,b}^{(0)}, beta_{u,b}^{(0)})` are inherited hierarchically from global and industry statistics. The calibrated confidence function is the isotonic projection of posterior means:

$$
\bar{p}_{u,b} = E[\theta_{u,b} \mid D],
$$

$$
g_u(s) = \mathrm{IsoMonotone}(\bar{p}_{u, b(s)}).
$$

This guarantees monotonicity while remaining data-efficient for new tenants.

Finally, promotion is framed as a posterior utility test. For a staging candidate, define

$$
U = \Delta Acc + a_1 \Delta AutoAccept - a_2 \Delta Corrections - a_3 \Delta ECE - a_4 \Delta Latency,
$$

where:

- `Delta Acc` is accuracy lift,
- `Delta AutoAccept` is the increase in safe automation,
- `Delta Corrections` is the reduction in analyst rework,
- `Delta ECE` is calibration improvement,
- `Delta Latency` is inference-time overhead.

Promote if and only if

$$
P(U > \epsilon \mid D_{AB}) \ge 1 - \delta,
$$

and rollback if

$$
P(U < -\epsilon \mid D_{AB}) \ge 1 - \delta_{rb}.
$$

The self-improving flywheel is therefore controlled by weighted evidence, hierarchical adaptation, calibrated uncertainty, and bounded deployment risk.

## 3. Technical Architecture & Pseudocode

The production architecture of EWHAC has five algorithmic stages:

1. `Event Canonicalization`: collapse revision chains and discard unstable feedback.
2. `Evidence Weighting`: assign an authority weight to each stable event.
3. `Hierarchical Adaptation`: build or update industry and tenant adapters from weighted multi-task samples.
4. `Bayesian Calibration`: update tenant-aware confidence posteriors and fit a monotone calibration curve.
5. `Risk-Bounded Promotion`: evaluate candidates offline, deploy to staging, and promote or rollback using posterior utility.

```text
PROCEDURE EWHAC_FLYWHEEL(event_batch, global_model, state):
    # event_batch is assumed to be timestamp-ordered if it comes from Kafka.
    # If the source is a backfill, sort once before processing.

    canonical_events = COLLAPSE_REVISION_CHAINS(event_batch, state.debounce_horizon)
    active_tenants = EmptySet()

    FOR each event IN canonical_events:
        tenant_id = event.tenant_id
        industry_id = event.industry_id

        weight = COMPUTE_AUTHORITY_WEIGHT(event, state.channel_weights, state.weight_model)
        IF weight <= state.min_signal_weight:
            CONTINUE  # Ignore unstable or clearly low-authority supervision.

        training_samples = MATERIALIZE_MULTITASK_SAMPLES(event, weight)
        APPEND_TO_TENANT_BUFFER(state.tenant_buffers[tenant_id], training_samples)

        # Update correctness statistics for calibration using the final confirmed outcome.
        UPDATE_CALIBRATION_COUNTS(
            calibrator_state = state.calibration[tenant_id],
            raw_score        = event.raw_score,
            context_key      = BUILD_CONTEXT_KEY(event),
            confirmed_label  = event.was_model_correct,
            weight           = weight,
        )

        # Share only clipped low-rank sketches to the industry pool.
        IF event.allow_industry_sharing IS TRUE:
            sketch = BUILD_PRIVATE_GRADIENT_SKETCH(training_samples, state.privacy_cfg)
            MERGE_SECTOR_SKETCH(state.industry_sketches[industry_id], sketch)

        active_tenants.ADD(tenant_id)

    FOR each tenant_id IN active_tenants:
        buffer = state.tenant_buffers[tenant_id]
        n_eff = EFFECTIVE_SAMPLE_SIZE(buffer.weights)
        expected_gain = ESTIMATE_UTILITY_GAIN(buffer, state.online_metrics[tenant_id])

        # Trigger on information content, not on raw event count alone.
        IF n_eff < state.min_information_threshold AND expected_gain < state.min_expected_gain:
            CONTINUE

        industry_id = LOOKUP_INDUSTRY(tenant_id)
        sector_adapter = BUILD_OR_REFRESH_SECTOR_ADAPTER(
            industry_sketch = state.industry_sketches[industry_id],
            base_model      = global_model,
            rank            = state.sector_rank,
        )

        tenant_rank = CHOOSE_DYNAMIC_RANK(
            n_eff   = n_eff,
            r0      = state.rank_floor,
            rmax    = state.rank_cap,
            tau0    = state.rank_scale,
        )

        tenant_gate = n_eff / (n_eff + state.gate_kappa)
        candidate_adapter = TRAIN_HIERARCHICAL_ADAPTER(
            base_model     = global_model,
            sector_adapter = sector_adapter,
            tenant_buffer  = buffer,
            tenant_rank    = tenant_rank,
            tenant_gate    = tenant_gate,
            train_cfg      = state.train_cfg,
        )

        candidate_calibrator = FIT_BAYESIAN_MONOTONE_CALIBRATOR(
            global_prior  = state.global_calibration,
            sector_prior  = state.sector_calibration[industry_id],
            tenant_counts = state.calibration[tenant_id],
            bins          = state.num_calibration_bins,
        )

        offline_metrics = OFFLINE_EVALUATE(
            model       = candidate_adapter,
            calibrator  = candidate_calibrator,
            holdout_set = state.holdouts[tenant_id],
        )

        IF VIOLATES_GUARDRAILS(offline_metrics, state.guardrails):
            ARCHIVE_CANDIDATE(tenant_id, candidate_adapter, reason = "offline regression")
            CONTINUE

        DEPLOY_TO_STAGING(
            tenant_id  = tenant_id,
            adapter    = candidate_adapter,
            calibrator = candidate_calibrator,
            traffic    = state.staging_ratio,
        )

    FOR each tenant_id WITH active staging traffic:
        posterior = UPDATE_PROMOTION_POSTERIOR(
            metrics        = state.online_metrics[tenant_id],
            epsilon        = state.utility_margin,
            latency_budget = state.latency_budget,
        )

        IF posterior.prob_improve >= 1 - state.promote_delta AND posterior.regret <= state.max_regret:
            PROMOTE_TO_PRODUCTION(tenant_id)
            SNAPSHOT_MODEL_STATE(tenant_id, tag = "promoted")

        ELSE IF posterior.prob_regress >= 1 - state.rollback_delta:
            ROLLBACK_STAGING(tenant_id)
            SNAPSHOT_MODEL_STATE(tenant_id, tag = "rolled_back")


PROCEDURE COLLAPSE_REVISION_CHAINS(event_batch, debounce_horizon):
    chains = HashMap()  # key = (tenant, document, object_id, channel)

    FOR each event IN event_batch:
        key = (event.tenant_id, event.document_id, event.object_id, event.channel)
        chains[key].APPEND(event)

    stable_events = EmptyList()

    FOR each key, chain IN chains:
        # Because the batch is time-ordered, the last event is the newest candidate.
        final_event = LAST(chain)

        IF final_event.document_finalized IS TRUE:
            stable_events.APPEND(final_event)
            CONTINUE

        # If the analyst kept editing inside the debounce window, defer learning.
        IF NOW() - final_event.timestamp < debounce_horizon:
            CONTINUE

        stable_events.APPEND(final_event)

    RETURN stable_events


PROCEDURE COMPUTE_AUTHORITY_WEIGHT(event, channel_weights, weight_model):
    # Features include dwell time, bulk action size, analyst role, reversal count,
    # review depth, and whether the document reached a locked/finalized state.
    features = EXTRACT_AUTHORITY_FEATURES(event)
    attention_score = SIGMOID(DOT(weight_model, features))
    reversal_penalty = EXP(-event.reversal_count)

    RETURN channel_weights[event.channel] * attention_score * reversal_penalty


PROCEDURE MATERIALIZE_MULTITASK_SAMPLES(event, weight):
    samples = EmptyList()

    IF event.channel == "explicit":
        samples.APPEND(MAKE_MAPPING_POSITIVE(event.correct_item, weight))
        samples.APPEND(MAKE_MAPPING_HARD_NEGATIVE(event.suggested_item, weight))

    ELSE IF event.channel == "implicit":
        # Implicit acceptance is weaker supervision, but still useful when weighted.
        samples.APPEND(MAKE_MAPPING_POSITIVE(event.suggested_item, weight))

    ELSE IF event.channel == "expr":
        samples.APPEND(MAKE_EXPRESSION_SAMPLE(event.correct_expression, weight))

    ELSE IF event.channel == "value":
        samples.APPEND(MAKE_SCALE_SAMPLE(event.scale_factor, weight))

    ELSE IF event.channel == "zone":
        samples.APPEND(MAKE_ZONE_SAMPLE(event.correct_zone, weight))

    ELSE IF event.channel == "xref":
        samples.APPEND(MAKE_XREF_SAMPLE(event.linked_section, weight))

    RETURN samples


PROCEDURE TRAIN_HIERARCHICAL_ADAPTER(base_model, sector_adapter, tenant_buffer, tenant_rank, tenant_gate, train_cfg):
    model = ATTACH_SECTOR_ADAPTER(base_model, sector_adapter)
    model = ATTACH_EMPTY_TENANT_ADAPTER(model, rank = tenant_rank, gate = tenant_gate)

    FOR epoch FROM 1 TO train_cfg.epochs:
        FOR batch IN WEIGHTED_MINIBATCHES(tenant_buffer, train_cfg.batch_size):
            predictions = FORWARD(model, batch.inputs)
            loss = 0

            FOR each task_name IN batch.active_tasks:
                task_loss = TASK_LOSS(task_name, predictions[task_name], batch.targets[task_name])
                loss = loss + batch.task_weight[task_name] * task_loss

            # Sample weights are applied after task losses so every channel can
            # influence its own head without being artificially flattened.
            loss = WEIGHT_BY_SAMPLE(loss, batch.sample_weights)
            loss = loss + DRIFT_REGULARIZER(model.tenant_adapter) + RANK_REGULARIZER(model.tenant_adapter)

            BACKPROPAGATE(loss)
            OPTIMIZER_STEP(model.tenant_adapter)
            ZERO_GRAD(model.tenant_adapter)

    RETURN EXPORT_TENANT_ADAPTER(model)


PROCEDURE FIT_BAYESIAN_MONOTONE_CALIBRATOR(global_prior, sector_prior, tenant_counts, bins):
    posterior_means = Array(size = bins)

    FOR b FROM 1 TO bins:
        alpha0, beta0 = MIX_PRIORS(global_prior[b], sector_prior[b])
        success = tenant_counts[b].weighted_success
        failure = tenant_counts[b].weighted_failure
        posterior_means[b] = (alpha0 + success) / (alpha0 + beta0 + success + failure)

    # Pool-adjacent-violators enforces monotonicity without discarding the Bayesian prior.
    monotone_curve = ISOTONIC_PROJECTION(posterior_means)
    RETURN BUILD_CALIBRATOR(monotone_curve)
```

## 4. Rigorous Complexity Analysis

Let:

- `N` be the number of raw feedback events in the processing window,
- `M <= N` be the number of stable events after revision collapse,
- `T_a` be the number of tenants that received new stable events in the window,
- `Q_j` be the number of weighted training samples for triggered tenant `j`,
- `E` be the number of fine-tuning epochs,
- `L` be the number of adapted transformer layers,
- `P_a` be the number of adapted projections per layer,
- `d` be the hidden width of the backbone,
- `r_j` be the tenant adapter rank for tenant `j`,
- `B` be the number of calibration bins,
- `S` be the number of online staging requests observed during the promotion phase,
- `C_f` be the frozen-backbone forward/backward cost per training sample.

### Time Complexity Derivation

**Step 1: Revision collapse**

- With a timestamp-ordered event stream, each raw event is inserted into exactly one hash bucket and the final stable event per bucket is selected once.
- Average-case cost: `O(N)`.
- Worst-case cost with unordered backfill requiring sort first: `O(N log N)`.

**Step 2: Authority weighting and sample materialization**

- Authority feature extraction is constant-time because the number of channels and metadata fields is fixed.
- Sample materialization per event is also constant-time because each event activates at most a constant number of task heads.
- Total cost over all stable events: `O(M)`.

**Step 3: Calibration count updates**

- Each stable event updates exactly one contextual score bin.
- Total update cost: `O(M)`.
- Fitting the monotone calibration curve with pool-adjacent-violators over `B` bins is `O(B)` per triggered tenant.

**Step 4: Trigger evaluation**

- Effective sample size computation for one tenant buffer is linear in the number of weights for that tenant.
- Aggregated over all active tenants, this is `O(M + T_a)` because every stable sample contributes to exactly one tenant.

**Step 5: Hierarchical LoRA training**

- For one triggered tenant `j`, each minibatch pass costs `O(C_f + L P_a d r_j)`.
- `C_f` is the frozen backbone pass, while `L P_a d r_j` is the incremental low-rank adaptation cost.
- Over `Q_j` weighted samples and `E` epochs, the total training cost is:

$$
O\left(E Q_j (C_f + L P_a d r_j)\right).
$$

- If `K` tenants trigger retraining in the same window, the total training cost is:

$$
O\left(\sum_{j=1}^{K} E Q_j (C_f + L P_a d r_j)\right).
$$

This is the dominant term in the pipeline.

**Step 6: Offline evaluation and staging posterior updates**

- If offline evaluation uses a holdout of size `H_j` for tenant `j`, the cost is `O(H_j (C_f + L P_a d r_j))`.
- Online posterior updates are constant-time per staging request if sufficient statistics are maintained incrementally.
- Therefore the online promotion phase costs `O(S)`.

### Worst-Case Time Complexity

The worst case occurs when:

- the input batch must be sorted,
- every tenant in the active window crosses the information threshold,
- every triggered tenant runs full adapter training and offline evaluation.

The total worst-case complexity is therefore

$$
O\left(N \log N + M + \sum_{j=1}^{K} E Q_j (C_f + L P_a d r_j) + K B + S\right).
$$

Since training dominates, this is typically summarized as

$$
O\left(N \log N + \sum_{j=1}^{K} E Q_j (C_f + L P_a d r_j) + S\right).
$$

### Best-Case Time Complexity

The best case occurs when no tenant crosses the information threshold and no retraining is triggered.

- Revision collapse, weighting, and calibration updates still run.
- No training, no offline evaluation, and no new staging deployment occurs.

Thus the best-case complexity is

$$
O(N)
$$

for an ordered stream, or `O(N log N)` only if a sort is required.

### Average-Case Time Complexity

Let `p` be the probability that an active tenant crosses the trigger threshold in a given window, and let `bar{Q}` and `bar{r}` denote the average training sample count and average dynamic rank among triggered tenants. Then the expected cost is

$$
O\left(N + p T_a E \bar{Q} (C_f + L P_a d \bar{r}) + p T_a B + S\right).
$$

This average-case form is important because the algorithm is explicitly designed to keep `p` small by using effective information thresholds. The system does not retrain on every new correction burst.

### Space Complexity Derivation

**1. Event buffers**

- Stable event and sample buffers store at most `O(M)` records per active window.

**2. Calibration state**

- Each tenant-context stores `B` bins of weighted success/failure counts.
- If `U` contextual calibrators are retained, the calibration memory is `O(U B)`.

**3. Industry and tenant adapters**

- A LoRA-style adapter for one projection matrix of size `d x d` with rank `r` stores `A in R^{d x r}` and `B in R^{r x d}`.
- That is `2 d r` parameters per adapted matrix.
- Across `L` layers and `P_a` adapted matrices per layer, one adapter requires

$$
O(L P_a d r)
$$

parameters.

- With `G` industry adapters and `K_act` active tenant adapters, total adapter memory is

$$
O\left(G L P_a d r_g + \sum_{j=1}^{K_{act}} L P_a d r_j\right).
$$

This is dramatically smaller than full-clone tenant specialization, whose storage is `O(K_act L P_a d^2)`.

**4. Promotion statistics**

- The online posterior test stores only sufficient statistics per staging model, so its memory cost is `O(K_act)` or `O(K_act C)` if broken down by a constant number of tracked metrics.

### Total Space Complexity

The total space complexity is

$$
O\left(M + U B + G L P_a d r_g + \sum_{j=1}^{K_{act}} L P_a d r_j\right).
$$

In practice, this is precisely why EWHAC is operationally attractive. If `d = 768`, `L = 12`, `P_a = 4`, and a tenant rank grows only to `r = 8`, the tenant adapter remains on the order of megabytes rather than hundreds of megabytes. The asymptotic reduction from `d^2` to `d r` is the decisive storage advantage.

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

**Against raw-count retraining pipelines**

- The current codebase and many practical ML systems use crude trigger rules such as "retrain after 100 corrections."
- EWHAC replaces this with a weighted effective-sample-size trigger, which depends on signal authority, revision stability, and supervision diversity.
- This is theoretically superior because it optimizes on information content rather than event cardinality.

**Against standard LoRA or PEFT specialization**

- Vanilla LoRA solves parameter efficiency, but not when to adapt, how much rank to allocate, how to combine cross-tenant priors, or how to calibrate outputs.
- EWHAC adds dynamic rank scheduling, hierarchical industry-plus-tenant composition, and evidence-driven adapter gating.
- The differentiator is not low-rank adaptation alone, but low-rank adaptation governed by a weighted evidence controller.

**Against standard post-hoc calibration methods**

- Platt scaling, temperature scaling, and isotonic regression are known calibration tools, but they are generally applied globally or per model version.
- EWHAC uses hierarchical Bayesian priors so new tenants inherit global and industry calibration structure before enough local data exists.
- This directly addresses the sparse-data regime that breaks naive per-tenant calibration.

**Against standard A/B testing or fixed-horizon model promotion**

- Fixed-horizon A/B tests optimize deployment process but do not define a task-specific utility functional combining accuracy, calibration, analyst burden, and latency.
- EWHAC promotes models with a posterior utility criterion and explicit rollback probability threshold.
- This is more aligned with the actual product objective: reduce analyst effort without increasing risk.

**Against recommender-system feedback loops**

- Recommendation systems use implicit signals such as clicks and watch time, but they generally operate on single-task preference learning.
- EWHAC is multi-task and document-grounded: the same evidence event may affect semantic mapping, scale detection, expression inference, and cross-reference linkage.
- That coupling is rare in standard RecSys literature and particularly valuable in financial-spreading workflows.

### Specific Claims

- A method for converting heterogeneous analyst interactions into a unified typed evidence stream using revision-chain collapse, authority scoring, and channel-specific sample materialization prior to model adaptation.

- A method for triggering tenant-specific low-rank adaptation using weighted effective sample size and expected utility gain, rather than raw correction counts, with adaptive rank allocation as evidence accumulates.

- A hierarchical personalization method in which an industry adapter and a tenant adapter are composed at inference time with an evidence-driven tenant gate, while industry knowledge is learned from privacy-preserving low-rank gradient sketches instead of raw tenant text.

- A deployment-control method that jointly evaluates accuracy lift, correction reduction, confidence calibration error, and inference latency, and promotes or rolls back staged models using posterior utility thresholds.

The strongest patentability posture does not rest on claiming LoRA, isotonic calibration, or A/B testing in isolation, because those components already exist in prior art. The potentially patentable contribution is the integrated process that couples weighted evidence extraction, adaptive low-rank personalization, hierarchical Bayesian calibration, and posterior-risk deployment control into one self-improving financial-document learning loop.