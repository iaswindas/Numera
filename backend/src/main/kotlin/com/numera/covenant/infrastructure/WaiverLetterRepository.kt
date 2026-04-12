package com.numera.covenant.infrastructure

import com.numera.covenant.domain.WaiverLetter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WaiverLetterRepository : JpaRepository<WaiverLetter, UUID> {

    fun findByMonitoringItemId(monitoringItemId: UUID): List<WaiverLetter>

    fun findByTenantId(tenantId: UUID): List<WaiverLetter>

    fun findByMonitoringItemIdAndTenantId(monitoringItemId: UUID, tenantId: UUID): List<WaiverLetter>
}
