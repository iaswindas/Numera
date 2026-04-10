package com.numera.covenant.dto

import java.util.UUID

data class TriggerActionRequest(
    val comments: String? = null,
)

data class CheckerDecisionRequest(
    val decision: String,    // APPROVE or REJECT
    val comments: String? = null,
)
