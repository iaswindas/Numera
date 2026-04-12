package com.numera.integration

import com.numera.integration.application.ConflictResolution
import com.numera.integration.application.IntegrationSyncService
import com.numera.integration.domain.ExternalSystem
import com.numera.integration.domain.ExternalSystemRepository
import com.numera.integration.domain.ExternalSystemType
import com.numera.integration.domain.SyncDirection
import com.numera.integration.domain.SyncRecord
import com.numera.integration.domain.SyncRecordRepository
import com.numera.integration.domain.SyncStatus
import com.numera.integration.spi.AdapterResponse
import com.numera.integration.spi.CanonicalSpreadPayload
import com.numera.integration.spi.ExternalAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class IntegrationSyncServiceTest {

    private val externalSystemRepo: ExternalSystemRepository = mockk()
    private val syncRecordRepo: SyncRecordRepository = mockk()
    private val adapter: ExternalAdapter = mockk()

    private lateinit var service: IntegrationSyncService

    private val tenantId = UUID.randomUUID()
    private val systemId = UUID.randomUUID()
    private val spreadItemId = UUID.randomUUID()

    private val system = ExternalSystem().apply {
        id = systemId
        this.tenantId = this@IntegrationSyncServiceTest.tenantId
        name = "Test CreditLens"
        type = ExternalSystemType.CREDITLENS
        baseUrl = "https://creditlens.example.com"
        active = true
    }

    @BeforeEach
    fun setUp() {
        every { adapter.systemType() } returns ExternalSystemType.CREDITLENS
        service = IntegrationSyncService(externalSystemRepo, syncRecordRepo, listOf(adapter))
    }

    @Nested
    inner class PushSpreadTests {

        @Test
        fun `pushSpread creates record and returns COMPLETED on success`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey(any()) } returns null
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } returns
                AdapterResponse(success = true, externalId = "ext-123", message = "ok")

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.COMPLETED, result.status)
            assertEquals("ext-123", result.externalId)
            assertNotNull(result.syncedAt)
            assertNull(result.lastError)
        }

        @Test
        fun `pushSpread returns existing record when idempotency key already completed`() {
            val existing = SyncRecord().apply {
                this.tenantId = this@IntegrationSyncServiceTest.tenantId
                externalSystemId = systemId
                entityId = spreadItemId
                direction = SyncDirection.PUSH
                status = SyncStatus.COMPLETED
                idempotencyKey = "$systemId:$spreadItemId:PUSH"
            }
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey("$systemId:$spreadItemId:PUSH") } returns existing

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.COMPLETED, result.status)
            verify(exactly = 0) { adapter.pushSpread(any(), any()) }
        }

        @Test
        fun `pushSpread marks FAILED when adapter returns failure`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey(any()) } returns null
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } returns
                AdapterResponse(success = false, errorCode = "SERVER_ERROR_500", message = "Internal Server Error")

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.FAILED, result.status)
            assertEquals("Internal Server Error", result.lastError)
        }

        @Test
        fun `pushSpread marks FAILED on exception`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey(any()) } returns null
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } throws RuntimeException("Connection refused")

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.FAILED, result.status)
            assertEquals("Connection refused", result.lastError)
        }
    }

    @Nested
    inner class RetryTests {

        @Test
        fun `retryFailedSyncs retries failed records under max retries`() {
            val failedRecord = SyncRecord().apply {
                id = UUID.randomUUID()
                this.tenantId = this@IntegrationSyncServiceTest.tenantId
                externalSystemId = systemId
                entityType = "SPREAD"
                entityId = spreadItemId
                direction = SyncDirection.PUSH
                status = SyncStatus.FAILED
                retryCount = 1
                maxRetries = 3
                idempotencyKey = UUID.randomUUID().toString()
            }

            every { syncRecordRepo.findByStatusAndRetryCountLessThan(SyncStatus.FAILED, 3) } returns listOf(failedRecord)
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } returns
                AdapterResponse(success = true, externalId = "ext-456")

            val count = service.retryFailedSyncs()

            assertEquals(1, count)
            assertEquals(SyncStatus.COMPLETED, failedRecord.status)
            assertEquals(2, failedRecord.retryCount)
        }
    }

    @Nested
    inner class ConflictResolutionTests {

        @Test
        fun `resolveConflict with KEEP_LOCAL resets to PENDING`() {
            val conflictRecord = SyncRecord().apply {
                id = UUID.randomUUID()
                status = SyncStatus.CONFLICT
                retryCount = 2
                lastError = "Version mismatch"
            }
            every { syncRecordRepo.findById(conflictRecord.id!!) } returns Optional.of(conflictRecord)
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.resolveConflict(conflictRecord.id!!, ConflictResolution.KEEP_LOCAL)

            assertEquals(SyncStatus.PENDING, result.status)
            assertEquals(0, result.retryCount)
            assertNull(result.lastError)
        }

        @Test
        fun `resolveConflict with KEEP_REMOTE marks COMPLETED`() {
            val conflictRecord = SyncRecord().apply {
                id = UUID.randomUUID()
                status = SyncStatus.CONFLICT
            }
            every { syncRecordRepo.findById(conflictRecord.id!!) } returns Optional.of(conflictRecord)
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.resolveConflict(conflictRecord.id!!, ConflictResolution.KEEP_REMOTE)

            assertEquals(SyncStatus.COMPLETED, result.status)
            assertNotNull(result.syncedAt)
        }

        @Test
        fun `resolveConflict throws on non-CONFLICT record`() {
            val record = SyncRecord().apply {
                id = UUID.randomUUID()
                status = SyncStatus.COMPLETED
            }
            every { syncRecordRepo.findById(record.id!!) } returns Optional.of(record)

            assertThrows<com.numera.shared.exception.ApiException> {
                service.resolveConflict(record.id!!, ConflictResolution.KEEP_LOCAL)
            }
        }
    }

    @Nested
    inner class PullMetadataTests {

        @Test
        fun `pullMetadata delegates to adapter`() {
            val payload = CanonicalSpreadPayload(
                customerId = "c1", customerName = "Acme", period = "2025-12",
                periodType = "ANNUAL", templateName = "Standard",
                lineItems = emptyList(),
            )
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { adapter.pullMetadata(system, "ref-99") } returns payload

            val result = service.pullMetadata(tenantId, systemId, "ref-99")

            assertNotNull(result)
            assertEquals("Acme", result!!.customerName)
        }

        @Test
        fun `pullMetadata returns null when adapter returns null`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { adapter.pullMetadata(system, "missing") } returns null

            val result = service.pullMetadata(tenantId, systemId, "missing")

            assertNull(result)
        }
    }
}
