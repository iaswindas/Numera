# Topic 1 of 6: Arithmetic Relationship Inference in Financial Documents

## 1. Critical Analysis of Attached Context

The attached context is directionally strong, but it still stops short of a mathematically unified inference engine. Across the current code and research notes, the system is best described as semantic anchoring plus local subtotal validation, with global accounting logic applied only after most decisions have already been made.

### Deconstruction of the Current Methodologies

**1. Semantic pre-filtering is useful, but inherently unary.**

- `ml-service/app/ml/semantic_matcher.py` computes source-to-target cosine similarity with a fixed cross-zone penalty.
- Computationally, the matching stage is roughly $O((n+m)d + nm)$ after embedding, where $n$ is the number of source rows, $m$ is the number of target items, and $d$ is the embedding dimension.
- The structural limitation is not runtime; it is representational. Each source row is scored against each target independently, so the engine cannot express the fact that one target may be explained by a signed combination of several source values.
- The fixed zone penalty is also theoretically weak. A constant multiplier is not calibrated to ambiguity, document type, or statement topology, so it behaves as a heuristic prior rather than a principled constraint.

**2. The existing expression engine is a local recognizer, not a discovery engine.**

- `ml-service/app/ml/expression_engine.py` constructs mappings after semantic matches already exist.
- `_detect_hierarchy()` is a single forward scan over contiguous rows, so its complexity is only $O(n)$, but the model of hierarchy is extremely narrow: pending indented rows are attached to the next total row and reset on headers or top-level rows.
- `_try_sum_expression()` is effectively $O(c)$ for a total with $c$ candidate children, but it only validates a subtotal that is already obvious from indentation. It does not search over alternative child sets, signed relations, cross-page groupings, or cross-statement flows.
- Because it only upgrades an already matched total row into a `SUM` expression, it cannot discover latent arithmetic structure when labels are noisy, multilingual, or semantically uninformative.

**3. The formula engine evaluates syntax; it does not infer structure.**

- `backend/src/main/kotlin/com/numera/model/application/FormulaEngine.kt` is a correct arithmetic parser/evaluator for formulas that are already known.
- Its time complexity is linear in formula length after tokenization, which is appropriate for execution.
- Its theoretical limitation is categorical: it solves evaluation, not inference. It cannot propose candidate formulas, rank alternatives, or enforce source exclusivity.

**4. The current ARIE design note identifies the right combinatorial core, but still treats the problem too locally.**

- `IPRD/ip2_arithmetic_relationship_inference.md` correctly frames the task as a signed subset-sum/CSP variant and correctly identifies zone, hierarchy, and accounting identities as pruning signals.
- However, the proposed use of `used_sources` makes the solver greedy in a way that is mathematically dangerous. Once an early target claims a source value, later targets do not see it, even if a globally superior assignment exists.
- The architecture alternates between a 3-pass and a 5-pass description across the attached notes, which is a signal that the real optimization boundary has not yet been formalized.

**5. The more detailed ARIE research output improves the narrative, but not the inferential coupling.**

- `IPRD/ip2_arie_research_output.md` upgrades the proposal to a multi-pass constrained search with cross-statement validation and a later global optimization step.
- The main bottleneck remains candidate generation by iterative enumeration of signed $k$-subsets. For a target with candidate pool size $p$ and max arity $K$, the stated cost is

$$
\sum_{k=2}^{K} \binom{p}{k} 2^k,
$$

which is already much better than unconstrained $2^n$, but it is still a per-target local search that scales poorly as ambiguity grows.
- More importantly, Pass 4 and Pass 5 remain corrective layers. Cross-statement identities are used to confirm or repair local decisions after the local search has already committed. That means the strongest accounting constraints are not steering the search frontier itself.

**6. The FDKG concept is present in the broader research program, but ARIE is not yet graph-native.**

