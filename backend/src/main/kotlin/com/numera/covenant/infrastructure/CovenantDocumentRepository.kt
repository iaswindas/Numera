package com.numera.covenant.infrastructure

import com.numera.covenant.domain.CovenantDocument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CovenantDocumentRepository : JpaRepository<CovenantDocument, UUID> {

    fun findByMonitoringItemId(monitoringItemId: UUID): List<CovenantDocument>
}
