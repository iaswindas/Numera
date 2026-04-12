package com.numera.workflow.infrastructure

import com.numera.workflow.domain.TaskStatus
import com.numera.workflow.domain.WorkflowTask
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface WorkflowTaskRepository : JpaRepository<WorkflowTask, UUID> {

    fun findByInstanceIdAndStatus(instanceId: UUID, status: TaskStatus): List<WorkflowTask>

    fun findByAssignedRoleAndStatus(role: String, status: TaskStatus): List<WorkflowTask>

    fun findByDueAtBeforeAndStatusIn(dueAt: Instant, statuses: List<TaskStatus>): List<WorkflowTask>
}
