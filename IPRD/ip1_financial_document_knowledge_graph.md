# IP-1: Financial Document Knowledge Graph (FDKG)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **Financial Document Knowledge Graph** — a persistent, evolving graph representation of financial documents that captures semantic structure, cross-references, and relationships between sections. This is NOT about extracting text from PDFs (that's commodity OCR). This is about **understanding the document as an interconnected knowledge structure** where every table, note, header, and cross-reference is a node with typed edges.

**The goal**: When a bank analyst opens a Balance Sheet that says "Property, Plant & Equipment — see Note 7", the system should AUTOMATICALLY navigate to Note 7, extract the breakdown (Land, Buildings, Machinery, Accumulated Depreciation), link each sub-item back to the parent BS line, and pre-populate the spread with the full hierarchy — zero manual work.

---

## Context: What Exists Today

### Current Codebase
- **VLM Processor** (`ocr-service/app/ml/vlm_processor.py`): Uses Qwen3-VL-8B to extract tables, detect zones (IS/BS/CF), and classify text. Output is flat: a list of detected zones with bounding boxes and extracted values.
- **Zone Classification** (`ocr-service/app/services/zone_classifier.py`): Labels detected regions as Income Statement, Balance Sheet, Cash Flow, or Notes. No relationship between zones.
- **Expression Engine** (`ml-service/app/ml/expression_engine.py`): Detects parent-child via indentation within a SINGLE table. Cannot cross table/page boundaries.
- **Mapping Orchestrator** (`backend/.../MappingOrchestrator.kt`): Takes flat zones + flat semantic matches and produces flat spread values. No graph structure.

