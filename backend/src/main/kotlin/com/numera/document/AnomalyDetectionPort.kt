package com.numera.document

import com.numera.document.infrastructure.MlServiceClient
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Public API for OW-PGGR anomaly detection exposed by the document module.
 *
 * Follows the same port pattern as [CovenantPredictionPort] and [SpreadProcessingPort]:
 * lives in the document ROOT package so the spreading module can call anomaly detection
 * without crossing into document's private infrastructure.
 */
@Service
class AnomalyDetectionPort(
    private val mlClient: MlServiceClient,
) {
    fun detectAnomalies(request: AnomalyDetectionPortRequest): AnomalyDetectionPortResponse {
        val response = mlClient.detectAnomalies(
            MlServiceClient.AnomalyDetectionRequest(
                spread_values = request.spreadValues.map {
                    MlServiceClient.SpreadValueDto(
                        line_item_id = it.lineItemId,
                        label = it.label,
                        value = it.value,
                        zone_type = it.zoneType,
                    )
                },
                historical_values = request.historicalValues,
                template_validations = request.templateValidations,
            )
        )
        return AnomalyDetectionPortResponse(
            anomalies = response.anomalies.map {
                AnomalyItem(
                    lineItemId = it.line_item_id,
                    label = it.label,
                    anomalyType = it.anomaly_type,
                    severity = it.severity,
                    score = it.score,
                    message = it.message,
                )
            },
            overallRiskScore = response.overall_risk_score,
            summary = response.summary,
            totalItemsChecked = response.total_items_checked,
            flaggedCount = response.flagged_count,
        )
    }
}

data class AnomalyDetectionPortRequest(
    val spreadValues: List<AnomalySpreadValue>,
    val historicalValues: List<List<BigDecimal?>> = emptyList(),
    val templateValidations: List<Map<String, Any>> = emptyList(),
)

data class AnomalySpreadValue(
    val lineItemId: String,
    val label: String,
    val value: BigDecimal?,
    val zoneType: String? = null,
)

data class AnomalyDetectionPortResponse(
    val anomalies: List<AnomalyItem>,
    val overallRiskScore: BigDecimal,
    val summary: String,
    val totalItemsChecked: Int,
    val flaggedCount: Int,
)

data class AnomalyItem(
    val lineItemId: String,
    val label: String,
    val anomalyType: String,
    val severity: String,
    val score: BigDecimal,
    val message: String,
)
