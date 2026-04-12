# IP-3: Cryptographic Merkle Audit Chain (CMAC)
## Research & Discovery Task — Delegatable to Independent Agent

---

## Mission Statement

Design and specify a **cryptographically tamper-proof audit system** for financial spreading operations that provides independently verifiable proof-of-integrity without requiring access to the source system. Think "blockchain for audit trails" but without the blockchain overhead — just the cryptographic guarantees that regulators need.

**The problem**: When a bank regulator asks "Prove this spread was approved by Manager X on March 15 and hasn't been modified since", current systems can only show database logs — which the database admin could have altered. CMAC produces a **compact cryptographic certificate** (256 bytes) that proves integrity mathematically, independently of the Numera system.

**Why this matters commercially**: Banks pay millions for audit compliance. A system with cryptographic audit proof becomes a regulatory selling point that creates permanent client lock-in (migrating away means breaking the audit chain).

---

## Context: What Exists Today

### Current Codebase
- **HashChainService** (`backend/.../shared/audit/HashChainService.kt`):
  ```kotlin
  fun computeHash(previousHash: String, payload: Any): String {
      val body = objectMapper.writeValueAsString(payload)
      val material = "$previousHash::$body"
      return SHA-256(material)
  }
  ```
  - This is a simple sequential hash chain (each hash includes the previous hash)
  - NO Merkle tree structure
  - NO proof generation capability
  - NO external witnessing
  - NO independent verification possible without full chain replay

- **AuditService** (`backend/.../shared/audit/AuditService.kt`):
  - Records events to `audit_event_log` table
  - Uses `HashChainService` to compute each event's hash
  - Stores: event_type, action, entity_type, entity_id, diff_json, hash, previous_hash, actor info

- **SpreadVersionService** (`backend/.../spreading/application/SpreadVersionService.kt`):
  - Creates version snapshots on every state change
  - Stores full cell-level snapshot as JSON
  - Uses sequential version numbers (v1, v2, v3...)
  - NO cryptographic integrity on snapshots

### The Gap
1. Sequential hash chain can prove "no gaps" but NOT efficiently prove a specific event's integrity
2. No Merkle tree = no compact proofs (must replay entire chain to verify any single event)
3. No external witness = system admin can rebuild the entire chain from scratch
4. No proof export = auditors must trust the system, can't verify independently

---

## Research Directives

### R1: Merkle Tree Architecture for Financial Audit

Design the Merkle tree structure optimized for financial audit use cases.

**Questions to answer:**
- What is the leaf node structure? (Hash of: entity_id + action + actor + timestamp + payload_hash + previous_leaf_hash?)
- How do we handle different entity types in the same tree? (Separate trees per entity type? Single global tree with entity-typed leaves?)
- What is the tree rebuild frequency? (Per-event = always consistent but slow? Batched every N events = fast but has a consistency window?)
- How do we handle concurrent events? (Multiple analysts submitting spreads simultaneously — ordering guarantee?)
- Should we use a binary Merkle tree, a Merkle Mountain Range (MMR), or a Sparse Merkle Tree (SMT)?
- What hash function? (SHA-256 is standard, but SHA-3 or BLAKE3 might offer advantages)

**Tree design options to evaluate:**

| Option | Structure | Proof Size | Rebuild Cost | Best For |
|---|---|---|---|---|
| Binary Merkle Tree | Full binary tree, leaves = events | O(log n) | O(n) per rebuild | Batch auditing |
| Merkle Mountain Range | Append-only, multiple peaks | O(log n) | O(1) per append | Real-time auditing |
| Sparse Merkle Tree | Fixed-size tree, most leaves empty | O(log n) constant | O(1) per update | Key-value proofs |
| Epoch-based Tree | New tree per time epoch (day/week) | O(log n) within epoch | Small trees | Regulatory periods |

