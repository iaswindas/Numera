package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "covenant_monitoring_items")
class CovenantMonitoringItem : TenantAwareEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "covenant_id")
    lateinit var covenant: Covenant

    @Column(nullable = false)
    var periodStart: LocalDate = LocalDate.now()

    @Column(nullable = false)
    var periodEnd: LocalDate = LocalDate.now()

    @Column(nullable = false)
    var dueDate: LocalDate = LocalDate.now()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CovenantStatus = CovenantStatus.DUE

    /** System-computed value from the linked spread formula */
    @Column
    var calculatedValue: BigDecimal? = null

    /** Analyst override with mandatory justification */
    @Column
    var manualValue: BigDecimal? = null

    @Column(columnDefinition = "TEXT")
    var manualValueJustification: String? = null

    // ── Non-financial workflow tracking ───────────────────────────────────

    @Column
    var submittedBy: UUID? = null

    @Column
    var submittedAt: Instant? = null

    @Column
    var approvedBy: UUID? = null

    @Column
    var approvedAt: Instant? = null

    @Column(columnDefinition = "TEXT")
    var checkerComments: String? = null

    // ── Predictive intelligence ───────────────────────────────────────────

    /** ML-computed probability [0.0, 1.0] that this covenant will breach */
    @Column
    var breachProbability: BigDecimal? = null

    @OneToMany(mappedBy = "monitoringItem", cascade = [CascadeType.ALL], orphanRemoval = true)
    var documents: MutableList<CovenantDocument> = mutableListOf()
}
