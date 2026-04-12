package com.numera.shared.events

import java.util.UUID

data class WorkflowStartedEvent(
    val tenantId: UUID,
    val instanceId: UUID,
    val definitionId: UUID,
    val entityType: String,
    val entityId: UUID,
    val startedBy: String,
)
