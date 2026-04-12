# Topic 1 of 6: Financial Document Knowledge Graph

## 1. Critical Analysis of Attached Context

The attached material identifies the correct product gap: the platform can extract page-level financial content, but it still cannot reason over the document as a persistent relational object. The implemented pipeline is fundamentally flat. It transforms pages into zones, zones into candidate row labels, and candidate row labels into spread values. At no point does it construct a typed, queryable, temporally reusable document graph. That missing representation is the root theoretical limitation.

### Deconstruction of the Current Methodologies

**1. Page extraction is rich in local perception but null in cross-page state.**

- `ocr-service/app/ml/vlm_processor.py` runs a strong page-local VLM pass for OCR, table extraction, and zone hints.
- Computationally, if a report has $P$ pages and the VLM forward cost per page is $C_{vlm}$, extraction cost is approximately $O(P \cdot C_{vlm})$.
- The structural problem is not the VLM. The issue is that each page is processed independently and the resulting tables are only enriched with `table_id` and `page_number`.
- No persistent node identity is created for headings, note references, paragraph spans, or inter-page relationships.

**2. Zone classification is unary classification, not relational inference.**

- `ml-service/app/ml/zone_classifier.py` classifies a table or text region into balance sheet, income statement, cash flow, or note subtypes.
- Its heuristic fallback is essentially keyword scoring, which is $O(L)$ in table-text length. The LayoutLM path is a single model inference pass per zone.
- This produces useful labels, but it cannot express that a balance sheet row points to a note on another page, or that two note tables belong to the same section split across a page break.

**3. The semantic matcher is pairwise, target-local, and destructively normalizes away reference identity.**

- `ml-service/app/ml/semantic_matcher.py` computes a source-target cosine matrix after embedding source rows and target labels.
- With $n$ source rows, $m$ target items, and embedding dimension $d$, the matching cost is approximately $O((n + m)d + nm)$.
- The matcher applies a fixed cross-zone penalty of `0.3`, which is a blunt prior rather than a learned structural constraint.
- More importantly, `_clean_text()` removes note identifiers via `re.sub(r"\(?[Nn]ote\s*\d+\)?", "", text)`. This means the model erases the exact symbol sequence that would be required for later cross-reference resolution.

**4. OCR post-processing removes the strongest navigation signal before graph construction even starts.**

- `ocr-service/app/utils/text_cleaning.py` includes `strip_note_references()` which explicitly removes strings such as `(Note 5)` and `Note 12`.
- This is not merely a preprocessing detail. It is an information-destroying transformation.
- Any future cross-reference engine bolted onto the current pipeline would therefore start from already-degraded evidence.

**5. The expression engine only reasons over contiguous local indentation.**

- `ml-service/app/ml/expression_engine.py` has two useful but narrow primitives: `_detect_hierarchy()` and `_try_sum_expression()`.
- `_detect_hierarchy()` is a single forward scan and is $O(n)$ in row count.
- `_try_sum_expression()` is $O(c)$ for a total row with $c$ candidate children.
- This is efficient, but the efficiency comes from the fact that it solves only the easiest case: a subtotal immediately following indented rows in the same local table.
- It cannot resolve note references, cross-page table continuations, intra-note references, or statement-to-note arithmetic reconciliation.

**6. Existing memory is label-memory, not structure-memory.**

- `ExpressionMemory` stores a tenant-customer keyed list of source labels and expression types in an in-memory dictionary.
- Lookup is effectively $O(k)$ in the number of stored patterns for that customer, which is cheap.
- The theoretical limitation is categorical: this is not graph persistence, not temporal alignment, and not anonymized transfer. It is only pattern replay by label string.
- If note numbering shifts or a section moves from page 14 to page 17, the current memory mechanism has no representation of that change.

**7. The backend mapping layer hard-flattens the extracted document.**

- `backend/src/main/kotlin/com/numera/spreading/application/MappingOrchestrator.kt` serializes source rows using only `table_id`, `zone_type`, and `zone_label` before calling the ML service.
- That transformation discards page topology, row hierarchy, heading lineage, note ordinals, and cross-page continuation evidence.
- The backend then persists flat spread values keyed by item code, not a graph of document entities and relations.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

1. **The current pipeline destroys reference tokens before it attempts any higher-order inference.**
   `strip_note_references()` and `SemanticMatcher._clean_text()` remove exactly the ordinal anchors required to resolve `Note 7`, `page 45`, and similar links.

