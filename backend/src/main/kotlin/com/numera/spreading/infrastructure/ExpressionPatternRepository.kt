package com.numera.spreading.infrastructure

import com.numera.spreading.domain.ExpressionPattern
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExpressionPatternRepository : JpaRepository<ExpressionPattern, UUID> {
    fun findByTenantIdAndCustomerIdAndTemplateIdAndItemCode(
        tenantId: UUID,
        customerId: UUID,
        templateId: UUID,
        itemCode: String,
    ): ExpressionPattern?

    fun findByTenantIdAndCustomerIdAndTemplateId(
        tenantId: UUID,
        customerId: UUID,
        templateId: UUID,
    ): List<ExpressionPattern>
}