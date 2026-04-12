package com.numera.workflow.api

import com.numera.shared.security.TenantContext
import com.numera.workflow.application.WorkflowService
import com.numera.workflow.domain.WorkflowDefinition
import com.numera.workflow.domain.WorkflowInstance
import com.numera.workflow.domain.WorkflowTask
import com.numera.workflow.infrastructure.WorkflowDefinitionRepository
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val workflowService: WorkflowService,
    private val definitionRepo: WorkflowDefinitionRepository,
) {

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    fun listDefinitions(): List<WorkflowDefinition> {
        val tenantId = UUID.fromString(TenantContext.get() ?: return emptyList())
        return definitionRepo.findAll().filter { it.tenantId == tenantId }
    }

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun createDefinition(@RequestBody definition: WorkflowDefinition): WorkflowDefinition {
        val tenantId = TenantContext.get()
        if (tenantId != null) {
            definition.tenantId = UUID.fromString(tenantId)
        }
        return definitionRepo.save(definition)
    }

    @GetMapping("/instances/{entityType}/{entityId}")
    fun getActiveInstance(
        @PathVariable entityType: String,
        @PathVariable entityId: UUID,
    ): WorkflowInstance? {
        return workflowService.getActiveInstance(entityType, entityId)
    }

    @PostMapping("/instances/{instanceId}/tasks/{taskId}/complete")
    fun completeTask(
        @PathVariable instanceId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: CompleteTaskRequest,
    ): WorkflowInstance {
        return workflowService.advanceWorkflow(
            instanceId = instanceId,
            taskId = taskId,
            outcome = request.outcome,
            comment = request.comment,
            completedBy = request.completedBy,
        )
    }

    @GetMapping("/tasks/pending")
    fun getPendingTasks(@RequestParam role: String): List<WorkflowTask> {
        val tenantId = UUID.fromString(TenantContext.get() ?: return emptyList())
        return workflowService.getPendingTasks(role, tenantId)
    }

    @DeleteMapping("/instances/{instanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelWorkflow(
        @PathVariable instanceId: UUID,
        @RequestParam cancelledBy: String,
    ) {
        workflowService.cancelWorkflow(instanceId, cancelledBy)
    }
}

data class CompleteTaskRequest(
    val outcome: String,
    val comment: String? = null,
    val completedBy: String,
)
