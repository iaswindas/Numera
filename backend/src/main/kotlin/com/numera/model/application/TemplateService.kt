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
    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    fun findAll(): List<TemplateResponse> =
        templateRepository.findByTenantIdAndActiveTrue(tenantId).map { toResponse(it) }

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
            it.tenantId = tenantId
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
            tenantId = tenantId.toString(),
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
