package com.numera.covenant.api

import com.numera.covenant.application.CovenantAnalyticsService
import com.numera.covenant.application.CovenantMetrics
import com.numera.covenant.application.CustomerCovenantRisk
import com.numera.covenant.application.MonitoringItemAnalytics
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.shared.domain.TenantAwareEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/covenants")
class CovenantReportController(
    private val covenantMonitoringRepository: CovenantMonitoringRepository,
    private val analyticsService: CovenantAnalyticsService,
) {
    /**
     * Legacy endpoint: Get covenant summary.
     * GET /api/covenants/summary
     */
    @GetMapping("/summary")
    fun covenantSummary(): Map<String, Any> {
        val items = covenantMonitoringRepository.findByTenantId(TenantAwareEntity.DEFAULT_TENANT)
        return mapOf(
            "total" to items.size,
            "byStatus" to items.groupingBy { it.status.name }.eachCount(),
            "items" to items.map {
                mapOf(
                    "id" to it.id.toString(),
                    "covenantName" to it.covenant.name,
                    "customerName" to it.covenant.covenantCustomer.customer.name,
                    "status" to it.status.name,
                    "periodEnd" to it.periodEnd.toString(),
                    "dueDate" to it.dueDate.toString(),
                )
            },
        )
    }

    /**
     * Get status distribution of all monitoring items.
     * GET /api/covenants/analytics/status-distribution
     */
    @GetMapping("/analytics/status-distribution")
    fun getStatusDistribution(): Map<String, Int> =
        analyticsService.getStatusDistribution()

    /**
     * Get breach risk probabilities per customer×covenant.
     * GET /api/covenants/analytics/breach-probabilities
     */
    @GetMapping("/analytics/breach-probabilities")
    fun getBreachProbabilities(): List<CustomerCovenantRisk> =
        analyticsService.getBreachProbabilities()

    /**
     * Get monitoring items due in the next N days.
     * GET /api/covenants/analytics/upcoming?days=30
     */
    @GetMapping("/analytics/upcoming")
    fun getUpcomingDueDates(
        @RequestParam(defaultValue = "30") days: Int,
    ): List<MonitoringItemAnalytics> =
        analyticsService.getUpcomingDueDates(days = days)

    /**
     * Get key metrics for the dashboard.
     * GET /api/covenants/analytics/metrics
     */
    @GetMapping("/analytics/metrics")
    fun getKeyMetrics(): CovenantMetrics =
        analyticsService.getKeyMetrics()
}
