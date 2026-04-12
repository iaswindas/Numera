package com.numera.spreading.application

import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.infrastructure.ExpressionPatternRepository
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AutofillService(
    private val expressionPatternRepository: ExpressionPatternRepository,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun bestMatch(
        tenantId: UUID,
        customerId: UUID,
        templateId: UUID,
        itemCode: String,
    ): String? = expressionPatternRepository
        .findByTenantIdAndCustomerIdAndTemplateIdAndItemCode(tenantId, customerId, templateId, itemCode)
        ?.patternJson

    /**
     * Auto-fill a new spread from a base (prior) period.
     * Copies expression patterns and mappings from baseSpreadId into the new spread values.
     */
    fun autofillFromBasePeriod(spreadItemId: UUID, baseSpreadId: UUID): Int {
        val baseValues = spreadValueRepository.findBySpreadItemId(baseSpreadId)
        val currentValues = spreadValueRepository.findBySpreadItemId(spreadItemId)

        if (baseValues.isEmpty()) {
            log.info("No base period values found for spread {}", baseSpreadId)
            return 0
        }

        val baseMap = baseValues.associateBy { it.itemCode }
        var filledCount = 0

        for (value in currentValues) {
            if (value.mappedValue != null) continue // Already has a value

            val baseValue = baseMap[value.itemCode] ?: continue

            // Copy mapping information from prior period
            value.mappedValue = baseValue.mappedValue
            value.expressionType = baseValue.expressionType
            value.expressionDetailJson = baseValue.expressionDetailJson
            value.scaleFactor = baseValue.scaleFactor
            value.autofilled = true
            value.confidenceScore = java.math.BigDecimal("0.75")
            value.confidenceLevel = "MEDIUM"
            value.sourceText = "Autofilled from prior period"

            spreadValueRepository.save(value)
            filledCount++
        }

        log.info("Autofilled {} values from base spread {} into spread {}", filledCount, baseSpreadId, spreadItemId)
        return filledCount
    }

    /**
     * Find the most recent submitted spread for the same customer+template (prior period).
     */
    fun findBasePeriod(customerId: UUID, templateId: UUID, excludeSpreadId: UUID): UUID? {
        return spreadItemRepository
            .findTopByCustomerIdAndTemplateIdAndIdNotOrderByStatementDateDesc(customerId, templateId, excludeSpreadId)
            ?.id
    }
}