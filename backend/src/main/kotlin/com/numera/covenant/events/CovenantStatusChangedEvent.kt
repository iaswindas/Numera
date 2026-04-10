package com.numera.covenant.events

import java.util.UUID

/** Published on every status transition of a monitoring item */
data class CovenantStatusChangedEvent(
    val tenantId: UUID,
    val monitoringItemId: UUID,
    val covenantId: UUID,
    val previousStatus: String,
    val newStatus: String,
    val actorId: UUID?,
)
