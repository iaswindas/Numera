package com.numera.spreading.dto

import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class SpreadValueUpdateRequest(
    @field:NotNull @field:Digits(integer = 15, fraction = 6) val mappedValue: BigDecimal,
    @field:Size(max = 2000) val overrideComment: String? = null,
    @field:Size(max = 50) val expressionType: String = "MANUAL",
)