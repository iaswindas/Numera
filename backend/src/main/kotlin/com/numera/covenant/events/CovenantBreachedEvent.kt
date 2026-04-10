package com.numera.covenant.events

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Published when a covenant monitoring item transitions to BREACHED status */
data class CovenantBreachedEvent(
    val tenantId: UUID,
    val covenantId: UUID,
    val monitoringItemId: UUID,
    val covenantName: String,
    val customerName: String,
    val periodEnd: LocalDate,
    val calculatedValue: BigDecimal?,
    val thresholdValue: BigDecimal?,
)
