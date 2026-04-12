# IP-5: Document Format Fingerprinting (DFF)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **document format recognition system** that creates a compact "fingerprint" of each financial document's visual and structural layout, enabling instant recognition of previously-seen formats. When a new document arrives that matches a known fingerprint, the system bypasses expensive ML processing and directly applies the known zone classifications, column assignments, and period detection — achieving **near-zero processing time and near-perfect accuracy**.

**The insight**: Banks receive annual reports from the same companies every year. Auditing firms use consistent templates. A KPMG audit of a UAE bank looks structurally identical to last year's KPMG audit of the same bank. Instead of treating every document as unknown, the system should recognize "I've seen this format before" and apply learned knowledge instantly.

---

## Context: What Exists Today

### Current Codebase
- **VLM Processor** (`ocr-service/app/ml/vlm_processor.py`): Processes every document from scratch using Qwen3-VL-8B. ~30-60 seconds per document on CPU, ~5-10 seconds on GPU.
- **Zone Classifier** (`ocr-service/app/services/zone_classifier.py`): Classifies detected regions into IS/BS/CF/Notes. Uses VLM output, no format memory.
- **Expression Memory** (`ml-service/app/ml/expression_engine.py`): `ExpressionMemory` class caches expression patterns per client, but:
  - In-memory only (lost on restart)
  - Matches by exact label text (fragile)
  - No visual/structural matching

### The Gap
1. Every document processed from scratch — no format memory
2. No visual layout analysis — only text-based matching
3. No cross-client format sharing — "KPMG UAE format" not recognized across clients
4. No speed optimization — even known formats take full processing time

---

## Research Directives

### R1: Fingerprint Computation Algorithm

Design the algorithm that computes a compact, comparison-friendly fingerprint from a financial document.

**Fingerprint components to consider:**

| Component | What It Captures | Representation |
|---|---|---|
| **Page Layout Hash** | Position and size of text blocks, tables, images per page | Grid-quantized bounding box hash |
| **Table Structure** | Number of tables, rows/columns per table, header patterns | Structural descriptor vector |
| **Section Sequence** | Order of financial statement sections | Ordered list: [IS, BS, CF, NOTES...] |
| **Typography Fingerprint** | Font sizes, bold/italic patterns (headers vs data) | Font usage distribution |
| **Column Count Pattern** | How many data columns per table (2=single period, 4=dual period+comparison) | Integer sequence |
| **Note Numbering Pattern** | How notes are numbered and referenced | Pattern string |
| **Page Count Bucket** | Approximate document length | Bucket: <20, 20-40, 40-80, 80+ |

**Questions to answer:**
- What is the optimal dimensionality for the fingerprint vector? (64-d? 128-d? 256-d? Tradeoff: too small → false positives, too large → false negatives)
- Should the fingerprint be a fixed vector (for cosine similarity) or a variable-length structural descriptor (for graph matching)?
- How do we handle minor layout changes between years? (Same company, page numbers shifted by 2, one new note added. Fingerprint should still match.)
- What is the right level of abstraction? (Too specific → only matches exact same version. Too generic → matches unrelated documents.)
- Can we use a learned embedding? (Train a small CNN/ViT to produce a layout embedding from page images?)
- How do we compute the fingerprint without full OCR? (Fingerprinting should be FAST — ideally from the first few pages only, before full VLM processing)

**Research areas:**
- Document layout hashing (LayoutLM hash, VisualHasher)
- Perceptual hashing for images (pHash, dHash) — adapted for document pages
- Structural similarity index (SSIM) for document comparison
- Document clustering by visual layout (CDFF: Composite Document Format Fingerprint)
- SimHash / MinHash for approximate matching at scale

### R2: Fingerprint Matching & Library Management

Design the system that stores, indexes, and queries fingerprints.

**Matching requirements:**
- **Speed**: Match an incoming fingerprint against a library of 10,000+ formats in <50ms
- **Accuracy**: True positive rate >95%, false positive rate <2%
- **Fuzzy matching**: Tolerate minor layout variations (page shifts, font changes)
- **Hierarchical matching**: Match at multiple levels (exact format, same auditor, same industry, same jurisdiction)

**Library structure:**
```
Fingerprint Library
├── Company-Specific Formats
│   ├── CompanyX_AnnualReport_2024
│   ├── CompanyX_AnnualReport_2025  → cluster: CompanyX_Annual
│   └── CompanyY_InterimReport_2024
├── Auditor Templates
│   ├── KPMG_UAE_Banking
│   ├── Deloitte_UK_Insurance
│   └── EY_US_Technology
├── Industry Patterns
│   ├── Banking_IFRS_Standard
│   └── Manufacturing_USGAAP_Standard
└── Generic Patterns
    ├── 3Statement_DualPeriod
    └── InterimReport_SinglePeriod
```

**Questions to answer:**
- What indexing structure enables sub-50ms matching? (LSH — Locality Sensitive Hashing? FAISS? Annoy? ScaNN?)
- How do we cluster fingerprints into hierarchical groups automatically? (Agglomerative clustering? DBSCAN?)
- How often should we re-cluster? (After every N new fingerprints? Nightly batch?)
- What metadata should be stored with each fingerprint? (Auditor, jurisdiction, document type, accuracy stats, last matched timestamp)
- How do we handle fingerprint evolution? (Company changes auditor → format changes completely. Old fingerprint becomes stale.)
- What is the storage footprint? (Fingerprint size × library size. Budget: <100MB for 10,000 fingerprints?)

