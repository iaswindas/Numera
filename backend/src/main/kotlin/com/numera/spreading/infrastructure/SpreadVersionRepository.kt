package com.numera.spreading.infrastructure

import com.numera.spreading.domain.SpreadVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SpreadVersionRepository : JpaRepository<SpreadVersion, UUID> {
    fun findBySpreadItemIdOrderByVersionNumberDesc(spreadItemId: UUID): List<SpreadVersion>
    fun findBySpreadItemIdAndVersionNumber(spreadItemId: UUID, versionNumber: Int): SpreadVersion?
}