**Research areas:**
- Certificate Transparency (CT) log structure — Google's approach to tamper-evident logs
- AWS QLDB (Quantum Ledger Database) — their journal-based Merkle approach
- Apache Trillian — open-source transparency log framework
- Hyperledger Fabric's hash chain architecture
- Academic: "Efficient Merkle Tree Construction for Append-Only Data" (USENIX)

### R2: Proof Generation & Verification Protocol

Design the protocol for generating and verifying audit proofs.

**Proof structure:**
```json
{
  "proof_type": "MERKLE_INCLUSION",
  "entity_type": "spread_version",
  "entity_id": "550e8400-e29b-41d4-a716-446655440000",
  "event_hash": "a7f3c2d8...",
  "merkle_root": "b4e9f1a2...",
  "merkle_path": [
    {"position": "LEFT", "hash": "c3d4e5f6..."},
    {"position": "RIGHT", "hash": "d5e6f7g8..."},
    {"position": "LEFT", "hash": "e7f8g9h0..."}
  ],
  "tree_epoch": "2026-Q1",
  "tree_size": 15234,
  "witness_timestamp": "2026-03-15T14:30:00Z",
  "witness_signature": "MIIBIjANBg..."
}
```

**Verification algorithm (must be standalone — no Numera system access needed):**
```
1. Compute expected_leaf_hash = SHA-256(event_data)
2. Walk merkle_path bottom-up, combining hashes
3. Compare computed_root with proof.merkle_root
4. Verify witness_signature against known public key
5. Check witness_timestamp is within expected range
6. Return: VALID / INVALID / WITNESS_EXPIRED
```

**Questions to answer:**
- What data goes INTO the leaf hash? (Full event payload? Or just a commitment to the payload?)
- How do we handle proof requests for entities that span multiple events? (A spread with 5 versions = 5 leaves. Do we provide 5 proofs or a batch proof?)
- Can we provide "non-existence proofs"? (Prove that NO event exists for entity X after timestamp T — useful for proving "nothing was modified after approval")
- How do we handle tree root rotation? (When a new batch is added, the root changes. Old proofs reference old roots. Both must remain valid.)
- What is the proof size budget? (Must fit in a standard PDF attachment? Must be embeddable in a QR code?)
- Should we provide a web-based verification tool? (Public URL where anyone can paste a proof and verify it)

**Research areas:**
- RFC 6962: Certificate Transparency — proof format specification
- Ethereum Merkle Patricia Trie proofs
- Bitcoin SPV (Simplified Payment Verification) proofs
- NIST SP 800-184: Guide for Cybersecurity Event Recovery (audit requirements)
- SOC 2 Type II audit trail requirements for financial services

### R3: External Witnessing Mechanisms

Research how to create an external anchor point that Numera itself cannot forge.

**The threat model**: Without external witnessing, a malicious database admin could:
1. Delete all events after March 15
2. Insert fabricated events
3. Recompute the entire hash chain from the modified data
4. Present a "valid" chain that tells a false story

External witnessing prevents this by publishing the Merkle root to a location that Numera cannot control.

**Witnessing options to evaluate:**

| Option | Trust Model | Cost | Latency | Permanence |
|---|---|---|---|---|
| **Public blockchain** (Ethereum L2, Bitcoin via OpenTimestamps) | Decentralized, trustless | $0.01-0.10 per witness | 1-15 min | Permanent |
| **AWS QLDB** | Trust AWS | Pay-per-use | Seconds | AWS retention |
| **Signed timestamping** (RFC 3161 TSA) | Trust TSA provider | ~$0.01 per stamp | Seconds | Depends on TSA |
| **Publishing to multiple authorities** | Distributed trust | Free | Varies | Best of all worlds |
| **Customer's own key signing** | Customer self-witnesses | Free | Instant | Customer controls |
| **Public web archive** (e.g., publish to Archive.org) | Trust Archive.org | Free | Minutes | "Pretty permanent" |

