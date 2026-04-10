package com.numera.covenant.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CovenantMonitoringItemResponse(
    val id: UUID,
    val covenantId: UUID,
    val covenantName: String,
    val covenantType: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val dueDate: LocalDate,
    val status: String,
    val calculatedValue: BigDecimal?,
    val manualValue: BigDecimal?,
    val manualValueJustification: String?,
    val submittedBy: UUID?,
    val submittedAt: Instant?,
    val approvedBy: UUID?,
    val approvedAt: Instant?,
    val checkerComments: String?,
    val breachProbability: BigDecimal?,
    val documentCount: Int,
    val updatedAt: Instant,
)