2. **The representation is not closed under the target task.**
   A flat `(row -> target item)` mapping can represent semantic similarity, but it cannot represent `row -> note heading -> detail table -> note subrows -> prior period analog`. The problem domain requires typed multi-hop state; the implemented system does not provide it.

3. **All current scoring is pairwise and local.**
   Zone classification, semantic matching, and expression inference score local objects independently. There is no global objective over the family of all candidate cross-references.

4. **If note resolution were added naively, the search would scale poorly.**
   A simple implementation that scans all headings for every reference mention would cost $O(RH)$ where $R$ is the number of references and $H$ is the number of candidate headings or note sections. That is acceptable for one document, but wasteful when prior-period structure already provides a strong prior.

5. **The system has no explicit temporal alignment operator.**
   The attached IP brief correctly notes that annual reports are structurally stable across periods, but the current code has no graph edit operator, no alignment dynamic program, and no prototype bank. Every document is effectively re-solved from scratch.

6. **Anonymous pattern transfer is absent at the representation level.**
   Because only labels and values are persisted today, there is no privacy-preserving structural signature that can be reused across clients without leaking raw content.

7. **Property storage alone is not the innovation.**
   Simply placing extracted rows into Neo4j or PostgreSQL JSONB would not solve the problem. The real theoretical gap is the lack of an inference algorithm that converts ambiguous local evidence into globally consistent typed graph edges.

8. **Relevant academic baselines are still missing one critical dimension.**
   XBRL provides a concept taxonomy but not PDF-specific structural grounding. Layout-aware document models such as LayoutLMv3 or DocGraph-style systems capture local relations, but they do not provide persistent temporal alignment, arithmetic-backed reference validation, or privacy-preserving cross-client transfer for financial spreading workflows.

The attached context therefore exposes a precise opportunity: build a graph-native resolver that preserves ordinal anchors, aligns new documents to prior structural skeletons, and resolves all references jointly rather than one mention at a time.

## 2. The Novel Algorithmic Proposal

### Name

**Temporal Anchor-Skeleton Hypergraph Resolution (TASHR)**

### Core Intuition

TASHR changes the problem representation before it changes the solver. Instead of treating a financial document as a bag of extracted rows, it first compresses the document into a typed anchor skeleton: statement headers, note headings, page markers, appendix markers, table captions, and ordinal references. Those anchors are the stable invariants that survive across reporting periods even when page counts, font choices, or row spacing change.

The core novelty is a three-part mechanism:

1. **Anchor-skeleton alignment** compares the current document skeleton to prior-period graphs and anonymized prototype skeletons before any expensive reference search begins. This produces page-window and section-window priors.
2. **Typed candidate hyperedges** represent a cross-reference as a joint object, not a pairwise match. A candidate does not just connect a mention to a note heading. It jointly links the mention, the candidate target section, the supporting detail subgraph beneath that section, and the memory source that predicted the region.
3. **Global sparse hypergraph resolution** selects the best family of non-conflicting links under ordering, capacity, and arithmetic-reconciliation constraints. This prevents the standard failure mode where a locally plausible note match blocks a globally superior interpretation.

This is fundamentally different from common SOTA baselines. Rule-based systems detect `Note 7` and scan for the nearest `Note 7` heading. Pairwise entity-linking systems score one mention against one candidate. Standard graph traversal assumes the correct graph already exists. TASHR instead constructs the missing graph edges by solving a constrained hypergraph selection problem whose weights are informed by temporal memory and note-level arithmetic support.

### Mathematical Formulation

Let the extracted document produce a typed property graph

$$
G = (V, E, \tau_V, \tau_E, X),
$$

where:

- $V$ is the node set,
- $E$ is the edge set,
- $\tau_V : V \to \mathcal{T}_V$ maps nodes to node types,
- $\tau_E : E \to \mathcal{T}_E$ maps edges to edge types,
- $X$ is a feature map.

Use the node type set

$$
\mathcal{T}_V = \{
\text{Document}, \text{Page}, \text{Section}, \text{Heading}, \text{Table}, \text{Row}, \text{Cell},
\text{ReferenceMention}, \text{ValueAtom}, \text{Prototype}
\}.
$$

Use the edge type set

