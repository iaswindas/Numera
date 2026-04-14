package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class WaiverType {
    INSTANCE, PERMANENT
}

/**
 * Persisted waiver letter generated for a covenant monitoring item.
 * Used for PDF generation and email delivery tracking.
 */
@Entity
@Table(name = "waiver_letters")
class WaiverLetter : TenantAwareEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "monitoring_item_id")
    lateinit var monitoringItem: CovenantMonitoringItem

    @Column(nullable = false)
    var waiverType: WaiverType = WaiverType.INSTANCE

    @Column(nullable = false)
    var waived: Boolean = false

    @Column(columnDefinition = "TEXT", nullable = false)
    var letterContent: String = ""

    @Column
    var templateId: UUID? = null

    @Column
    var signatureId: UUID? = null

    @Column(columnDefinition = "TEXT")
    var comments: String? = null

    @Column
    var generatedBy: UUID? = null

    @Column(nullable = false)
    var generatedAt: Instant = Instant.now()

    @Column
    var sentAt: Instant? = null

    @Column
    var sentBy: UUID? = null
}
