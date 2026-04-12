# Topic 3 of 6: Cryptographic Merkle Audit Chain

## 1. Critical Analysis of Attached Context

The attached material identifies the correct commercial target, but the present architecture is still one abstraction level too weak for regulator-grade proof portability. The current backend can attest that a sequence of events is internally self-consistent if Numera replays the entire history. It cannot yet produce a compact standalone proof that a specific spread approval existed at a specific time and that no later mutation occurred before a later audit checkpoint.

### Deconstruction of the Current Methodologies

**1. The implemented backend is a linear hash chain, not a verifiable log.**

- `HashChainService` computes `SHA-256(previousHash || payload)` and therefore gives each event a predecessor commitment.
- `AuditService.record()` materializes that commitment into `event_log`, while `AuditService.verifyChain()` replays every event from genesis.
- If a tenant has $n$ audit events and the average canonicalized payload length is $s$ bytes, verifying even one disputed event costs $O(ns)$ because the verifier must recompute the entire prefix.
- This is sufficient for gap detection, but insufficient for selective proofs, append-only consistency proofs, or third-party auditability.

**2. The versioning layer is immutable in application logic, but not cryptographically bound.**

- `SpreadVersionService.createSnapshot()` persists full JSON snapshots and monotonically increasing version numbers.
- Those snapshots are excellent audit inputs, but they are not independently committed into any authenticated index beyond the linear chain.
- In consequence, a regulator can be shown a database state and a hash chain, but cannot receive a proof object that verifies a particular spread version in isolation.

**3. The current proposal in the attached research brief correctly enumerates Merkle-family options, but stops before the real hard problem.**

- Binary Merkle trees solve inclusion proofs, but they do not by themselves solve post-approval quiescence: "nothing happened to this entity after approval".
- Merkle Mountain Ranges solve efficient append-only accumulation, but they are still event-centric. They prove that event $e_i$ exists in prefix $[1,n]$; they do not prove that no later event for the same entity exists unless a secondary authenticated index is added.
- Sparse Merkle trees solve keyed membership and non-membership, but they do not encode append order or prefix consistency.
- Certificate Transparency and Trillian prove append-only evolution of a global log, but they do not natively optimize entity lifecycle proofs where auditors care about a specific `spread_item` and its approval boundary.

**4. Existing verifiable-ledger systems remain database-centric rather than proof-centric.**

- AWS QLDB and immudb provide verifiable transaction histories, but their proof interfaces are oriented around journal or database verification semantics rather than portable, regulator-facing, entity-specific audit certificates.
- They also do not directly optimize for the most valuable banking query: prove both inclusion and non-modification after a control action such as `APPROVE`, `LOCK`, or `ROLLBACK`.

**5. The witness discussion in the attached brief is directionally right, but incomplete.**

- Timestamping a root externally prevents silent retroactive rewriting of that root.
- It does not, by itself, prove that an auxiliary index used for non-existence proofs is consistent with the event log.
- Therefore, any architecture that uses both a global append-only log and a keyed entity index must either make the secondary index fully derivable from the public proof transcript or require an independent witness to replay and validate state transitions before signing checkpoints.

### Strict Limitations, Computational Bottlenecks, and Theoretical Gaps

1. **Current verification is linear in tenant history.**
   The replay loop in `AuditService.verifyChain()` is $O(ns)$ for any verification request, even if the regulator only disputes a single approval.

2. **The system has no selective proof primitive.**
   There is no authenticated path, no consistency proof, and no portable checkpoint signature. The only verification mode is full-chain trust plus server-side replay.

3. **The current state cannot prove quiescence.**
   A linear chain can prove that an event occurred. It cannot efficiently prove that no later event for the same entity occurred before time $T$ without scanning all later events.

4. **A plain Merkle retrofit still leaves a hard theoretical gap.**
   If Numera adds only an MMR or a binary Merkle tree, inclusion becomes $O(\log n)$, but the "no later mutation" query remains unresolved unless an authenticated entity-local state is added.

5. **The 256-byte standalone-proof target is not attainable under pure Merkle-path semantics for arbitrary scale.**
   In a hash-based proof system, a standalone inclusion proof for $n$ leaves has an information-theoretic lower bound of $\Omega(\log n)$ sibling hashes. With 32-byte hashes, path material alone is roughly $32\lceil \log_2 n \rceil$ bytes before signatures and metadata. A fixed 256-byte proof is realistic only for the signed checkpoint certificate, not for the complete proof transcript, unless recursive SNARKs or constant-size vector commitments are introduced.

