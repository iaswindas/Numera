package com.numera.admin.infrastructure

import com.numera.admin.domain.ManagedZone
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ManagedZoneRepository : JpaRepository<ManagedZone, UUID> {
    fun findByTenantIdOrderBySortOrderAsc(tenantId: UUID): List<ManagedZone>
    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<ManagedZone>
}
