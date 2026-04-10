package com.numera.spreading.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.document.infrastructure.DocumentRepository
import com.numera.model.application.FormulaEngine
import com.numera.model.application.TemplateService
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.spreading.domain.SpreadItem
import com.numera.spreading.domain.SpreadStatus
import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.dto.BulkAcceptRequest
import com.numera.spreading.dto.BulkAcceptResponse
import com.numera.spreading.dto.DiffResponse
import com.numera.spreading.dto.MappingResultResponse
import com.numera.spreading.dto.SpreadItemRequest
import com.numera.spreading.dto.SpreadItemResponse
import com.numera.spreading.dto.SpreadValueResponse
import com.numera.spreading.dto.SpreadValueUpdateRequest
import com.numera.spreading.dto.SubmitSpreadRequest
import com.numera.spreading.dto.SubmitSpreadResponse
import com.numera.spreading.dto.VersionHistoryResponse
import com.numera.spreading.events.SpreadSubmittedEvent
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import com.numera.model.infrastructure.TemplateRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class SpreadService(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val customerRepository: CustomerRepository,
    private val documentRepository: DocumentRepository,
    private val templateRepository: TemplateRepository,
    private val templateService: TemplateService,
    private val formulaEngine: FormulaEngine,
    private val mappingOrchestrator: MappingOrchestrator,
    private val spreadVersionService: SpreadVersionService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    fun listByCustomer(customerId: UUID): List<SpreadItemResponse> =
        spreadItemRepository.findByCustomerId(customerId).map { it.toResponse() }

    fun get(spreadItemId: UUID): SpreadItemResponse =
        spreadItemRepository.findById(spreadItemId).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }.toResponse()

    @Transactional
    fun create(customerId: UUID, request: SpreadItemRequest): SpreadItemResponse {
        val customer = customerRepository.findById(customerId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }
        val document = documentRepository.findById(request.documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }
        val template = templateRepository.findById(request.templateId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }

        val spreadItem = spreadItemRepository.save(SpreadItem().also {
            it.tenantId = tenantId
            it.customer = customer
            it.document = document
            it.template = template
            it.statementDate = request.statementDate
            it.frequency = request.frequency
            it.auditMethod = request.auditMethod
            it.sourceCurrency = request.sourceCurrency
            it.consolidation = request.consolidation
            it.status = SpreadStatus.DRAFT
            it.currentVersion = 0
        })

        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "SPREAD_CREATED",
            action = AuditAction.CREATE,
            entityType = "spread_item",
            entityId = spreadItem.id.toString(),
            parentEntityType = "customer",
            parentEntityId = customerId.toString(),
        )

        return spreadItem.toResponse()
    }

    @Transactional
    fun process(spreadItemId: UUID): MappingResultResponse {
        val spreadItem = spreadItemRepository.findById(spreadItemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }
        return mappingOrchestrator.processSpread(spreadItem, spreadItem.document)
    }

    fun values(spreadItemId: UUID): List<SpreadValueResponse> = spreadValueRepository.findBySpreadItemId(spreadItemId).map { it.toResponse() }

    @Transactional
    fun updateValue(spreadItemId: UUID, valueId: UUID, request: SpreadValueUpdateRequest): SpreadValueResponse {
        val value = spreadValueRepository.findById(valueId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread value not found") }
        if (value.spreadItem.id != spreadItemId) {
            throw ApiException(ErrorCode.NOT_FOUND, "Spread value does not belong to spread item")
        }

        val old = value.mappedValue
        value.mappedValue = request.mappedValue
        value.overrideComment = request.overrideComment
        value.expressionType = request.expressionType
        value.manualOverride = true

        val saved = spreadValueRepository.save(value)

        auditService.record(
            tenantId = saved.spreadItem.tenantId.toString(),
            eventType = "SPREAD_VALUE_CORRECTED",
            action = AuditAction.CORRECT,
            entityType = "spread_value",
            entityId = saved.id.toString(),
            parentEntityType = "spread_item",
            parentEntityId = spreadItemId.toString(),
            diffJson = """[{\"field\":\"mapped_value\",\"old\":\"$old\",\"new\":\"${saved.mappedValue}\"}]""",
        )

        return saved.toResponse()
    }

    @Transactional
    fun bulkAccept(spreadItemId: UUID, request: BulkAcceptRequest): BulkAcceptResponse {
        val levels = when (request.confidenceThreshold.uppercase()) {
            "HIGH" -> listOf("HIGH")
            "MEDIUM" -> listOf("HIGH", "MEDIUM")
            else -> listOf("HIGH", "MEDIUM", "LOW")
        }

        val candidates = spreadValueRepository.findBySpreadItemIdAndConfidenceLevelIn(spreadItemId, levels)
        val filtered = if (request.itemCodes.isNullOrEmpty()) candidates else candidates.filter { it.itemCode in request.itemCodes }
        filtered.forEach { it.accepted = true }
        spreadValueRepository.saveAll(filtered)

        val spread = spreadItemRepository.findById(spreadItemId).orElseThrow()
        auditService.record(
            tenantId = spread.tenantId.toString(),
            eventType = "SPREAD_BULK_ACCEPTED",
            action = AuditAction.UPDATE,
            entityType = "spread_item",
            entityId = spreadItemId.toString(),
        )

        return BulkAcceptResponse(accepted = filtered.size, total = candidates.size)
    }

    @Transactional
    fun submit(spreadItemId: UUID, request: SubmitSpreadRequest): SubmitSpreadResponse {
        val spread = spreadItemRepository.findById(spreadItemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }

        val values = spreadValueRepository.findBySpreadItemId(spreadItemId)
        val template = templateService.findById(spread.template.id!!)
        val requiredCodes = template.lineItems.filter { it.required }.map { it.itemCode }.toSet()
        val unmappedRequired = values
            .filter { it.itemCode in requiredCodes && it.mappedValue == null }
            .map { it.itemCode }
            .sorted()

        val validations = template.validations.map {
            val difference = formulaEngine.evaluate(it.expression, values.associateBy({ value -> value.itemCode }, { value -> value.mappedValue }))
                ?: BigDecimal.ZERO
            com.numera.spreading.dto.MappingValidation(
                name = it.name,
                status = if (difference.compareTo(BigDecimal.ZERO) == 0) "PASS" else "FAIL",
                difference = difference,
                severity = it.severity,
            )
        }

        val failingValidations = validations.filter { it.status == "FAIL" }
        if (unmappedRequired.isNotEmpty() || (failingValidations.isNotEmpty() && !request.overrideValidationWarnings)) {
            throw ApiException(
                errorCode = ErrorCode.VALIDATION_FAILED,
                message = buildString {
                    append(unmappedRequired.size + failingValidations.size)
                    append(" validation errors found")
                },
                validations = failingValidations.map {
                    mapOf(
                        "name" to it.name,
                        "status" to it.status,
                        "difference" to it.difference,
                        "severity" to it.severity,
                    )
                },
                unmappedRequired = unmappedRequired,
            )
        }

        spread.status = SpreadStatus.SUBMITTED
        spreadItemRepository.save(spread)

        val snapshot = spreadVersionService.createSnapshot(
            spreadItemId = spreadItemId,
            action = "SUBMITTED",
            comments = request.comments,
            createdBy = "Demo Analyst",
            cellsChanged = values.count { it.manualOverride },
        )

        eventPublisher.publishEvent(SpreadSubmittedEvent(spreadItemId, spread.tenantId))

        auditService.record(
            tenantId = spread.tenantId.toString(),
            eventType = "SPREAD_SUBMITTED",
            action = AuditAction.SUBMIT,
            entityType = "spread_item",
            entityId = spreadItemId.toString(),
        )

        return SubmitSpreadResponse(
            status = spread.status.name,
            version = snapshot.versionNumber,
            validations = validations,
        )
    }

    fun history(spreadItemId: UUID): VersionHistoryResponse = spreadVersionService.history(spreadItemId)

    fun diff(spreadItemId: UUID, fromVersion: Int, toVersion: Int): DiffResponse =
        spreadVersionService.diff(spreadItemId, fromVersion, toVersion)

    @Transactional
    fun rollback(spreadItemId: UUID, version: Int, comments: String): Map<String, Any> =
        spreadVersionService.rollback(spreadItemId, version, comments)

    private fun SpreadItem.toResponse() = SpreadItemResponse(
        id = id.toString(),
        customerId = customer.id.toString(),
        documentId = document.id.toString(),
        statementDate = statementDate.toString(),
        status = status,
        currentVersion = currentVersion,
        createdAt = createdAt.toString(),
    )

    private fun SpreadValue.toResponse(): SpreadValueResponse {
        val type = object : TypeReference<Map<String, Any>>() {}
        return SpreadValueResponse(
            id = id.toString(),
            itemCode = itemCode,
            label = label,
            mappedValue = mappedValue,
            rawValue = rawValue,
            expressionType = expressionType,
            expressionDetail = expressionDetailJson?.let { objectMapper.readValue(it, type) },
            scaleFactor = scaleFactor,
            confidenceScore = confidenceScore,
            confidenceLevel = confidenceLevel,
            sourcePage = sourcePage,
            sourceText = sourceText,
            isManualOverride = manualOverride,
            isAutofilled = autofilled,
            isFormulaCell = formulaCell,
        )
    }
}
