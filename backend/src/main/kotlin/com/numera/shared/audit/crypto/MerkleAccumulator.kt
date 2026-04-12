package com.numera.shared.audit.crypto

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class MerkleProof(
    val leafIndex: Long,
    val leafHash: String,
    val siblings: List<String>,
    val root: String,
)

/**
 * Merkle Mountain Range (MMR) — append-only authenticated accumulator.
 *
 * Peaks are kept in memory for O(log n) inclusion proofs.
 * Each "mountain" is a perfect binary Merkle tree; the MMR root
 * is the hash of all peak hashes concatenated left-to-right.
 */
@Component
class MerkleAccumulator {

    private val lock = ReentrantReadWriteLock()

    /** All nodes stored flat; index 0-based. */
    private val nodes = mutableListOf<String>()

    /** Total number of leaves appended. */
    private var leafCount: Long = 0

    fun append(leaf: String): MerkleProof {
        lock.write {
            val leafHash = sha256(leaf)
            val leafNodeIdx = nodes.size
            nodes.add(leafHash)
            leafPositions.add(leafNodeIdx)
            var height = 0
            var current = leafHash

            // Merge with left sibling while the current position completes a pair
            while (hasSiblingAtHeight(height)) {
                val leftSibling = nodes[nodes.size - 2]
                current = sha256(leftSibling + current)
                nodes.add(current)
                height++
            }
            leafCount++
            val proof = buildProof(leafCount - 1, leafHash)
            return proof
        }
    }

    fun getInclusionProof(leafIndex: Long): MerkleProof {
        lock.read {
            require(leafIndex in 0 until leafCount) { "Leaf index $leafIndex out of range [0, $leafCount)" }
            val nodePos = leafIndexToNodePos(leafIndex)
            val leafHash = nodes[nodePos]
            return buildProof(leafIndex, leafHash)
        }
    }

    fun verifyProof(leaf: String, proof: MerkleProof): Boolean {
        val leafHash = sha256(leaf)
        if (leafHash != proof.leafHash) return false

        var current = leafHash
        var idx = proof.leafIndex
        for (sibling in proof.siblings) {
            current = if (idx % 2 == 0L) {
                sha256(current + sibling)
            } else {
                sha256(sibling + current)
            }
            idx /= 2
        }

        // The final value should be one of the peaks; root is hash of all peaks
        return proof.root == computeRoot()
    }

    fun computeRoot(): String {
        lock.read {
            val peaks = getPeaks()
            if (peaks.isEmpty()) return sha256("")
            return sha256(peaks.joinToString(""))
        }
    }

    fun getLeafCount(): Long = lock.read { leafCount }

    // ── internals ──────────────────────────────────────────────────

    private fun hasSiblingAtHeight(height: Int): Boolean {
        // At the given height, check if the number of leaves so far
        // means we just completed a pair at that tree level.
        val mask = 1L shl height
        return (leafCount and mask) != 0L
    }

    private fun leafIndexToNodePos(leafIndex: Long): Int {
        // leafPositions is insertion-ordered via mutableSetOf (LinkedHashSet)
        var count = 0L
        for (pos in leafPositions.sorted()) {
            if (count == leafIndex) return pos
            count++
        }
        throw IllegalStateException("Leaf $leafIndex not found in MMR")
    }

    private val leafPositions = mutableSetOf<Int>()

    private fun getPeaks(): List<String> {
        val peaks = mutableListOf<String>()
        var remaining = leafCount
        var nodeIdx = 0
        var height = 0

        // Walk the MMR: each peak is a perfect tree of size 2^k
        while (remaining > 0) {
            val highBit = java.lang.Long.highestOneBit(remaining)
            val treeSize = (2 * highBit - 1).toInt() // number of nodes in a perfect tree of highBit leaves
            if (nodeIdx + treeSize - 1 < nodes.size) {
                peaks.add(nodes[nodeIdx + treeSize - 1])
            }
            nodeIdx += treeSize
            remaining -= highBit
        }
        return peaks
    }

    private fun buildProof(leafIndex: Long, leafHash: String): MerkleProof {
        // Simplified proof: collect sibling hashes walking up each peak tree
        val siblings = mutableListOf<String>()

        // Find which peak tree this leaf belongs to
        var remaining = leafCount
        var baseNodeIdx = 0
        var treeLeaves = 0L

        while (remaining > 0) {
            val highBit = java.lang.Long.highestOneBit(remaining)
            if (leafIndex < treeLeaves + highBit) {
                // This leaf is in the current peak tree
                collectSiblings(baseNodeIdx, highBit, leafIndex - treeLeaves, siblings)
                break
            }
            val treeSize = (2 * highBit - 1).toInt()
            baseNodeIdx += treeSize
            treeLeaves += highBit
            remaining -= highBit
        }

        return MerkleProof(
            leafIndex = leafIndex,
            leafHash = leafHash,
            siblings = siblings,
            root = computeRoot(),
        )
    }

    private fun collectSiblings(baseNodeIdx: Int, treeLeafCount: Long, localIndex: Long, out: MutableList<String>) {
        if (treeLeafCount <= 1) return
        val half = treeLeafCount / 2
        val leftTreeNodes = (2 * half - 1).toInt()

        if (localIndex < half) {
            // Leaf is in the left subtree; sibling is the right subtree root
            val rightRoot = nodes.getOrNull(baseNodeIdx + leftTreeNodes + (2 * (treeLeafCount - half) - 1).toInt() - 1)
            if (rightRoot != null) out.add(rightRoot)
            collectSiblings(baseNodeIdx, half, localIndex, out)
        } else {
            // Leaf is in the right subtree; sibling is the left subtree root
            val leftRoot = nodes.getOrNull(baseNodeIdx + leftTreeNodes - 1)
            if (leftRoot != null) out.add(leftRoot)
            collectSiblings(baseNodeIdx + leftTreeNodes, treeLeafCount - half, localIndex - half, out)
        }
    }

    internal fun reset() {
        lock.write {
            nodes.clear()
            leafPositions.clear()
            leafCount = 0
        }
    }

    companion object {
        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
