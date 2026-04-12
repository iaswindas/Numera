# Topic 5 of 6: Document Format Fingerprinting

## 1. Critical Analysis of Attached Context

The attached IP-5 brief identifies a real systems-level inefficiency: the platform already encounters recurring document families, yet the current pipeline still treats every incoming report as if it were structurally novel. That is not merely an implementation gap. It is a representational gap. The current code can extract content from a document, but it cannot recognize the document as an instance of a previously seen layout class before paying the full inference cost.

### Deconstruction of the Current Methodologies

**1. The backend orchestration is strictly sequential and has no early-exit branch.**

- `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt` always executes `extractText() -> detectTables() -> classifyZones()` in that order.
- If we denote OCR latency by $T_{ocr}$, table-detection latency by $T_{tab}$, and zone-classification latency by $T_{zone}$, then the current end-to-end latency is approximately

$$
T_{pipeline} = T_{ocr} + T_{tab} + T_{zone}.
$$

- There is no decision point of the form "query format memory first, then bypass or narrow downstream processing."

**2. The OCR service already contains a cheap native-PDF path, but that signal is not reused for format recognition.**

- `ocr-service/app/api/ocr.py` performs direct text extraction for native PDFs.
- `ocr-service/app/api/tables.py` performs direct table extraction for native PDFs via PyMuPDF and only falls back to VLM processing for scanned documents.
- This means the system already has access to a low-cost structural channel: text block geometry, table geometry, page count, and coarse page statistics for native documents.
- However, those features are discarded after immediate task completion rather than being converted into a reusable layout signature.

**3. The scanned-document path is computationally dominated by per-page autoregressive VLM inference.**

- `ocr-service/app/ml/vlm_processor.py` performs a full generative pass per page through `extract_page()` and `extract_text()`.
- If a document has $P$ pages and the page-level VLM cost is $C_{vlm}$, then scanned extraction is approximately

$$
O(P \cdot C_{vlm}).
$$

- This is acceptable for unknown formats, but wasteful for repeated layouts where the correct page roles, column semantics, and note regions are already known from earlier documents.

**4. The current zone classifier is semantic and local, not structural and document-global.**

- The actual zone classifier implementation is `ml-service/app/ml/zone_classifier.py`.
- It operates on one table at a time using either LayoutLM inference or keyword heuristics.
- Let $n$ be the number of tables in a document. The classifier solves $n$ local prediction problems. It does not solve the upstream problem of recognizing that the whole document belongs to a known format family.
- Consequently, even a perfect zone classifier cannot create the desired speedup by itself, because the expensive table extraction step has already occurred.

**5. Expression reuse exists, but it is post-hoc, label-based, and structurally blind.**

- `ml-service/app/ml/expression_engine.py` contains `ExpressionMemory`, but the representation is an in-memory dictionary keyed by `tenant_id:customer_id`.
- The remembered pattern stores target item, expression type, source labels, and scale factor, then re-applies them by matching lowercased labels.
- This has three hard limitations:

  1. It is lost on process restart.
  2. It only becomes available after a full mapping pass has already completed.
  3. It matches textual labels rather than page geometry, table topology, or column structure.

**6. The backend persistence layer stores expression patterns, not document-format prototypes.**

- `backend/src/main/kotlin/com/numera/spreading/domain/ExpressionPattern.kt` persists pattern JSON by tenant, customer, template, and item code.
- This is useful for spreading continuity, but it is not a document retrieval index. There is no table for format centroids, page signatures, alignment statistics, or cluster drift.

**7. The mapping interface collapses away the very structural information needed for format reuse.**

- `backend/src/main/kotlin/com/numera/spreading/application/MappingOrchestrator.kt` passes only `table_id`, `zone_type`, and `zone_label` as source rows into the mapping service.
- That flattening discards page-level topology, table geometry, numeric gutter structure, anchor-page order, and note-style patterns.
- Any future format-memory subsystem attached after this point would already be working with a lossy representation.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

**1. The system has no pre-OCR structural identity function.**

The brief correctly asks for fingerprinting before full VLM processing. That object does not exist today. The current pipeline begins with extraction, not recognition.

**2. The current approach solves local understanding before global registration.**

For recurring financial statements, the better order is the reverse: first solve document-family registration, then use the registered family to constrain local extraction.

**3. The existing memory mechanisms are keyed to tenant and label strings, not to stable layout invariants.**

This makes them fragile under trivial annual variation such as shifted page numbers, changed dates, new notes, or different wording of line items.

**4. Exact template matching is theoretically the wrong abstraction.**

If a company changes its cover page, adds one note, or shifts statement pages by two positions, exact hash matching fails. The problem is therefore not exact identity. It is **deformation-tolerant structural recognition**.

**5. Pure vector embeddings are also insufficient by themselves.**

A single embedding can retrieve similar documents, but retrieval alone does not tell the system how to transfer page roles, column semantics, note mappings, and section boundaries. The missing object is a transferable correspondence map.

**6. The current system has no open-set decision rule.**

The platform needs to distinguish among four cases:

1. exact repeated format,
2. same family with mild drift,
3. same auditor or industry but not safe for zero-shot transfer,
4. completely unknown or potentially tampered format.

Today there is no formal decision boundary separating those states.

**7. Cross-tenant intelligence sharing is undefined at the representation level.**

