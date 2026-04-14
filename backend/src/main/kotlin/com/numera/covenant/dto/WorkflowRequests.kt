package com.numera.covenant.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class TriggerActionRequest(
    @field:Size(max = 2000) val comments: String? = null,
)

data class CheckerDecisionRequest(
    @field:NotBlank @field:Size(max = 50) val decision: String,    // APPROVE or REJECT
    @field:Size(max = 2000) val comments: String? = null,
)
