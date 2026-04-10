package com.numera.covenant.dto

import java.math.BigDecimal
import java.util.UUID

data class CovenantRequest(
    val covenantCustomerId: UUID,
    val covenantType: String,
    val name: String,
    val description: String? = null,
    val frequency: String,
    // Financial fields
    val formula: String? = null,
    val operator: String? = null,
    val thresholdValue: BigDecimal? = null,
    val thresholdMin: BigDecimal? = null,
    val thresholdMax: BigDecimal? = null,
    // Non-financial fields
    val documentType: String? = null,
    val itemType: String? = null,
)
