package com.numera.covenant.dto

import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class ManualValueRequest(
    @field:NotNull @field:Digits(integer = 15, fraction = 6) val value: BigDecimal,
    @field:NotBlank @field:Size(max = 2000) val justification: String,
)
