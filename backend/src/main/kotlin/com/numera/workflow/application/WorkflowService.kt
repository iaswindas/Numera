package com.numera.workflow.application

import com.numera.shared.config.FeatureFlagService
import com.numera.shared.events.WorkflowCompletedEvent
import com.numera.shared.events.WorkflowStartedEvent
import com.numera.shared.events.WorkflowTaskCompletedEvent
import com.numera.shared.security.TenantContext
import com.numera.workflow.domain.InstanceStatus
import com.numera.workflow.domain.TaskStatus
import com.numera.workflow.domain.WorkflowInstance
import com.numera.workflow.domain.WorkflowTask
import com.numera.workflow.infrastructure.WorkflowDefinitionRepository
import com.numera.workflow.infrastructure.WorkflowInstanceRepository
import com.numera.workflow.infrastructure.WorkflowTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkflowService(
    private val definitionRepo: WorkflowDefinitionRepository,
    private val instanceRepo: WorkflowInstanceRepository,
    private val taskRepo: WorkflowTaskRepository,
    private val featureFlags: FeatureFlagService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(WorkflowService::class.java)

    private fun isEnabled(): Boolean =
        featureFlags.isEnabled("workflowEngine", TenantContext.get())

    @Transactional
    fun startWorkflow(
        definitionId: UUID,
        entityType: String,
        entityId: UUID,
        startedBy: String,
    ): WorkflowInstance {
        val definition = definitionRepo.findById(definitionId)
            .orElseThrow { IllegalArgumentException("Workflow definition not found: $definitionId") }

        if (!isEnabled()) {
            log.info("Workflow engine disabled — creating pass-through instance for {} {}", entityType, entityId)
        }

        val instance = WorkflowInstance().apply {
            this.definition = definition
            this.entityType = entityType
            this.entityId = entityId
            this.status = InstanceStatus.ACTIVE
            this.currentStepOrder = 0
            this.startedAt = Instant.now()
            this.startedBy = startedBy
            this.tenantId = definition.tenantId
        }

        val saved = instanceRepo.save(instance)

        if (isEnabled()) {
            createNextTask(saved)
        } else {
            saved.status = InstanceStatus.COMPLETED
            saved.completedAt = Instant.now()
            instanceRepo.save(saved)
        }

        eventPublisher.publishEvent(
            WorkflowStartedEvent(
                tenantId = saved.tenantId,
                instanceId = saved.id!!,
                definitionId = definitionId,
                entityType = entityType,
                entityId = entityId,
                startedBy = startedBy,
            )
        )

        log.info("Workflow instance {} started for {} {}", saved.id, entityType, entityId)
        return saved
    }

    @Transactional
    fun advanceWorkflow(
        instanceId: UUID,
        taskId: UUID,
        outcome: String,
        comment: String?,
        completedBy: String,
    ): WorkflowInstance {
        val instance = instanceRepo.findById(instanceId)
            .orElseThrow { IllegalArgumentException("Workflow instance not found: $instanceId") }

        val task = taskRepo.findById(taskId)
            .orElseThrow { IllegalArgumentException("Workflow task not found: $taskId") }

        require(task.instance?.id == instanceId) { "Task does not belong to instance" }
        require(task.status == TaskStatus.PENDING || task.status == TaskStatus.IN_PROGRESS) {
            "Task is not actionable: ${task.status}"
        }

        task.status = TaskStatus.COMPLETED
        task.outcome = outcome
        task.comment = comment
        task.completedAt = Instant.now()
        task.completedBy = completedBy
        taskRepo.save(task)

        eventPublisher.publishEvent(
            WorkflowTaskCompletedEvent(
                tenantId = instance.tenantId,
                instanceId = instanceId,
                taskId = taskId,
                stepName = task.stepName,
                outcome = outcome,
                completedBy = completedBy,
            )
        )

        val definition = instance.definition!!
        val nextStepOrder = task.stepOrder + 1
        val hasNextStep = definition.steps.any { it.stepOrder == nextStepOrder }

        if (hasNextStep) {
            instance.currentStepOrder = nextStepOrder
            createNextTask(instance)
        } else {
            instance.status = InstanceStatus.COMPLETED
            instance.completedAt = Instant.now()
            eventPublisher.publishEvent(
                WorkflowCompletedEvent(
                    tenantId = instance.tenantId,
                    instanceId = instanceId,
                    definitionId = definition.id!!,
                    entityType = instance.entityType,
                    entityId = instance.entityId,
                    finalOutcome = outcome,
                )
            )
            log.info("Workflow instance {} completed with outcome {}", instanceId, outcome)
        }

        return instanceRepo.save(instance)
    }

    @Transactional(readOnly = true)
    fun getActiveInstance(entityType: String, entityId: UUID): WorkflowInstance? {
        return instanceRepo.findByEntityTypeAndEntityId(entityType, entityId)
            .firstOrNull { it.status == InstanceStatus.ACTIVE }
    }

    @Transactional(readOnly = true)
    fun getPendingTasks(role: String, tenantId: UUID): List<WorkflowTask> {
        return taskRepo.findByAssignedRoleAndStatus(role, TaskStatus.PENDING)
            .filter { it.instance?.tenantId == tenantId }
    }

    @Transactional
    fun cancelWorkflow(instanceId: UUID, cancelledBy: String) {
        val instance = instanceRepo.findById(instanceId)
            .orElseThrow { IllegalArgumentException("Workflow instance not found: $instanceId") }

        require(instance.status == InstanceStatus.ACTIVE) {
            "Cannot cancel workflow in status: ${instance.status}"
        }

        instance.status = InstanceStatus.CANCELLED
        instance.completedAt = Instant.now()

        val pendingTasks = taskRepo.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING)
        pendingTasks.forEach { task ->
            task.status = TaskStatus.SKIPPED
            task.completedAt = Instant.now()
            task.completedBy = cancelledBy
        }
        taskRepo.saveAll(pendingTasks)
        instanceRepo.save(instance)

        log.info("Workflow instance {} cancelled by {}", instanceId, cancelledBy)
    }

    private fun createNextTask(instance: WorkflowInstance) {
        val definition = instance.definition!!
        val stepDef = definition.steps.firstOrNull { it.stepOrder == instance.currentStepOrder }
            ?: return

        val task = WorkflowTask().apply {
            this.instance = instance
            this.stepOrder = stepDef.stepOrder
            this.stepName = stepDef.name
            this.assignedRole = stepDef.requiredRole
            this.status = TaskStatus.PENDING
            if (stepDef.slaHours != null) {
                this.dueAt = Instant.now().plusSeconds(stepDef.slaHours!!.toLong() * 3600)
            }
        }

        val saved = taskRepo.save(task)
        instance.tasks.add(saved)
    }
}