**Research areas:**
- Approximate Nearest Neighbor search (FAISS, ScaNN, Annoy benchmarks)
- Document retrieval by layout (industry: Docufai, Rossum, ABBYY approach)
- Hierarchical clustering for document formats
- Inverted index for structured features (Elasticsearch approach)

### R3: Zero-Shot Transfer from Fingerprint Match

When a fingerprint matches, design the system that transfers knowledge to the new document.

**What transfers from a matched fingerprint:**
1. **Zone assignments** — "Pages 3-4 are IS, pages 5-7 are BS, pages 8-9 are CF, pages 10+ are Notes"
2. **Column roles** — "Column 1 is label, Column 2 is Current Year, Column 3 is Prior Year, Column 4 is Budget"
3. **Period detection** — "The dates '31 December 2025' and '31 December 2024' appear on page 3"
4. **Section boundaries** — "Assets section ends at row 23, Liabilities start at row 24"
5. **Note mapping** — "Note 7 is always Property, Plant & Equipment"
6. **Expression patterns** — "Total Assets = sum of rows 1-23 in BS section"

**Questions to answer:**
- How much confidence should we assign to fingerprint-transferred knowledge? (High = good UX. Too high = dangerous if match is wrong.)
- How do we handle partial matches? (Format is 90% the same but Notes section has changed. Transfer zones for IS/BS/CF, but re-process Notes.)
- How do we validate the transfer was correct? (Cross-check: if fingerprint says "page 5 is BS" but VLM detects no table on page 5, flag mismatch)
- What is the expected speed improvement? (Fingerprint transfer vs full VLM processing. Target: 10x faster for matched documents.)
- How do we update the fingerprint after successful transfer? (Reinforce the match, update any shifted page numbers)

**Research areas:**
- Transfer learning in document understanding
- Template matching in invoice processing systems (Context: Rossum, ABBYY, Kofax)
- Layout-guided OCR (using known layout to reduce OCR errors)
- Adaptive template matching with deformable models

### R4: Fingerprint as an Anti-Piracy / Attribution System

Research whether fingerprints can serve a dual purpose: not just speeding up processing, but also providing **document attribution** (identifying the source/auditor/format of a document).

**Use cases:**
1. **Detect duplicate submissions** — Same document submitted twice (for different customers, or by mistake)
2. **Identify auditor** — Automated metadata: "This report appears to be prepared by KPMG based on layout fingerprint"
3. **Detect counterfeit documents** — If a document's fingerprint doesn't match ANY known format, it may be fabricated
4. **Regulatory compliance** — Track which auditing firms' reports are being processed (geographic distribution, auditor concentration)

**Questions to answer:**
- Is it possible to reliably identify the auditing firm from layout alone? (Hypothesize: yes, because Big 4 firms use consistent templates per region)
- Can we detect document tampering via fingerprint mismatch? (Modified page inserted into an otherwise legitimate report)
- What is the false positive rate for duplicate detection? (Two different reports from same auditor might look very similar)
- What are the legal/ethical implications of auditor identification? (Is it okay to say "this looks like a KPMG report"?)

### R5: Privacy & Cross-Client Fingerprint Sharing

Research how to share fingerprint intelligence across tenants while respecting data boundaries.

**Questions to answer:**
- Do fingerprints contain any client-identifiable information? (Layout alone shouldn't, but header text might)
- Can we strip PII from the fingerprint while preserving matching utility?
- What opt-in/opt-out model for cross-client fingerprint sharing?
- How do we handle the scenario where two competing banks submit the same company's annual report? (Both benefit from the shared fingerprint — is this a feature or a concern?)
- What is the minimum fingerprint that's useful for matching but contains zero PII?

---

## Competitive Analysis Required

1. **ABBYY FlexiCapture / Vantage** — Their "document definition" template learning. How does it compare?
2. **Rossum (Elis)** — Their "communication mining" that learns document formats. Architecture?
3. **Kofax / Tungsten Automation** — Template learning for invoice processing. Adaptable to financial statements?
4. **Hyperscience** — Their document classification approach. Any fingerprinting?
5. **Academic**: DocFormer, LayoutParser, SelfDoc — any layout fingerprinting in research?
6. **Google Document AI** — Their processor versioning and template detection

---

## Deliverables

1. **Fingerprint Algorithm Specification** — Complete vector computation from document input
2. **Matching System Design** — Index structure, query protocol, hierarchical matching
3. **Transfer Protocol** — What transfers when a match is found, confidence assignment
4. **Library Architecture** — Storage, clustering, evolution, garbage collection
5. **Anti-Piracy Specification** — Duplicate detection, auditor identification, tamper detection
6. **Privacy Analysis** — PII risk assessment, anonymization approach for cross-client sharing
7. **Benchmark Design** — Test with real financial documents (accuracy, speed, false positive/negative rates)
8. **Implementation Roadmap** — Build plan integrating with existing VLM pipeline

---

## Success Criteria

DFF is successful when:
- Format recognition achieves >95% true positive rate on repeated formats
- False positive rate (wrong format match) is <2%
- Documents with matched fingerprint process 10x faster than unknown formats
- Fingerprint library grows to 500+ entries within first 6 months of production
- Cross-client format sharing improves new-client accuracy by ≥15%
- Fingerprint computation completes in <500ms per document (before main VLM processing)
