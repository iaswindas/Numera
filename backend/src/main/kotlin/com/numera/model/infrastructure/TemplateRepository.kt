package com.numera.model.infrastructure

import com.numera.model.domain.ModelTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TemplateRepository : JpaRepository<ModelTemplate, UUID> {
    fun findByTenantIdAndActiveTrue(tenantId: UUID): List<ModelTemplate>
    fun findByTenantIdAndCustomerIdAndActiveTrue(tenantId: UUID, customerId: UUID): List<ModelTemplate>
    fun findByIdAndCustomerId(templateId: UUID, customerId: UUID): ModelTemplate?
    fun findByTenantIdAndCustomerIdIsNull(tenantId: UUID): List<ModelTemplate>
}