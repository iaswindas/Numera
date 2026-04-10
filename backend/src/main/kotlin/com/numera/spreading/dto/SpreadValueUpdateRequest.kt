package com.numera.spreading.dto

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class SpreadValueUpdateRequest(
    @field:NotNull val mappedValue: BigDecimal,
    val overrideComment: String? = null,
    val expressionType: String = "MANUAL",
)