- `IPRD/ip1_financial_document_knowledge_graph.md` argues that financial documents should be represented as persistent, typed graphs.
- Yet the current arithmetic reasoning still operates over flat candidate pools plus simple indentation heuristics.
- This is the main theoretical gap: the document structure exists, but it is not being used as the optimization substrate.

### Strict Limitations, Bottlenecks, and Theoretical Gaps

1. **Greedy exclusivity is globally suboptimal.**
   A local `used_sources` policy converts a joint assignment problem into a sequence of irreversible decisions. That is not merely an implementation detail; it changes the problem class from global set packing with constraints to greedy matching without optimality guarantees.

2. **The current subset search is still target-centric rather than explanation-centric.**
   Each target is solved independently, so the system never represents the global family of competing arithmetic explanations and their overlaps. Without that representation, exclusivity and identity constraints cannot be enforced elegantly.

3. **Rounding is treated as scalar tolerance instead of structured quantization.**
   A single relative tolerance $\varepsilon$ is too blunt for financial documents where rounding depends on units, display precision, and statement type. The absence of a target-specific quantization model creates both false positives and unnecessary search.

4. **Cross-statement identities are post-hoc, not endogenous.**
   Balance-sheet, income-statement, and cash-flow constraints should shape the candidate search itself. In the current design, they are mostly validation rules layered on top of already chosen local matches.

5. **No compressed representation of candidate arithmetic relations exists.**
   The current notes search directly over subsets. A more scalable representation is a conflict-aware arithmetic hypergraph in which each feasible subset explanation is a weighted hyperedge and inference happens over that graph.

6. **The current theory does not separate unavoidable NP-hardness from removable computational waste.**
   The global problem is still NP-hard, but the present design leaves removable waste in the local search procedure. In particular, it does not exploit meet-in-the-middle decomposition, tolerance-lattice bucketing, or candidate dominance pruning.

7. **The system has no principled ambiguity management.**
   Multiple valid combinations are expected in real statements. The current design ranks them heuristically, but it does not maintain dual prices, posterior-style penalties, or any explicit mechanism for resolving conflicts under global constraints.

The attached context therefore reveals a precise opportunity: replace greedy local subset search with a graph-native, rounding-aware, globally coupled inference algorithm.

## 2. The Novel Algorithmic Proposal

### Name

**Adaptive Tolerance-Lattice Hypergraph Inference (ATLHI)**

### Core Intuition

ATLHI changes the optimization object. Instead of asking, one target at a time, "which subset sums to this value?", it first compresses the local arithmetic search space into a set of feasible signed explanations, then performs joint global inference over those explanations.

The novel mechanism has three tightly coupled components:

1. **Adaptive tolerance lattices** convert each target-specific rounding regime into a bounded integer residual problem, so rounding ambiguity is modeled structurally rather than as a vague scalar margin.
2. **Signed meet-in-the-middle candidate generation** enumerates feasible partial sums on two halves of a local neighborhood, using complement lookup on quantized residues instead of naive full subset enumeration.
3. **Conflict-aware hypergraph selection with identity dual variables** chooses a globally consistent family of arithmetic explanations while enforcing source exclusivity and penalizing violations of accounting identities during inference, not after inference.

This is fundamentally different from the current SOTA in the attached material. The present system searches locally and validates globally. ATLHI generates local explanations, then solves the global consistency problem directly on a weighted arithmetic hypergraph.

### Mathematical Formulation

Let the extracted source values be

$$
S = \{s_i\}_{i=1}^{n}, \quad s_i = (v_i, z_i, h_i, r_i, \ell_i),
$$

where $v_i \in \mathbb{R}$ is the numeric value, $z_i$ is the zone, $h_i$ is the hierarchy level, $r_i$ is the row position, and $\ell_i$ is the label.

Let the target items be

$$
T = \{t_j\}_{j=1}^{m}, \quad t_j = (u_j, z_j^*, h_j^*, r_j^*, \ell_j^*),
$$

where $u_j$ is the expected or observed target value when available.

