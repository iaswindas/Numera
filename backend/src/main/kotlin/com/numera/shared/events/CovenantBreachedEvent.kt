package com.numera.shared.events

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Published when a covenant monitoring item transitions to BREACHED status. Lives in shared.events
 *  so both the covenant module (publisher) and shared.notification (consumer) can use it without
 *  forming a cross-module private-package dependency. */
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
