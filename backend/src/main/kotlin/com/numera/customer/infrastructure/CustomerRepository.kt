package com.numera.customer.infrastructure

import com.numera.customer.domain.Customer
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByTenantId(tenantId: UUID): List<Customer>

    @Query(
        """
        select c from Customer c
        where c.tenantId = :tenantId
          and (cast(:query as string) is null or lower(c.name) like lower(concat('%', cast(:query as string), '%')) or lower(c.customerCode) like lower(concat('%', cast(:query as string), '%')))
          and (cast(:industry as string) is null or c.industry = :industry)
          and (cast(:country as string) is null or c.country = :country)
        """
    )
    fun search(
        @Param("tenantId") tenantId: UUID,
        @Param("query") query: String?,
        @Param("industry") industry: String?,
        @Param("country") country: String?,
    ): List<Customer>

    @Query(
        """
        select c from Customer c
        where c.tenantId = :tenantId
          and c.id in :customerIds
          and (cast(:query as string) is null or lower(c.name) like lower(concat('%', cast(:query as string), '%')) or lower(c.customerCode) like lower(concat('%', cast(:query as string), '%')))
          and (cast(:industry as string) is null or c.industry = :industry)
          and (cast(:country as string) is null or c.country = :country)
        """
    )
    fun searchWithVisibility(
        @Param("tenantId") tenantId: UUID,
        @Param("customerIds") customerIds: List<UUID>,
        @Param("query") query: String?,
        @Param("industry") industry: String?,
        @Param("country") country: String?,
    ): List<Customer>

    @Query(
        """
        select c from Customer c
        inner join com.numera.admin.domain.GroupCustomerAccess gca on c.id = gca.customerId
        inner join com.numera.admin.domain.UserGroup ug on gca.group.id = ug.id
        where c.tenantId = :tenantId
          and ug.id in :groupIds
        """
    )
    fun findAllByTenantIdAndGroupIdIn(
        @Param("tenantId") tenantId: UUID,
        @Param("groupIds") groupIds: List<UUID>,
        pageable: Pageable,
    ): Page<Customer>

    @Query(
        """
        select c from Customer c
        inner join com.numera.admin.domain.GroupCustomerAccess gca on c.id = gca.customerId
        inner join com.numera.admin.domain.UserGroup ug on gca.group.id = ug.id
        where c.id = :id
          and c.tenantId = :tenantId
          and ug.id in :groupIds
        """
    )
    fun findByIdAndTenantIdAndGroupIdIn(
        @Param("id") id: UUID,
        @Param("tenantId") tenantId: UUID,
        @Param("groupIds") groupIds: List<UUID>,
    ): Optional<Customer>
}