$$
\mathcal{T}_E = \{
\text{CONTAINS}, \text{PRECEDES}, \text{HEADS}, \text{MENTIONS}, \text{DERIVES\_FROM},
\text{RESOLVES\_TO}, \text{RECONCILES\_WITH}, \text{ALIGNS\_WITH}, \text{INSTANCE\_OF}
\}.
$$

Each node $v \in V$ has feature vector

$$
x(v) = \big(\tau_V(v), p(v), b(v), z(v), \ell(v), \omega(v), \nu(v)\big),
$$

where:

- $p(v)$ is page index,
- $b(v)$ is bounding box,
- $z(v)$ is statement zone,
- $\ell(v)$ is normalized text,
- $\omega(v)$ is normalized ordinal token (for example `7`, `VII`, `seven` all map to the same canonical ordinal),
- $\nu(v)$ is a multiset of numeric values attached to the node or its row.

Define the **anchor skeleton** as the ordered subsequence of stable anchors

$$
A(G) = \langle a_1, a_2, \dots, a_S \rangle,
$$

where each $a_i$ is a node of type `Section`, `Heading`, or `ReferenceMention` with a stable ordinal or semantic title.

For a memory bank $\mathcal{B} = \{A^{(1)}, \dots, A^{(K)}\}$ containing prior-period or anonymized prototype skeletons, TASHR computes a banded alignment score

$$
F_k(i,j) = \max \begin{cases}
F_k(i-1,j) - g, \\
F_k(i,j-1) - g, \\
F_k(i-1,j-1) + \psi(a_i, a^{(k)}_j)
\end{cases}
$$

subject to $|i-j| \le w$, where $g$ is a gap penalty, $w$ is the alignment band, and

$$
\psi(a_i, a^{(k)}_j) =
\beta_1 \cdot \mathbf{1}[\tau(a_i)=\tau(a^{(k)}_j)]
+ \beta_2 \cdot \mathbf{1}[\omega(a_i)=\omega(a^{(k)}_j)]
+ \beta_3 \cdot \text{sim}(\ell(a_i), \ell(a^{(k)}_j))
- \beta_4 \cdot |p(a_i)-p(a^{(k)}_j)|.
$$

The best alignments induce a memory prior over candidate target regions:

$$
\pi_{mem}(t \mid r) \propto \exp\big(\rho \cdot \text{AlignScore}(r,t)\big).
$$

Now let $R$ be the set of detected reference mentions. For each $r \in R$, define a candidate set $C(r)$ of target headings or note sections that are type-compatible, ordinal-compatible, and within the memory-prior window.

A candidate cross-reference is represented as a hyperedge

$$
h = (r, t, Q_h, m_h),
$$

where:

- $r$ is the mention node,
- $t$ is the candidate target heading or note section,
- $Q_h \subseteq V$ is the supporting detail subgraph under $t$,
- $m_h$ is the prior graph or prototype that induced the region prior.

For each candidate target $t$, build an **aggregate sketch**

$$
\Sigma(t) = \{\alpha_1, \alpha_2, \dots, \alpha_q\},
$$

where each $\alpha_j$ is a bottom-up subtotal or subtree total extracted from descendants of $t$. This is a compact arithmetic signature of the note section.

If $u(r)$ is the parent row or statement line containing mention $r$, and $v(u(r))$ is its numeric value, define arithmetic support as

$$
\phi_{arith}(r,t) = \max_{\alpha \in \Sigma(t)} \exp\left(-\frac{|v(u(r)) - \alpha|}{\tau_r}\right),
$$

where $\tau_r$ is a mention-specific tolerance derived from unit scale and rounding regime.

Define the hyperedge weight

$$
w_h =
\lambda_1 \phi_{ord}(r,t)
+ \lambda_2 \phi_{lex}(r,t)
+ \lambda_3 \phi_{layout}(r,t)
+ \lambda_4 \phi_{arith}(r,t)
+ \lambda_5 \phi_{mem}(r,t,m_h)
- \lambda_6 \phi_{complex}(Q_h),
$$

where:

- $\phi_{ord}$ measures canonical ordinal agreement,
- $\phi_{lex}$ measures semantic agreement between mention context and target heading,
- $\phi_{layout}$ rewards plausible reading-order and page-distance behavior,
- $\phi_{arith}$ measures note-to-statement numeric reconciliation,
- $\phi_{mem}$ injects prior-period and prototype priors,
- $\phi_{complex}$ penalizes overly diffuse support subgraphs.

