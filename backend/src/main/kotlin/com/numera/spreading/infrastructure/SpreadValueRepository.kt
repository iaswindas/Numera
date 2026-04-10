package com.numera.spreading.infrastructure

import com.numera.spreading.domain.SpreadValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SpreadValueRepository : JpaRepository<SpreadValue, UUID> {
    fun findBySpreadItemId(spreadItemId: UUID): List<SpreadValue>
    fun findBySpreadItemIdAndConfidenceLevelIn(spreadItemId: UUID, confidenceLevels: Collection<String>): List<SpreadValue>
}