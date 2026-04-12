package com.numera.portfolio.infrastructure

import com.numera.portfolio.domain.SharedDashboard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SharedDashboardRepository : JpaRepository<SharedDashboard, UUID> {
    fun findByToken(token: String): SharedDashboard?
    fun findByTenantIdAndCreatedBy(tenantId: UUID, createdBy: UUID): List<SharedDashboard>
}
