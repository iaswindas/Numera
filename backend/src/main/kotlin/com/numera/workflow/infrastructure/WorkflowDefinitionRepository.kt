package com.numera.workflow.infrastructure

import com.numera.workflow.domain.WorkflowDefinition
import com.numera.workflow.domain.WorkflowType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowDefinitionRepository : JpaRepository<WorkflowDefinition, UUID> {

    fun findByTypeAndActiveTrue(type: WorkflowType): WorkflowDefinition?

    fun findByTenantIdAndType(tenantId: UUID, type: WorkflowType): List<WorkflowDefinition>
}
