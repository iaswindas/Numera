package com.numera.covenant.infrastructure

import com.numera.covenant.domain.Signature
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SignatureRepository : JpaRepository<Signature, UUID> {

    fun findByTenantId(tenantId: UUID): List<Signature>

    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<Signature>
}
