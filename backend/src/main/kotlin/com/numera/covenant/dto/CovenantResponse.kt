package com.numera.covenant.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CovenantResponse(
    val id: UUID,
    val covenantCustomerId: UUID,
    val covenantCustomerName: String,
    val covenantType: String,
    val name: String,
    val description: String?,
    val frequency: String,
    val formula: String?,
    val operator: String?,
    val thresholdValue: BigDecimal?,
    val thresholdMin: BigDecimal?,
    val thresholdMax: BigDecimal?,
    val documentType: String?,
    val itemType: String?,
    val auditMethod: String?,
    val reminderDaysBefore: Int,
    val reminderDaysAfter: Int,
    val isActive: Boolean,
    val createdAt: Instant,
)
