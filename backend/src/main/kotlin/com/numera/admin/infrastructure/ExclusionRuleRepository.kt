package com.numera.admin.infrastructure

import com.numera.admin.domain.ExclusionRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExclusionRuleRepository : JpaRepository<ExclusionRule, UUID> {
    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<ExclusionRule>
    fun findByTenantIdAndCategory(tenantId: UUID, category: String): List<ExclusionRule>
    fun findByTenantId(tenantId: UUID): List<ExclusionRule>
}
