package com.numera.shared.exception

import java.time.Instant

data class ErrorResponse(
    val error: ErrorCode,
    val message: String,
    val validations: List<Map<String, Any?>>? = null,
    val unmappedRequired: List<String>? = null,
    val timestamp: Instant = Instant.now(),
)
