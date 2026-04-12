package com.numera.spreading.events

import java.util.UUID

data class SpreadRejectedEvent(
    val spreadItemId: UUID,
    val tenantId: UUID,
)
