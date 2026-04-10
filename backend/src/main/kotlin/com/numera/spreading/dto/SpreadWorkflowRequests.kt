package com.numera.spreading.dto

import jakarta.validation.constraints.NotBlank

data class SubmitSpreadRequest(
    val comments: String? = null,
    val overrideValidationWarnings: Boolean = false,
)

data class SubmitSpreadResponse(
    val status: String,
    val version: Int,
    val validations: List<MappingValidation>,
)

data class RollbackRequest(
    @field:NotBlank val comments: String,
)