package com.numera.customer.application

import com.numera.customer.domain.Customer
import com.numera.customer.dto.CustomerRequest
import com.numera.customer.dto.CustomerResponse
import com.numera.customer.dto.CustomerSearchRequest
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val auditService: AuditService,
) {
    fun findAll(tenantId: UUID): List<CustomerResponse> =
        customerRepository.findByTenantId(tenantId).map { it.toResponse() }

    fun search(tenantId: UUID, request: CustomerSearchRequest): List<CustomerResponse> =
        customerRepository.search(tenantId, request.query, request.industry, request.country).map { it.toResponse() }

    fun findById(id: UUID): CustomerResponse =
        customerRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }.toResponse()

    @Transactional
    fun create(tenantId: UUID, request: CustomerRequest): CustomerResponse {
        val saved = customerRepository.save(Customer().also {
            it.tenantId = tenantId
            it.customerCode = request.customerCode
            it.name = request.name
            it.industry = request.industry
            it.country = request.country
            it.relationshipManager = request.relationshipManager
        })

        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "CUSTOMER_CREATED",
            action = AuditAction.CREATE,
            entityType = "customer",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: UUID, request: CustomerRequest): CustomerResponse {
        val customer = customerRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }
        customer.customerCode = request.customerCode
        customer.name = request.name
        customer.industry = request.industry
        customer.country = request.country
        customer.relationshipManager = request.relationshipManager

        val saved = customerRepository.save(customer)
        auditService.record(
            tenantId = customer.tenantId.toString(),
            eventType = "CUSTOMER_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "customer",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val customer = customerRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }
        customerRepository.delete(customer)
        auditService.record(
            tenantId = customer.tenantId.toString(),
            eventType = "CUSTOMER_DELETED",
            action = AuditAction.DELETE,
            entityType = "customer",
            entityId = id.toString(),
        )
    }

    private fun Customer.toResponse() = CustomerResponse(
        id = id.toString(),
        tenantId = tenantId.toString(),
        customerCode = customerCode,
        name = name,
        industry = industry,
        country = country,
        relationshipManager = relationshipManager,
    )
}