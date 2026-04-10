package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "covenants")
class Covenant : TenantAwareEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "covenant_customer_id")
    lateinit var covenantCustomer: CovenantCustomer

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var covenantType: CovenantType = CovenantType.FINANCIAL

    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var frequency: CovenantFrequency = CovenantFrequency.QUARTERLY

    // ── Financial covenant fields ──────────────────────────────────────────

    /** e.g. "{Total_Debt} / {EBITDA}" — references model line item codes */
    @Column(columnDefinition = "TEXT")
    var formula: String? = null

    @Enumerated(EnumType.STRING)
    @Column
    var operator: CovenantThresholdOperator? = null

    @Column
    var thresholdValue: BigDecimal? = null

    @Column
    var thresholdMin: BigDecimal? = null

    @Column
    var thresholdMax: BigDecimal? = null

    // ── Non-financial covenant fields ──────────────────────────────────────

    /** e.g. "FINANCIAL_STATEMENT", "INSURANCE_CERTIFICATE" */
    @Column
    var documentType: String? = null

    /** Free-form item type label */
    @Column
    var itemType: String? = null

    // ── Common ────────────────────────────────────────────────────────────

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    var createdBy: UUID? = null
}
