package com.numera.shared.audit

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EventLogRepository : JpaRepository<AuditEvent, java.util.UUID> {
    fun findFirstByTenantIdOrderByCreatedAtDesc(tenantId: String): AuditEvent?
    fun findByTenantIdOrderByCreatedAtAsc(tenantId: String): List<AuditEvent>
    fun findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType: String, entityId: String): List<AuditEvent>

    @Query(
        """
        select e from AuditEvent e
        where (e.entityType = :entityType and e.entityId = :entityId)
           or (e.parentEntityType = :entityType and e.parentEntityId = :entityId)
        order by e.createdAt desc
        """
    )
    fun findByEntityOrParent(
        @Param("entityType") entityType: String,
        @Param("entityId") entityId: String,
    ): List<AuditEvent>
}