For each target $t_j$, define a target-specific tolerance lattice width

$$
\delta_j = \max\big(\beta_{z_j^*}, \alpha_{z_j^*} |u_j|, \kappa \cdot \text{unit}_j\big),
$$

where:

- $\beta_{z_j^*}$ is a zone-level absolute tolerance floor,
- $\alpha_{z_j^*}$ is a relative tolerance coefficient,
- $\text{unit}_j \in \{1, 10^3, 10^6\}$ is the detected display unit,
- $\kappa$ is a rounding slack constant.

Quantize source and target values onto the lattice of target $j$:

$$
q_i^{(j)} = \left\lfloor \frac{v_i}{\delta_j} \right\rceil,
\qquad
q_j = \left\lfloor \frac{u_j}{\delta_j} \right\rceil.
$$

For each target $j$, build a local arithmetic neighborhood

$$
N_j = \left\{ i : z_i \in \mathcal{N}(z_j^*),\; h_i \ge h_j^* - 1,\; |r_i - r_j^*| \le W_j \right\},
$$

where $\mathcal{N}(z_j^*)$ is the allowed zone neighborhood and $W_j$ is a document-window radius.

A candidate arithmetic explanation for target $j$ is a signed hyperedge

$$
e = (j, U_e, \sigma_e),
$$

where $U_e \subseteq N_j$ and $\sigma_e : U_e \to \{-1, +1\}$.

Its quantized residual is

$$
\rho_e^{(q)} = \left| q_j - \sum_{i \in U_e} \sigma_e(i) q_i^{(j)} \right|,
$$

and its exact real-valued residual is

$$
\rho_e = \left| u_j - \sum_{i \in U_e} \sigma_e(i) v_i \right|.
$$

Only edges satisfying $\rho_e^{(q)} \le \eta_j$ and $\rho_e \le \tau_j$ are retained, where $\eta_j$ is the allowable lattice slack and $\tau_j = \delta_j \eta_j$.

Each retained edge receives a score

$$
w_e = \lambda_1 \cdot \text{fit}(e)
    + \lambda_2 \cdot \text{sem}(e)
    + \lambda_3 \cdot \text{loc}(e)
    + \lambda_4 \cdot \text{hier}(e)
    - \lambda_5 \cdot |U_e|,
$$

where:

- $\text{fit}(e) = 1 - \rho_e / \max(|u_j|, \tau_j)$,
- $\text{sem}(e)$ is aggregate label agreement,
- $\text{loc}(e)$ rewards page and row locality,
- $\text{hier}(e)$ rewards structural coherence,
- $|U_e|$ penalizes unnecessarily large decompositions.

Now let $E_j$ be the retained candidate edges for target $j$, and $E = \bigcup_j E_j$.
Introduce binary decision variables

$$
x_e \in \{0,1\}, \qquad e \in E,
$$

where $x_e = 1$ means that hyperedge $e$ is selected as the explanation for its target.

Let the accounting identities be represented as

$$
\sum_{j=1}^{m} a_{rj} y_j = b_r, \qquad r = 1, \dots, R,
$$

where $y_j$ is the realized value assigned to target $j$.

Under ATLHI,

$$
y_j = y_j^{\text{anchor}} + \sum_{e \in E_j} \hat{y}_e x_e,
\qquad
\hat{y}_e = \sum_{i \in U_e} \sigma_e(i) v_i.
$$

The global optimization becomes

$$
\max_{x,\,\xi \ge 0}
\sum_{e \in E} w_e x_e - \gamma \sum_{r=1}^{R} \xi_r
$$

subject to

$$
\sum_{e \in E_j} x_e \le 1, \qquad \forall j,
$$

$$
\sum_{e : i \in U_e} x_e \le 1, \qquad \forall i,
$$

$$
-\xi_r - \tau_r \le \sum_{j=1}^{m} a_{rj} y_j - b_r \le \xi_r + \tau_r,
\qquad \forall r,
$$

