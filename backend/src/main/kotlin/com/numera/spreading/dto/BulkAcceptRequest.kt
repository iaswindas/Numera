package com.numera.spreading.dto

import jakarta.validation.constraints.NotBlank

data class BulkAcceptRequest(
    @field:NotBlank val confidenceThreshold: String,
    val itemCodes: List<String>? = null,
)

data class BulkAcceptResponse(
    val accepted: Int,
    val total: Int,
)