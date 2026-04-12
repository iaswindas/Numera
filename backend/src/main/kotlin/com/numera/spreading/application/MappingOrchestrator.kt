package com.numera.spreading.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.document.domain.Document
import com.numera.document.SpreadProcessingPort
import com.numera.model.application.FormulaEngine
import com.numera.model.application.TemplateService
import com.numera.model.domain.ModelItemType
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.spreading.domain.ExpressionPattern
import com.numera.spreading.domain.SpreadItem
import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.dto.MappingResultResponse
import com.numera.spreading.dto.MappingSummary
import com.numera.spreading.dto.MappingValidation
import com.numera.spreading.dto.SpreadValueResponse
import com.numera.spreading.infrastructure.ExpressionPatternRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class MappingOrchestrator(
    private val spreadProcessingPort: SpreadProcessingPort,
    private val formulaEngine: FormulaEngine,
    private val templateService: TemplateService,
    private val spreadValueRepo: SpreadValueRepository,
    private val spreadVersionService: SpreadVersionService,
    private val expressionPatternRepo: ExpressionPatternRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun processSpread(spreadItem: SpreadItem, document: Document): MappingResultResponse {
        val started = System.currentTimeMillis()

        // Use customer-specific template if it exists, otherwise use global template
        val actualTemplate = templateService.getTemplateForCustomer(spreadItem.customer.id!!, spreadItem.template.id!!)
        val template = templateService.findById(actualTemplate.id!!)
        val sourceRows: List<Map<String, Any>> = spreadProcessingPort.getDocumentZoneRows(document.id!!)

        val targetItems: List<Map<String, Any>> = template.lineItems.map {
            mapOf(
                "id" to it.id,
                "itemCode" to it.itemCode,
                "label" to it.label,
                "aliases" to it.aliases,
                "itemType" to it.itemType.name,
                "formula" to (it.formula ?: ""),
            )
        }

        val suggest = spreadProcessingPort.suggestMappings(
            documentId = document.id.toString(),
            sourceRows = sourceRows,
            targetItems = targetItems,
            tenantId = spreadItem.tenantId.toString(),
        )

        val expressionResult = spreadProcessingPort.buildExpressions(
            documentId = document.id.toString(),
            tenantId = spreadItem.tenantId.toString(),
            customerId = spreadItem.customer.id.toString(),
            templateId = spreadItem.template.id.toString(),
            sourceRows = sourceRows,
            semanticMatches = suggest ?: emptyList(),
        )

        spreadValueRepo.deleteAll(spreadValueRepo.findBySpreadItemId(spreadItem.id!!))

        val mappedByCode: Map<String, Map<String, Any>> = expressionResult?.expressions
            ?.mapNotNull { exp ->
                val code = exp["item_code"]?.toString() ?: return@mapNotNull null
                code to exp
            }
            ?.toMap()
            ?: emptyMap()

        val patternsByCode = expressionPatternRepo.findByTenantIdAndCustomerIdAndTemplateId(
            spreadItem.tenantId,
            spreadItem.customer.id!!,
            spreadItem.template.id!!,
        ).associateBy { it.itemCode }

        val createdValues = template.lineItems.map { item ->
            val mapped = mappedByCode[item.itemCode]
            val pattern = patternsByCode[item.itemCode]
            val fallbackExpression = pattern?.patternJson?.takeIf { mapped == null }
                ?.let { objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {}) }
            SpreadValue().also { value ->
                value.spreadItem = spreadItem
                value.lineItemId = java.util.UUID.fromString(item.id)
                value.itemCode = item.itemCode
                value.label = item.label

                val effectiveExpression = mapped ?: fallbackExpression
                val rawNumber = (effectiveExpression?.get("value") as? Number)?.toString()?.toBigDecimalOrNull()
                val scale = (effectiveExpression?.get("scale_factor") as? Number)?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ONE

                value.rawValue = rawNumber
                value.mappedValue = if (rawNumber != null) rawNumber.multiply(scale) else null
                value.expressionType = effectiveExpression?.get("expression_type")?.toString() ?: pattern?.patternType
                value.expressionDetailJson = effectiveExpression?.let { objectMapper.writeValueAsString(it) }
                value.scaleFactor = scale
                value.confidenceScore = (effectiveExpression?.get("confidence") as? Number)?.toString()?.toBigDecimalOrNull()
                value.confidenceLevel = effectiveExpression?.get("confidence_level")?.toString()
                    ?: if (fallbackExpression != null) "MEDIUM" else "LOW"
                value.sourcePage = (effectiveExpression?.get("page") as? Number)?.toInt()
                value.sourceText = effectiveExpression?.get("source_text")?.toString()
                value.sourceDocumentName = document.originalFilename.ifBlank { document.fileName }
                value.sourceBbox = extractSourceBbox(effectiveExpression)
                value.notes = buildAutoGeneratedNotes(
                    documentName = value.sourceDocumentName,
                    sourcePage = value.sourcePage,
                    sourceText = value.sourceText,
                    rawValue = value.rawValue,
                )
                value.manualOverride = false
                value.autofilled = effectiveExpression?.get("autofilled") == true || fallbackExpression != null
                value.formulaCell = item.itemType == ModelItemType.FORMULA
                value.accepted = false
            }
        }

        val valueByCode = createdValues.associateBy { it.itemCode }.toMutableMap()

        template.lineItems.filter { it.itemType == ModelItemType.FORMULA && !it.formula.isNullOrBlank() }.forEach { formulaItem ->
            val current = valueByCode.getValue(formulaItem.itemCode)
            val context = valueByCode.mapValues { it.value.mappedValue }
            current.mappedValue = formulaEngine.evaluate(formulaItem.formula!!, context)
            current.formulaCell = true
            current.expressionType = "FORMULA"
        }

        val saved = spreadValueRepo.saveAll(createdValues)
        saved.filter { it.mappedValue != null }.forEach {
            auditService.record(
                tenantId = spreadItem.tenantId.toString(),
                eventType = "SPREAD_VALUE_MAPPED_AI",
                action = AuditAction.PROCESS,
                entityType = "spread_value",
                entityId = it.id.toString(),
                parentEntityType = "spread_item",
                parentEntityId = spreadItem.id.toString(),
            )
        }

        val validations = template.validations.map {
            val result = formulaEngine.evaluate(it.expression, saved.associate { v -> v.itemCode to v.mappedValue })
            MappingValidation(
                name = it.name,
                status = if (result == null || result.compareTo(BigDecimal.ZERO) == 0) "PASS" else "FAIL",
                difference = result ?: BigDecimal.ZERO,
                severity = it.severity,
            )
        }

        saved.filter { it.expressionDetailJson != null }.forEach {
            val existing = expressionPatternRepo.findByTenantIdAndCustomerIdAndTemplateIdAndItemCode(
                spreadItem.tenantId,
                spreadItem.customer.id!!,
                spreadItem.template.id!!,
                it.itemCode,
            )
            val pattern = existing ?: ExpressionPattern().also { exp ->
                exp.tenantId = spreadItem.tenantId
                exp.customer = spreadItem.customer
                exp.template = spreadItem.template
                exp.itemCode = it.itemCode
            }
            pattern.patternType = it.expressionType ?: "EXPRESSION"
            pattern.patternJson = it.expressionDetailJson ?: "{}"
            pattern.usageCount += 1
            pattern.lastUsedAt = Instant.now()
            expressionPatternRepo.save(pattern)
        }

        spreadVersionService.createSnapshot(
            spreadItemId = spreadItem.id!!,
            action = "CREATED",
            comments = "AI mapping completed",
            createdBy = "System",
            cellsChanged = saved.count { it.mappedValue != null },
        )

        val high = saved.count { it.confidenceLevel == "HIGH" }
        val medium = saved.count { it.confidenceLevel == "MEDIUM" }
        val low = saved.count { it.confidenceLevel == "LOW" }
        val mapped = saved.count { it.mappedValue != null }
        val total = saved.size

        return MappingResultResponse(
            spreadItemId = spreadItem.id.toString(),
            processingTimeMs = System.currentTimeMillis() - started,
            summary = MappingSummary(
                totalItems = total,
                mapped = mapped,
                highConfidence = high,
                mediumConfidence = medium,
                lowConfidence = low,
                unmapped = total - mapped,
                formulaComputed = saved.count { it.formulaCell },
                autofilled = saved.count { it.autofilled },
                coveragePct = if (total == 0) BigDecimal.ZERO else BigDecimal(mapped)
                    .multiply(BigDecimal(100))
                    .divide(BigDecimal(total), 2, RoundingMode.HALF_UP),
            ),
            unitScale = expressionResult?.unitScale?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ONE,
            validations = validations,
            values = saved.map { it.toResponse() }
        )
    }

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

    private fun extractSourceBbox(expression: Map<String, Any>?): String? {
        val raw = expression?.get("source_bbox")
            ?: expression?.get("bbox")
            ?: expression?.get("coordinates")
            ?: return null

        return when (raw) {
            is String -> raw
            else -> objectMapper.writeValueAsString(raw)
        }
    }

    private fun buildAutoGeneratedNotes(
        documentName: String?,
        sourcePage: Int?,
        sourceText: String?,
        rawValue: BigDecimal?,
    ): String? {
        if (documentName.isNullOrBlank() && sourcePage == null && sourceText.isNullOrBlank() && rawValue == null) {
            return null
        }

        val lines = mutableListOf<String>()
        documentName?.takeIf { it.isNotBlank() }?.let { lines += "Source: $it" }
        sourcePage?.let { lines += "Page: $it" }
        sourceText?.takeIf { it.isNotBlank() }?.let { lines += "Line Item: $it" }
        rawValue?.let { lines += "Value: ${it.stripTrailingZeros().toPlainString()}" }
        return lines.joinToString("\n")
    }
}
