package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.CovenantRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Analytics and reporting service for covenant monitoring.
 * Provides status distributions, breach probabilities, and upcoming due dates.
 */
@Service
class CovenantAnalyticsService(
    private val covenantRepository: CovenantRepository,
    private val monitoringRepository: CovenantMonitoringRepository,
) {

    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    /**
     * Get distribution of monitoring items by status.
     * Returns a map like: {DUE: 5, OVERDUE: 2, MET: 10, BREACHED: 1, CLOSED: 25}
     */
    @Transactional(readOnly = true)
    fun getStatusDistribution(tenantId: UUID? = null): Map<String, Int> {
        val tid = tenantId ?: resolvedTenantId()
        val allItems = monitoringRepository.findByTenantId(tid)

        return allItems.groupingBy { it.status.name }
            .eachCount()
            .toSortedMap()
    }

    /**
     * Get breach risk probabilities for each customer×covenant combination.
     * Returns sorted list by risk score (highest first).
     */
    @Transactional(readOnly = true)
    fun getBreachProbabilities(tenantId: UUID? = null): List<CustomerCovenantRisk> {
        val tid = tenantId ?: resolvedTenantId()
        val allItems = monitoringRepository.findByTenantId(tid)

        return allItems
            .filter { it.breachProbability != null }
            .groupBy { "${it.covenant.covenantCustomer.customer.name}|${it.covenant.name}" }
            .map { (key, items) ->
                val parts = key.split("|")
                val customerName = parts[0]
                val covenantName = parts[1]
                val avgProbability = items.mapNotNull { it.breachProbability }
                    .reduceOrNull { acc, value -> (acc + value) / BigDecimal(2) } ?: BigDecimal.ZERO
                val breachCount = items.count { it.status == CovenantStatus.BREACHED }

                CustomerCovenantRisk(
                    customerName = customerName,
                    covenantName = covenantName,
                    riskScore = avgProbability.toDouble(),
                    breachCount = breachCount,
                    pendingItemCount = items.count { it.status in listOf(CovenantStatus.DUE, CovenantStatus.OVERDUE) },
                )
            }
            .sortedByDescending { it.riskScore }
    }

    /**
     * Get monitoring items due within the specified number of days.
     * Returns items sorted by due date (soonest first).
     */
    @Transactional(readOnly = true)
    fun getUpcomingDueDates(tenantId: UUID? = null, days: Int = 30): List<MonitoringItemAnalytics> {
        val tid = tenantId ?: resolvedTenantId()
        val cutoffDate = LocalDate.now().plusDays(days.toLong())

        return monitoringRepository.findByTenantId(tid)
            .filter { item ->
                val isFuture = item.dueDate.isAfter(LocalDate.now())
                val isWithinWindow = item.dueDate <= cutoffDate
                val isPending = item.status in listOf(CovenantStatus.DUE, CovenantStatus.SUBMITTED)
                isFuture && isWithinWindow && isPending
            }
            .map { item ->
                val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), item.dueDate)
                MonitoringItemAnalytics(
                    id = item.id!!,
                    covenantId = item.covenant.id!!,
                    covenantName = item.covenant.name,
                    customerName = item.covenant.covenantCustomer.customer.name,
                    periodEnd = item.periodEnd,
                    dueDate = item.dueDate,
                    daysUntilDue = daysUntilDue.toInt(),
                    status = item.status.name,
                    calculatedValue = item.calculatedValue,
                    manualValue = item.manualValue,
                )
            }
            .sortedBy { it.dueDate }
    }

    /**
     * Get key metrics for dashboard.
     */
    @Transactional(readOnly = true)
    fun getKeyMetrics(tenantId: UUID? = null): CovenantMetrics {
        val tid = tenantId ?: resolvedTenantId()
        val allItems = monitoringRepository.findByTenantId(tid)
        val statusDist = getStatusDistribution(tid)

        return CovenantMetrics(
            totalMonitoringItems = allItems.size,
            dueCount = statusDist["DUE"] ?: 0,
            overdueCount = statusDist["OVERDUE"] ?: 0,
            breachedCount = statusDist["BREACHED"] ?: 0,
            metCount = statusDist["MET"] ?: 0,
            closedCount = statusDist["CLOSED"] ?: 0,
            averageBreachProbability = allItems
                .mapNotNull { it.breachProbability }
                .takeIf { it.isNotEmpty() }
                ?.reduceOrNull { acc, value -> (acc + value) / BigDecimal(2) }
                ?.toDouble() ?: 0.0,
            completionRate = if (allItems.isEmpty()) 0.0
                else (statusDist["CLOSED"]?.toDouble() ?: 0.0) / allItems.size,
        )
    }
}

// ── Response DTOs ────────────────────────────────────────────────────

data class CustomerCovenantRisk(
    val customerName: String,
    val covenantName: String,
    val riskScore: Double,  // [0.0, 1.0]
    val breachCount: Int,
    val pendingItemCount: Int,
)

data class MonitoringItemAnalytics(
    val id: UUID,
    val covenantId: UUID,
    val covenantName: String,
    val customerName: String,
    val periodEnd: LocalDate,
    val dueDate: LocalDate,
    val daysUntilDue: Int,
    val status: String,
    val calculatedValue: java.math.BigDecimal?,
    val manualValue: java.math.BigDecimal?,
)

data class CovenantMetrics(
    val totalMonitoringItems: Int,
    val dueCount: Int,
    val overdueCount: Int,
    val breachedCount: Int,
    val metCount: Int,
    val closedCount: Int,
    val averageBreachProbability: Double,
    val completionRate: Double,  // [0.0, 1.0]
)