where $\xi_r$ is a slack variable for identity $r$ and $\tau_r$ is the allowed accounting tolerance.

This is a weighted hypergraph set-packing problem with linear identity penalties. ATLHI solves it by dual decomposition, so the strong global constraints influence the local decisions while the algorithm remains fast enough for interactive use.

## 3. Technical Architecture & Pseudocode

### Technical Architecture

ATLHI is implemented as a four-stage inference pipeline:

1. **Anchor stage**: high-confidence semantic and direct-value matches are fixed only when they are conflict-free and identity-safe.
2. **Neighborhood stage**: each unresolved target induces a compact, structure-aware candidate neighborhood.
3. **Hyperedge generation stage**: signed meet-in-the-middle on the target lattice generates feasible arithmetic explanations.
4. **Global selection stage**: a dual-decomposed hypergraph optimizer selects a globally consistent subset of explanations and then runs exact repair only on the remaining conflict frontier.

### Production-Grade Pseudocode

```text
DATA TYPES
  SourceRow:
    id, value, zone, indent, row_pos, page, label, semantic_priors

  TargetItem:
    id, observed_value, zone, indent, row_pos, label, type

  Hyperedge:
    target_id
    signed_sources        // list of (source_id, sign)
    exact_value
    quantized_residual
    exact_residual
    score

  Identity:
    coefficients          // sparse map: target_id -> coefficient
    rhs
    tolerance


ATLHI(sources, targets, identities, params):
    anchors = BUILD_SAFE_ANCHORS(sources, targets, identities, params)

    candidate_edges = dict()   // target_id -> list[Hyperedge]

    for target in targets:
        if target.id in anchors:
            continue

        neighborhood = BUILD_NEIGHBORHOOD(target, sources, anchors, params)
        if neighborhood is empty:
            candidate_edges[target.id] = []
            continue

        lattice = BUILD_TARGET_LATTICE(target, neighborhood, params)
        edges = GENERATE_HYPEREDGES_MITM(target, neighborhood, lattice, params)
        candidate_edges[target.id] = KEEP_NONDOMINATED_TOP_B(edges, params.max_edges_per_target)

    provisional = DUAL_HYPERGRAPH_SELECTION(candidate_edges, anchors, identities, params)

    // Only the still-ambiguous frontier is searched exactly; this keeps the
    // exponential part tiny and localized instead of global.
    final_solution = EXACT_FRONTIER_REPAIR(provisional, candidate_edges, anchors, identities, params)

    return MATERIALIZE_ASSIGNMENTS(final_solution, anchors)


BUILD_SAFE_ANCHORS(sources, targets, identities, params):
    anchor_map = {}
    used_source_ids = set()

    semantic_pairs = SCORE_SEMANTIC_PAIRS(sources, targets, params)
    direct_pairs = SCORE_DIRECT_VALUE_PAIRS(sources, targets, params)

    // Merge high-confidence unary evidence, but reject anchors that would
    // immediately create identity contradictions or duplicate source usage.
    for pair in SORT_DESCENDING_BY_CONFIDENCE(semantic_pairs + direct_pairs):
        if pair.source_id in used_source_ids:
            continue
        if pair.target_id in anchor_map:
            continue
        if pair.confidence < params.anchor_threshold:
            continue
        if VIOLATES_HARD_IDENTITIES_IF_FIXED(pair, anchor_map, identities, params):
            continue

        anchor_map[pair.target_id] = pair
        used_source_ids.add(pair.source_id)

    return anchor_map


BUILD_TARGET_LATTICE(target, neighborhood, params):
    unit_scale = DETECT_UNIT_SCALE(target, neighborhood, params)
    abs_floor = params.zone_abs_floor[target.zone]
    rel_term = params.zone_rel_coeff[target.zone] * abs(target.observed_value)
    delta = max(abs_floor, rel_term, params.rounding_slack * unit_scale)
    eta = params.zone_quantized_slack[target.zone]
    q_target = ROUND(target.observed_value / delta)

    return {delta: delta, eta: eta, q_target: q_target}


BUILD_NEIGHBORHOOD(target, sources, anchors, params):
    used = {anchor.source_id for anchor in anchors.values()}
    neighborhood = []

    for source in sources:
        if source.id in used:
            continue
        if source.zone not in ALLOWED_ZONE_NEIGHBORHOOD(target.zone, params):
            continue
        if source.indent < target.indent - 1:
            continue
        if ABS(source.row_pos - target.row_pos) > params.row_window[target.zone]:
            continue
        if ABS(source.value) > params.value_ceiling_factor * ABS(target.observed_value) + params.zone_abs_floor[target.zone]:
            continue
        neighborhood.append(source)

    // Sort once so later dominance and locality scores are deterministic.
    return SORT_BY_LOCALITY_AND_SEMANTICS(neighborhood, target)


GENERATE_HYPEREDGES_MITM(target, neighborhood, lattice, params):
    left_half, right_half = BALANCED_SPLIT(neighborhood)

    left_states = ENUMERATE_SIGNED_PARTIALS(left_half, params.max_arity, lattice.delta)
    right_states = ENUMERATE_SIGNED_PARTIALS(right_half, params.max_arity, lattice.delta)

    left_index = HASH_MAP()   // key: (quantized_sum, arity) -> list[PartialState]

    for state in left_states:
        key = (state.q_sum, state.arity)
        // Dominance pruning is exact: if two states hit the same bucket and one
        // is no worse on locality, semantic prior, and exact residual bound, the
        // worse state can never win later.
        INSERT_IF_NONDOMINATED(left_index[key], state)

    edges = []

    for r_state in right_states:
        remaining_arity = params.max_arity - r_state.arity
        needed = lattice.q_target - r_state.q_sum

        // Search only the narrow tolerance band induced by target-specific
        // rounding, not the whole sum space.
        for slack in RANGE(-lattice.eta, lattice.eta):
            for left_arity in RANGE(0, remaining_arity):
                key = (needed + slack, left_arity)
                for l_state in left_index.get(key, []):
                    merged = MERGE_STATES(l_state, r_state)
                    if not IS_STRUCTURALLY_VALID(merged, target, params):
                        continue

                    exact_value = SIGNED_SUM(merged)
                    exact_residual = ABS(target.observed_value - exact_value)
                    if exact_residual > lattice.delta * lattice.eta:
                        continue

                    edge = Hyperedge(
                        target_id = target.id,
                        signed_sources = merged.signed_sources,
                        exact_value = exact_value,
                        quantized_residual = ABS(lattice.q_target - ROUND(exact_value / lattice.delta)),
                        exact_residual = exact_residual,
                        score = SCORE_EDGE(merged, target, exact_residual, params),
                    )
                    edges.append(edge)

    return REMOVE_DUPLICATE_AND_DOMINATED_EDGES(edges)


ENUMERATE_SIGNED_PARTIALS(rows, max_arity, delta):
    partials = [EMPTY_STATE]

    for row in rows:
        next_partials = partials.copy()
        for state in partials:
            if state.arity == max_arity:
                continue

            // Three-way branching: skip row, add row, subtract row.
            // The solver never enumerates the full target-space directly.
            q = ROUND(row.value / delta)
            next_partials.append(ADD_ROW_WITH_SIGN(state, row, +1, q))
            next_partials.append(ADD_ROW_WITH_SIGN(state, row, -1, q))

        partials = next_partials

    return partials


DUAL_HYPERGRAPH_SELECTION(candidate_edges, anchors, identities, params):
    mu = defaultdict(0.0)    // source-use prices
    nu = defaultdict(0.0)    // identity-violation prices

    best_solution = EMPTY_SOLUTION()
    best_objective = -INFINITY

    for iteration in RANGE(1, params.max_dual_iterations):
        chosen = {}

        // Because dual prices absorb the coupling, each target can now choose
        // its best explanation independently under the current global penalties.
        for target_id, edges in candidate_edges.items():
            best_edge = NONE
            best_reduced_score = 0.0

            for edge in edges:
                reduced = edge.score
                for (source_id, sign) in edge.signed_sources:
                    reduced -= mu[source_id]
                for identity in identities:
                    reduced -= nu[identity.id] * EDGE_IDENTITY_CONTRIBUTION(edge, identity)

                if reduced > best_reduced_score:
                    best_reduced_score = reduced
                    best_edge = edge

            if best_edge is not NONE:
                chosen[target_id] = best_edge

        source_load = COMPUTE_SOURCE_LOAD(chosen)
        identity_residuals = COMPUTE_IDENTITY_RESIDUALS(chosen, anchors, identities)

        objective = PRIMAL_OBJECTIVE(chosen, anchors, identities, params)
        if objective > best_objective:
            best_solution = chosen
            best_objective = objective

        if IS_FEASIBLE(source_load, identity_residuals, identities):
            return chosen

        step = params.dual_step_0 / SQRT(iteration)

        for source_id, load in source_load.items():
            // If a source is overused, increase its price so future iterations
            // stop selecting overlapping explanations.
            mu[source_id] = MAX(0.0, mu[source_id] + step * (load - 1.0))

        for identity in identities:
            residual = identity_residuals[identity.id]
            // Identities with positive residual become more expensive to break,
            // which pushes subsequent local choices toward globally balanced sets.
            nu[identity.id] = nu[identity.id] + step * residual

    return best_solution


EXACT_FRONTIER_REPAIR(provisional, candidate_edges, anchors, identities, params):
    frontier_targets = FIND_CONFLICT_FRONTIER(provisional, anchors, identities)
    if frontier_targets is empty:
        return provisional

    frontier_edges = {t: candidate_edges[t] for t in frontier_targets}

    // Branch-and-bound is only run on the tiny ambiguous frontier created after
    // dual compression. This is the only intentionally exponential component.
    return BRANCH_AND_BOUND_FRONTIER(frontier_edges, provisional, anchors, identities, params)
```

