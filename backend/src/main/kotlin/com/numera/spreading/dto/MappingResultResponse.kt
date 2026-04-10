package com.numera.spreading.dto

import java.math.BigDecimal

data class MappingSummary(
    val totalItems: Int,
    val mapped: Int,
    val highConfidence: Int,
    val mediumConfidence: Int,
    val lowConfidence: Int,
    val unmapped: Int,
    val formulaComputed: Int,
    val autofilled: Int,
    val coveragePct: BigDecimal,
)

data class MappingValidation(
    val name: String,
    val status: String,
    val difference: BigDecimal,
    val severity: String? = null,
)

data class MappingResultResponse(
    val spreadItemId: String,
    val processingTimeMs: Long,
    val summary: MappingSummary,
    val unitScale: BigDecimal,
    val validations: List<MappingValidation>,
    val values: List<SpreadValueResponse>,
)