Let $H = \bigcup_{r \in R} H(r)$ be the full candidate hyperedge set, where $H(r)$ is the candidate family for mention $r$. Introduce binary decision variables

$$
x_h \in \{0,1\}, \qquad h \in H.
$$

Let $\mathcal{C}$ be the conflict set containing pairs of hyperedges that cannot both be selected because they violate mention exclusivity, impossible page ordering, incompatible ordinal reuse, or contradictory section assignments.

TASHR solves

$$
\max_{x} \sum_{h \in H} w_h x_h
$$

subject to

$$
\sum_{h \in H(r)} x_h \le 1, \qquad \forall r \in R,
$$

$$
x_h + x_{h'} \le 1, \qquad \forall (h,h') \in \mathcal{C}.
$$

This is a weighted sparse hypergraph packing problem. The advantage is critical: TASHR resolves references jointly, not independently.

Finally, to support anonymous transfer, define a privacy-preserving structural signature

$$
\sigma(G) = \text{SimHash}_{salt}\Big(f\big(A(G), \text{gapHist}(A(G)), \text{shapeHist}(G), \text{edgeTypeHist}(G)\big)\Big).
$$

Prototype centroids are updated as

$$
P_c \leftarrow (1 - \eta) P_c + \eta \sigma(G),
$$

where $P_c$ is a cluster centroid and $\eta$ is the update rate. Because $\sigma(G)$ stores only hashed structural descriptors, not raw values or note text, structural learning transfers without directly exposing proprietary content.

**Why a property graph, not RDF/OWL:** the workload is dominated by mutable node properties, sparse local traversals, score-based candidate generation, and iterative optimization. That is a closed-world, transactional, weighted-inference workload. It is materially better matched to a typed property graph than to open-world symbolic reasoning.

## 3. Technical Architecture & Pseudocode

### Technical Architecture

TASHR should be implemented as a five-stage pipeline:

1. **Typed graph materialization**
   Convert VLM output into a typed document graph while preserving note identifiers, page identifiers, table continuations, and heading lineage.

2. **Anchor-skeleton extraction and memory alignment**
   Build the stable anchor sequence, then align it to prior-period graphs and anonymized prototype skeletons using banded dynamic programming.

3. **Sparse hyperedge generation**
   Detect explicit and implicit references, retrieve candidate target sections using indexed ordinal and page-window filters, and attach aggregate sketches for arithmetic support.

4. **Global hypergraph resolution**
   Solve a sparse weighted selection problem using dual decomposition with final exact repair on the small residual conflict frontier.

5. **Temporal persistence and prototype update**
   Persist the resolved graph instance, compute a structural delta versus the prior graph, and update anonymous structural prototypes using only hashed signatures.

### Production-Grade Pseudocode

