package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "risk_heatmap_entries")
class RiskHeatmapEntry : TenantAwareEntity() {

    @Column(nullable = false)
    var customerId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var customerName: String = ""

    @Column(nullable = false)
    var covenantId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var covenantName: String = ""

    @Column(nullable = false, precision = 6, scale = 4)
    var breachProbability: BigDecimal = BigDecimal.ZERO

    @Column
    var currentValue: BigDecimal? = null

    @Column
    var threshold: BigDecimal? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CovenantStatus = CovenantStatus.DUE

    @Column(nullable = false)
    var lastUpdated: Instant = Instant.now()
}
