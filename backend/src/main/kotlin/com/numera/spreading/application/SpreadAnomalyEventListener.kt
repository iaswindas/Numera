package com.numera.spreading.application

import com.numera.shared.events.SpreadSubmittedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Listens for [SpreadSubmittedEvent] and triggers OW-PGGR anomaly detection asynchronously.
 * Does not block the main spread processing flow.
 */
@Component
class SpreadAnomalyEventListener(
    private val anomalyDetectionService: AnomalyDetectionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onSpreadSubmitted(event: SpreadSubmittedEvent) {
        log.info("Triggering anomaly detection for spreadItem={}, tenant={}", event.spreadItemId, event.tenantId)
        try {
            val report = anomalyDetectionService.detectAnomalies(event.spreadItemId, event.tenantId)
            if (report != null) {
                log.info(
                    "Anomaly detection complete for spreadItem={}: riskScore={}, flagged={}",
                    event.spreadItemId, report.overallRiskScore, report.anomaliesJson,
                )
            }
        } catch (e: Exception) {
            log.error("Anomaly detection failed for spreadItem={}: {}", event.spreadItemId, e.message, e)
        }
    }
}
