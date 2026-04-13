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
import com.numera.customer.domain.Customer
import com.numera.document.domain.Document
import com.numera.model.domain.ModelTemplate
import com.numera.spreading.domain.SpreadItem
import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
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
import java.time.Instant

class IntegrationSyncServiceTest {

    private val externalSystemRepo: ExternalSystemRepository = mockk()
    private val syncRecordRepo: SyncRecordRepository = mockk()
    private val adapter: ExternalAdapter = mockk()
    private val spreadItemRepository: SpreadItemRepository = mockk()
    private val spreadValueRepository: SpreadValueRepository = mockk()

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

        // Mock SpreadItem and SpreadValue for buildCanonicalPayload
        val mockCustomer = mockk<Customer>(relaxed = true).apply {
            every { customerCode } returns "CUST-001"
            every { name } returns "Test Customer"
            every { id } returns UUID.randomUUID()
        }
        val mockDocument = mockk<Document>(relaxed = true).apply {
            every { id } returns UUID.randomUUID()
        }
        val mockTemplate = mockk<ModelTemplate>(relaxed = true).apply {
            every { name } returns "IFRS Template"
        }
        val mockSpreadItem = mockk<SpreadItem>(relaxed = true).apply {
            every { customer } returns mockCustomer
            every { document } returns mockDocument
            every { template } returns mockTemplate
            every { statementDate } returns java.time.LocalDate.of(2024, 12, 31)
            every { frequency } returns "ANNUAL"
            every { auditMethod } returns null
            every { sourceCurrency } returns "USD"
            every { consolidation } returns null
        }
        every { spreadItemRepository.findById(any()) } returns Optional.of(mockSpreadItem)
        every { spreadValueRepository.findBySpreadItemId(any()) } returns emptyList()

        service = IntegrationSyncService(externalSystemRepo, syncRecordRepo, listOf(adapter), spreadItemRepository, spreadValueRepository)
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
        fun `pushSpread marks RETRYING when adapter returns failure and retries remain`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey(any()) } returns null
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } returns
                AdapterResponse(success = false, errorCode = "SERVER_ERROR_500", message = "Internal Server Error")

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.RETRYING, result.status)
            assertEquals("Internal Server Error", result.lastError)
            assertNotNull(result.nextRetryAt)
        }

        @Test
        fun `pushSpread marks RETRYING on exception when retries remain`() {
            every { externalSystemRepo.findById(systemId) } returns Optional.of(system)
            every { syncRecordRepo.findByIdempotencyKey(any()) } returns null
            val savedSlot = slot<SyncRecord>()
            every { syncRecordRepo.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { adapter.pushSpread(any(), any()) } throws RuntimeException("Connection refused")

            val result = service.pushSpread(tenantId, systemId, spreadItemId)

            assertEquals(SyncStatus.RETRYING, result.status)
            assertEquals("Connection refused", result.lastError)
            assertNotNull(result.nextRetryAt)
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
                status = SyncStatus.RETRYING
                retryCount = 1
                maxRetries = 3
                idempotencyKey = UUID.randomUUID().toString()
            }

            every { syncRecordRepo.findByStatusAndNextRetryAtBefore(SyncStatus.RETRYING, any()) } returns listOf(failedRecord)
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
