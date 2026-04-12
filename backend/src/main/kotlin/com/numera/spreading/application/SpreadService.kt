package com.numera.spreading.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.customer.CustomerQueryPort
import com.numera.document.DocumentQueryPort
import com.numera.model.application.FormulaEngine
import com.numera.model.TemplateQueryPort
import com.numera.model.application.TemplateService
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
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
import com.numera.spreading.dto.SpreadVarianceDto
import com.numera.spreading.dto.SubmitSpreadRequest
import com.numera.spreading.dto.SubmitSpreadResponse
import com.numera.spreading.dto.VersionHistoryResponse
import com.numera.spreading.events.SpreadApprovedEvent
import com.numera.spreading.events.SpreadRejectedEvent
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import com.numera.shared.events.SpreadSubmittedEvent
import com.numera.shared.infrastructure.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class SpreadService(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val customerQueryPort: CustomerQueryPort,
    private val documentQueryPort: DocumentQueryPort,
    private val templateQueryPort: TemplateQueryPort,
    private val templateService: TemplateService,
    private val formulaEngine: FormulaEngine,
    private val mappingOrchestrator: MappingOrchestrator,
    private val spreadVersionService: SpreadVersionService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: DomainEventPublisher,
) {
    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun listByCustomer(customerId: UUID): List<SpreadItemResponse> =
        spreadItemRepository.findByCustomerId(customerId).map { it.toResponse() }

    fun get(spreadItemId: UUID): SpreadItemResponse =
        spreadItemRepository.findById(spreadItemId).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }.toResponse()

    @Transactional
    fun create(customerId: UUID, request: SpreadItemRequest): SpreadItemResponse {
        val customer = customerQueryPort.findEntityById(customerId)
        val document = documentQueryPort.findEntityById(request.documentId)
        val template = templateQueryPort.findEntityById(request.templateId)

        val spreadItem = spreadItemRepository.save(SpreadItem().also {
            it.tenantId = resolvedTenantId()
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
            tenantId = spreadItem.tenantId.toString(),
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
    fun updateNotes(spreadItemId: UUID, valueId: UUID, notes: String): SpreadValueResponse {
        val value = spreadValueRepository.findById(valueId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread value not found") }
        if (value.spreadItem.id != spreadItemId) {
            throw ApiException(ErrorCode.NOT_FOUND, "Spread value does not belong to spread item")
        }

        value.notes = notes

        val saved = spreadValueRepository.save(value)

        auditService.record(
            tenantId = saved.spreadItem.tenantId.toString(),
            eventType = "SPREAD_VALUE_NOTES_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "spread_value",
            entityId = saved.id.toString(),
            parentEntityType = "spread_item",
            parentEntityId = spreadItemId.toString(),
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

        eventPublisher.publish(
            SpreadSubmittedEvent(
                spreadItemId = spreadItemId,
                tenantId = spread.tenantId,
                customerId = spread.customer.id!!,
                statementDate = spread.statementDate,
                spreadValues = values.associate { it.itemCode to it.mappedValue },
            )
        )

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

    @Transactional
    fun approveSpread(spreadId: UUID, comment: String?, approverId: UUID): SubmitSpreadResponse {
        val spread = spreadItemRepository.findById(spreadId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }

        if (spread.status != SpreadStatus.SUBMITTED) {
            throw ApiException(
                errorCode = ErrorCode.VALIDATION_FAILED,
                message = "Spread must be in SUBMITTED status to approve"
            )
        }

        spread.status = SpreadStatus.APPROVED
        spreadItemRepository.save(spread)

        val snapshot = spreadVersionService.createSnapshot(
            spreadItemId = spreadId,
            action = "APPROVED",
            comments = comment,
            createdBy = approverId.toString(),
            cellsChanged = 0,
        )

        eventPublisher.publish(SpreadApprovedEvent(spreadId, spread.tenantId))

        auditService.record(
            tenantId = spread.tenantId.toString(),
            eventType = "SPREAD_APPROVED",
            action = AuditAction.APPROVE,
            entityType = "spread_item",
            entityId = spreadId.toString(),
        )

        return SubmitSpreadResponse(
            status = spread.status.name,
            version = snapshot.versionNumber,
            validations = emptyList(),
        )
    }

    @Transactional
    fun rejectSpread(spreadId: UUID, comment: String, approverId: UUID): SubmitSpreadResponse {
        val spread = spreadItemRepository.findById(spreadId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }

        if (spread.status != SpreadStatus.SUBMITTED) {
            throw ApiException(
                errorCode = ErrorCode.VALIDATION_FAILED,
                message = "Spread must be in SUBMITTED status to reject"
            )
        }

        if (comment.isBlank()) {
            throw ApiException(
                errorCode = ErrorCode.VALIDATION_FAILED,
                message = "Comment is required for rejection"
            )
        }

        spread.status = SpreadStatus.DRAFT
        spreadItemRepository.save(spread)

        val snapshot = spreadVersionService.createSnapshot(
            spreadItemId = spreadId,
            action = "REJECTED",
            comments = comment,
            createdBy = approverId.toString(),
            cellsChanged = 0,
        )

        eventPublisher.publish(SpreadRejectedEvent(spreadId, spread.tenantId))

        auditService.record(
            tenantId = spread.tenantId.toString(),
            eventType = "SPREAD_REJECTED",
            action = AuditAction.REJECT,
            entityType = "spread_item",
            entityId = spreadId.toString(),
        )

        return SubmitSpreadResponse(
            status = spread.status.name,
            version = snapshot.versionNumber,
            validations = emptyList(),
        )
    }

    fun getVariance(spreadId: UUID, compareSpreadId: UUID): List<SpreadVarianceDto> {
        val spread = spreadItemRepository.findById(spreadId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }
        val compareSpread = spreadItemRepository.findById(compareSpreadId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Compare spread item not found") }

        if (spread.tenantId != compareSpread.tenantId) {
            throw ApiException(ErrorCode.FORBIDDEN, "Cannot compare spreads from different tenants")
        }

        val currentValues = spreadValueRepository.findBySpreadItemId(spreadId).associateBy { it.itemCode }
        val compareValues = spreadValueRepository.findBySpreadItemId(compareSpreadId).associateBy { it.itemCode }

        return currentValues.keys.union(compareValues.keys).sorted().map { itemCode ->
            val current = currentValues[itemCode]
            val compare = compareValues[itemCode]
            val currentValue = current?.mappedValue
            val compareValue = compare?.mappedValue
            val absoluteChange = when {
                currentValue != null && compareValue != null -> currentValue.subtract(compareValue)
                currentValue != null -> currentValue
                compareValue != null -> compareValue.negate()
                else -> null
            }
            val percentageChange = when {
                compareValue != null && compareValue.compareTo(BigDecimal.ZERO) != 0 && absoluteChange != null ->
                    absoluteChange.divide(compareValue.abs(), 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
                else -> null
            }

            SpreadVarianceDto(
                lineItemId = current?.lineItemId?.toString() ?: compare?.lineItemId?.toString() ?: "",
                lineItemCode = itemCode,
                lineItemLabel = current?.label ?: compare?.label ?: "",
                currentValue = currentValue,
                compareValue = compareValue,
                absoluteChange = absoluteChange,
                percentageChange = percentageChange,
            )
        }
    }

    private fun SpreadItem.toResponse() = SpreadItemResponse(
        id = id.toString(),
        customerId = customer.id.toString(),
        documentId = document.id.toString(),
        templateId = template.id?.toString(),
        statementDate = statementDate.toString(),
        status = status,
        currentVersion = currentVersion,
        createdAt = createdAt.toString(),
        baseSpreadId = baseSpread?.id?.toString(),
        periodSequence = periodSequence,
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
            sourceDocumentName = sourceDocumentName,
            sourceBbox = sourceBbox,
            notes = notes,
            isManualOverride = manualOverride,
            isAutofilled = autofilled,
            isFormulaCell = formulaCell,
        )
    }
}
