package com.numera.admin.infrastructure

import com.numera.admin.domain.TaxonomyEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface TaxonomyEntryRepository : JpaRepository<TaxonomyEntry, UUID> {
    fun findByTenantIdAndLanguage(tenantId: UUID, language: String): List<TaxonomyEntry>
    fun findByTenantId(tenantId: UUID): List<TaxonomyEntry>
    fun findByTenantIdAndItemCodeAndLanguage(tenantId: UUID, itemCode: String, language: String): Optional<TaxonomyEntry>
    fun findByTenantIdAndCategory(tenantId: UUID, category: String): List<TaxonomyEntry>
}