```text
DATA TYPES
  Node:
    id
    type
    page
    bbox
    zone
    text
    ordinal_token
    numeric_value
    parent_id
    child_ids

  Hyperedge:
    id
    ref_node_id
    target_node_id
    support_node_ids
    memory_source_id
    weight
    reduced_weight

  Prototype:
    id
    centroid_signature
    page_window_priors
    ordinal_transition_priors


PROCEDURE TASHR(document_atoms, prior_graphs, prototype_bank, params):
  G = BUILD_TYPED_GRAPH(document_atoms, params)
  aggregates = BUILD_AGGREGATE_SKETCHES(G)
  skeleton = EXTRACT_ANCHOR_SKELETON(G)

  // Use historical structure before scoring references.
  // This is the search-space reduction step that a flat pipeline lacks.
  priors = BUILD_MEMORY_PRIORS(skeleton, prior_graphs, prototype_bank, params)

  refs = DETECT_REFERENCE_MENTIONS(G, params)
  hyperedges = GENERATE_CANDIDATE_HYPEREDGES(G, refs, aggregates, priors, params)

  selected = RESOLVE_HYPEREDGES(hyperedges, params)
  MATERIALIZE_RESOLUTIONS(G, selected)

  delta = COMPUTE_STRUCTURAL_DELTA(G, prior_graphs, params)
  UPDATE_PROTOTYPE_BANK(G, prototype_bank, params)

  return G, delta


PROCEDURE BUILD_TYPED_GRAPH(document_atoms, params):
  G = empty property graph

  for each page in document_atoms.pages:
    page_node = ADD_NODE(G, type="Page", page=page.number, bbox=page.bbox)

    // Preserve reading order instead of flattening to a table label.
    sorted_blocks = SORT_BY_READING_ORDER(page.blocks)

    previous_block = NULL
    for each block in sorted_blocks:
      node_type = INFER_BLOCK_TYPE(block)
      ordinal = CANONICALIZE_ORDINAL(block.text)  // Handles 7, VII, seven, n.7.
      block_node = ADD_NODE(
        G,
        type=node_type,
        page=page.number,
        bbox=block.bbox,
        zone=block.zone,
        text=block.text,
        ordinal_token=ordinal,
        numeric_value=PARSE_PRIMARY_VALUE(block.text)
      )

      ADD_EDGE(G, page_node, block_node, type="CONTAINS")

      if previous_block is not NULL:
        ADD_EDGE(G, previous_block, block_node, type="PRECEDES")

      parent = FIND_STRUCTURAL_PARENT(G, block_node)
      if parent is not NULL:
        ADD_EDGE(G, parent, block_node, type="CONTAINS")

      previous_block = block_node

  return G


PROCEDURE BUILD_AGGREGATE_SKETCHES(G):
  aggregates = map from target_node_id to set of subtotal values

  // Bottom-up aggregation is linear because each row subtree is visited once.
  for each table_or_section node t in POSTORDER_SECTIONS_AND_TABLES(G):
    subtotal_set = empty set

    for each child subtree rooted under t:
      subtotal = SUM_EXPLICIT_AND_IMPLICIT_CHILD_VALUES(child subtree)
      if subtotal is not NULL:
        subtotal_set.add(subtotal)

    // Keep only the top informative subtotals to control memory.
    aggregates[t.id] = TOP_K_BY_MAGNITUDE_AND_DIVERSITY(subtotal_set, K=params.max_aggregate_sketch)

  return aggregates


PROCEDURE EXTRACT_ANCHOR_SKELETON(G):
  skeleton = empty list

  for each node v in READING_ORDER(G):
    if v.type in {"Section", "Heading", "ReferenceMention", "Page"}:
      if IS_STABLE_ANCHOR(v):
        skeleton.append(v)

  return skeleton


PROCEDURE BUILD_MEMORY_PRIORS(skeleton, prior_graphs, prototype_bank, params):
  priors = empty structure

  memory_skeletons = []
  for each prior_graph in prior_graphs:
    memory_skeletons.append(EXTRACT_ANCHOR_SKELETON(prior_graph))
  for each prototype in prototype_bank:
    memory_skeletons.append(prototype)

  for each memory_skeleton in memory_skeletons:
    alignment = BANDED_ALIGN(skeleton, memory_skeleton, params.band_width)

    // Convert aligned anchors into page-window priors rather than exact page locks.
    // This makes the method robust to inserted disclosures and shifted pagination.
    for each aligned_pair in alignment:
      UPDATE_PRIOR_WINDOWS(priors, aligned_pair, params)

  NORMALIZE_PRIORS(priors)
  return priors


PROCEDURE DETECT_REFERENCE_MENTIONS(G, params):
  refs = empty list

  for each node v in NODES_OF_TYPE(G, {"Row", "Heading", "Cell"}):
    patterns = MATCH_REFERENCE_PATTERNS(v.text)

    // Patterns include explicit notes, page references, appendix links,
    // prior-period references, and intra-note references.
    for each pattern in patterns:
      ref_node = ADD_NODE(
        G,
        type="ReferenceMention",
        page=v.page,
        bbox=v.bbox,
        zone=v.zone,
        text=pattern.surface_form,
        ordinal_token=CANONICALIZE_ORDINAL(pattern.ordinal),
        numeric_value=NULL
      )
      ADD_EDGE(G, v, ref_node, type="MENTIONS")
      refs.append(ref_node)

  return refs


PROCEDURE GENERATE_CANDIDATE_HYPEREDGES(G, refs, aggregates, priors, params):
  hyperedges = empty list
  heading_index = BUILD_TARGET_INDEX(G)  // Indexed by type, ordinal, zone, and page.

  for each ref in refs:
    candidate_targets = LOOKUP_CANDIDATES(heading_index, ref, priors, params)

    for each target in candidate_targets:
      support_nodes = DESCENDANT_SUPPORT_SUBGRAPH(G, target, params)
      arithmetic_score = SCORE_ARITHMETIC(ref, target, support_nodes, aggregates, params)
      lexical_score = SCORE_LEXICAL(ref, target)
      ordinal_score = SCORE_ORDINAL(ref, target)
      layout_score = SCORE_LAYOUT(ref, target, priors)
      memory_score = SCORE_MEMORY(ref, target, priors)

      weight = (
        params.lambda_ord * ordinal_score +
        params.lambda_lex * lexical_score +
        params.lambda_layout * layout_score +
        params.lambda_arith * arithmetic_score +
        params.lambda_mem * memory_score -
        params.lambda_complex * COMPLEXITY_PENALTY(support_nodes)
      )

      if weight >= params.hyperedge_threshold:
        hyperedges.append(
          Hyperedge(
            id=NEW_ID(),
            ref_node_id=ref.id,
            target_node_id=target.id,
            support_node_ids=IDS(support_nodes),
            memory_source_id=BEST_MEMORY_SOURCE(ref, target, priors),
            weight=weight,
            reduced_weight=weight
          )
        )

  return hyperedges


PROCEDURE RESOLVE_HYPEREDGES(hyperedges, params):
  conflict_graph = BUILD_CONFLICT_GRAPH(hyperedges)
  lambda_ref = default 0
  lambda_conflict = default 0
  active = empty set

  for iter from 1 to params.max_dual_iters:
    active = empty set

    for each hyperedge h in hyperedges:
      h.reduced_weight = h.weight
      h.reduced_weight -= lambda_ref[h.ref_node_id]

      // Only local conflicts are charged, which keeps each iteration sparse.
      for each neighbor_id in conflict_graph[h.id]:
        h.reduced_weight -= lambda_conflict[(h.id, neighbor_id)]

    grouped = GROUP_BY_REFERENCE(hyperedges)

    for each ref_id, group in grouped:
      best = ARGMAX_POSITIVE(group, key=reduced_weight)
      if best is not NULL:
        active.add(best.id)

    ref_violations = COUNT_REFERENCE_VIOLATIONS(active, hyperedges)
    conflict_violations = COUNT_CONFLICT_VIOLATIONS(active, conflict_graph)

    if ref_violations == 0 and conflict_violations == 0:
      break

    // Standard subgradient ascent on the dual variables.
    // Violated constraints become more expensive in the next iteration.
    step = params.initial_step / SQRT(iter)

    for each ref_id, amount in ref_violations:
      lambda_ref[ref_id] += step * amount

    for each pair_id, amount in conflict_violations:
      lambda_conflict[pair_id] += step * amount

  // Exact repair on the residual frontier removes any remaining ties or soft conflicts.
  repaired = EXACT_REPAIR_ON_FRONTIER(active, hyperedges, conflict_graph, params)
  return repaired


PROCEDURE MATERIALIZE_RESOLUTIONS(G, selected_hyperedge_ids):
  for each hyperedge h where h.id in selected_hyperedge_ids:
    ref = GET_NODE(G, h.ref_node_id)
    target = GET_NODE(G, h.target_node_id)

    ADD_EDGE(G, ref, target, type="RESOLVES_TO")

    for each support_id in h.support_node_ids:
      support = GET_NODE(G, support_id)
      ADD_EDGE(G, ref, support, type="RECONCILES_WITH")


PROCEDURE COMPUTE_STRUCTURAL_DELTA(G, prior_graphs, params):
  if prior_graphs is empty:
    return NULL

  prior = MOST_RECENT(prior_graphs)
  current_skeleton = EXTRACT_ANCHOR_SKELETON(G)
  prior_skeleton = EXTRACT_ANCHOR_SKELETON(prior)

  alignment = BANDED_ALIGN(current_skeleton, prior_skeleton, params.band_width)
  return SUMMARIZE_INSERT_DELETE_MOVE_OPERATIONS(alignment)


PROCEDURE UPDATE_PROTOTYPE_BANK(G, prototype_bank, params):
  signature = HASHED_STRUCTURAL_SIGNATURE(G, params.prototype_salt)
  cluster = FIND_NEAREST_PROTOTYPE(signature, prototype_bank, params)

  if cluster is NULL:
    CREATE_NEW_PROTOTYPE(signature, prototype_bank)
  else:
    // Update only the structural centroid, never raw labels or values.
    cluster.centroid_signature = MOVING_AVERAGE(cluster.centroid_signature, signature, params.prototype_lr)
```

