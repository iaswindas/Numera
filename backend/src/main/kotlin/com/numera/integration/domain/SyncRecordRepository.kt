package com.numera.integration.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SyncRecordRepository : JpaRepository<SyncRecord, UUID> {

    fun findByIdempotencyKey(idempotencyKey: String): SyncRecord?

    fun findByStatusAndRetryCountLessThan(status: SyncStatus, maxRetries: Int): List<SyncRecord>

    fun findByExternalSystemIdAndEntityIdAndDirection(
        externalSystemId: UUID,
        entityId: UUID,
        direction: SyncDirection,
    ): List<SyncRecord>

    fun findByExternalSystemId(externalSystemId: UUID): List<SyncRecord>

    fun findByTenantId(tenantId: UUID): List<SyncRecord>
}