6. **Concurrent event ingestion is not yet formalized cryptographically.**
   Today the order is whatever the database last-write query returns. For regulator-grade proofs, order must be deterministic and replayable, for example by tenant-local monotone sequence assignment.

7. **Existing SOTA baselines each optimize only one axis.**
   Hash chains optimize minimal append logic. MMR/CT logs optimize append-only inclusion and consistency. Sparse trees optimize keyed membership and non-membership. None of these alone optimize all three requirements simultaneously: append order, entity-local quiescence, and independent witnessing.

8. **GDPR and privacy constraints are unresolved in the current framing.**
   If raw actor identifiers or raw diffs are inserted into immutable leaves, the design creates avoidable legal pressure. The correct object to commit is a canonicalized commitment to the payload, not the raw payload itself.

The attached context therefore exposes a precise theoretical opportunity: construct an audit algorithm that simultaneously commits global order and entity-local frontier state, and that binds both under a witness-validated checkpoint so that inclusion and quiescence become independently verifiable.

## 2. The Novel Algorithmic Proposal

### Name

**Cross-Anchored Frontier Merkle Accumulator (CAFMA)**

### Core Intuition

CAFMA replaces the single audit chain with two synchronized authenticated structures per tenant:

1. A **global append-only Merkle Mountain Range** that provides efficient inclusion and consistency proofs over the total event order.
2. A **frontier authenticated map** that stores, for each entity, the latest entity-local event counter and digest.

The novelty is not merely using two indices. The key mechanism is that every new event leaf is **cross-anchored** to the previous frontier digest of the same entity. This couples global chronology to entity-local causality. At checkpoint time, an independent witness replays the canonical event delta, recomputes both authenticated states, and signs the resulting checkpoint tuple. The verifier therefore does not need Numera's database. It needs only:

- the event material for the disputed action,
- the authenticated path in the global accumulator,
- the authenticated frontier proof at the later checkpoint,
- the witness signature, and
- an optional external timestamp anchor on the signed checkpoint digest.

This is fundamentally different from current SOTA baselines.

- A standard Merkle log proves inclusion and append-only growth, but not entity-local quiescence.
- A standard sparse Merkle map proves keyed state, but not append-only chronology.
- CAFMA proves both by combining them under a witness-replayed checkpoint protocol.

### Mathematical Formulation

Fix a tenant $\theta$. Let the tenant's accepted audit stream be the totally ordered sequence

$$
E^{(\theta)} = \langle e_1, e_2, \dots, e_n \rangle.
$$

Each event is canonicalized as

$$
e_i = (k_i, a_i, \tau_i, \eta_i, p_i),
$$

where:

- $k_i \in \mathcal{K}$ is the entity key, for example `tenantId || entityType || entityId`,
- $a_i \in \mathcal{A}$ is the action,
- $\tau_i$ is the monotone event timestamp or sequence timestamp,
- $\eta_i$ is the actor commitment,
- $p_i$ is the canonical payload bytes.

Define the payload commitment

$$
c_i = H(p_i),
$$

where $H$ is domain-separated SHA-256.

For each entity key $k$, define its entity-local counter at position $i$ as

$$
u_i = 1 + \left|\{j < i : k_j = k_i\}\right|.
$$

Let the frontier state before event $i$ for entity $k_i$ be

$$
F_{i-1}(k_i) = (u_i^{-}, d_i^{-}),
$$

with genesis value

$$
F_0(k) = (0, d_\bot), \qquad d_\bot = H(0xFF).
$$

#### Event Leaf Construction

CAFMA defines the event leaf digest as

$$
\ell_i = H\Big(
0x00 \parallel \mathrm{enc}(\theta) \parallel \mathrm{enc}(k_i) \parallel \mathrm{enc}(u_i)
\parallel \mathrm{enc}(a_i) \parallel \mathrm{enc}(\tau_i) \parallel \eta_i
\parallel c_i \parallel \mathrm{enc}(u_i^{-}) \parallel d_i^{-}
\Big).
$$

This cross-anchor is the first novel step. The leaf is not only a commitment to the event payload; it is also a commitment to the immediately preceding authenticated state for the same entity.

#### Global Accumulator

Let

$$
R_i = \operatorname{MMRRoot}(\ell_1, \ell_2, \dots, \ell_i)
$$

be the Merkle Mountain Range root after $i$ events. The MMR is chosen over a naive batch-built binary Merkle tree because append is incremental and consistency proofs are natural.

#### Frontier Accumulator