## 4. Rigorous Complexity Analysis

Let:

- $N = |V|$ be the number of atomic graph nodes created from a document,
- $E = |E|$ be the number of graph edges,
- $S = |A(G)|$ be the anchor-skeleton length,
- $K$ be the number of prior or prototype skeletons consulted,
- $w$ be the alignment band width,
- $R$ be the number of detected reference mentions,
- $C$ be the average number of pruned candidate targets per reference,
- $B = R \cdot C$ be the total hyperedge count after pruning,
- $\Delta$ be the average conflict degree per hyperedge,
- $I$ be the number of dual iterations.

### Time Complexity

**Step 1: Typed graph materialization**

- If page blocks arrive already in reading order, node and edge creation is linear: $O(N + E)$.
- If blocks must be sorted geometrically per page, the dominant term is sorting, so the bound becomes $O(N \log N)$.

Therefore:

- Best case: $O(N + E)$
- Average case: $O(N \log N)$
- Worst case: $O(N \log N)$

**Step 2: Aggregate-sketch construction**

- Each row subtree is visited once in postorder.
- Each subtotal candidate is inserted into a bounded sketch structure.
- Because the sketch size is capped by `max_aggregate_sketch`, insertion is $O(1)$ amortized.

Therefore this stage is:

$$
O(N + E).
$$

