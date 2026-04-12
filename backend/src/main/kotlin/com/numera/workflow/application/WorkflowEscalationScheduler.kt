package com.numera.workflow.application

import com.numera.shared.config.FeatureFlagService
import com.numera.shared.security.TenantContext
import com.numera.workflow.domain.TaskStatus
import com.numera.workflow.infrastructure.WorkflowTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class WorkflowEscalationScheduler(
    private val taskRepo: WorkflowTaskRepository,
    private val workflowService: WorkflowService,
    private val featureFlags: FeatureFlagService,
) {
    private val log = LoggerFactory.getLogger(WorkflowEscalationScheduler::class.java)

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun checkOverdueTasks() {
        if (!featureFlags.isEnabled("workflowEngine", TenantContext.get())) {
            return
        }

        val overdueTasks = taskRepo.findByDueAtBeforeAndStatusIn(
            Instant.now(),
            listOf(TaskStatus.PENDING, TaskStatus.IN_PROGRESS),
        )

        for (task in overdueTasks) {
            val instance = task.instance ?: continue
            val definition = instance.definition ?: continue
            val stepDef = definition.steps.firstOrNull { it.stepOrder == task.stepOrder }

            if (stepDef == null) {
                log.warn("No step definition found for task {} step order {}", task.id, task.stepOrder)
                continue
            }

            if (stepDef.autoApproveOnSlaExpiry) {
                log.info("Auto-approving overdue task {} for instance {}", task.id, instance.id)
                workflowService.advanceWorkflow(
                    instanceId = instance.id!!,
                    taskId = task.id!!,
                    outcome = "AUTO_APPROVED",
                    comment = "Automatically approved due to SLA expiry",
                    completedBy = "SYSTEM",
                )
            } else if (stepDef.escalateTo != null) {
                log.info("Escalating overdue task {} to role {}", task.id, stepDef.escalateTo)
                task.status = TaskStatus.ESCALATED
                task.escalatedAt = Instant.now()
                task.assignedRole = stepDef.escalateTo
                taskRepo.save(task)
            } else {
                log.info("Timing out overdue task {} (no escalation configured)", task.id)
                task.status = TaskStatus.TIMED_OUT
                taskRepo.save(task)
            }
        }
    }
}
