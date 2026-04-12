package com.numera.workflow.infrastructure

import com.numera.workflow.domain.InstanceStatus
import com.numera.workflow.domain.WorkflowInstance
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowInstanceRepository : JpaRepository<WorkflowInstance, UUID> {

    fun findByEntityTypeAndEntityId(entityType: String, entityId: UUID): List<WorkflowInstance>

    fun findByStatusAndTenantId(status: InstanceStatus, tenantId: UUID): List<WorkflowInstance>
}