**Step 3: Anchor-skeleton alignment**

- Full dynamic programming between two skeletons of length $S$ is $O(S^2)$.
- TASHR uses a banded alignment with width $w$, which reduces a single alignment to $O(Sw)$ when the true drift is local.
- Aligning against $K$ memory skeletons yields:

$$
O(KSw) \quad \text{average}, \qquad O(KS^2) \quad \text{worst}.
$$

- If a strong hash or exact ordinal map immediately identifies the matching prior section, the band collapses and the practical best case is $O(KS)$.

**Step 4: Reference detection**

- Regex and ordinal-pattern scans are linear in the total text length attached to candidate nodes.
- Under bounded average token length per node, this is $O(N)$.

**Step 5: Candidate hyperedge generation**

- Indexed lookup by ordinal, type, and page window is $O(\log S)$ or $O(1)$ average depending on the index implementation.
- For $R$ references, candidate retrieval is therefore $O(R \log S + B)$.
- Arithmetic scoring is $O(1)$ per candidate because aggregate sketches are precomputed.
- In the naive unindexed regime, each reference might scan all $S$ anchors, yielding $O(RS)$.

Thus:

- Best case: $O(R + B)$
- Average case: $O(R \log S + B)$
- Worst case: $O(RS)$

**Step 6: Dual hypergraph resolution**

- In one dual iteration, each hyperedge is rescored once and only its local conflict neighborhood is visited.
- That gives $O(B(1 + \Delta))$ work per iteration.
- Over $I$ iterations, the average complexity is:

$$
O\big(I B (1 + \Delta)\big).
$$

- In the worst case, the conflict graph becomes dense, so $\Delta = O(B)$ and the bound becomes:

$$
O(I B^2).
$$

- In the best case, each reference has one surviving candidate and conflicts are empty, so the stage collapses to $O(B)$.

### Overall Time Complexity

Combining the stages gives:

**Best case**

$$
O\big((N + E) + KS + R + B\big).
$$

**Average case**

$$
O\big(N \log N + KSw + R \log S + B + I B (1 + \Delta)\big).
$$

**Worst case**

$$
O\big(N \log N + KS^2 + RS + I B^2\big).
$$

This is the correct asymptotic story. The global problem is still combinatorial in the worst case, but TASHR removes the avoidable waste. It does so by compressing the search space with anchor skeletons, by restricting candidate generation with memory priors, and by using precomputed aggregate sketches so arithmetic support does not require an inner subset search.

### Space Complexity

**1. Graph storage**

- Nodes plus typed edges require $O(N + E)$ space.

**2. Aggregate sketches**

- Each section or table stores a bounded sketch of subtotal candidates.
- With a constant sketch cap, this is $O(N)$.

**3. Alignment tables**

