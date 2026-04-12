package com.numera.integration.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalSystemRepository : JpaRepository<ExternalSystem, UUID> {

    fun findByTenantIdAndActiveTrue(tenantId: UUID): List<ExternalSystem>

    fun findByTenantId(tenantId: UUID): List<ExternalSystem>
}
