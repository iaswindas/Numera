package com.numera.integration.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class SyncDirection {
    PUSH,
    PULL,
}

enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CONFLICT,
}

@Entity
@Table(name = "sync_records")
class SyncRecord : TenantAwareEntity() {

    @Column(nullable = false)
    var externalSystemId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var entityId: UUID = UUID.randomUUID()

    @Column
    var externalId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var direction: SyncDirection = SyncDirection.PUSH

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SyncStatus = SyncStatus.PENDING

    @Column(nullable = false, unique = true)
    var idempotencyKey: String = UUID.randomUUID().toString()

    @Column(nullable = false)
    var retryCount: Int = 0

    @Column(nullable = false)
    var maxRetries: Int = 3

    @Column(columnDefinition = "TEXT")
    var lastError: String? = null

    @Column
    var syncedAt: Instant? = null
}
