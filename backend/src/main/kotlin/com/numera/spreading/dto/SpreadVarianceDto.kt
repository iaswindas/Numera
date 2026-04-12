package com.numera.spreading.dto

import java.math.BigDecimal

data class SpreadVarianceDto(
    val lineItemId: String,
    val lineItemCode: String,
    val lineItemLabel: String,
    val currentValue: BigDecimal?,
    val compareValue: BigDecimal?,
    val absoluteChange: BigDecimal?,
    val percentageChange: BigDecimal?,
)
