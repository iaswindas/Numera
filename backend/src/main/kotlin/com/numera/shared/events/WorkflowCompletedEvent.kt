package com.numera.shared.events

import java.util.UUID

data class WorkflowCompletedEvent(
    val tenantId: UUID,
    val instanceId: UUID,
    val definitionId: UUID,
    val entityType: String,
    val entityId: UUID,
    val finalOutcome: String,
)