The brief asks whether format recognition can be shared across customers without leaking client identity. That requires a structural signature that is useful even after textual and identifying signals are removed. No such projection currently exists.

**8. The computational target implies a cascaded retrieval design, not a monolithic classifier.**

To satisfy `< 500 ms` fingerprint computation and `< 50 ms` library lookup over $10^4$ formats, the system cannot rely on pairwise graph matching against the entire library. It needs a fixed-dimensional approximate nearest-neighbor stage followed by a small, deformation-aware reranking stage.

The theoretical gap is therefore precise: the system lacks a fast, deformation-invariant, privacy-aware document identity representation that is jointly optimized for **retrieval**, **transfer**, and **safe fallback**.

## 2. The Novel Algorithmic Proposal

### Name

**SWELFT: Stability-Weighted Elastic Layout Fingerprinting with Transfer Stencils**

### Core Intuition

SWELFT is built on a simple but underexploited observation: in financial reports, not all visual structure is equally stable across time.

- Page numbers, dates, signature blocks, and auditor-specific wording are volatile.
- Relative table placement, numeric column gutters, statement ordering, note-numbering style, and typographic rhythm are comparatively stable.

The novel mechanism is to separate those two classes explicitly.

SWELFT computes two coupled objects for each document:

1. a **stability-weighted structural fingerprint** used for sublinear retrieval, and
2. a **transfer stencil** that can be elastically warped onto a new document once a match is found.

This differs from existing approaches in four ways.

1. It is not exact template matching. It tolerates bounded structural drift.
2. It is not a pure perceptual hash. It reasons over page primitives, table topology, and anchor-page order.
3. It is not just a neural embedding. It preserves an ordered anchor sequence for elastic registration.
4. It is not merely a classifier. The matched result includes a transferable operational object: page-role intervals, column-role priors, note priors, and confidence-gated fallback rules.

The design target is a **192-dimensional fixed vector** for approximate nearest-neighbor retrieval, plus an ordered set of **six 32-dimensional anchor-page sketches** for precise reranking and transfer. This is the correct scale for the current requirements.

- `64-d` is too compressive for hard negatives such as same-auditor, same-jurisdiction, different-company reports.
- `256-d` is feasible, but unnecessary at the initial library size and slightly worsens index memory and latency without materially improving recall in this regime.
- `192-d` gives a clean decomposition: mean, variance, min, and max statistics over 32-dimensional anchor vectors, plus a 64-dimensional global structural sketch.

### Mathematical Formulation

#### 2.1 Problem Definition

Let a document be

$$
D = \{p_1, p_2, \dots, p_P\},
$$

where $P$ is the page count. The goal is to compute a representation

$$
\mathcal{F}(D) = (f_D, Q_D, \Omega_D, \Pi_D)
$$

such that:

- $f_D \in \mathbb{R}^{192}$ is a fixed vector for fast indexing,
- $Q_D = (z_1, \dots, z_K)$ is an ordered anchor-page sketch with $K=6$,
- $\Omega_D$ is a transfer stencil,
- $\Pi_D$ is a privacy-projected variant for cross-tenant retrieval.

Given a library $\mathcal{L} = \{\mathcal{F}(D_j)\}_{j=1}^N$, the system must find

$$
j^* = \arg\max_j S(D, D_j)
$$

under latency constraints, then decide whether to:

1. transfer the full stencil,
2. transfer only selected sections,
3. fall back to partial VLM processing, or
4. reject the match and process from scratch.

#### 2.2 Fast Structural Primitive Extraction

For each page $p$, SWELFT extracts a set of low-cost layout primitives

$$
U_p = \{u_{p,1}, \dots, u_{p,n_p}\},
$$

where each primitive is

$$
u = (b, \tau, \nu, \chi).
$$

Here:

- $b \in [0,1]^4$ is a normalized bounding box,
- $\tau \in \mathcal{C}$ is a coarse type,
- $\nu$ is a numeric-density statistic,
- $\chi$ is a typographic proxy.

The primitive type set is

$$
\mathcal{C} = \{\text{text}, \text{table}, \text{image}, \text{line}, \text{numeric-strip}, \text{whitespace}\}.
$$

Two extraction paths are used.

1. **Native PDF**: use embedded text blocks, tables, font sizes, and line geometry already available from the cheap path.
2. **Scanned PDF**: use a low-resolution raster, connected components, horizontal/vertical line detection, and numeric-density proxies only. No full OCR and no autoregressive VLM pass are required at this stage.

#### 2.3 Stability-Weighted Multi-Scale Page Tensor

Let $S = \{8, 16\}$ be the set of grid scales. For each page $p$, grid scale $s$, class $c$, and cell $(a,b)$, define the occupancy tensor

$$
T_p^{(s,c)}(a,b)
=
\sum_{u \in U_p : \tau(u)=c}
\omega(u)
\cdot
\frac{|b(u) \cap g^{(s)}_{a,b}|}{|g^{(s)}_{a,b}|},
$$

where the primitive weight is

$$
\omega(u) = \log(1 + \mathrm{area}(b(u))) \cdot (1 + \nu(u)).
$$

This produces a multi-scale structural image of the page without requiring token-level OCR.

