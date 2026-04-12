package com.numera.shared.events

import java.util.UUID

/** Published on every status transition of a covenant monitoring item. Lives in shared.events
 *  so both covenant (publisher) and shared.notification (consumer) can use it without crossing
 *  module private-package boundaries. */
data class CovenantStatusChangedEvent(
    val tenantId: UUID,
    val monitoringItemId: UUID,
    val covenantId: UUID,
    val previousStatus: String,
    val newStatus: String,
    val actorId: UUID?,
)