## 4. Rigorous Complexity Analysis

Let:

- $m_u$ be the number of unresolved targets after anchoring,
- $p_j = |N_j|$ be the neighborhood size for target $j$,
- $h_j = \lceil p_j / 2 \rceil$ be the half-size in meet-in-the-middle,
- $K$ be the maximum allowed arity,
- $E = \sum_j |E_j|$ be the number of retained hyperedges,
- $R$ be the number of accounting identities,
- $H$ be the number of dual iterations.

Define the number of signed partial states for one half as

$$
\Psi(h_j, K) = \sum_{a=0}^{K} \binom{h_j}{a} 2^a.
$$

The factor $2^a$ appears because each chosen row can enter with sign $+1$ or $-1$.

### Time Complexity Derivation

**Step 1: Safe anchoring**

- Semantic scoring is dominated by embedding plus similarity evaluation.
- If embeddings are treated as precomputed or amortized, pair scoring is $O(nm)$.
- Identity-safe anchor filtering is linear in the number of retained high-confidence pairs.

So the anchoring stage is

$$
O(nm).
$$

**Step 2: Neighborhood construction**

- For each unresolved target, all sources are filtered by zone, hierarchy, locality, and value bounds.
- This is $O(n)$ per target, hence

$$
O(m_u n).
$$