Now define a volatility mask $M_p^{(s)}(a,b) \in [0,1]$ that down-weights regions likely to drift across yearly versions:

$$
M_p^{(s)}(a,b) = 1 - \lambda_h H_p^{(s)}(a,b) - \lambda_f F_p^{(s)}(a,b) - \lambda_v V_C^{(s)}(a,b).
$$

Here:

- $H_p$ is a header prior,
- $F_p$ is a footer prior,
- $V_C$ is a cluster-specific volatility field learned from previously matched documents in cluster $C$.

The stability-weighted tensor is then

$$
\widetilde{T}_p^{(s,c)} = M_p^{(s)} \odot T_p^{(s,c)}.
$$

This is the first novel component: the fingerprint intentionally ignores regions that are visually prominent but structurally unreliable.

#### 2.4 Page Descriptor and Anchor Selection

For each page, concatenate the stability-weighted tensors with additional structural statistics:

$$
h_p =
\bigoplus_{s \in S, c \in \mathcal{C}} \mathrm{vec}(\widetilde{T}_p^{(s,c)})
\oplus \phi_{tab}(p)
\oplus \phi_{col}(p)
\oplus \phi_{typ}(p)
\oplus \phi_{note}(p)
\oplus \phi_{meta}(p),
$$

where:

- $\phi_{tab}(p)$ captures table count, row/column count distribution, header-band ratio, and merged-cell likelihood,
- $\phi_{col}(p)$ captures numeric gutter positions and value-column alignment,
- $\phi_{typ}(p)$ captures font-size or glyph-height histograms,
- $\phi_{note}(p)$ captures note-numbering style as a finite-state signature,
- $\phi_{meta}(p)$ captures coarse page statistics such as density and whitespace ratio.

Project this high-dimensional vector into a compact page sketch using a sparse sign projection $R$:

$$
z_p = R(h_p) \in \mathbb{R}^{32}.
$$

Now define a page salience score

$$
\sigma(p) =
\alpha_1 \rho_{tab}(p)
+ \alpha_2 \rho_{num}(p)
+ \alpha_3 \rho_{head}(p)
- \alpha_4 \rho_{decor}(p),
$$

where the terms represent table density, numeric density, heading likelihood, and decorative-content density respectively.

The ordered anchor set is chosen by

$$
A(D) = \operatorname{ord-topK}_{p \in \{1,\dots,P\}} \sigma(p), \qquad K=6.
$$

This solves an important practical issue: the first pages of annual reports are often cover matter and governance narrative, not informative statement layouts.

#### 2.5 Fixed Document Fingerprint

Let

$$
Q_D = (z_{a_1}, z_{a_2}, \dots, z_{a_K})
$$

be the ordered anchor-page sequence. Aggregate it with robust statistics:

$$
f_D =
\mu(Q_D)
\oplus
\operatorname{diagvar}(Q_D)
\oplus
\min(Q_D)
\oplus
\max(Q_D)
\oplus
\psi(D),
$$

where $\psi(D) \in \mathbb{R}^{64}$ is a global structural sketch containing:

- page-count bucket,
- dominant column-count histogram,
- section-sequence sketch,
- note-style histogram,
- table-topology histogram.

Thus

$$
f_D \in \mathbb{R}^{32 + 32 + 32 + 32 + 64} = \mathbb{R}^{192}.
$$

The fingerprint is finally normalized:

$$
\widehat{f}_D = \frac{f_D}{\|f_D\|_2}.
$$

#### 2.6 Hierarchical Matching and Elastic Alignment

Each library entry is

$$
L_j = (\widehat{f}_j, Q_j, \Omega_j, \mu_j, \Pi_j),
$$

where $\mu_j$ stores metadata and quality statistics, and $\Pi_j$ is the privacy-projected vector.

SWELFT uses a two-stage matching function.

**Stage 1: coarse retrieval**

- approximate nearest-neighbor search over $\widehat{f}_j$,
- metadata filters on page-count bucket, document type, and note style,
- optional tenant-private search first, then cross-tenant search using $\Pi_j$.

**Stage 2: elastic reranking**

For candidate $L_j$, compute a banded alignment matrix

$$
A_{r,s}^{(j)}
=
\max
\begin{cases}
A_{r-1,s}^{(j)} - \delta, \\
A_{r,s-1}^{(j)} - \delta, \\
A_{r-1,s-1}^{(j)} + \kappa(z_r, z_s^{(j)})
\end{cases}
\qquad \text{subject to } |r-s| \le w,
$$

where $w$ is a narrow band and