### What's Missing (the gap this IP fills)
1. No cross-reference resolution (Note references, "see page X", appendix links)
2. No inter-table relationships (IS Net Income → BS Retained Earnings → CF Operating)
3. No structural persistence across periods (every document processed from scratch)
4. No format learning across clients (same auditor's report processed as "new" every time)

---

## Research Directives

### R1: Graph Representation Design
Research and propose the optimal graph schema for representing financial documents.

**Questions to answer:**
- What node types are needed? (Document, Section, Table, Row, Cell, Note, Footnote, Header, PageBreak, CrossReference, Formula?)
- What edge types capture all relationships? (CONTAINS, REFERENCES, SUMS_TO, FLOWS_TO, ADJUSTS, RECONCILES, SUPERSEDES?)
- How do we represent temporal relationships? (Q1 2024 IS → Q1 2024 BS → Annual 2024 Consolidation)
- Should we use a property graph model (Neo4j-style) or RDF/OWL? What are the tradeoffs for our use case?
- How do we handle multi-period documents (3-column IS with Current Year, Prior Year, Budget)?

**Research areas:**
- Academic papers on financial document graph representations
- XBRL taxonomy as a graph (it already IS a taxonomy — can we leverage it?)
- Knowledge graph construction from semi-structured documents (Google's KELM, Microsoft's Graphrag)
- Document understanding graph models (DocGraphLM, LayoutLMv3 graph attention)

### R2: Cross-Reference Resolution Engine
This is the crown jewel. Research how to automatically resolve cross-references in financial documents.

**Types of cross-references to handle:**
1. **Explicit note references**: "Property, Plant & Equipment (Note 7)" → find Note 7 table
2. **Page references**: "See page 45 for detail" → extract content from page 45
3. **Implicit references**: "As discussed in the auditor's report" → link to auditor section
4. **Intra-note references**: Note 7 says "includes revaluation surplus (Note 12)" → link Note 7 → Note 12
5. **Prior period references**: "Restated figures — see Note 2.1" → link to accounting policy note
6. **Subsidiary/segment references**: "Segment information (Note 4)" → link to segment breakdown

**Questions to answer:**
- What NLP techniques best detect these reference patterns? (regex + NER? LLM extraction? fine-tuned model?)
- How do we resolve "Note 7" to the actual Note 7 heading on some arbitrary page? (page-level search? table of contents parsing? heading detection?)
- How do we handle variations? ("Note 7", "note 7", "n.7", "refer note seven", "as detailed in note VII")
- What is the expected accuracy? What's acceptable for production? Can we use confidence scoring to flag uncertain links?
- How do audited vs unaudited reports differ in cross-reference density?

**Research areas:**
- Entity linking in financial documents
- Coreference resolution techniques adapted for structured documents
- XBRL footnote linking as ground truth data
- SEC EDGAR filing cross-reference patterns (10-K, 10-Q)
- IFRS vs US GAAP note numbering conventions

### R3: Structural Memory & Period-Over-Period Diffing
Research how to maintain and leverage structural memory across document versions.

**The insight**: When Company X submits their annual report every year, the structure is ~90% identical. Pages shift, but the section order, table layouts, and note numbering are remarkably consistent.

**Questions to answer:**
- How do we compute a "structural diff" between two document graphs?
- What graph similarity metrics work best? (Graph Edit Distance? Subgraph isomorphism? GNN-based embedding similarity?)
- How do we handle structural changes? (New note added, sections reordered, merger with subsidiary adds new segment)
- Can we use the structural diff to PREDICT where values will be in the next period's document before processing it?
- How much processing time can we save by using structural memory vs. processing from scratch?

**Research areas:**
- Graph matching algorithms for document comparison
- Versioned knowledge graphs (temporal RDF, property graph with time dimensions)
- Change detection in semi-structured documents
- Academic work on financial statement comparability (FASB/IASB research)

### R4: Anonymous Pattern Transfer
Research how to transfer structural learning across clients without exposing proprietary data.

**The insight**: If we learn that "Deloitte-audited UAE bank reports always have depreciation in Note 7 with a specific 3-column layout", this knowledge should help process OTHER Deloitte-audited UAE bank reports, even for a brand new client.

**Questions to answer:**
- What structural features are transferable without exposing values? (layout, section ordering, note numbering, table formats)
- How do we anonymize the graph while preserving structural utility?
- Can we build "document type clusters" — groups of structurally similar documents? What signals drive clustering? (Auditor? Jurisdiction? Industry? Company size?)
- What's the privacy risk? Can a sophisticated adversary reconstruct client identity from anonymized structural patterns? How do we mitigate?
- What's the expected accuracy improvement from pattern transfer? (Measure: zero-shot vs few-shot vs pattern-transfer accuracy)

**Research areas:**
- Federated learning for document understanding
- Privacy-preserving knowledge transfer
- Document clustering by layout (visual similarity hashing)
- Transfer learning in graph neural networks

### R5: Storage & Query Architecture
Research the best storage backend for the FDKG.

**Options to evaluate:**
1. **Neo4j** — native graph DB, Cypher queries, best for complex traversals. But adds operational complexity (another DB to manage).
2. **PostgreSQL + ltree + JSONB** — use existing PG instance. ltree for hierarchy, JSONB for node properties, materialized paths for cross-references. Simpler ops but complex graph queries are awkward.
3. **Apache AGE** — PostgreSQL extension that adds Cypher graph query capabilities. Best of both worlds?
4. **In-memory graph** (JGraphT / NetworkX) built on demand from relational data — no graph DB needed, but no persistence of graph structure.

**Questions to answer:**
- What query patterns will be most common? (Traverse from BS line → Note → sub-items? Find all cross-references? Structural diff?)
- What's the expected graph size per document? (Nodes: ~500-2000. Edges: ~1000-5000. Is this small enough for in-memory?)
- What's the latency requirement? (Graph must be queryable in <100ms for interactive spreading)
- Do we need full-text search on node labels? (Elasticsearch integration?)

---

## Competitive Analysis Required

Research and document what the following competitors do (and critically, what they DON'T do):

1. **Moody's Spreadsmart / CreditLens** — How do they handle multi-page documents? Any cross-reference capability?
2. **S&P Capital IQ Pro** — Their document extraction pipeline. Any graph-based understanding?
3. **nCino Spreading** — Their automation approach. Primarily template-matching?
4. **Finagraph** — Their "SmartBiz" data extraction. Any structural intelligence?
5. **Finsight.ai / Vena Solutions** — Any knowledge graph approaches?
6. **Academic**: EDGAR-based research systems (FinBERT, DocAI) — what graph representations do they use?

---

## Deliverables

1. **Graph Schema Specification** — Complete node/edge type definitions with properties, examples from real financial documents
2. **Cross-Reference Resolution Algorithm** — Pseudocode + accuracy estimates for each reference type
3. **Structural Memory Protocol** — How graphs are stored, versioned, and diffed across periods
4. **Pattern Transfer Mechanism** — Anonymization approach + transfer learning protocol
5. **Storage Architecture Recommendation** — With benchmarks and migration path
6. **Risk Assessment** — Privacy risks, accuracy failure modes, edge cases
7. **Implementation Roadmap** — Phased build plan with effort estimates per component

---

## Success Criteria

The FDKG is successful when:
- Given a 60-page annual report with 25 Notes, the system resolves >90% of cross-references automatically
- Second annual report from same company processes 40% faster due to structural memory
- Documents from same auditor/industry cluster show 25% accuracy improvement from pattern transfer
- The graph supports sub-100ms traversal queries for interactive spreading
