package com.numera.spreading.events

import java.util.UUID

data class SpreadSubmittedEvent(
    val spreadItemId: UUID,
    val tenantId: UUID,
)