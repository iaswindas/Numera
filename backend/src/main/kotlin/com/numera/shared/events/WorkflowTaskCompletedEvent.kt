package com.numera.shared.events

import java.util.UUID

data class WorkflowTaskCompletedEvent(
    val tenantId: UUID,
    val instanceId: UUID,
    val taskId: UUID,
    val stepName: String,
    val outcome: String,
    val completedBy: String,
)
