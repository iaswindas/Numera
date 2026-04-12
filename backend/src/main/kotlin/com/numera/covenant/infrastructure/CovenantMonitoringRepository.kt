package com.numera.covenant.infrastructure

import com.numera.covenant.domain.CovenantMonitoringItem
import com.numera.covenant.domain.CovenantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface CovenantMonitoringRepository : JpaRepository<CovenantMonitoringItem, UUID> {

    fun findByCovenantId(covenantId: UUID): List<CovenantMonitoringItem>

    fun findByTenantId(tenantId: UUID): List<CovenantMonitoringItem>

    fun findByTenantIdAndStatus(tenantId: UUID, status: CovenantStatus): List<CovenantMonitoringItem>

    fun findByTenantIdAndStatusIn(tenantId: UUID, statuses: List<CovenantStatus>): List<CovenantMonitoringItem>

    fun findByStatusIn(statuses: List<CovenantStatus>): List<CovenantMonitoringItem>

    /** Items past due date and not yet closed */
    @Query(
        """
        select m from CovenantMonitoringItem m
        where m.tenantId = :tenantId
          and m.dueDate < :today
          and m.status in ('DUE', 'SUBMITTED')
        """
    )
    fun findOverdue(
        @Param("tenantId") tenantId: UUID,
        @Param("today") today: LocalDate,
    ): List<CovenantMonitoringItem>

    /** Count items by status for dashboard aggregation */
    @Query(
        """
        select m.status, count(m) from CovenantMonitoringItem m
        where m.tenantId = :tenantId
        group by m.status
        """
    )
    fun countByStatus(@Param("tenantId") tenantId: UUID): List<Array<Any>>
}
