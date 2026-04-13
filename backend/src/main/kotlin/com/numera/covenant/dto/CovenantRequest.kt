package com.numera.covenant.dto

import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CovenantRequest(
    @field:NotNull val covenantCustomerId: UUID,
    @field:NotBlank @field:Size(max = 50) val covenantType: String,
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    @field:NotBlank @field:Size(max = 50) val frequency: String,
    // Financial fields
    @field:Size(max = 2000) val formula: String? = null,
    @field:Size(max = 50) val operator: String? = null,
    @field:Digits(integer = 15, fraction = 6) val thresholdValue: BigDecimal? = null,
    @field:Digits(integer = 15, fraction = 6) val thresholdMin: BigDecimal? = null,
    @field:Digits(integer = 15, fraction = 6) val thresholdMax: BigDecimal? = null,
    // Non-financial fields
    @field:Size(max = 50) val documentType: String? = null,
    @field:Size(max = 50) val itemType: String? = null,
    // Audit & Compliance
    @field:Size(max = 50) val auditMethod: String? = null,
    // Reminder Configuration
    val reminderDaysBefore: Int = 7,
    val reminderDaysAfter: Int = 3,
)