Define a frontier entry update digest

$$
\delta_i = H\Big(0x01 \parallel \mathrm{enc}(k_i) \parallel \mathrm{enc}(u_i)
\parallel \ell_i \parallel d_i^{-}\Big).
$$

The authenticated frontier map after $i$ events is

$$
F_i(k) =
\begin{cases}
(u_i, \delta_i), & k = k_i, \\
F_{i-1}(k), & k \neq k_i.
\end{cases}
$$

Its root is

$$
Q_i = \operatorname{SMTRoot}(F_i),
$$

where the map is implemented as a compressed sparse Merkle tree or Patricia-style authenticated trie over $k$.

#### Witnessed Checkpoints

Let checkpoints occur at event indices

$$
0 = n_0 < n_1 < n_2 < \dots < n_m = n.
$$

At checkpoint $j$, CAFMA defines the checkpoint digest

$$
\kappa_j = H\Big(
0x02 \parallel \mathrm{enc}(j) \parallel \mathrm{enc}(n_j)
\parallel R_{n_j} \parallel Q_{n_j} \parallel \kappa_{j-1} \parallel \mathrm{enc}(T_j)
\Big),
$$

where $T_j$ is the wall-clock witness time and $\kappa_0 = H(0xEE)$.

The independent witness accepts $\kappa_j$ only if it can replay the canonical event delta $e_{n_{j-1}+1:n_j}$ from the prior checkpoint and recompute the same pair $(R_{n_j}, Q_{n_j})$. It then issues

$$
\sigma_j = \operatorname{Sign}_{sk_W}(\kappa_j).
$$

Optionally, the signed checkpoint digest is externally timestamp-anchored:

$$
\alpha_j = \operatorname{Anchor}\big(H(\kappa_j \parallel \sigma_j)\big),
$$

where `Anchor` may be RFC 3161, OpenTimestamps, or a customer PKI timestamp service.

#### Proof Objects

For an event $e_i$ and checkpoint $j$ with $i \le n_j$, define the inclusion proof

$$
\Pi_{\mathrm{inc}}(i,j) = \big(e_i, u_i^{-}, d_i^{-}, P^{\mathrm{mmr}}_{i \rightarrow n_j}, \kappa_j, \sigma_j, \alpha_j\big),
$$

where $P^{\mathrm{mmr}}_{i \rightarrow n_j}$ is the MMR inclusion path from $\ell_i$ to $R_{n_j}$.

For an approval-like event $e_i$ and a later checkpoint $j' \ge j$, define the quiescence proof