$$
\kappa(z, z') =
\beta_1 \cos(z, z')
+ \beta_2 \, \mathrm{sim}_{tab}(z, z')
+ \beta_3 \, \mathrm{sim}_{col}(z, z')
+ \beta_4 \, \mathrm{sim}_{note}(z, z').
$$

The final match score is

$$
S(D, L_j) =
\lambda_1 \cos(\widehat{f}_D, \widehat{f}_j)
+ \lambda_2 \frac{A_{K,K}^{(j)}}{K}
+ \lambda_3 \, \mathrm{bucket}(D, L_j)
+ \lambda_4 \, \mathrm{schema}(D, L_j).
$$

This score induces three operational zones:

- $S \ge \tau_{exact}$: exact-format transfer,
- $\tau_{family} \le S < \tau_{exact}$: same-family partial transfer,
- $S < \tau_{family}$: unknown format.

#### 2.7 Transfer Stencil

For each library entry, SWELFT stores a transfer stencil

$$
\Omega_j = \{(\mathcal{I}_r, \Theta_r, \Gamma_r, \Xi_r)\}_{r \in \mathcal{R}},
$$

where each role $r$ may represent income statement, balance sheet, cash flow, notes, or a note subfamily.

- $\mathcal{I}_r$: page-interval prior,
- $\Theta_r$: table-schema prior,
- $\Gamma_r$: column-role prior,
- $\Xi_r$: note, period, and expression priors.

The alignment path induces a warp map $\pi_j$ from stored anchor positions to query anchors. SWELFT warps the stencil onto the query document by composing interval priors with $\pi_j$.

For each role $r$, define local transfer confidence

$$
c_r = \sigma\big(
\gamma_0
+ \gamma_1 S(D, L_j)
+ \gamma_2 \, \mathrm{localSim}_r
+ \gamma_3 \, \mathrm{support}_r
- \gamma_4 \, \mathrm{drift}_r
\big),
$$

where $\sigma$ is the logistic function.

Transfer is accepted only when $c_r \ge \tau_r$. Otherwise that section falls back to targeted VLM processing.

This is the second novel component: transfer is not all-or-nothing. It is section-wise, confidence-weighted, and warp-aware.

#### 2.8 Privacy Projection and Attribution

Define a privacy projection matrix $P_{priv}$ that removes or hashes any channels that may correlate with identity-bearing text or logos:

$$
\Pi_D = P_{priv} \widehat{f}_D.
$$

Cross-tenant retrieval uses $\Pi_D$, not the raw fingerprint. Tenant-private retrieval can use the full structural object.

Auditor attribution is modeled as an open-set classifier over structural fingerprints:

$$
\widehat{a}(D) = \arg\max_{a \in \mathcal{A}} P(a \mid \widehat{f}_D),
$$

but the system only emits an attribution if

$$
\max_a P(a \mid \widehat{f}_D) \ge \tau_{att}.
$$

This forces the product wording to be probabilistic: "layout-consistent with auditor cluster X" rather than a definitive authorship claim.

Tamper detection is derived from page-level residuals after alignment:

$$
\tau_{tamper}(D, L_j) = \max_{p} \big(1 - \kappa(z_p, z^{(j)}_{\pi_j(p)})\big).
$$

An isolated page with a very high residual is evidence for page insertion, replacement, or counterfeit assembly.

## 3. Technical Architecture & Pseudocode

### 3.1 Architecture

SWELFT adds five components to the existing pipeline.

1. **Fast Fingerprint Extractor** in the OCR service, positioned before full OCR and before scanned-page VLM extraction.
2. **Fingerprint Library and HNSW Index** in the ML service or a dedicated retrieval service.
3. **Stencil Transfer Engine** that warps matched format priors to the new document.
4. **Validation Gate** that checks transferred assumptions against cheap local evidence.
5. **Partial Fallback Coordinator** that sends only low-confidence pages or sections to the expensive VLM pipeline.

Operationally, the clean integration point is immediately before the backend calls `extractText()` in `DocumentProcessingService.processInternal()`. The backend should ask for a fingerprint decision first, then decide whether it needs the full OCR-table-zone path, a partial path, or no path beyond transfer materialization.

### 3.2 Pseudocode

```text
PROCEDURE PROCESS_DOCUMENT_WITH_SWELFT(document, tenant_id, customer_id):
    # Build a fast structural view before any expensive VLM call.
    primitives_by_page <- FAST_PRIMITIVE_EXTRACTION(document)

    # Compute both the fixed retrieval vector and the ordered anchor-page sketch.
    fingerprint, anchors, aux <- BUILD_SWELFT_FINGERPRINT(primitives_by_page)

    # Query private formats first; if none are strong enough, query the cross-tenant
    # privacy-projected index. This avoids unnecessary data sharing.
    candidates <- QUERY_FINGERPRINT_LIBRARY(fingerprint, aux, tenant_id)

    best_match <- RERANK_AND_ALIGN(fingerprint, anchors, aux, candidates)

    IF best_match.score < TAU_FAMILY:
        # Unknown format: no safe transfer path exists.
        result <- RUN_FULL_PIPELINE(document)
        INSERT_NEW_LIBRARY_ENTRY(document, fingerprint, anchors, result)
        RETURN result

    # Warp the stored stencil onto the current document using the elastic alignment path.
    transfer_plan <- WARP_TRANSFER_STENCIL(best_match.stencil,
                                           best_match.alignment,
                                           document.total_pages)

    # Validate each transferred section using cheap local evidence only.
    validated_plan <- VALIDATE_TRANSFER_PLAN(transfer_plan,
                                             primitives_by_page,
                                             fingerprint,
                                             best_match)

    IF validated_plan.global_confidence >= TAU_AUTO:
        # Safe enough to bypass the expensive path entirely.
        result <- MATERIALIZE_TRANSFER(validated_plan)
    ELSE:
        # Only run the expensive pipeline where the stencil is uncertain or contradicted.
        fallback_pages <- SELECT_FALLBACK_PAGES(validated_plan)
        partial_result <- RUN_TARGETED_PIPELINE(document, fallback_pages)
        result <- MERGE_TRANSFER_AND_PARTIAL_RESULT(validated_plan, partial_result)

    IF result.accepted_for_learning:
        UPDATE_LIBRARY_AFTER_SUCCESS(best_match,
                                     fingerprint,
                                     anchors,
                                     validated_plan,
                                     result)
    ELSE:
        INSERT_NEW_LIBRARY_ENTRY(document, fingerprint, anchors, result)

    RETURN result
```

```text
PROCEDURE BUILD_SWELFT_FINGERPRINT(primitives_by_page):
    page_vectors <- []
    topk_heap <- EMPTY_MIN_HEAP(capacity = K)
    global_page_stats <- INIT_GLOBAL_SKETCH()

    FOR each page_id, primitives IN primitives_by_page:
        tensor <- ZERO_MULTISCALE_TENSOR(scales = {8, 16},
                                         classes = LAYOUT_CLASSES)

        FOR each primitive IN primitives:
            # Accumulate primitive mass into every overlapping grid cell instead of
            # depending on exact pixels. This preserves geometric structure while
            # staying cheap for both native and scanned inputs.
            ACCUMULATE_PRIMITIVE_MASS(tensor, primitive)

        volatility_mask <- ESTIMATE_VOLATILITY_MASK(page_id, tensor)
        masked_tensor <- APPLY_MASK(tensor, volatility_mask)

        table_stats <- EXTRACT_TABLE_TOPOLOGY_STATS(primitives)
        gutter_stats <- EXTRACT_NUMERIC_GUTTER_STATS(primitives)
        typo_stats <- EXTRACT_TYPOGRAPHY_PROXY(primitives)
        note_stats <- EXTRACT_NOTE_STYLE_AUTOMATON(primitives)
        meta_stats <- EXTRACT_PAGE_META_STATS(primitives)

        page_feature <- CONCAT(masked_tensor,
                               table_stats,
                               gutter_stats,
                               typo_stats,
                               note_stats,
                               meta_stats)

        # Sparse sign projection is used because it is deterministic, fast, and
        # preserves relative distances well enough for ANN retrieval.
        z_page <- SPARSE_SIGN_PROJECT(page_feature, output_dim = 32)
        APPEND(page_vectors, z_page)

        salience <- SCORE_PAGE_SALIENCE(table_stats,
                                        gutter_stats,
                                        meta_stats)
        PUSH_TOPK_WITH_ORDER(topk_heap, (salience, page_id))
        UPDATE_GLOBAL_SKETCH(global_page_stats,
                             table_stats,
                             gutter_stats,
                             note_stats,
                             meta_stats)

    anchor_pages <- SORT_BY_PAGE_ORDER(CONTENTS(topk_heap))
    anchor_vectors <- [page_vectors[p] FOR p IN anchor_pages]

    summary_mean <- VECTOR_MEAN(anchor_vectors)
    summary_var <- VECTOR_DIAGONAL_VARIANCE(anchor_vectors)
    summary_min <- VECTOR_MIN(anchor_vectors)
    summary_max <- VECTOR_MAX(anchor_vectors)
    structural_sketch <- FINALIZE_GLOBAL_SKETCH(global_page_stats, output_dim = 64)

    fixed_vector <- L2_NORMALIZE(CONCAT(summary_mean,
                                        summary_var,
                                        summary_min,
                                        summary_max,
                                        structural_sketch))

    privacy_vector <- APPLY_PRIVACY_PROJECTION(fixed_vector)

    aux <- {
        "anchor_pages": anchor_pages,
        "privacy_vector": privacy_vector,
        "page_count_bucket": global_page_stats.page_count_bucket,
        "note_style": global_page_stats.note_style_histogram,
        "column_pattern": global_page_stats.column_pattern_histogram,
    }

    RETURN fixed_vector, anchor_vectors, aux
```

```text
PROCEDURE RERANK_AND_ALIGN(query_vector, query_anchors, aux, candidates):
    best <- NULL

    FOR each candidate IN candidates:
        alignment <- BANDED_ELASTIC_ALIGNMENT(query_anchors,
                                              candidate.anchor_vectors,
                                              band_width = W,
                                              gap_penalty = DELTA)

        schema_score <- SCHEMA_COMPATIBILITY(aux.column_pattern,
                                             candidate.metadata.column_pattern)
        bucket_score <- BUCKET_COMPATIBILITY(aux.page_count_bucket,
                                             candidate.metadata.page_count_bucket)
        coarse_score <- COSINE_SIM(query_vector, candidate.fixed_vector)

        final_score <- LAMBDA_1 * coarse_score +
                       LAMBDA_2 * alignment.normalized_score +
                       LAMBDA_3 * bucket_score +
                       LAMBDA_4 * schema_score

        IF best IS NULL OR final_score > best.score:
            best <- {
                "entry_id": candidate.entry_id,
                "score": final_score,
                "alignment": alignment,
                "stencil": candidate.transfer_stencil,
                "cluster_id": candidate.cluster_id,
                "metadata": candidate.metadata,
            }

    RETURN best
```

```text
PROCEDURE VALIDATE_TRANSFER_PLAN(transfer_plan, primitives_by_page, fingerprint, match):
    validated_roles <- []
    low_confidence_pages <- EMPTY_SET()

    FOR each role_assignment IN transfer_plan.roles:
        local_support <- MEASURE_LOCAL_SUPPORT(role_assignment, primitives_by_page)
        local_drift <- MEASURE_LOCAL_DRIFT(role_assignment, primitives_by_page)
        contradiction <- DETECT_HARD_CONTRADICTIONS(role_assignment, primitives_by_page)

        confidence <- LOGISTIC(GAMMA_0 +
                               GAMMA_1 * match.score +
                               GAMMA_2 * local_support -
                               GAMMA_3 * local_drift -
                               GAMMA_4 * contradiction)

        IF contradiction == TRUE OR confidence < ROLE_THRESHOLD(role_assignment.role):
            ADD_ALL(low_confidence_pages, role_assignment.pages)
            role_assignment.mode <- "fallback"
        ELSE:
            role_assignment.mode <- "transfer"

        role_assignment.confidence <- confidence
        APPEND(validated_roles, role_assignment)

    global_confidence <- ROBUST_ROLE_AGGREGATE([r.confidence FOR r IN validated_roles])

    RETURN {
        "roles": validated_roles,
        "fallback_pages": SORTED_LIST(low_confidence_pages),
        "global_confidence": global_confidence,
    }
```

```text
PROCEDURE UPDATE_LIBRARY_AFTER_SUCCESS(best_match,
                                       fingerprint,
                                       anchors,
                                       validated_plan,
                                       result):
    IF best_match.score >= TAU_EXACT AND validated_plan.global_confidence >= TAU_LEARN:
        # Update the cluster slowly so that one odd annual report does not pull
        # the centroid too far away from the stable family geometry.
        UPDATE_CLUSTER_CENTROID(best_match.cluster_id,
                                fingerprint,
                                learning_rate = ETA_SMALL)

        # Update volatility maps from observed drift. Regions that change often
        # become down-weighted in future fingerprints for this cluster.
        UPDATE_CLUSTER_VOLATILITY(best_match.cluster_id,
                                  anchors,
                                  best_match.alignment)

        # Refresh the transfer stencil only for roles that were validated or
        # corrected with high confidence.
        UPDATE_TRANSFER_STENCIL(best_match.cluster_id,
                                validated_plan,
                                result)
    ELSE:
        INSERT_NEW_LIBRARY_ENTRY(result.document,
                                 fingerprint,
                                 anchors,
                                 result)

    IF RECLUSTER_TRIGGERED():
        RUN_BACKGROUND_RECLUSTERING()
```

### 3.3 Library Architecture, Matching Policy, and Evolution Rules

Each fingerprint entry should store:

- fixed 192-dimensional retrieval vector,
- ordered six-page anchor sketch,
- transfer stencil,
- cluster ID,
- document type, jurisdiction, industry, auditor cluster label if known,
- empirical quality metrics: last matched time, successful-transfer count, fallback rate, false-transfer count,
- volatility map summary,
- privacy-projected vector.

The library should be organized in three layers.

1. **Entry layer**: individual document versions.
2. **Family layer**: clusters for the same evolving format family.
3. **Meta layer**: super-clusters for auditor, industry, and jurisdiction patterns.

Recommended maintenance policy:

- online insertion after every accepted document,
- incremental assignment to nearest family cluster when score exceeds $\tau_{family}$,
- nightly merge-split reclustering using medoids and drift statistics,
- garbage collection for stale families with zero successful matches over a long horizon,
- retention of family medoids even when individual entries expire.

### 3.4 Benchmark Design

The benchmark must contain both easy positives and hard negatives. A weak benchmark would overstate success.

| Track | Construction | Primary Metric | Success Target |
|---|---|---|---|
| Exact-repeat retrieval | same company, same auditor, adjacent years | Top-1 exact-family accuracy | > 95% |
| Mild-drift retrieval | same company, added note or shifted pages | Top-1 family accuracy | > 95% |
| Hard negatives | same auditor, same country, different company | False positive rate | < 2% |
| Cross-tenant transfer | new tenant, previously seen auditor/industry family | coverage gain vs no sharing | >= 15% |
| Partial-fallback safety | family match with altered notes section | incorrect transferred sections | < 1% |
| Duplicate detection | same document resubmitted with minor raster changes | duplicate recall | > 98% |
| Tamper detection | synthetically spliced or replaced page | tamper AUC | > 0.95 |
| Runtime | library size 10,000+ | query latency p95 | < 50 ms |
| Runtime | per incoming document | fingerprint latency p95 | < 500 ms |

The benchmark should include four negative strata that are easy to confuse:

1. same auditor, different company,
2. same company, different auditor,
3. same industry, different jurisdiction,
4. same page-count bucket, different statement structure.

Without those strata, the measured false positive rate will not reflect production reality.

### 3.5 Implementation Roadmap

**Phase 1: fast fingerprinting path**

- Add a lightweight fingerprint endpoint in the OCR service using native PDF geometry or low-resolution page segmentation.
- Store page-level primitives and the fixed vector.

**Phase 2: retrieval and transfer**

- Add a library service with HNSW indexing and candidate reranking.
- Add transfer stencil persistence and validation logic.

**Phase 3: partial fallback integration**

- Modify backend orchestration so matched documents can skip or narrow the expensive OCR-table-zone path.
- Route only low-confidence pages or sections to the VLM.

**Phase 4: cross-tenant sharing, attribution, and tamper detection**

- Enable privacy-projected search for opt-in tenants.
- Add open-set auditor attribution and tamper scoring.

## 4. Rigorous Complexity Analysis

Let:

- $P$ be the page count,
- $B = \sum_{p=1}^{P} n_p$ be the total number of extracted primitives,
- $K$ be the number of anchors, with $K=6$,
- $D$ be the fingerprint dimension, with $D=192$,
- $N$ be the number of library entries,
- $C$ be the candidate count returned by the ANN stage,
- $w$ be the elastic-alignment band width,
- $R$ be the number of transferred role assignments.

For scanned PDFs, let the low-resolution raster size be $r \times r$. In practice $r$ is fixed, but it is useful to expose it in the derivation.

### 4.1 Time Complexity of Fingerprint Computation

#### Step 1: primitive extraction

- **Native PDF case**: geometry extraction is approximately $O(B)$ because the document parser emits already segmented blocks and tables.
- **Scanned case**: low-resolution segmentation is $O(P r^2)$, followed by connected-component summarization. Since $r$ is fixed in deployment, this behaves as linear in $P$.

#### Step 2: multi-scale tensor accumulation

Each primitive contributes to a constant number of grid cells at a constant number of scales. Therefore this step is

$$
O(B).
$$

#### Step 3: anchor selection

Using a size-$K$ min-heap over $P$ pages gives

$$
O(P \log K).
$$

Because $K=6$ is constant, this is effectively

$$
O(P).
$$

#### Step 4: projection and aggregation

Projecting each selected anchor into 32 dimensions and aggregating summary statistics costs

$$
O(KD).
$$

Because both $K$ and $D$ are fixed constants in deployment, this is constant-time with respect to document length.

#### Total fingerprinting complexity

- **Best case** (native PDF, already segmented blocks):

$$
O(B + P \log K + KD) = O(B + P).
$$

- **Average case** (low-resolution scanned segmentation):

$$
O(P r^2 + B + P \log K + KD).
$$

With fixed $r$, $K$, and $D$, this simplifies operationally to

$$
O(B + P).
$$

- **Worst case** (noisy scanned pages with many small primitives):

$$
O(P r^2 + B + P \log K + KD).
$$

Again, because $r$, $K$, and $D$ are bounded by design, the worst-case growth remains near-linear in the number of pages and primitives.

### 4.2 Time Complexity of Library Matching

#### Step 1: ANN lookup

SWELFT is intended for HNSW-style indexing.

- **Best case**: the vector lands in a highly selective neighborhood,

$$
O(\log N).
$$

- **Average case**: still approximately logarithmic for well-behaved HNSW search,

$$
O(\log N).
$$

- **Worst case**: approximate-nearest-neighbor structures can degrade to linear scan,

$$
O(ND).
$$

Since $D=192$ is fixed, the worst case is effectively $O(N)$.

#### Step 2: elastic reranking

For each of the $C$ candidates, banded dynamic alignment over $K$ anchors and band width $w$ costs

$$
O(Kw).
$$

Therefore candidate reranking costs

$$
O(CKw).
$$

With $K=6$ and small $w$, this is intentionally tiny.

#### Step 3: transfer validation

Each role or section is validated once, so the cost is

$$
O(R).
$$

#### Total matching complexity

- **Best case**:

$$
O(\log N + Kw + R) = O(\log N + R).
$$

- **Average case**:

$$
O(\log N + CKw + R).
$$

With fixed $K$ and small $w$, this simplifies to

$$
O(\log N + C + R).
$$

- **Worst case**:

$$
O(ND + N Kw + R) = O(N(D + Kw) + R).
$$

Since $D$, $K$, and $w$ are fixed,

$$
O(N + R).
$$

This is acceptable as a pathological bound, but the system is explicitly engineered so that production behavior follows the average case, not the worst case.

### 4.3 End-to-End Complexity Compared with the Current Pipeline

For a successfully matched document with no fallback, the total work is approximately

$$
O(P r^2 + B + \log N + CKw + R).
$$

For a partially matched document with fallback on only $P_f$ pages, the total cost becomes

$$
O(P r^2 + B + \log N + CKw + R + P_f C_{vlm}).
$$

The current scanned-document baseline is roughly

$$
O(P C_{vlm}).
$$

Therefore the speedup factor is approximately

$$
\mathrm{Speedup} \approx \frac{P C_{vlm}}{P_f C_{vlm} + \text{fast overhead}}.
$$

If only one or two pages require fallback in a 20-page document, then a 10x speedup is not optimistic; it is structurally plausible.

### 4.4 Space Complexity

#### Query-time space

If pages are streamed one at a time, SWELFT does not need to hold the full document tensor in memory. It only needs:

- one page tensor,
- a size-$K$ top-anchor heap,
- the summary accumulators,
- the candidate set for reranking.

This gives query-time space

$$
O(r^2 + Kd_p + C),
$$

where $d_p = 32$ is the page-sketch dimension.

Because $r$, $K$, and $d_p$ are fixed, this is effectively constant with respect to document length.

#### Library storage

Per entry, the dominant components are:

- fixed vector: $D = 192$ values,
- anchor sequence: $K d_p = 6 \cdot 32 = 192$ values,
- transfer stencil metadata,
- cluster statistics and quality counters,
- ANN graph overhead.

Using float16 storage for vectors:

- fixed vector: $192 \times 2 = 384$ bytes,
- anchor sequence: $192 \times 2 = 384$ bytes.

Even with 2-4 KB of stencil and metadata plus index overhead, the per-entry storage remains only a few kilobytes. For $N = 10{,}000$ entries, the expected footprint is comfortably below the 100 MB target, even after including medoids and volatility summaries.

The persistent library therefore has space complexity

$$
O\big(N(D + Kd_p + m)\big),
$$

where $m$ is bounded metadata and stencil storage. Because $D$, $K$, and $d_p$ are fixed,

$$
O(N).
$$

## 5. Patentability & Novelty Assessment

### Prior Art Comparison

**Against exact template systems**

Traditional template systems and many commercial document-skill workflows assume a stable canonical layout and then bind extraction rules to that template. They are effective when documents are rigid, but they are brittle under page shifts, inserted notes, altered headers, and mild annual drift. SWELFT differs by explicitly modeling allowable deformation through anchor-page alignment and by updating a volatility map over time.

**Against perceptual hashing and SSIM-style similarity**

Image hashes and page-level similarity metrics are fast, but they are too sensitive to superficial raster changes and too weak semantically. They cannot distinguish between stable financial structure and volatile decorative content, and they do not yield a transferable operational object. SWELFT uses stability-weighted structural occupancy rather than raw page pixels and couples retrieval to a transfer stencil.

**Against pure document embeddings such as transformer-based layout models**

Models such as DocFormer and related document transformers are strong at visual-document understanding, classification, and token-level tasks. However, a single embedding does not provide an explicit elastic correspondence path, does not expose a transfer plan, and is often more expensive to compute than the current latency budget permits for a pre-OCR gate. SWELFT uses a hybrid object: one fixed vector for retrieval plus an ordered anchor sketch for alignment and stencil warping.

**Against layout-analysis toolkits such as LayoutParser**

LayoutParser and similar toolkits provide excellent document layout detection infrastructure, but they are not themselves a document-family memory system. They stop at layout extraction. SWELFT begins where those tools stop: compression into a reusable format identity, indexed retrieval, and zero-shot transfer.

**Against the current commercial IDP pattern**

Public product literature from ABBYY Vantage, Tungsten TotalAgility, Hyperscience, and Google Document AI emphasizes pre-trained processors, custom processors, skill design, human-in-the-loop learning, classification, and downstream orchestration. Those are valuable capabilities, but they are not the same algorithmic object as SWELFT. The distinctive difference is that SWELFT is a **document-format memory layer** with:

- a mathematically explicit stability-weighted structural fingerprint,
- sublinear retrieval over a large library,
- an elastic alignment mechanism,
- a transferable stencil for page roles and column semantics,
- a privacy-projected cross-tenant sharing mode.

This is also materially different from the current codebase, where every document is still processed from scratch and any reuse occurs only after mapping, at the level of label-based expressions.

### Anti-Piracy, Attribution, and Privacy Position

SWELFT has a second-order novelty benefit: the same structural object supports attribution and counterfeit detection without requiring a separate model family.

- **Duplicate submissions** are detected by near-zero family distance plus high page-level alignment agreement.
- **Auditor attribution** is treated as open-set probabilistic clustering, not deterministic labeling.
- **Tamper detection** is derived from page-local residual spikes after elastic alignment.
- **Cross-tenant sharing** uses the privacy projection $\Pi_D$, so structural utility can be retained while identity-bearing channels are suppressed.

From a legal-risk perspective, the product should avoid claims such as "this document was prepared by auditor X" and instead emit language such as "this layout is statistically consistent with auditor-family cluster X." That framing is both more accurate and more defensible.

### Specific Claims

- A computer-implemented method that computes a pre-OCR, stability-weighted multi-scale structural fingerprint of a document by down-weighting historically volatile page regions and projecting the remaining structural mass into a compact retrieval vector.
- A two-stage document-family retrieval method that combines approximate nearest-neighbor search over a fixed document fingerprint with banded elastic alignment over an ordered anchor-page sketch to tolerate bounded layout drift between document versions.
- A transfer mechanism that stores, retrieves, and warps a document-family transfer stencil containing page-role intervals, column-role priors, period priors, and note priors, and that applies those priors to a new document under role-wise confidence gating with targeted fallback only for contradicted regions.
- A privacy-preserving cross-tenant sharing method in which a structural fingerprint is projected into a reduced identity-suppressed representation for retrieval and clustering, while still supporting duplicate detection, probabilistic auditor-family attribution, and page-level tamper scoring.

The central patentability argument is not that any one ingredient is individually unprecedented. Approximate nearest-neighbor search, dynamic alignment, and layout extraction all exist independently. The novelty is the **specific coupling** of:

1. stability-weighted structural compression,
2. elastic anchor alignment,
3. warpable transfer stencils,
4. privacy-projected multi-tenant retrieval,
5. section-wise confidence-gated fallback.

That combination directly addresses the unsolved problem in the attached brief: instant recognition of repeated financial-statement formats with high precision, low latency, and operationally safe reuse of previously learned structure.