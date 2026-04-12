package com.numera.shared.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.audit.crypto.ChameleonHash
import com.numera.shared.audit.crypto.InMemoryKeyProvider
import com.numera.shared.audit.crypto.MerkleAccumulator
import com.numera.shared.config.FeatureFlagService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ZkRfaAuditServiceTest {

    private val objectMapper = ObjectMapper()
    private val chameleonHash = ChameleonHash()
    private val merkleAccumulator = MerkleAccumulator()
    private val keyProvider = InMemoryKeyProvider()
    private val featureFlagService: FeatureFlagService = mockk()
    private val eventLogRepository: EventLogRepository = mockk()
    private val hashChainService = HashChainService(objectMapper)

    private lateinit var service: ZkRfaAuditService

    @BeforeEach
    fun setUp() {
        merkleAccumulator.reset()
        service = ZkRfaAuditService(
            eventLogRepository = eventLogRepository,
            hashChainService = hashChainService,
            chameleonHash = chameleonHash,
            merkleAccumulator = merkleAccumulator,
            keyProvider = keyProvider,
            featureFlagService = featureFlagService,
            objectMapper = objectMapper,
        )
    }

    @Nested
    inner class ChameleonHashTests {

        @Test
        fun `computeHash produces deterministic output`() {
            val key = keyProvider.getTrapdoorKey("tenant-1")
            val randomness = chameleonHash.generateRandomness()
            val msg = "audit payload"

            val h1 = chameleonHash.computeHash(msg, randomness, key)
            val h2 = chameleonHash.computeHash(msg, randomness, key)

            assertEquals(h1, h2)
            assertTrue(h1.length == 64) // SHA-256 hex length
        }

        @Test
        fun `different messages produce different hashes`() {
            val key = keyProvider.getTrapdoorKey("tenant-1")
            val randomness = chameleonHash.generateRandomness()

            val h1 = chameleonHash.computeHash("message-a", randomness, key)
            val h2 = chameleonHash.computeHash("message-b", randomness, key)

            assertTrue(h1 != h2)
        }

        @Test
        fun `findCollision returns different randomness`() {
            val key = keyProvider.getTrapdoorKey("tenant-1")
            val originalRandomness = chameleonHash.generateRandomness()
            val originalMsg = "original audit entry"
            val newMsg = "[REDACTED]"

            val collisionRandomness = chameleonHash.findCollision(
                originalMsg, originalRandomness, newMsg, key,
            )

            assertFalse(originalRandomness.contentEquals(collisionRandomness))
        }
    }

    @Nested
    inner class MerkleAccumulatorTests {

        @Test
        fun `append returns valid proof with correct leaf index`() {
            val proof0 = merkleAccumulator.append("leaf-0")
            val proof1 = merkleAccumulator.append("leaf-1")
            val proof2 = merkleAccumulator.append("leaf-2")

            assertEquals(0L, proof0.leafIndex)
            assertEquals(1L, proof1.leafIndex)
            assertEquals(2L, proof2.leafIndex)
        }

        @Test
        fun `root changes after each append`() {
            val proof0 = merkleAccumulator.append("leaf-0")
            val root0 = proof0.root
            val proof1 = merkleAccumulator.append("leaf-1")
            val root1 = proof1.root

            assertTrue(root0 != root1)
        }

        @Test
        fun `inclusion proof can be retrieved`() {
            merkleAccumulator.append("leaf-0")
            merkleAccumulator.append("leaf-1")
            merkleAccumulator.append("leaf-2")

            val proof = merkleAccumulator.getInclusionProof(1)
            assertEquals(1L, proof.leafIndex)
            assertNotNull(proof.root)
        }

        @Test
        fun `verify proof returns true for valid leaf`() {
            merkleAccumulator.append("leaf-0")
            val proof = merkleAccumulator.append("leaf-1")

            // Verify against current root
            assertTrue(merkleAccumulator.verifyProof("leaf-1", proof.copy(root = merkleAccumulator.computeRoot())))
        }

        @Test
        fun `leaf count tracks appends`() {
            assertEquals(0L, merkleAccumulator.getLeafCount())
            merkleAccumulator.append("a")
            merkleAccumulator.append("b")
            assertEquals(2L, merkleAccumulator.getLeafCount())
        }
    }

    @Nested
    inner class RecordEventTests {

        @Test
        fun `recordEvent enhances event when feature flag enabled`() {
            every { featureFlagService.isEnabled("zkRfaAudit", any()) } returns true

            val event = createTestEvent()
            val enhanced = service.recordEvent(event)

            assertNotNull(enhanced.chameleonRandomness)
            assertNotNull(enhanced.mmrIndex)
            assertNotNull(enhanced.mmrRoot)
            assertNotNull(enhanced.mmrProofJson)
        }

        @Test
        fun `recordEvent leaves event unchanged when feature flag disabled`() {
            every { featureFlagService.isEnabled("zkRfaAudit", any()) } returns false

            val event = createTestEvent()
            val result = service.recordEvent(event)

            assertNull(result.chameleonRandomness)
            assertNull(result.mmrIndex)
            assertNull(result.mmrRoot)
            assertNull(result.mmrProofJson)
        }
    }

    @Nested
    inner class VerifyChainTests {

        @Test
        fun `verifyChain returns valid for empty chain`() {
            every { eventLogRepository.findByTenantIdOrderByCreatedAtAsc("t1") } returns emptyList()

            val result = service.verifyChain("t1")

            assertTrue(result.valid)
            assertEquals(0, result.eventsVerified)
            assertTrue(result.sha256ChainValid)
        }

        @Test
        fun `verifyChain detects broken SHA-256 chain`() {
            val event = createTestEvent().apply {
                previousHash = "GENESIS"
                currentHash = "wrong-hash"
            }
            every { eventLogRepository.findByTenantIdOrderByCreatedAtAsc("t1") } returns listOf(event)

            val result = service.verifyChain("t1")

            assertFalse(result.sha256ChainValid)
            assertEquals(0, result.firstInvalidIndex)
        }
    }

    private fun createTestEvent(): AuditEvent = AuditEvent().apply {
        tenantId = "t1"
        eventType = "DOCUMENT"
        action = "CREATE"
        actorEmail = "test@numera.local"
        entityType = "Document"
        entityId = "doc-1"
        previousHash = "GENESIS"
        currentHash = "abc123"
        diffJson = """{"field":"value"}"""
    }
}
