package com.numera.customer.infrastructure

import com.numera.customer.domain.Customer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByTenantId(tenantId: UUID): List<Customer>

    @Query(
        """
        select c from Customer c
        where c.tenantId = :tenantId
          and (:query is null or lower(c.name) like lower(concat('%', :query, '%')) or lower(c.customerCode) like lower(concat('%', :query, '%')))
          and (:industry is null or c.industry = :industry)
          and (:country is null or c.country = :country)
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
          and (:query is null or lower(c.name) like lower(concat('%', :query, '%')) or lower(c.customerCode) like lower(concat('%', :query, '%')))
          and (:industry is null or c.industry = :industry)
          and (:country is null or c.country = :country)
        """
    )
    fun searchWithVisibility(
        @Param("tenantId") tenantId: UUID,
        @Param("customerIds") customerIds: List<UUID>,
        @Param("query") query: String?,
        @Param("industry") industry: String?,
        @Param("country") country: String?,
    ): List<Customer>
}