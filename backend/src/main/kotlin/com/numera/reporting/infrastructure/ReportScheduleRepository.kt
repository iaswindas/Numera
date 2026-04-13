package com.numera.reporting.infrastructure

import com.numera.reporting.domain.ReportSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ReportScheduleRepository : JpaRepository<ReportSchedule, UUID> {
    fun findByEnabledTrueAndNextRunAtBefore(now: Instant): List<ReportSchedule>
    fun findByTenantId(tenantId: UUID): List<ReportSchedule>
}
