package com.numera.customer.application

import com.numera.customer.domain.Customer
import com.numera.customer.dto.CustomerRequest
import com.numera.customer.dto.CustomerResponse
import com.numera.customer.dto.CustomerSearchRequest
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.admin.UserGroupPort
import com.numera.auth.UserLookupFacade
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val auditService: AuditService,
    private val userGroupPort: UserGroupPort,
    private val userLookupFacade: UserLookupFacade,
) {
    private fun getCurrentUserIdAndRoles(): Pair<UUID?, List<String>> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated) {
            return Pair(null, emptyList())
        }

        val email = auth.name
        val userId = userLookupFacade.findIdByEmail(email)
        val roles = userLookupFacade.findRolesByEmail(email)
        return Pair(userId, roles)
    }

    private fun isAdminRole(roles: List<String>): Boolean {
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_GLOBAL_MANAGER")
    }

    @Transactional(readOnly = true)
    fun findAll(tenantId: UUID, userId: UUID? = null): List<CustomerResponse> {
        val (currentUserId, currentRoles) = getCurrentUserIdAndRoles()
        
        // Skip group filtering for admin/global manager
        val visibleCustomerIds = if (isAdminRole(currentRoles)) {
            null // No filtering
        } else {
            val gId = userId ?: currentUserId
            gId?.let { userGroupPort.getVisibleCustomerIds(it) }
        }

        val customers = if (visibleCustomerIds != null) {
            customerRepository.searchWithVisibility(tenantId, visibleCustomerIds, null, null, null)
        } else {
            customerRepository.findByTenantId(tenantId)
        }
        return customers.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun search(tenantId: UUID, request: CustomerSearchRequest, pageable: Pageable? = null, userId: UUID? = null): Page<CustomerResponse> {
        val (currentUserId, currentRoles) = getCurrentUserIdAndRoles()
        
        // Skip group filtering for admin/global manager
        val visibleCustomerIds = if (isAdminRole(currentRoles)) {
            null // No filtering
        } else {
            val gId = userId ?: currentUserId
            gId?.let { userGroupPort.getVisibleCustomerIds(it) }
        }

        val customers = if (visibleCustomerIds != null) {
            customerRepository.searchWithVisibility(tenantId, visibleCustomerIds, request.query, request.industry, request.country)
        } else {
            customerRepository.search(tenantId, request.query, request.industry, request.country)
        }
        val responses = customers.map { it.toResponse() }
        return if (pageable != null) {
            val start = pageable.offset.toInt().coerceAtMost(responses.size)
            val end = (start + pageable.pageSize).coerceAtMost(responses.size)
            PageImpl(responses.subList(start, end), pageable, responses.size.toLong())
        } else {
            PageImpl(responses)
        }
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): CustomerResponse {
        val customer = customerRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }

        // Check group membership if not admin
        val (currentUserId, currentRoles) = getCurrentUserIdAndRoles()
        if (!isAdminRole(currentRoles) && currentUserId != null) {
            val visibleCustomerIds = userGroupPort.getVisibleCustomerIds(currentUserId)
            if (visibleCustomerIds != null && !visibleCustomerIds.contains(id)) {
                throw ApiException(ErrorCode.FORBIDDEN, "You do not have access to this customer")
            }
        }

        return customer.toResponse()
    }

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
        
        // Check group membership if not admin
        val (currentUserId, currentRoles) = getCurrentUserIdAndRoles()
        if (!isAdminRole(currentRoles) && currentUserId != null) {
            val visibleCustomerIds = userGroupPort.getVisibleCustomerIds(currentUserId)
            if (visibleCustomerIds != null && !visibleCustomerIds.contains(id)) {
                throw ApiException(ErrorCode.FORBIDDEN, "You do not have access to this customer")
            }
        }

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
        
        // Check group membership if not admin
        val (currentUserId, currentRoles) = getCurrentUserIdAndRoles()
        if (!isAdminRole(currentRoles) && currentUserId != null) {
            val visibleCustomerIds = userGroupPort.getVisibleCustomerIds(currentUserId)
            if (visibleCustomerIds != null && !visibleCustomerIds.contains(id)) {
                throw ApiException(ErrorCode.FORBIDDEN, "You do not have access to this customer")
            }
        }

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