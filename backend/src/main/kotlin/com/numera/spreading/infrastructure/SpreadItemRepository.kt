package com.numera.spreading.infrastructure

import com.numera.spreading.domain.SpreadItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SpreadItemRepository : JpaRepository<SpreadItem, UUID> {
    fun findByCustomerId(customerId: UUID): List<SpreadItem>
    fun findByTenantId(tenantId: UUID): List<SpreadItem>
    fun findTopByCustomerIdAndTemplateIdAndIdNotOrderByStatementDateDesc(
        customerId: UUID,
        templateId: UUID,
        excludeId: UUID,
    ): SpreadItem?
}