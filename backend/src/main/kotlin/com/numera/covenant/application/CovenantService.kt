package com.numera.covenant.application

import com.numera.covenant.domain.Covenant
import com.numera.covenant.domain.CovenantContact
import com.numera.covenant.domain.CovenantCustomer
import com.numera.covenant.domain.CovenantFrequency
import com.numera.covenant.domain.CovenantThresholdOperator
import com.numera.covenant.domain.CovenantType
import com.numera.covenant.dto.CovenantContactRequest
import com.numera.covenant.dto.CovenantContactResponse
import com.numera.covenant.dto.CovenantCustomerRequest
import com.numera.covenant.dto.CovenantCustomerResponse
import com.numera.covenant.dto.CovenantRequest
import com.numera.covenant.dto.CovenantResponse
import com.numera.covenant.infrastructure.CovenantCustomerRepository
import com.numera.covenant.infrastructure.CovenantRepository
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CovenantService(
    private val covenantCustomerRepository: CovenantCustomerRepository,
    private val covenantRepository: CovenantRepository,
    private val customerRepository: CustomerRepository,
    private val auditService: AuditService,
) {

    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    // ── Covenant Customers ────────────────────────────────────────────────

    fun listCovenantCustomers(query: String?): List<CovenantCustomerResponse> =
        covenantCustomerRepository.search(tenantId, query).map { it.toResponse() }

    fun getCovenantCustomer(id: UUID): CovenantCustomerResponse =
        covenantCustomerRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant customer not found: $id") }
            .toResponse()

    @Transactional
    fun createCovenantCustomer(request: CovenantCustomerRequest): CovenantCustomerResponse {
        val customer = customerRepository.findById(request.customerId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found: ${request.customerId}") }

        val existing = covenantCustomerRepository.findByTenantIdAndCustomerId(tenantId, request.customerId)
        if (existing != null) throw ApiException(ErrorCode.CONFLICT, "Covenant customer already exists for this customer")

        val cc = CovenantCustomer().also {
            it.tenantId = tenantId
            it.customer = customer
            it.rimId = request.rimId
            it.clEntityId = request.clEntityId
            it.financialYearEnd = request.financialYearEnd
        }
        request.contacts.forEach { c -> cc.contacts.add(buildContact(cc, c)) }

        val saved = covenantCustomerRepository.save(cc)
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "COVENANT_CUSTOMER_CREATED",
            action = AuditAction.CREATE,
            entityType = "covenant_customer",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateCovenantCustomer(id: UUID, request: CovenantCustomerRequest): CovenantCustomerResponse {
        val cc = covenantCustomerRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant customer not found: $id") }

        cc.rimId = request.rimId
        cc.clEntityId = request.clEntityId
        cc.financialYearEnd = request.financialYearEnd
        cc.contacts.clear()
        request.contacts.forEach { c -> cc.contacts.add(buildContact(cc, c)) }

        val saved = covenantCustomerRepository.save(cc)
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "COVENANT_CUSTOMER_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "covenant_customer",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun toggleActive(id: UUID, active: Boolean): CovenantCustomerResponse {
        val cc = covenantCustomerRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant customer not found: $id") }
        cc.isActive = active
        return covenantCustomerRepository.save(cc).toResponse()
    }

    // ── Covenant Definitions ──────────────────────────────────────────────

    fun listCovenants(covenantCustomerId: UUID): List<CovenantResponse> =
        covenantRepository.findByCovenantCustomerIdAndIsActiveTrue(covenantCustomerId).map { it.toResponse() }

    fun getCovenant(id: UUID): CovenantResponse =
        covenantRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant not found: $id") }
            .toResponse()

    @Transactional
    fun createCovenant(request: CovenantRequest): CovenantResponse {
        val cc = covenantCustomerRepository.findById(request.covenantCustomerId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant customer not found: ${request.covenantCustomerId}") }

        val covenant = Covenant().also {
            it.tenantId = tenantId
            it.covenantCustomer = cc
            it.covenantType = CovenantType.valueOf(request.covenantType)
            it.name = request.name
            it.description = request.description
            it.frequency = CovenantFrequency.valueOf(request.frequency)
            it.formula = request.formula
            it.operator = request.operator?.let { op -> CovenantThresholdOperator.valueOf(op) }
            it.thresholdValue = request.thresholdValue
            it.thresholdMin = request.thresholdMin
            it.thresholdMax = request.thresholdMax
            it.documentType = request.documentType
            it.itemType = request.itemType
        }

        val saved = covenantRepository.save(covenant)
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "COVENANT_CREATED",
            action = AuditAction.CREATE,
            entityType = "covenant",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateCovenant(id: UUID, request: CovenantRequest): CovenantResponse {
        val covenant = covenantRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant not found: $id") }

        covenant.name = request.name
        covenant.description = request.description
        covenant.frequency = CovenantFrequency.valueOf(request.frequency)
        covenant.formula = request.formula
        covenant.operator = request.operator?.let { op -> CovenantThresholdOperator.valueOf(op) }
        covenant.thresholdValue = request.thresholdValue
        covenant.thresholdMin = request.thresholdMin
        covenant.thresholdMax = request.thresholdMax
        covenant.documentType = request.documentType
        covenant.itemType = request.itemType

        val saved = covenantRepository.save(covenant)
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "COVENANT_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "covenant",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun deactivateCovenant(id: UUID) {
        val covenant = covenantRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant not found: $id") }
        covenant.isActive = false
        covenantRepository.save(covenant)
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "COVENANT_DEACTIVATED",
            action = AuditAction.DELETE,
            entityType = "covenant",
            entityId = id.toString(),
        )
    }

    // ── Mapping helpers ───────────────────────────────────────────────────

    private fun buildContact(cc: CovenantCustomer, req: CovenantContactRequest) =
        CovenantContact().also {
            it.covenantCustomer = cc
            it.contactType = req.contactType
            it.userId = req.userId
            it.name = req.name
            it.email = req.email
        }

    private fun CovenantCustomer.toResponse() = CovenantCustomerResponse(
        id = id!!,
        customerId = customer.id!!,
        customerName = customer.name,
        rimId = rimId,
        clEntityId = clEntityId,
        financialYearEnd = financialYearEnd,
        isActive = isActive,
        contacts = contacts.map { it.toResponse() },
        createdAt = createdAt,
    )

    private fun CovenantContact.toResponse() = CovenantContactResponse(
        id = id!!,
        contactType = contactType,
        userId = userId,
        name = name,
        email = email,
    )

    private fun Covenant.toResponse() = CovenantResponse(
        id = id!!,
        covenantCustomerId = covenantCustomer.id!!,
        covenantCustomerName = covenantCustomer.customer.name,
        covenantType = covenantType.name,
        name = name,
        description = description,
        frequency = frequency.name,
        formula = formula,
        operator = operator?.name,
        thresholdValue = thresholdValue,
        thresholdMin = thresholdMin,
        thresholdMax = thresholdMax,
        documentType = documentType,
        itemType = itemType,
        isActive = isActive,
        createdAt = createdAt,
    )
}
