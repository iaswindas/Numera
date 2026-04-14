package com.numera.covenant.infrastructure

import com.numera.covenant.domain.CovenantCustomer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CovenantCustomerRepository : JpaRepository<CovenantCustomer, UUID> {

    fun findByTenantId(tenantId: UUID): List<CovenantCustomer>

    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<CovenantCustomer>

    fun findByTenantIdAndCustomerId(tenantId: UUID, customerId: UUID): CovenantCustomer?

    @Query(
        """
        select cc from CovenantCustomer cc
        where cc.tenantId = :tenantId
          and (cast(:query as string) is null
               or lower(cc.customer.name) like lower(concat('%', cast(:query as string), '%'))
               or lower(cc.rimId) like lower(concat('%', cast(:query as string), '%')))
        """
    )
    fun search(
        @Param("tenantId") tenantId: UUID,
        @Param("query") query: String?,
    ): List<CovenantCustomer>
}