In practice, this is small because it is a simple predicate pipeline.

**Step 3: Signed meet-in-the-middle hyperedge generation**

For one target $j$:

1. Enumerating left partial states costs $O(\Psi(h_j, K))$.
2. Enumerating right partial states costs $O(\Psi(h_j, K))$.
3. Hash insertion is expected $O(1)$ per state, so building the left index is $O(\Psi(h_j, K))$.
4. Each right state queries a tolerance band of width $2\eta_j + 1$ over at most $K+1$ arity buckets.

Under the normal dispersion assumption that quantized partial sums spread over many buckets, the expected number of complementary collisions per query is constant. Therefore the expected cost per target is

$$
O(\Psi(h_j, K)).
$$

Across all unresolved targets, the expected candidate-generation time is

$$
O\left(\sum_{j=1}^{m_u} \Psi(h_j, K)\right).
$$

If we upper-bound all neighborhoods by $p$, then $h = \lceil p/2 \rceil$ and

$$
O\big(m_u \Psi(h, K)\big).
$$

**Worst case for candidate generation**

The underlying problem is NP-hard, so an exponential worst case cannot be removed. In the pathological case where many partial sums collide into the same tolerance buckets, the complement lookups can degenerate and the local stage becomes

$$
O\big(\Psi(h_j, K)^2\big)
$$

