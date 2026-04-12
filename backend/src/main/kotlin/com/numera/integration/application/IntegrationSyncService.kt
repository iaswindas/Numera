package com.numera.integration.application

import com.numera.integration.domain.ExternalSystemRepository
import com.numera.integration.domain.SyncDirection
import com.numera.integration.domain.SyncRecord
import com.numera.integration.domain.SyncRecordRepository
import com.numera.integration.domain.SyncStatus
import com.numera.integration.spi.CanonicalLineItem
import com.numera.integration.spi.CanonicalSpreadPayload
import com.numera.integration.spi.CanonicalSource
import com.numera.integration.spi.ExternalAdapter
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

@Service
class IntegrationSyncService(
    private val externalSystemRepo: ExternalSystemRepository,
    private val syncRecordRepo: SyncRecordRepository,
    private val adapters: List<ExternalAdapter>,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
) {
    private val log = LoggerFactory.getLogger(IntegrationSyncService::class.java)

    private fun adapterFor(typeKey: com.numera.integration.domain.ExternalSystemType): ExternalAdapter =
        adapters.firstOrNull { it.systemType() == typeKey }
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "No adapter registered for type $typeKey")

    // ── Push ──────────────────────────────────────────────────────────────

    @Transactional
    fun pushSpread(tenantId: UUID, systemId: UUID, spreadItemId: UUID): SyncRecord {
        val system = externalSystemRepo.findById(systemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "External system not found: $systemId") }

        val idempotencyKey = buildIdempotencyKey(systemId, spreadItemId, SyncDirection.PUSH)
        val existing = syncRecordRepo.findByIdempotencyKey(idempotencyKey)
        if (existing != null && existing.status == SyncStatus.COMPLETED) {
            log.debug("Idempotent skip — sync already completed: {}", idempotencyKey)
            return existing
        }

        val record = existing ?: SyncRecord().apply {
            this.tenantId = tenantId
            this.externalSystemId = systemId
            this.entityType = "SPREAD"
            this.entityId = spreadItemId
            this.direction = SyncDirection.PUSH
            this.status = SyncStatus.PENDING
            this.idempotencyKey = idempotencyKey
        }

        record.status = SyncStatus.IN_PROGRESS
        syncRecordRepo.save(record)

        return try {
            val adapter = adapterFor(system.type)
            // Build a minimal canonical payload — real implementation would load SpreadItem
            val payload = buildCanonicalPayload(spreadItemId)
            val response = adapter.pushSpread(system, payload)

            if (response.success) {
                record.status = SyncStatus.COMPLETED
                record.externalId = response.externalId
                record.syncedAt = Instant.now()
                record.lastError = null
            } else {
                handleFailure(record, response.message ?: "Push failed: ${response.errorCode}")
            }
            syncRecordRepo.save(record)
        } catch (ex: Exception) {
            handleFailure(record, ex.message ?: "Unexpected error")
            syncRecordRepo.save(record)
        }
    }

    // ── Pull ─────────────────────────────────────────────────────────────

    @Transactional
    fun pullMetadata(tenantId: UUID, systemId: UUID, externalRef: String): CanonicalSpreadPayload? {
        val system = externalSystemRepo.findById(systemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "External system not found: $systemId") }

        val adapter = adapterFor(system.type)
        return adapter.pullMetadata(system, externalRef)
    }

    // ── Retry ────────────────────────────────────────────────────────────

    @Transactional
    fun retryFailedSyncs(): Int {
        val failedRecords = syncRecordRepo.findByStatusAndRetryCountLessThan(SyncStatus.FAILED, 3)
        var retried = 0
        for (record in failedRecords) {
            try {
                val system = externalSystemRepo.findById(record.externalSystemId).orElse(null) ?: continue
                val adapter = adapterFor(system.type)

                record.status = SyncStatus.IN_PROGRESS
                record.retryCount++
                syncRecordRepo.save(record)

                val payload = buildCanonicalPayload(record.entityId)
                val response = adapter.pushSpread(system, payload)

                if (response.success) {
                    record.status = SyncStatus.COMPLETED
                    record.externalId = response.externalId
                    record.syncedAt = Instant.now()
                    record.lastError = null
                } else {
                    handleFailure(record, response.message ?: "Retry push failed")
                }
                syncRecordRepo.save(record)
                retried++
            } catch (ex: Exception) {
                log.warn("Retry failed for sync record {}: {}", record.id, ex.message)
                handleFailure(record, ex.message ?: "Retry error")
                syncRecordRepo.save(record)
            }
        }
        log.info("Retried {} failed sync records", retried)
        return retried
    }

    // ── Manual retry ─────────────────────────────────────────────────────

    @Transactional
    fun retrySingle(syncRecordId: UUID): SyncRecord {
        val record = syncRecordRepo.findById(syncRecordId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Sync record not found: $syncRecordId") }

        if (record.status != SyncStatus.FAILED && record.status != SyncStatus.CONFLICT) {
            throw ApiException(ErrorCode.BAD_REQUEST, "Only FAILED or CONFLICT records can be retried")
        }

        val system = externalSystemRepo.findById(record.externalSystemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "External system not found") }

        val adapter = adapterFor(system.type)
        record.status = SyncStatus.IN_PROGRESS
        record.retryCount++
        syncRecordRepo.save(record)

        return try {
            val payload = buildCanonicalPayload(record.entityId)
            val response = adapter.pushSpread(system, payload)
            if (response.success) {
                record.status = SyncStatus.COMPLETED
                record.externalId = response.externalId
                record.syncedAt = Instant.now()
                record.lastError = null
            } else {
                handleFailure(record, response.message ?: "Retry failed")
            }
            syncRecordRepo.save(record)
        } catch (ex: Exception) {
            handleFailure(record, ex.message ?: "Unexpected error")
            syncRecordRepo.save(record)
        }
    }

    // ── Conflict resolution ──────────────────────────────────────────────

    @Transactional
    fun resolveConflict(syncRecordId: UUID, resolution: ConflictResolution): SyncRecord {
        val record = syncRecordRepo.findById(syncRecordId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Sync record not found: $syncRecordId") }

        if (record.status != SyncStatus.CONFLICT) {
            throw ApiException(ErrorCode.BAD_REQUEST, "Record is not in CONFLICT state")
        }

        return when (resolution) {
            ConflictResolution.KEEP_LOCAL -> {
                record.status = SyncStatus.PENDING
                record.retryCount = 0
                record.lastError = null
                syncRecordRepo.save(record)
            }
            ConflictResolution.KEEP_REMOTE -> {
                record.status = SyncStatus.COMPLETED
                record.syncedAt = Instant.now()
                record.lastError = null
                syncRecordRepo.save(record)
            }
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────

    fun listSyncRecords(tenantId: UUID): List<SyncRecord> =
        syncRecordRepo.findByTenantId(tenantId)

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildIdempotencyKey(systemId: UUID, entityId: UUID, direction: SyncDirection): String =
        "$systemId:$entityId:$direction"

    private fun handleFailure(record: SyncRecord, message: String) {
        record.lastError = message
        if (record.retryCount >= record.maxRetries) {
            record.status = SyncStatus.FAILED
            log.warn("Sync record {} moved to dead-letter (max retries exceeded): {}", record.id, message)
        } else {
            record.status = SyncStatus.FAILED
        }
    }

    private fun buildCanonicalPayload(spreadItemId: UUID): CanonicalSpreadPayload {
        val spreadItem = spreadItemRepository.findById(spreadItemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found: $spreadItemId") }

        val lineItems = spreadValueRepository.findBySpreadItemId(spreadItemId)
            .asSequence()
            .filter { it.mappedValue != null }
            .sortedBy { it.itemCode }
            .map { value ->
                CanonicalLineItem(
                    lineItemCode = value.itemCode,
                    label = value.label,
                    value = value.mappedValue!!,
                    source = canonicalSource(value),
                    confidence = value.confidenceScore,
                )
            }
            .toList()

        val metadata = linkedMapOf(
            "spreadItemId" to spreadItemId.toString(),
            "documentId" to spreadItem.document.id.toString(),
            "statementDate" to spreadItem.statementDate.toString(),
        ).apply {
            spreadItem.auditMethod?.takeIf { it.isNotBlank() }?.let { put("auditMethod", it) }
            spreadItem.sourceCurrency?.takeIf { it.isNotBlank() }?.let { put("sourceCurrency", it) }
            spreadItem.consolidation?.takeIf { it.isNotBlank() }?.let { put("consolidation", it) }
        }

        return CanonicalSpreadPayload(
            customerId = spreadItem.customer.customerCode.ifBlank { spreadItem.customer.id.toString() },
            customerName = spreadItem.customer.name,
            period = spreadItem.statementDate.toString(),
            periodType = spreadItem.frequency,
            templateName = spreadItem.template.name,
            lineItems = lineItems,
            metadata = metadata,
        )
    }

    private fun canonicalSource(value: SpreadValue): CanonicalSource = when {
        value.manualOverride -> CanonicalSource.OVERRIDE
        !value.sourceText.isNullOrBlank() || value.confidenceScore != null -> CanonicalSource.AI
        else -> CanonicalSource.MANUAL
    }
}
