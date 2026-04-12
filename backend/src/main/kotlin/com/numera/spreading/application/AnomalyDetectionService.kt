package com.numera.spreading.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.document.AnomalyDetectionPort
import com.numera.document.AnomalyDetectionPortRequest
import com.numera.document.AnomalyDetectionPortResponse
import com.numera.document.AnomalySpreadValue
import com.numera.shared.config.FeatureFlagService
import com.numera.spreading.domain.AnomalyReport
import com.numera.spreading.domain.AnomalyReportRepository
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AnomalyDetectionService(
    private val anomalyDetectionPort: AnomalyDetectionPort,
    private val featureFlags: FeatureFlagService,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val anomalyReportRepository: AnomalyReportRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun detectAnomalies(spreadItemId: UUID, tenantId: UUID): AnomalyReport? {
        if (!featureFlags.isEnabled("owpggrAnomaly", tenantId.toString())) {
            log.debug("OW-PGGR anomaly detection disabled for tenant {}", tenantId)
            return null
        }

        val spreadItem = spreadItemRepository.findById(spreadItemId).orElse(null) ?: run {
            log.warn("Spread item {} not found for anomaly detection", spreadItemId)
            return null
        }

        val currentValues = spreadValueRepository.findBySpreadItemId(spreadItemId)
        if (currentValues.isEmpty()) {
            log.debug("No spread values for item {}, skipping anomaly detection", spreadItemId)
            return null
        }

        val spreadValues = currentValues.map { sv ->
            AnomalySpreadValue(
                lineItemId = sv.lineItemId.toString(),
                label = sv.label,
                value = sv.mappedValue,
                zoneType = sv.expressionType,
            )
        }

        // Collect historical values from prior spreads for the same customer/template
        val historicalValues = collectHistoricalValues(spreadItem, currentValues.map { it.itemCode })

        val request = AnomalyDetectionPortRequest(
            spreadValues = spreadValues,
            historicalValues = historicalValues,
        )

        val response: AnomalyDetectionPortResponse = try {
            anomalyDetectionPort.detectAnomalies(request)
        } catch (e: Exception) {
            log.warn("OW-PGGR anomaly detection failed for spreadItem={}: {}", spreadItemId, e.message)
            return null
        }

        val report = AnomalyReport().also {
            it.spreadItemId = spreadItemId
            it.overallRiskScore = response.overallRiskScore
            it.summary = response.summary
            it.anomaliesJson = objectMapper.writeValueAsString(response.anomalies)
            it.checkedAt = Instant.now()
        }

        return anomalyReportRepository.save(report)
    }

    private fun collectHistoricalValues(
        spreadItem: com.numera.spreading.domain.SpreadItem,
        itemCodes: List<String>,
    ): List<List<java.math.BigDecimal?>> {
        val priorSpread = spreadItemRepository.findTopByCustomerIdAndTemplateIdAndIdNotOrderByStatementDateDesc(
            customerId = spreadItem.customer.id!!,
            templateId = spreadItem.template.id!!,
            excludeId = spreadItem.id!!,
        ) ?: return emptyList()

        val priorValues = spreadValueRepository.findBySpreadItemId(priorSpread.id!!)
            .associateBy { it.itemCode }

        // Build a single row of prior-period values aligned to current item codes
        val historicalRow = itemCodes.map { code -> priorValues[code]?.mappedValue }
        return listOf(historicalRow)
    }
}