for target $j$.

However, this worst case is still materially better than direct enumeration over full signed subsets of the whole neighborhood. Full naive signed enumeration up to arity $K$ is

$$
\sum_{a=0}^{K} \binom{p_j}{a} 2^a,
$$

whereas ATLHI only enumerates partial states over halves and uses complement lookup instead of full cross-comparison.

**Step 4: Hypergraph dual selection**

Each dual iteration scans every retained hyperedge once and then updates source and identity multipliers:

$$
O(E + n + R)
$$

per iteration, so over $H$ iterations the cost is

$$
O\big(H(E + n + R)\big).
$$

**Step 5: Exact frontier repair**

Let $F$ be the number of targets in the conflict frontier after dual selection. Branch-and-bound on this frontier is exponential in $F$:

$$
O(2^F)
$$

in the worst case.

This is acceptable because ATLHI is explicitly designed so that $F \ll m_u$ in normal operation.

### Worst-Case Time Complexity

Combining the pieces, a rigorous worst-case upper bound is

$$
O\left(
nm + m_u n + \sum_{j=1}^{m_u} \Psi(h_j, K)^2 + H(E + n + R) + 2^F
\right).
$$

If all neighborhoods are bounded by the same $p$, this simplifies to

$$
O\big(nm + m_u n + m_u \Psi(\lceil p/2 \rceil, K)^2 + H(E + n + R) + 2^F\big).
$$

### Best-Case Time Complexity

The best case occurs when:

- semantic/direct anchors resolve nearly all targets,
- unresolved targets have empty or singleton neighborhoods,
- dual selection converges immediately.

Then meet-in-the-middle generation disappears and frontier repair is empty, yielding

$$
O(nm + m_u n + E + n + R).
$$

With tiny $m_u$, this is effectively near-linear after the initial similarity matrix.

### Average-Case Time Complexity

For realistic financial documents after structural filtering, $p$ is not the raw number of rows; it is the size of a local neighborhood, typically in the low teens. With $p = 16$ and $K = 4$:

$$
h = 8,
\qquad
\Psi(8, 4) = \binom{8}{0}2^0 + \binom{8}{1}2^1 + \binom{8}{2}2^2 + \binom{8}{3}2^3 + \binom{8}{4}2^4
$$

$$
= 1 + 16 + 112 + 448 + 1120 = 1697.
$$

So the expected local state work per unresolved target is on the order of a few thousand partial states, not tens of thousands of full signed subsets. If $m_u \approx 15$ unresolved targets, the local candidate phase remains comfortably interactive.

The average-case total is therefore well approximated by

$$
O\big(nm + m_u n + m_u \Psi(\lceil p/2 \rceil, K) + H(E + n + R)\big),
$$

with $F$ usually very small.

### Space Complexity Derivation

**1. Similarity matrix and anchor metadata**

- Semantic matching stores up to $O(nm)$ similarity scores if materialized densely.
- In production, this can be reduced to top-$k$ candidates per source, but the rigorous bound is $O(nm)$.

