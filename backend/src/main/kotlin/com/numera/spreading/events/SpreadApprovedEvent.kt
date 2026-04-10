package com.numera.spreading.events

import java.util.UUID

data class SpreadApprovedEvent(
    val spreadItemId: UUID,
    val tenantId: UUID,
)