$$
\Pi_{\mathrm{qui}}(i,j,j') = \Big(
\Pi_{\mathrm{inc}}(i,j),
P^{\mathrm{cons}}_{n_j \rightarrow n_{j'}},
P^{\mathrm{smt}}_{k_i \mapsto (u_i,\delta_i)}(Q_{n_{j'}}),
\kappa_{j'}, \sigma_{j'}, \alpha_{j'}
\Big),
$$

where:

- $j$ is the first witnessed checkpoint such that $i \le n_j$,
- $P^{\mathrm{cons}}_{n_j \rightarrow n_{j'}}$ is the MMR consistency proof that the later checkpoint extends the earlier one,
- $P^{\mathrm{smt}}_{k_i \mapsto (u_i,\delta_i)}(Q_{n_{j'}})$ proves that at checkpoint $j'$ the frontier value for entity $k_i$ is still the digest produced by event $e_i$.

#### Soundness Claim

Assume:

1. $H$ is collision resistant and second-preimage resistant.
2. The witness signs a checkpoint only after deterministic replay of the delta from the previously signed checkpoint.
3. Event ordering is deterministic per tenant.

Then, if $\Pi_{\mathrm{qui}}(i,j,j')$ verifies, there does not exist an accepted event $e_t$ with $t > i$, $t \le n_{j'}$, and $k_t = k_i$.

**Reason.** Any such later event would increment the entity-local counter and force the replayed frontier value for $k_i$ at checkpoint $j'$ to become $(u_i + r, \delta_t)$ for some $r \ge 1$, contradicting the verified frontier proof that the latest value remains $(u_i, \delta_i)$.

This is the key property missing from the current hash-chain design and from a plain CT-style append-only log.

## 3. Technical Architecture & Pseudocode

### Technical Architecture

CAFMA is deployed per tenant as four cooperating services.

1. **Canonical Event Builder**
   Converts `AuditEvent` and spread-version metadata into canonical bytes, hashes the payload, and assigns deterministic per-tenant sequence numbers.

2. **Global Accumulator Service**
   Maintains the tenant's MMR peaks and generates inclusion and consistency proofs.

3. **Frontier Accumulator Service**
   Maintains the authenticated entity frontier map keyed by `tenantId || entityType || entityId` and stores the latest `(entityLocalSeq, frontierDigest)` pair.

4. **Witness Checkpoint Service**
   Packages canonical deltas, submits them to an independent witness for replay validation, receives signed checkpoints, and optionally anchors them externally.

### Production-Grade Pseudocode

```text
DATA TYPES
  AuditEventCanonical:
    tenantId
    entityKey
    action
    actorCommitment
    eventTimestamp
    payloadBytes

  FrontierEntry:
    entityLocalSeq
    frontierDigest

  EventReceipt:
    globalSeq
    entityLocalSeq
    leafHash
    frontierDigest

  WitnessCheckpoint:
    checkpointId
    treeSize
    globalRoot
    frontierRoot
    previousCheckpointDigest
    witnessTime
    digest
    witnessSignature
    externalAnchors

CONSTANTS
  DOMAIN_EVENT = 0x00
  DOMAIN_FRONTIER = 0x01
  DOMAIN_CHECKPOINT = 0x02
  GENESIS_FRONTIER_DIGEST = SHA256(0xFF)
  GENESIS_CHECKPOINT_DIGEST = SHA256(0xEE)

FUNCTION Canonicalize(rawEvent):
  // Deterministic serialization is mandatory; otherwise two honest nodes
  // could hash semantically identical events differently.
  payloadBytes = CanonicalJsonSerialize(rawEvent.diffJson, rawEvent.snapshotHash, rawEvent.parentRefs)
  return AuditEventCanonical(
    tenantId = rawEvent.tenantId,
    entityKey = rawEvent.tenantId || "::" || rawEvent.entityType || "::" || rawEvent.entityId,
    action = rawEvent.action,
    actorCommitment = SHA256(rawEvent.actorEmail || rawEvent.actorRole || rawEvent.actorDeviceId),
    eventTimestamp = rawEvent.eventTimestamp,
    payloadBytes = payloadBytes,
  )

FUNCTION HashEventLeaf(eventCanonical, entityLocalSeq, previousFrontierEntry):
  payloadCommitment = SHA256(eventCanonical.payloadBytes)
  return SHA256(
    DOMAIN_EVENT
    || eventCanonical.tenantId
    || eventCanonical.entityKey
    || EncodeUInt64(entityLocalSeq)
    || eventCanonical.action
    || EncodeTimestamp(eventCanonical.eventTimestamp)
    || eventCanonical.actorCommitment
    || payloadCommitment
    || EncodeUInt64(previousFrontierEntry.entityLocalSeq)
    || previousFrontierEntry.frontierDigest
  )

FUNCTION HashFrontierDigest(entityKey, entityLocalSeq, leafHash, previousFrontierDigest):
  return SHA256(
    DOMAIN_FRONTIER
    || entityKey
    || EncodeUInt64(entityLocalSeq)
    || leafHash
    || previousFrontierDigest
  )

FUNCTION AppendAuditEvent(rawEvent, tenantState):
  eventCanonical = Canonicalize(rawEvent)
  previousFrontier = tenantState.frontierMap.get(eventCanonical.entityKey)
  if previousFrontier is null:
    previousFrontier = FrontierEntry(0, GENESIS_FRONTIER_DIGEST)

  entityLocalSeq = previousFrontier.entityLocalSeq + 1
  leafHash = HashEventLeaf(eventCanonical, entityLocalSeq, previousFrontier)

  // MMR append cost is the number of carried peaks. This is constant on average.
  globalSeq = tenantState.globalMMR.append(leafHash)

  frontierDigest = HashFrontierDigest(
    eventCanonical.entityKey,
    entityLocalSeq,
    leafHash,
    previousFrontier.frontierDigest,
  )

  tenantState.frontierMap.update(
    key = eventCanonical.entityKey,
    value = FrontierEntry(entityLocalSeq, frontierDigest),
  )

  tenantState.eventStore.insert(
    globalSeq = globalSeq,
    entityKey = eventCanonical.entityKey,
    entityLocalSeq = entityLocalSeq,
    canonicalEvent = eventCanonical,
    leafHash = leafHash,
    frontierDigest = frontierDigest,
  )

  return EventReceipt(globalSeq, entityLocalSeq, leafHash, frontierDigest)

FUNCTION BuildCheckpointDigest(checkpointId, treeSize, globalRoot, frontierRoot, previousCheckpointDigest, witnessTime):
  return SHA256(
    DOMAIN_CHECKPOINT
    || EncodeUInt64(checkpointId)
    || EncodeUInt64(treeSize)
    || globalRoot
    || frontierRoot
    || previousCheckpointDigest
    || EncodeTimestamp(witnessTime)
  )

FUNCTION SealCheckpoint(tenantState, witnessClient, anchorClients):
  checkpointId = tenantState.lastCheckpointId + 1
  treeSize = tenantState.globalMMR.size()
  globalRoot = tenantState.globalMMR.root()
  frontierRoot = tenantState.frontierMap.root()
  previousDigest = tenantState.lastCheckpointDigest or GENESIS_CHECKPOINT_DIGEST
  witnessTime = NowUtc()

  checkpointDigest = BuildCheckpointDigest(
    checkpointId,
    treeSize,
    globalRoot,
    frontierRoot,
    previousDigest,
    witnessTime,
  )

  deltaBundle = tenantState.eventStore.listRange(
    startGlobalSeq = tenantState.lastCheckpointTreeSize + 1,
    endGlobalSeq = treeSize,
  )

  // The witness must recompute both roots from the prior signed checkpoint and
  // the canonical delta. Signing without replay turns the witness into a blind TSA,
  // which is insufficient for entity-quiescence soundness.
  witnessSignature = witnessClient.replayValidateAndSign(
    previousCheckpoint = tenantState.lastCheckpoint,
    deltaBundle = deltaBundle,
    proposedTreeSize = treeSize,
    proposedGlobalRoot = globalRoot,
    proposedFrontierRoot = frontierRoot,
    proposedCheckpointDigest = checkpointDigest,
    witnessTime = witnessTime,
  )

  externalAnchors = []
  anchorPayload = SHA256(checkpointDigest || witnessSignature)
  for each anchorClient in anchorClients:
    externalAnchors.append(anchorClient.anchor(anchorPayload))

  checkpoint = WitnessCheckpoint(
    checkpointId = checkpointId,
    treeSize = treeSize,
    globalRoot = globalRoot,
    frontierRoot = frontierRoot,
    previousCheckpointDigest = previousDigest,
    witnessTime = witnessTime,
    digest = checkpointDigest,
    witnessSignature = witnessSignature,
    externalAnchors = externalAnchors,
  )

  tenantState.checkpointStore.insert(checkpoint)
  tenantState.lastCheckpoint = checkpoint
  tenantState.lastCheckpointId = checkpointId
  tenantState.lastCheckpointDigest = checkpointDigest
  tenantState.lastCheckpointTreeSize = treeSize
  return checkpoint

FUNCTION GenerateInclusionProof(tenantState, entityKey, entityLocalSeq, checkpointId):
  eventRow = tenantState.eventStore.findByEntityKeyAndLocalSeq(entityKey, entityLocalSeq)
  checkpoint = tenantState.checkpointStore.get(checkpointId)

  if eventRow.globalSeq > checkpoint.treeSize:
    raise Error("Event is newer than requested checkpoint")

  mmrPath = tenantState.globalMMR.proveInclusion(eventRow.globalSeq, checkpoint.treeSize)

  return {
    canonicalEvent: eventRow.canonicalEvent,
    entityLocalSeq: eventRow.entityLocalSeq,
    previousFrontierSeq: eventRow.entityLocalSeq - 1,
    previousFrontierDigest: tenantState.eventStore.previousFrontierDigest(entityKey, entityLocalSeq),
    leafHash: eventRow.leafHash,
    frontierDigest: eventRow.frontierDigest,
    mmrPath: mmrPath,
    checkpoint: checkpoint,
  }

FUNCTION GenerateQuiescenceProof(tenantState, entityKey, entityLocalSeq, targetCheckpointId):
  eventRow = tenantState.eventStore.findByEntityKeyAndLocalSeq(entityKey, entityLocalSeq)
  initialCheckpoint = tenantState.checkpointStore.firstAtOrAfterTreeSize(eventRow.globalSeq)
  targetCheckpoint = tenantState.checkpointStore.get(targetCheckpointId)

  if targetCheckpoint.treeSize < initialCheckpoint.treeSize:
    raise Error("Target checkpoint predates the event checkpoint")

  inclusionProof = GenerateInclusionProof(
    tenantState,
    entityKey,
    entityLocalSeq,
    initialCheckpoint.checkpointId,
  )

  consistencyProof = tenantState.globalMMR.proveConsistency(
    oldTreeSize = initialCheckpoint.treeSize,
    newTreeSize = targetCheckpoint.treeSize,
  )

  frontierProof = tenantState.frontierMap.proveMembership(
    key = entityKey,
    root = targetCheckpoint.frontierRoot,
  )

  if frontierProof.value.entityLocalSeq != entityLocalSeq:
    raise Error("Entity has a later event; quiescence proof does not exist")

  if frontierProof.value.frontierDigest != eventRow.frontierDigest:
    raise Error("Frontier digest mismatch; quiescence proof does not exist")

  return {
    baseInclusionProof: inclusionProof,
    targetCheckpoint: targetCheckpoint,
    consistencyProof: consistencyProof,
    frontierProof: frontierProof,
  }

FUNCTION VerifyInclusionProof(proof, witnessPublicKey):
  previousFrontier = FrontierEntry(
    proof.previousFrontierSeq,
    proof.previousFrontierDigest,
  )

  recomputedLeaf = HashEventLeaf(
    proof.canonicalEvent,
    proof.entityLocalSeq,
    previousFrontier,
  )

  if recomputedLeaf != proof.leafHash:
    return INVALID

  if not VerifyMMRInclusion(
    leafHash = recomputedLeaf,
    path = proof.mmrPath,
    expectedRoot = proof.checkpoint.globalRoot,
    treeSize = proof.checkpoint.treeSize,
  ):
    return INVALID

  if not VerifySignature(
    publicKey = witnessPublicKey,
    message = proof.checkpoint.digest,
    signature = proof.checkpoint.witnessSignature,
  ):
    return INVALID

  // External anchors are optional for correctness, but useful for stronger
  // time-of-existence evidence.
  for each anchor in proof.checkpoint.externalAnchors:
    if not VerifyExternalAnchor(anchor, SHA256(proof.checkpoint.digest || proof.checkpoint.witnessSignature)):
      return WITNESS_ANCHOR_INVALID

  return VALID

FUNCTION VerifyQuiescenceProof(proof, witnessPublicKey):
  baseStatus = VerifyInclusionProof(proof.baseInclusionProof, witnessPublicKey)
  if baseStatus != VALID:
    return baseStatus

  if not VerifySignature(
    publicKey = witnessPublicKey,
    message = proof.targetCheckpoint.digest,
    signature = proof.targetCheckpoint.witnessSignature,
  ):
    return INVALID

  for each anchor in proof.targetCheckpoint.externalAnchors:
    if not VerifyExternalAnchor(anchor, SHA256(proof.targetCheckpoint.digest || proof.targetCheckpoint.witnessSignature)):
      return WITNESS_ANCHOR_INVALID

  if not VerifyMMRConsistency(
    oldRoot = proof.baseInclusionProof.checkpoint.globalRoot,
    oldTreeSize = proof.baseInclusionProof.checkpoint.treeSize,
    newRoot = proof.targetCheckpoint.globalRoot,
    newTreeSize = proof.targetCheckpoint.treeSize,
    proof = proof.consistencyProof,
  ):
    return INVALID

  if not VerifySparseMembership(
    key = proof.baseInclusionProof.canonicalEvent.entityKey,
    expectedValue = FrontierEntry(
      proof.baseInclusionProof.entityLocalSeq,
      proof.baseInclusionProof.frontierDigest,
    ),
    root = proof.targetCheckpoint.frontierRoot,
    proof = proof.frontierProof,
  ):
    return INVALID

  return VALID_NO_LATER_ENTITY_MUTATION
```

### Implementation Notes

- The canonical event should commit the spread snapshot hash, diff hash, action, actor commitment, and parent entity references, but not raw personal data.
- Deterministic tenant-local sequencing can be implemented by writing audit deltas to an append-only queue and sealing in commit order.
- Checkpoints should be short-period and low-latency, for example every 5 minutes or every 512 events, whichever comes first.
- The verifier library can be shipped as a CLI, a browser verifier, or a JVM/Python package because proof verification needs only hash, signature, and Merkle-path primitives.

## 4. Rigorous Complexity Analysis

Let:

- $n$ be the total number of events for a tenant,
- $m$ be the number of active entity keys in the tenant frontier map,
- $s$ be the size in bytes of the canonicalized payload,
- $b(m)$ be the authenticated frontier path length,
- $w$ be the number of events in a checkpoint batch.

For a compressed sparse Merkle tree or Patricia-style trie, $b(m) = O(\log m)$ expected. With a fixed 256-bit namespace, the strict worst-case depth is bounded by $256$, but I keep $O(\log m)$ in the derivation because it reflects operational growth in active entities.

### Time Complexity

#### Append Operation

An append performs four substantive operations.

1. **Canonicalization and payload hashing**

   Canonical JSON serialization and payload hashing scan the payload once.

   $$
   T_{\mathrm{canon}}(n,m,s) = O(s).
   $$

2. **MMR append**

   Appending one leaf to an MMR merges exactly the number of trailing full peaks in the binary decomposition of the prior size. If $c(n)$ is the number of carried peaks, then

   $$
   T_{\mathrm{mmr}}(n) = O(c(n) + 1).
   $$

   Since $c(n) \le \lfloor \log_2 n \rfloor + 1$,

   $$
   T_{\mathrm{mmr}}^{\mathrm{worst}}(n) = O(\log n).
   $$

   The best case occurs when no merge is needed, so

   $$
   T_{\mathrm{mmr}}^{\mathrm{best}}(n) = O(1).
   $$

   For uniformly distributed append sizes, the number of trailing ones in the binary representation has geometric expectation $1$, hence

   $$
   T_{\mathrm{mmr}}^{\mathrm{avg}}(n) = O(1).
   $$

3. **Frontier map update**

   Updating one entity entry in the authenticated frontier requires rewriting one root-to-leaf path.

   $$
   T_{\mathrm{frontier}}(m) = O(b(m)) = O(\log m).
   $$

4. **Persistent index writes**

   Storing the event row and secondary lookup keys is $O(1)$ amortized from an algorithmic perspective, ignoring storage-engine constants.

Therefore the append complexity is

$$
T_{\mathrm{append}}(n,m,s) = O\big(s + c(n) + \log m\big).
$$

So the derived cases are:

- **Worst case**

  $$
  T_{\mathrm{append}}^{\mathrm{worst}}(n,m,s) = O(s + \log n + \log m).
  $$

- **Best case**

  $$
  T_{\mathrm{append}}^{\mathrm{best}}(n,m,s) = O(s + \log m).
  $$

- **Average case**

  $$
  T_{\mathrm{append}}^{\mathrm{avg}}(n,m,s) = O(s + \log m).
  $$

This is already a strict asymptotic improvement over full-chain verification, because append remains sublinear in tenant history and verification becomes logarithmic rather than linear.

#### Witness Checkpoint Sealing

The witness replays $w$ new events since the prior checkpoint. Each replay step performs one append-equivalent recomputation.

$$
T_{\mathrm{seal}}(w,n,m,s) = \sum_{r=1}^{w} O\big(s + c(n-r) + \log m\big).
$$

Thus:

- **Worst case**

  $$
  T_{\mathrm{seal}}^{\mathrm{worst}} = O\big(w(s + \log n + \log m)\big).
  $$

- **Average case**

  $$
  T_{\mathrm{seal}}^{\mathrm{avg}} = O\big(w(s + \log m)\big).
  $$

The signing and optional external anchor call add only $O(1)$ algorithmic work beyond external network latency.

#### Inclusion Proof Generation

Assuming an indexed lookup by `(entityKey, entityLocalSeq)`, retrieving the event row is $O(1)$ amortized. The expensive part is generating the MMR inclusion path.

$$
T_{\mathrm{inc-proof}}(n) = O(\log n).
$$

This is true in worst, best, and average cases because the path height is governed by tree size, not event content.

#### Quiescence Proof Generation

Quiescence generation requires:

1. one inclusion proof: $O(\log n)$,
2. one consistency proof between checkpoints: $O(\log n)$,
3. one frontier membership proof: $O(\log m)$.

Therefore,

$$
T_{\mathrm{qui-proof}}(n,m) = O(\log n + \log m).
$$

This is the critical result: the most valuable regulatory query no longer requires scanning all events after approval.

#### Verification Time

Verification recomputes a constant number of hashes over the event payload and then checks one inclusion path, one optional consistency path, and one frontier path.

- Inclusion verification:

  $$
  T_{\mathrm{verify-inc}}(n,s) = O(s + \log n).
  $$

- Quiescence verification:

  $$
  T_{\mathrm{verify-qui}}(n,m,s) = O(s + \log n + \log m).
  $$

Compared with the current replay model,

$$
O(s + \log n + \log m) \ll O(ns)
$$

for all realistic bank-scale tenants.

### Space Complexity

#### Persistent Audit Event Store

Each event stores canonical metadata, the leaf hash, the frontier digest, and lookup keys.

$$
S_{\mathrm{events}}(n,s) = O(ns).
$$

#### Global Accumulator Storage

The active MMR frontier contains only one peak per binary digit of $n$, so the live in-memory working state is

$$
S_{\mathrm{mmr,live}}(n) = O(\log n).
$$

If the service persists leaf hashes or tiles so that arbitrary proofs can be served later, total persisted MMR material is

$$
S_{\mathrm{mmr,persisted}}(n) = O(n).
$$

#### Frontier Accumulator Storage

The frontier stores one authenticated entry per active entity.

$$
S_{\mathrm{frontier}}(m) = O(m).
$$

#### Checkpoints

If checkpoints are sealed every $w$ events, the number of checkpoints is $\lceil n / w \rceil$, so

$$
S_{\mathrm{checkpoints}}(n,w) = O(n / w).
$$

#### Total Space

Combining the dominant terms,

$$
S_{\mathrm{total}}(n,m,s,w) = O(ns + n + m + n/w).
$$

Since $ns$ dominates for realistic payload sizes, the asymptotic storage growth is linear in total audit volume, which is unavoidable for any design that preserves replayability and proof generation.

### Proof Size Observation

CAFMA proof size is logarithmic, not constant.

- Inclusion proof size is approximately

  $$
  32\lceil \log_2 n \rceil + |\sigma| + O(1)
  $$

  bytes.

- Quiescence proof size is approximately

  $$
  32\big(\lceil \log_2 n \rceil + \lceil \log_2 m \rceil\big) + |\sigma| + O(1)
  $$

  bytes.

That is still small enough for PDF embedding or QR chunking at realistic scales, but it is the technically honest bound. Any claim of constant-size standalone proofs would require a different primitive family.

## 5. Patentability & Novelty Assessment

The following is a technical novelty assessment, not legal clearance. Formal patentability still requires jurisdiction-specific prior-art search and counsel review.

### Prior Art Comparison

| Baseline | What it does well | Why it is insufficient here | CAFMA differentiation |
|---|---|---|---|
| **Sequential hash chain** | Minimal append logic; predecessor integrity | Single-event verification is $O(n)$ and there is no selective proof or quiescence proof | CAFMA reduces event verification to logarithmic proof checks and adds entity-local non-modification proofs |
| **Binary Merkle tree** | Standard inclusion proof of size $O(\log n)$ | Naive rebuild is batch-oriented and it remains blind to entity-local latest state | CAFMA uses incremental MMR plus a synchronized frontier map |
| **Certificate Transparency / Trillian** | Append-only consistency proofs and globally witnessed tree heads | Event-centric only; proving "no later change to entity X" requires an application-specific secondary authenticated state | CAFMA makes that secondary state first-class and witness-validated |
| **Sparse Merkle tree** | Keyed membership and non-membership | No chronological append semantics, no prefix-consistency proof | CAFMA joins keyed frontier state with a global append-only accumulator |
| **QLDB / immudb** | Verifiable journal/database integrity | Database-centric verification model; not optimized for exportable lifecycle proofs around regulated workflow controls | CAFMA produces portable inclusion and quiescence certificates specifically for spread approvals and related control actions |
| **Plain external timestamping** | Prevents undetectable backdating of a root | Does not validate whether a secondary entity index is consistent with the event log | CAFMA requires witness replay of the delta before signing checkpoint tuples |

The core novelty is therefore not "using a Merkle tree for audit", which is old. The novelty is the **cross-anchored, dual-accumulator, witness-replayed composition** that turns a generic verifiable log into a regulator-usable proof system for financial lifecycle controls.

### Specific Claims

- A method for generating an audit-event digest that commits not only to canonical event content but also to the previously authenticated frontier digest and counter of the same entity, thereby coupling global chronology with entity-local causality.
- A method for maintaining, per tenant, a dual authenticated state comprising an append-only Merkle Mountain Range over all audit events and an authenticated frontier map over entity-local latest states, and for issuing proofs that combine both structures.
- A method for proving post-approval non-modification of an entity by combining an inclusion proof for the approval event, a consistency proof between witnessed checkpoints, and a frontier membership proof showing that the latest authenticated entity state remains the approval state at the later checkpoint.
- A witness protocol in which an independent witness replays canonical event deltas to recompute both the global accumulator root and the frontier root before signing a checkpoint digest, optionally followed by external timestamp anchoring of the signed checkpoint digest.
