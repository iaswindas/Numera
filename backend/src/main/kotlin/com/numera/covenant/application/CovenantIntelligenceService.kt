package com.numera.covenant.application

import com.numera.covenant.domain.CovenantMonitoringItem
import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.domain.CovenantThresholdOperator
import com.numera.covenant.domain.CovenantType
import com.numera.covenant.domain.RiskHeatmapEntry
import com.numera.covenant.dto.CalendarEntry
import com.numera.covenant.dto.CovenantColumn
import com.numera.covenant.dto.PortfolioSummary
import com.numera.covenant.dto.RiskHeatmapCell
import com.numera.covenant.dto.RiskHeatmapData
import com.numera.covenant.dto.RiskHeatmapRow
import com.numera.covenant.dto.TrendlineData
import com.numera.covenant.dto.TrendlinePoint
import com.numera.covenant.infrastructure.CovenantCustomerRepository
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.CovenantRepository
import com.numera.covenant.infrastructure.RiskHeatmapRepository
import com.numera.model.application.FormulaEngine
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CovenantIntelligenceService(
    private val covenantCustomerRepository: CovenantCustomerRepository,
    private val covenantRepository: CovenantRepository,
    private val monitoringRepository: CovenantMonitoringRepository,
    private val heatmapRepository: RiskHeatmapRepository,
    private val formulaEngine: FormulaEngine,
    private val predictionService: CovenantPredictionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    // ── Spread-triggered recomputation ────────────────────────────────────

    @Transactional
    fun recomputeForSpread(spreadItemId: UUID, tenantId: UUID) {
        log.info("Recomputing covenant intelligence for spread={}, tenant={}", spreadItemId, tenantId)

        // Refresh breach probabilities across all active items for this tenant
        predictionService.recalculateAllProbabilities()

        // Re-materialise the risk heatmap for the tenant
        materializeRiskHeatmap(tenantId)

        log.info("Covenant intelligence recomputation complete for tenant={}", tenantId)
    }

    @Transactional
    fun recomputeForCustomer(customerId: UUID, tenantId: UUID) {
        log.info("Recomputing covenant intelligence for customer={}, tenant={}", customerId, tenantId)

        val covenantCustomer = covenantCustomerRepository
            .findByTenantIdAndCustomerId(tenantId, customerId) ?: run {
            log.debug("No covenant customer linked to customer {} in tenant {}", customerId, tenantId)
            return
        }

        val covenants = covenantRepository
            .findByCovenantCustomerIdAndIsActiveTrue(covenantCustomer.id!!)
            .filter { it.covenantType == CovenantType.FINANCIAL }

        // Re-run breach probability for each covenant's monitoring items
        for (covenant in covenants) {
            val items = monitoringRepository.findByCovenantId(covenant.id!!)
                .filter { it.status !in listOf(CovenantStatus.CLOSED, CovenantStatus.MET) }

            for (item in items) {
                val history = monitoringRepository.findByCovenantId(covenant.id!!)
                    .sortedBy { it.periodEnd }
                    .mapNotNull { m ->
                        val value = m.calculatedValue ?: m.manualValue ?: return@mapNotNull null
                        value
                    }
                    .takeLast(12)

                item.breachProbability = predictionService.estimateBreachProbability(
                    history, covenant.thresholdValue, covenant.operator?.name,
                )
            }
            monitoringRepository.saveAll(items)
        }

        // Refresh heatmap rows for this customer
        materializeCustomerHeatmap(tenantId, customerId)

        log.info("Covenant intelligence recomputation complete for customer={}", customerId)
    }

    // ── Risk Heatmap Materialisation ──────────────────────────────────────

    @Transactional
    fun materializeRiskHeatmap(tenantId: UUID): RiskHeatmapData {
        val customers = covenantCustomerRepository.findByTenantIdAndIsActiveTrue(tenantId)

        for (cc in customers) {
            materializeCustomerHeatmap(tenantId, cc.customer.id!!)
        }

        return getRiskHeatmap(tenantId)
    }

    @Transactional
    fun materializeCustomerHeatmap(tenantId: UUID, customerId: UUID) {
        val cc = covenantCustomerRepository.findByTenantIdAndCustomerId(tenantId, customerId) ?: return
        val covenants = covenantRepository.findByCovenantCustomerIdAndIsActiveTrue(cc.id!!)
            .filter { it.covenantType == CovenantType.FINANCIAL }

        // Remove stale entries for this customer
        heatmapRepository.deleteByTenantIdAndCustomerId(tenantId, customerId)

        val entries = covenants.map { covenant ->
            val latestItem = monitoringRepository.findByCovenantId(covenant.id!!)
                .filter { it.calculatedValue != null || it.manualValue != null }
                .maxByOrNull { it.periodEnd }

            RiskHeatmapEntry().apply {
                this.tenantId = tenantId
                this.customerId = customerId
                this.customerName = cc.customer.name
                this.covenantId = covenant.id!!
                this.covenantName = covenant.name
                this.breachProbability = latestItem?.breachProbability ?: BigDecimal("0.5")
                this.currentValue = latestItem?.calculatedValue ?: latestItem?.manualValue
                this.threshold = covenant.thresholdValue
                this.status = latestItem?.status ?: CovenantStatus.DUE
                this.lastUpdated = Instant.now()
            }
        }

        heatmapRepository.saveAll(entries)
    }

    @Transactional(readOnly = true)
    fun getRiskHeatmap(tenantId: UUID? = null): RiskHeatmapData {
        val tid = tenantId ?: resolvedTenantId()
        val entries = heatmapRepository.findByTenantId(tid)

        val covenantColumns = entries
            .map { CovenantColumn(covenantId = it.covenantId, covenantName = it.covenantName) }
            .distinctBy { it.covenantId }
            .sortedBy { it.covenantName }

        val rows = entries.groupBy { it.customerId }
            .map { (customerId, customerEntries) ->
                RiskHeatmapRow(
                    customerId = customerId,
                    customerName = customerEntries.first().customerName,
                    cells = customerEntries.map { entry ->
                        RiskHeatmapCell(
                            covenantId = entry.covenantId,
                            covenantName = entry.covenantName,
                            breachProbability = entry.breachProbability,
                            currentValue = entry.currentValue,
                            threshold = entry.threshold,
                            status = entry.status.name,
                            lastUpdated = entry.lastUpdated,
                        )
                    }.sortedBy { it.covenantName },
                )
            }
            .sortedBy { it.customerName }

        return RiskHeatmapData(rows = rows, covenantColumns = covenantColumns)
    }

    // ── Trendlines ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getTrendlines(covenantId: UUID, periods: Int = 12): TrendlineData {
        val covenant = covenantRepository.findById(covenantId)
            .orElseThrow { IllegalArgumentException("Covenant not found: $covenantId") }

        val items = monitoringRepository.findByCovenantId(covenantId)
            .sortedBy { it.periodEnd }
            .takeLast(periods)

        val direction = when (covenant.operator) {
            CovenantThresholdOperator.GTE -> "MIN"
            CovenantThresholdOperator.LTE -> "MAX"
            else -> null
        }

        val values = items.mapNotNull { it.calculatedValue ?: it.manualValue }
        val forecast = if (values.size >= 3) extrapolateTrend(values, 4) else emptyList()

        val historicalPoints = items.map { item ->
            val value = item.calculatedValue ?: item.manualValue
            TrendlinePoint(
                period = formatQuarter(item.periodEnd),
                value = value,
                predictedValue = null,
                lowerBand = computeBand(value, covenant.thresholdValue, lower = true),
                upperBand = computeBand(value, covenant.thresholdValue, lower = false),
            )
        }

        val forecastPoints = forecast.mapIndexed { idx, predicted ->
            val lastPeriodEnd = items.lastOrNull()?.periodEnd ?: LocalDate.now()
            val forecastDate = lastPeriodEnd.plusMonths((idx + 1) * 3L)
            TrendlinePoint(
                period = formatQuarter(forecastDate),
                value = null,
                predictedValue = predicted,
                lowerBand = computeBand(predicted, covenant.thresholdValue, lower = true),
                upperBand = computeBand(predicted, covenant.thresholdValue, lower = false),
            )
        }

        // Determine regime based on recent trend
        val regime = determineRegime(values, covenant.thresholdValue, covenant.operator)

        return TrendlineData(
            covenantId = covenantId,
            covenantName = covenant.name,
            threshold = covenant.thresholdValue,
            direction = direction,
            points = historicalPoints + forecastPoints,
            regime = regime,
        )
    }

    // ── Calendar ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getCalendar(tenantId: UUID? = null, days: Int = 90): List<CalendarEntry> {
        val tid = tenantId ?: resolvedTenantId()
        val cutoff = LocalDate.now().plusDays(days.toLong())

        return monitoringRepository.findByTenantId(tid)
            .filter { it.dueDate >= LocalDate.now() && it.dueDate <= cutoff }
            .filter { it.status in listOf(CovenantStatus.DUE, CovenantStatus.OVERDUE, CovenantStatus.SUBMITTED) }
            .map { item ->
                CalendarEntry(
                    covenantId = item.covenant.id!!,
                    monitoringItemId = item.id!!,
                    customerName = item.covenant.covenantCustomer.customer.name,
                    covenantName = item.covenant.name,
                    dueDate = item.dueDate,
                    status = item.status.name,
                    daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), item.dueDate).toInt(),
                )
            }
            .sortedBy { it.dueDate }
    }

    // ── Portfolio Summary ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getPortfolioSummary(tenantId: UUID? = null): PortfolioSummary {
        val tid = tenantId ?: resolvedTenantId()
        val customers = covenantCustomerRepository.findByTenantIdAndIsActiveTrue(tid)
        val allItems = monitoringRepository.findByTenantId(tid)

        val statusDist = allItems.groupingBy { it.status.name }.eachCount().toSortedMap()
        val breachProbs = allItems.mapNotNull { it.breachProbability?.toDouble() }
        val avgProb = if (breachProbs.isNotEmpty()) breachProbs.average() else 0.0
        val highRisk = allItems.count { (it.breachProbability ?: BigDecimal.ZERO) >= BigDecimal("0.7") }

        val covenantCount = customers.sumOf { cc ->
            covenantRepository.findByCovenantCustomerIdAndIsActiveTrue(cc.id!!).size
        }

        return PortfolioSummary(
            totalCustomers = customers.size,
            totalCovenants = covenantCount,
            activeMonitoringItems = allItems.count { it.status !in listOf(CovenantStatus.CLOSED) },
            breachedCount = statusDist["BREACHED"] ?: 0,
            overdueCount = statusDist["OVERDUE"] ?: 0,
            averageBreachProbability = avgProb,
            highRiskCount = highRisk,
            statusDistribution = statusDist,
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun extrapolateTrend(values: List<BigDecimal>, periodsAhead: Int): List<BigDecimal> {
        val n = values.size
        val xMean = BigDecimal((n - 1).toDouble() / 2)
        val yMean = values.reduce { a, b -> a + b }.divide(BigDecimal(n), 8, RoundingMode.HALF_UP)

        var num = BigDecimal.ZERO
        var den = BigDecimal.ZERO
        values.forEachIndexed { i, y ->
            val x = BigDecimal(i) - xMean
            num += x * (y - yMean)
            den += x * x
        }

        val slope = if (den.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
        else num.divide(den, 8, RoundingMode.HALF_UP)

        val intercept = yMean - slope * xMean

        return (1..periodsAhead).map { offset ->
            val x = BigDecimal(n - 1 + offset)
            (slope * x + intercept).setScale(4, RoundingMode.HALF_UP)
        }
    }

    private fun computeBand(value: BigDecimal?, threshold: BigDecimal?, lower: Boolean): BigDecimal? {
        if (value == null) return null
        val bandWidth = threshold?.abs()?.multiply(BigDecimal("0.10")) ?: value.abs().multiply(BigDecimal("0.10"))
        return if (lower) value - bandWidth else value + bandWidth
    }

    private fun determineRegime(
        values: List<BigDecimal>,
        threshold: BigDecimal?,
        operator: CovenantThresholdOperator?,
    ): String {
        if (values.size < 3 || threshold == null) return "UNKNOWN"

        val recent = values.takeLast(3)
        val trend = recent.last() - recent.first()

        return when (operator) {
            CovenantThresholdOperator.GTE -> {
                if (recent.last() < threshold) "BREACHING"
                else if (trend < BigDecimal.ZERO) "DETERIORATING"
                else "STABLE"
            }
            CovenantThresholdOperator.LTE -> {
                if (recent.last() > threshold) "BREACHING"
                else if (trend > BigDecimal.ZERO) "DETERIORATING"
                else "STABLE"
            }
            else -> "STABLE"
        }
    }

    private fun formatQuarter(date: LocalDate): String {
        val quarter = ((date.monthValue - 1) / 3) + 1
        return "${date.year}-Q$quarter"
    }
}
