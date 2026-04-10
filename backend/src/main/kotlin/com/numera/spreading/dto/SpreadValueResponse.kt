package com.numera.spreading.dto

import java.math.BigDecimal

data class SpreadValueResponse(
    val id: String,
    val itemCode: String,
    val label: String,
    val mappedValue: BigDecimal?,
    val rawValue: BigDecimal?,
    val expressionType: String?,
    val expressionDetail: Map<String, Any>?,
    val scaleFactor: BigDecimal?,
    val confidenceScore: BigDecimal?,
    val confidenceLevel: String?,
    val sourcePage: Int?,
    val sourceText: String?,
    val isManualOverride: Boolean,
    val isAutofilled: Boolean,
    val isFormulaCell: Boolean,
)