**Questions to answer:**
- What is the witnessing frequency? (Every event? Every batch? Hourly? Daily?)
- How many witnesses are needed for sufficient trust?
- What happens if a witness service goes down? (Graceful degradation)
- What happens if a witness is compromised? (How do we detect, what's the blast radius?)
- Can customers bring their own witnessing infrastructure? (e.g., bank's internal PKI)
- What is the regulatory stance on each option? (Would a UAE Central Bank accept a blockchain timestamp as evidence? What about the European Banking Authority?)

**Research areas:**
- OpenTimestamps protocol (Bitcoin-anchored timestamping)
- Amazon QLDB architecture and compliance certifications
- RFC 3161: Internet X.509 PKI Time-Stamp Protocol
- European eIDAS regulation on electronic timestamps
- UK FCA and US SEC requirements for electronic audit evidence

### R4: Regulatory & Legal Framework

Research the regulatory environment for cryptographic audit proofs.

**Questions to answer:**
- What do banking regulators (Basel Committee, local CBs) require for audit evidence?
- Is a Merkle proof legally admissible as evidence in court? In which jurisdictions?
- How does this align with SOC 2 Type II audit requirements?
- How does this align with ISO 27001 information security requirements?
- What is the data retention requirement for audit chains? (7 years? 10 years? Indefinite?)
- Do any regulations MANDATE tamper-evident audit trails? (This would make CMAC a compliance requirement, not just a feature)
- What are the GDPR implications of storing audit data with personal identifiers in an immutable structure? (Right to erasure vs immutable chain)

**Research areas:**
- Basel III operational risk requirements
- BCBS 239 (Principles for effective risk data aggregation)
- UAE CBUAE regulations for electronic records
- EBA Guidelines on ICT and security risk management
- MAS (Monetary Authority of Singapore) technology risk management

### R5: Performance & Storage Optimization

**Questions to answer:**
- What is the storage growth rate? (If we generate 10,000 audit events/day, how fast does the tree grow?)
- Can we use tree pruning? (Archive old epochs, keep only the root and proofs for recent epochs?)
- What is the proof generation latency target? (<100ms for interactive use?)
- How do we handle database backup/restore without breaking the chain?
- Can the Merkle tree be computed in-memory and the roots persisted (reducing tree storage to just roots)?
- What is the computational cost of tree construction? (Can we use Merkle Mountain Range for O(1) append?)

---

## Competitive Analysis Required

1. **AWS QLDB** — How does their "transparent, immutable, and cryptographically verifiable" journal work? What's their proof format? Can we do better?
2. **Chainlink Proof of Reserve** — Their approach to cryptographic proof of financial data integrity
3. **Digital Asset / DAML** — Their approach to financial audit trails with cryptographic verification
4. **Trillian** (Google) — Open-source transparency log. Can we embed it?
5. **Immudb** — Immutable database with built-in Merkle tree. Should we just use this as our audit store?
6. **Banking competitors** — Do Finastra, Temenos, or FIS offer any cryptographic audit capabilities?

---

## Deliverables

1. **Merkle Tree Architecture** — Complete specification with tree type selection, leaf format, rebuild protocol
2. **Proof Format Specification** — JSON schema for proofs, verification algorithm pseudocode
3. **Witnessing Protocol** — External witness selection, frequency, fallback strategy
4. **Verification Tool Design** — How a standalone verifier works (web page, CLI, library)
5. **Regulatory Compliance Matrix** — Which regulations this satisfies, per jurisdiction
6. **GDPR Compatibility Analysis** — How to handle right-to-erasure in an immutable chain
7. **Migration Plan** — How to transition from current HashChainService to full CMAC
8. **Implementation Roadmap** — Phased build plan with effort estimates

---

## Success Criteria

CMAC is successful when:
- Any audit event can produce a Merkle proof in <100ms
- Proof can be independently verified without any Numera system access
- External witness creates a tamper-proof anchor within 1 hour of event
- System detects any chain tampering within 5 minutes
- Regulatory compliance team confirms the approach satisfies their top 3 jurisdictions' requirements
- Migration from current HashChainService is backward-compatible (old hashes remain valid)
