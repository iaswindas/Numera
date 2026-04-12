package com.numera.shared.audit

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "event_log")
class AuditEvent : BaseEntity() {
    @Column(nullable = false)
    var eventType: String = ""

    @Column(nullable = false)
    var action: String = ""

    @Column(nullable = false)
    var actorEmail: String = "system@numera.local"

    @Column(nullable = false)
    var tenantId: String = ""

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var entityId: String = ""

    @Column
    var parentEntityType: String? = null

    @Column
    var parentEntityId: String? = null

    @Column(columnDefinition = "text")
    var diffJson: String? = null

    @Column(nullable = false, length = 128)
    var previousHash: String = ""

    @Column(nullable = false, length = 128)
    var currentHash: String = ""

    // ZK-RFA fields (nullable — populated only when zkRfaAudit feature flag is enabled)
    @Column(columnDefinition = "text")
    var chameleonRandomness: String? = null

    @Column
    var mmrIndex: Long? = null

    @Column(length = 128)
    var mmrRoot: String? = null

    @Column(columnDefinition = "text")
    var mmrProofJson: String? = null
}