- Full DP storage is $O(S^2)$.
- Banded alignment reduces this to $O(Sw)$ average.

**4. Hyperedges and conflict graph**

- Hyperedges require $O(B)$.
- The sparse conflict graph requires $O(B\Delta)$ average and $O(B^2)$ worst case.

### Overall Space Complexity

**Best case**

$$
O(N + E + B).
$$

**Average case**

$$
O(N + E + Sw + B + B\Delta).
$$

**Worst case**

$$
O(N + E + S^2 + B^2).
$$

For the graph sizes described in the attached IP brief, roughly 500-2000 nodes and 1000-5000 edges per document, these bounds are operationally comfortable as long as $C$, $w$, and $\Delta$ are capped. Under those caps, the average-case runtime is close to linear in document size, and post-build traversals from a statement line to its resolved note detail are effectively indexed local graph traversals.

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

**Versus regex or nearest-heading reference resolution**

- Standard rule systems detect `Note 7` and search for the nearest matching heading.
- They fail when note numbering repeats across appendices, when Roman and Arabic ordinals mix, or when the relevant detail is split across pages.
- TASHR is different because it resolves a reference using a joint hyperedge that includes support-subgraph evidence and temporal priors, not just text-pattern proximity.

**Versus pairwise entity linking or SBERT-style similarity**

- Pairwise linkers optimize one mention-candidate score at a time.
- They do not solve the family of candidate references jointly, so local false positives remain common when several headings are semantically similar.
- TASHR uses a global sparse packing objective with explicit conflict handling, which is a materially different inference procedure.

**Versus standard graph traversal on a stored document graph**

- Graph traversal assumes that the reference edges already exist.
- The hard problem in this domain is constructing those edges from ambiguous evidence.
- TASHR is not a traversal method. It is a graph-construction method that infers the missing reference edges before traversal.

**Versus graph edit distance or vanilla temporal matching**

- Standard graph edit distance can compare two graphs after they are built, but it does not naturally generate page-window priors for unresolved references in a new graph.
- TASHR uses anchor-skeleton alignment as a predictive prior generator, not merely as an after-the-fact similarity metric.

**Versus XBRL and financial taxonomy methods**

- XBRL encodes concept relationships and some footnote semantics, but it presumes machine-readable filings.
- It does not solve PDF-native reference grounding where the relevant note exists as a visual-layout section with page drift, auditor-specific formatting, and OCR noise.
- TASHR exploits XBRL-like concept structure when available, but its novelty lies in resolving references directly in semi-structured documents.

**Versus publicly described competitor workflows**

- Public materials for systems such as Moody's CreditLens, S&P Capital IQ workflows, nCino spreading, Finagraph, and Vena emphasize extraction, spreading automation, and analyst workflow.
- Publicly available descriptions do not disclose a method that combines anchor-skeleton temporal alignment, support-subgraph arithmetic reconciliation, and privacy-preserving prototype transfer inside a global hypergraph resolver.
- The patent strategy should therefore claim the method-level mechanics, not generic graph storage or generic OCR automation.

### Specific Claims

- A method for constructing an anchor skeleton from a financial document graph and aligning that skeleton to prior-period and anonymized prototype skeletons to generate probabilistic page-window and section-window priors before resolving cross-references.
- A method for generating typed candidate hyperedges in which each candidate jointly binds a reference mention, a candidate target section, a supporting descendant detail subgraph, and a memory source, with a score derived from ordinal agreement, lexical compatibility, layout consistency, arithmetic reconciliation, and temporal priors.
- A method for resolving document cross-references by sparse hypergraph packing under mention-exclusivity and structural-conflict constraints, rather than by independent pairwise matching or nearest-heading heuristics.
- A privacy-preserving structural-transfer method in which hashed anchor-gap, layout-shape, and edge-type signatures are clustered into reusable prototypes that improve zero-shot document understanding without storing raw client labels or values.

### Assessment

The strongest patentable novelty is not the idea of storing documents as graphs. That by itself is too broad and too close to existing graph-database practice. The strongest novelty is the precise inference pipeline: anchor-skeleton alignment as a predictive prior, candidate resolution through support-bearing hyperedges, arithmetic reconciliation embedded in the reference score, and anonymized prototype transfer through structural signatures. That combination is both technically non-trivial and commercially aligned with the attached IP brief.