**2. Local meet-in-the-middle state storage**

- For one target, the left index stores $O(\Psi(h_j, K))$ partial states.
- Right states can be streamed, so they do not need to be stored permanently.

Thus the dominant local memory per target is

$$
O\big(\Psi(h_j, K)\big).
$$

Because targets are processed sequentially during candidate generation, this cost is not multiplied by $m_u$.

**3. Retained hyperedges**

- Storing all retained candidate explanations costs $O(EK)$ in the worst case, since each hyperedge stores up to $K$ signed sources.

**4. Dual variables and repair state**

- Source prices cost $O(n)$.
- Identity prices cost $O(R)$.
- Frontier repair bookkeeping costs $O(F)$ plus the active candidate set.

### Overall Space Complexity

The total space complexity is

$$
O\left(nm + \max_j \Psi(h_j, K) + EK + n + R + F\right).
$$

If top-$k$ semantic sparsification is used instead of a dense similarity matrix, the practical memory footprint drops to

$$
O\left(nk + \max_j \Psi(h_j, K) + EK + n + R + F\right).
$$

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

Relative to the baselines identified in the attached context, ATLHI differentiates itself in four concrete ways.

**1. Versus standard dynamic programming for subset sum**

- Standard DP is pseudo-polynomial in the target magnitude after discretization.
- Financial values are large and document-specific tolerances are heterogeneous, so naive DP either explodes in state size or requires overly coarse discretization.
- ATLHI avoids target-magnitude dependence by using target-specific tolerance lattices and local neighborhoods; complexity is driven by structural ambiguity, not by the raw monetary value scale.

**2. Versus direct graph traversal or indentation-based subtotal detection**

- Graph traversal can recover explicit parent-child structure, but it cannot discover latent arithmetic explanations when the structure is incomplete or mislabeled.
- ATLHI treats structure as a prior on the search space, not as the search result itself. It can still infer valid decompositions when the document hierarchy is only partially observable.

**3. Versus the currently proposed ARIE signed-subset enumeration**

- The current ARIE design enumerates signed $k$-subsets directly for each target and uses global consistency later.
- ATLHI first compresses explanations through meet-in-the-middle on an adaptive tolerance lattice, then optimizes globally over retained explanations using dual prices for exclusivity and identity balance.
- This replaces greedy local commitment with global explanation selection.

**4. Versus raw MILP/CSP formulations**

- A direct MILP over raw source-target assignment variables is expressive but often too large and too under-structured for interactive latency.
- ATLHI uses the hyperedge layer as a compression interface between local combinatorics and global optimization. The optimizer never reasons over all raw subsets simultaneously; it reasons over a much smaller set of scored, feasible explanations.

From a novelty standpoint, the most important distinction is that ATLHI is not merely "subset sum with heuristics." It is a two-level inference architecture in which rounding-aware candidate synthesis and accounting-identity-constrained global selection are mathematically coupled.

### Specific Claims

- A computer-implemented method that converts financial-document arithmetic discovery into target-specific tolerance-lattice search, wherein numeric values are quantized using adaptive zone- and unit-aware lattice widths before arithmetic candidate generation.
- A method for generating arithmetic explanations as signed hyperedges by meet-in-the-middle enumeration of partial sums over structure-constrained neighborhoods, followed by complement lookup within bounded lattice residual windows.
- A method for globally selecting non-overlapping arithmetic explanations using hypergraph set-packing with dual variables that penalize both source reuse and accounting-identity violations during inference rather than only after local matches are chosen.
- A method for resolving residual ambiguity by restricting exact branch-and-bound repair to a conflict frontier induced by the dual solution, thereby localizing exponential search to a dynamically identified subset of targets.

On the attached evidence, these mechanisms are substantially different from the existing semantic matcher, expression engine, formula evaluator, and even the current ARIE research draft. The strongest patentable core is the combination of adaptive tolerance lattices, signed hyperedge generation, and identity-coupled global selection.