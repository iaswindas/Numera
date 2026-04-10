package com.numera.covenant.infrastructure

import com.numera.covenant.domain.Covenant
import com.numera.covenant.domain.CovenantType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CovenantRepository : JpaRepository<Covenant, UUID> {

    fun findByCovenantCustomerId(covenantCustomerId: UUID): List<Covenant>

    fun findByCovenantCustomerIdAndIsActiveTrue(covenantCustomerId: UUID): List<Covenant>

    fun findByTenantIdAndCovenantType(tenantId: UUID, covenantType: CovenantType): List<Covenant>
}
