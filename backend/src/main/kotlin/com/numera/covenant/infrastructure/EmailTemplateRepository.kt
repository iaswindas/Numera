package com.numera.covenant.infrastructure

import com.numera.covenant.domain.EmailTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EmailTemplateRepository : JpaRepository<EmailTemplate, UUID> {

    fun findByTenantId(tenantId: UUID): List<EmailTemplate>

    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<EmailTemplate>

    fun findByTenantIdAndCovenantType(tenantId: UUID, covenantType: String): List<EmailTemplate>

    fun findByTenantIdAndTemplateCategory(tenantId: UUID, templateCategory: String): List<EmailTemplate>
}
