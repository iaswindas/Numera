package com.numera.model.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.model.domain.ModelLineItem
import com.numera.model.domain.ModelTemplate
import com.numera.model.domain.ModelValidation
import com.numera.model.dto.LineItemResponse
import com.numera.model.dto.TemplateItemsResponse
import com.numera.model.dto.TemplateResponse
import com.numera.model.dto.TemplateZoneItemResponse
import com.numera.model.dto.TemplateUpsertRequest
import com.numera.model.dto.ValidationResultResponse
import com.numera.model.infrastructure.LineItemRepository
import com.numera.model.infrastructure.TemplateRepository
import com.numera.model.infrastructure.ValidationRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TemplateService(
    private val templateRepository: TemplateRepository,
    private val lineItemRepository: LineItemRepository,
    private val validationRepository: ValidationRepository,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService,
) {
    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun findAll(): List<TemplateResponse> =
        templateRepository.findByTenantIdAndActiveTrue(resolvedTenantId()).map { toResponse(it) }

    fun findById(id: UUID): TemplateResponse {
        val template = templateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }
        return toResponse(template)
    }

    fun findItemsByZone(id: UUID, zone: String): TemplateItemsResponse {
        val template = templateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }
        return TemplateItemsResponse(
            templateId = template.id.toString(),
            templateName = template.name,
            zone = zone,
            items = lineItemRepository.findByTemplateIdAndZoneOrderBySortOrderAsc(id, zone).map {
                TemplateZoneItemResponse(
                    id = it.id.toString(),
                    itemCode = it.itemCode,
                    label = it.label,
                    category = it.category,
                    itemType = it.itemType.name,
                    formula = it.formula,
                    displayOrder = it.sortOrder,
                    indentLevel = it.indentLevel,
                    isTotal = it.total,
                    isRequired = it.required,
                    signConvention = it.signConvention,
                    synonyms = readAliases(it.aliasesJson),
                )
            }
        )
    }

    @Transactional
    fun create(request: TemplateUpsertRequest): TemplateResponse {
        val template = templateRepository.save(ModelTemplate().also {
            it.tenantId = resolvedTenantId()
            it.name = request.name
            it.version = request.version
            it.currency = request.currency
            it.active = request.active
        })

        lineItemRepository.saveAll(request.lineItems.map {
            ModelLineItem().also { li ->
                li.template = template
                li.itemCode = it.itemCode
                li.label = it.label
                li.zone = it.zone
                li.category = it.category
                li.itemType = it.itemType
                li.formula = it.formula
                li.required = it.required
                li.total = it.isTotal
                li.indentLevel = it.indentLevel
                li.signConvention = it.signConvention
                li.aliasesJson = objectMapper.writeValueAsString(it.aliases)
                li.sortOrder = it.sortOrder
            }
        })

        validationRepository.saveAll(request.validations.map {
            ModelValidation().also { v ->
                v.template = template
                v.name = it.name
                v.expression = it.expression
                v.severity = it.severity
            }
        })

        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "TEMPLATE_CREATED",
            action = AuditAction.CREATE,
            entityType = "model_template",
            entityId = template.id.toString(),
        )

        return toResponse(template)
    }

    @Transactional
    fun update(id: UUID, request: TemplateUpsertRequest): TemplateResponse {
        val template = templateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }

        template.name = request.name
        template.version = request.version
        template.currency = request.currency
        template.active = request.active
        templateRepository.save(template)

        lineItemRepository.deleteAll(lineItemRepository.findByTemplateIdOrderBySortOrderAsc(id))
        validationRepository.deleteAll(validationRepository.findByTemplateId(id))

        lineItemRepository.saveAll(request.lineItems.map {
            ModelLineItem().also { li ->
                li.template = template
                li.itemCode = it.itemCode
                li.label = it.label
                li.zone = it.zone
                li.category = it.category
                li.itemType = it.itemType
                li.formula = it.formula
                li.required = it.required
                li.total = it.isTotal
                li.indentLevel = it.indentLevel
                li.signConvention = it.signConvention
                li.aliasesJson = objectMapper.writeValueAsString(it.aliases)
                li.sortOrder = it.sortOrder
            }
        })

        validationRepository.saveAll(request.validations.map {
            ModelValidation().also { v ->
                v.template = template
                v.name = it.name
                v.expression = it.expression
                v.severity = it.severity
            }
        })

        auditService.record(
            tenantId = template.tenantId.toString(),
            eventType = "TEMPLATE_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "model_template",
            entityId = template.id.toString(),
        )

        return toResponse(template)
    }

    @Transactional
    fun copyTemplateForCustomer(templateId: UUID, customerId: UUID): ModelTemplate {
        val globalTemplate = templateRepository.findById(templateId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }

        if (globalTemplate.customerId != null) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Cannot copy a customer-specific template")
        }

        // Create a new template for the customer
        val customerTemplate = templateRepository.save(ModelTemplate().also {
            it.tenantId = globalTemplate.tenantId
            it.name = globalTemplate.name
            it.version = globalTemplate.version
            it.currency = globalTemplate.currency
            it.active = globalTemplate.active
            it.customerId = customerId
            it.parentTemplate = globalTemplate
            it.isGlobal = false
        })

        // Copy all line items
        val lineItems = lineItemRepository.findByTemplateIdOrderBySortOrderAsc(templateId)
        lineItemRepository.saveAll(lineItems.map { item ->
            ModelLineItem().also { li ->
                li.template = customerTemplate
                li.itemCode = item.itemCode
                li.label = item.label
                li.zone = item.zone
                li.category = item.category
                li.itemType = item.itemType
                li.formula = item.formula
                li.required = item.required
                li.total = item.total
                li.indentLevel = item.indentLevel
                li.signConvention = item.signConvention
                li.aliasesJson = item.aliasesJson
                li.sortOrder = item.sortOrder
            }
        })

        // Copy all validations
        val validations = validationRepository.findByTemplateId(templateId)
        validationRepository.saveAll(validations.map { validation ->
            ModelValidation().also { v ->
                v.template = customerTemplate
                v.name = validation.name
                v.expression = validation.expression
                v.severity = validation.severity
            }
        })

        auditService.record(
            tenantId = customerTemplate.tenantId.toString(),
            eventType = "TEMPLATE_COPIED",
            action = AuditAction.CREATE,
            entityType = "model_template",
            entityId = customerTemplate.id.toString(),
            parentEntityType = "model_template",
            parentEntityId = templateId.toString(),
        )

        return customerTemplate
    }

    fun getTemplateForCustomer(customerId: UUID, templateId: UUID): ModelTemplate {
        val tenantId = resolvedTenantId()

        // First check if customer has a copy of this template
        val customerTemplate = templateRepository.findByIdAndCustomerId(templateId, customerId)
        if (customerTemplate != null) {
            return customerTemplate
        }

        // If not, check if it's a global template
        val globalTemplate = templateRepository.findById(templateId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }

        if (globalTemplate.customerId == null && globalTemplate.isGlobal) {
            return globalTemplate
        }

        throw ApiException(ErrorCode.NOT_FOUND, "Template not available for customer")
    }

    private fun toResponse(template: ModelTemplate): TemplateResponse {
        val lineItems = lineItemRepository.findByTemplateIdOrderBySortOrderAsc(template.id!!)
        val validations = validationRepository.findByTemplateId(template.id!!)

        return TemplateResponse(
            id = template.id.toString(),
            name = template.name,
            version = template.version,
            currency = template.currency,
            active = template.active,
            lineItems = lineItems.map {
                LineItemResponse(
                    id = it.id.toString(),
                    itemCode = it.itemCode,
                    label = it.label,
                    zone = it.zone,
                    category = it.category,
                    itemType = it.itemType,
                    formula = it.formula,
                    required = it.required,
                    isTotal = it.total,
                    indentLevel = it.indentLevel,
                    signConvention = it.signConvention,
                    aliases = readAliases(it.aliasesJson),
                    displayOrder = it.sortOrder,
                )
            },
            validations = validations.map {
                ValidationResultResponse(
                    id = it.id.toString(),
                    name = it.name,
                    expression = it.expression,
                    severity = it.severity,
                )
            }
        )
    }

    private fun readAliases(aliasesJson: String?): List<String> =
        objectMapper.readValue(aliasesJson ?: "[]", List::class.java).map { alias -> alias.toString() }
}
