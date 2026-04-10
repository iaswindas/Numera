package com.numera.customer.dto

import jakarta.validation.constraints.NotBlank

data class CustomerRequest(
    @field:NotBlank val customerCode: String,
    @field:NotBlank val name: String,
    val industry: String? = null,
    val country: String? = null,
    val relationshipManager: String? = null,
)