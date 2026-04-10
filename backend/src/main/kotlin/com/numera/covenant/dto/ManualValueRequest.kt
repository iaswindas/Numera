package com.numera.covenant.dto

import java.math.BigDecimal

data class ManualValueRequest(
    val value: BigDecimal,
    val justification: String,
)
