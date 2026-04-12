package com.numera.covenant.infrastructure

import com.numera.covenant.domain.RiskHeatmapEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RiskHeatmapRepository : JpaRepository<RiskHeatmapEntry, UUID> {

    fun findByTenantId(tenantId: UUID): List<RiskHeatmapEntry>

    fun findByTenantIdAndCustomerId(tenantId: UUID, customerId: UUID): List<RiskHeatmapEntry>

    fun findByTenantIdAndCovenantId(tenantId: UUID, covenantId: UUID): List<RiskHeatmapEntry>

    @Modifying
    @Query("DELETE FROM RiskHeatmapEntry r WHERE r.tenantId = :tenantId AND r.customerId = :customerId")
    fun deleteByTenantIdAndCustomerId(
        @Param("tenantId") tenantId: UUID,
        @Param("customerId") customerId: UUID,
    )
}
