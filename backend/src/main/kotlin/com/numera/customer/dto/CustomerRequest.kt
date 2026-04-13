package com.numera.customer.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomerRequest(
    @field:NotBlank @field:Size(max = 50) val customerCode: String,
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 200) val industry: String? = null,
    @field:Size(max = 200) val country: String? = null,
    @field:Size(max = 200) val relationshipManager: String? = null,
)