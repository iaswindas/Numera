package com.numera.portfolio.application

import com.numera.portfolio.domain.AlertSeverity
import com.numera.portfolio.domain.PortfolioQueryRequest
import com.numera.portfolio.domain.PortfolioRatioRow
import com.numera.portfolio.domain.PortfolioRatioSnapshot
import com.numera.portfolio.domain.RatioAlert
import com.numera.portfolio.domain.RatioCode
import com.numera.portfolio.domain.RatioTrendPoint
import com.numera.portfolio.domain.SharedDashboard
import com.numera.portfolio.infrastructure.PortfolioRatioSnapshotRepository
import com.numera.portfolio.infrastructure.SharedDashboardRepository
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

@Service
class PortfolioService(
    private val snapshotRepository: PortfolioRatioSnapshotRepository,
    private val sharedDashboardRepository: SharedDashboardRepository,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
) {
    private val log = LoggerFactory.getLogger(PortfolioService::class.java)

    /**
     * Build cross-client ratio comparison table for the given tenant.
     * Materializes ratio snapshots from spread items if needed.
     */
    @Transactional(readOnly = true)
    fun getPortfolioRatios(tenantId: UUID): List<PortfolioRatioRow> {
        val snapshots = snapshotRepository.findByTenantId(tenantId)
        return buildRatioRows(snapshots)
    }

    /**
     * Materialise ratio snapshots from all spread items for a tenant.
     * Called on demand or scheduled.
     */
    @Transactional
    fun materializeSnapshots(tenantId: UUID) {
        val spreadItems = spreadItemRepository.findByTenantId(tenantId)
        for (item in spreadItems) {
            val existing = snapshotRepository.findByTenantIdAndSpreadItemId(tenantId, item.id!!)
            if (existing.isNotEmpty()) continue

            val values = spreadValueRepository.findBySpreadItemId(item.id!!)
            val valueMap = values.associateBy { it.itemCode.uppercase() }

            val computedRatios = computeRatios(valueMap)

            // Find previous spread for the same customer/template for change calculation
            val previous = spreadItemRepository.findTopByCustomerIdAndTemplateIdAndIdNotOrderByStatementDateDesc(
                item.customer.id!!, item.template.id!!, item.id!!
            )
            val previousValues = previous?.let { p ->
                val prevVals = spreadValueRepository.findBySpreadItemId(p.id!!)
                computeRatios(prevVals.associateBy { it.itemCode.uppercase() })
            } ?: emptyMap()

            for ((code, value) in computedRatios) {
                val snapshot = PortfolioRatioSnapshot().apply {
                    this.tenantId = tenantId
                    customerId = item.customer.id!!
                    customerName = item.customer.name
                    spreadItemId = item.id!!
                    statementDate = item.statementDate
                    ratioCode = code
                    ratioLabel = code.name.replace("_", " ")
                    this.value = value
                    this.previousValue = previousValues[code]
                    this.changePercent = previousValues[code]?.let { prev ->
                        if (prev.compareTo(BigDecimal.ZERO) != 0) {
                            value.subtract(prev).divide(prev.abs(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal(100))
                        } else null
                    }
                }
                snapshotRepository.save(snapshot)
            }
        }
        log.info("Materialized portfolio ratio snapshots for tenant {}", tenantId)
    }

    @Transactional(readOnly = true)
    fun getRatioTrends(tenantId: UUID, customerId: UUID, ratioCode: RatioCode): List<RatioTrendPoint> {
        return snapshotRepository.findTrend(tenantId, customerId, ratioCode).map {
            RatioTrendPoint(statementDate = it.statementDate, value = it.value)
        }
    }

    @Transactional(readOnly = true)
    fun queryPortfolio(tenantId: UUID, request: PortfolioQueryRequest): Page<PortfolioRatioSnapshot> {
        val sort = when (request.sortBy) {
            "value" -> Sort.by(Sort.Direction.fromString(request.sortDirection ?: "ASC"), "value")
            "date" -> Sort.by(Sort.Direction.fromString(request.sortDirection ?: "DESC"), "statementDate")
            "customer" -> Sort.by(Sort.Direction.fromString(request.sortDirection ?: "ASC"), "customerName")
            else -> Sort.by(Sort.Direction.DESC, "statementDate")
        }
        val pageable = PageRequest.of(request.page, request.size.coerceAtMost(200), sort)
        return snapshotRepository.findByQuery(tenantId, request.ratioCode, request.fromDate, request.toDate, pageable)
    }

    @Transactional(readOnly = true)
    fun getAlerts(tenantId: UUID, thresholdPercent: BigDecimal = BigDecimal(15)): List<RatioAlert> {
        return snapshotRepository.findSignificantChanges(tenantId, thresholdPercent).map {
            RatioAlert(
                ratioCode = it.ratioCode,
                ratioLabel = it.ratioLabel,
                previousValue = it.previousValue,
                currentValue = it.value,
                changePercent = it.changePercent,
                severity = when {
                    it.changePercent!!.abs() >= BigDecimal(50) -> AlertSeverity.CRITICAL
                    it.changePercent!!.abs() >= BigDecimal(25) -> AlertSeverity.WARNING
                    else -> AlertSeverity.INFO
                },
            )
        }
    }

    // ── Dashboard Sharing ──

    @Transactional
    fun createSharedDashboard(
        tenantId: UUID,
        userId: UUID,
        configJson: String,
        title: String?,
        expiryHours: Long,
    ): SharedDashboard {
        val token = generateSecureToken()
        val shared = SharedDashboard().apply {
            this.tenantId = tenantId
            this.createdBy = userId
            this.dashboardConfigJson = configJson
            this.title = title
            this.token = token
            this.expiresAt = Instant.now().plusSeconds(expiryHours * 3600)
        }
        return sharedDashboardRepository.save(shared)
    }

    @Transactional
    fun getSharedDashboard(token: String): SharedDashboard? {
        val shared = sharedDashboardRepository.findByToken(token) ?: return null
        if (!shared.active || shared.expiresAt.isBefore(Instant.now())) return null
        shared.viewCount += 1
        return sharedDashboardRepository.save(shared)
    }

    // ── Private helpers ──

    private fun buildRatioRows(snapshots: List<PortfolioRatioSnapshot>): List<PortfolioRatioRow> {
        return snapshots
            .groupBy { Triple(it.customerId, it.customerName, it.statementDate) }
            .map { (key, group) ->
                val ratios = group.associate { it.ratioCode to it.value }
                val alerts = group.filter {
                    it.changePercent != null && it.changePercent!!.abs() >= BigDecimal(15)
                }.map {
                    RatioAlert(
                        ratioCode = it.ratioCode,
                        ratioLabel = it.ratioLabel,
                        previousValue = it.previousValue,
                        currentValue = it.value,
                        changePercent = it.changePercent,
                        severity = when {
                            it.changePercent!!.abs() >= BigDecimal(50) -> AlertSeverity.CRITICAL
                            it.changePercent!!.abs() >= BigDecimal(25) -> AlertSeverity.WARNING
                            else -> AlertSeverity.INFO
                        },
                    )
                }
                PortfolioRatioRow(
                    customerId = key.first,
                    customerName = key.second,
                    statementDate = key.third,
                    ratios = ratios,
                    alerts = alerts,
                )
            }
            .sortedByDescending { it.statementDate }
    }

    private fun computeRatios(valueMap: Map<String, com.numera.spreading.domain.SpreadValue>): Map<RatioCode, BigDecimal> {
        val ratios = mutableMapOf<RatioCode, BigDecimal>()

        fun getVal(code: String): BigDecimal? = valueMap[code]?.mappedValue

        val currentAssets = getVal("CURRENT_ASSETS")
        val currentLiabilities = getVal("CURRENT_LIABILITIES")
        val inventory = getVal("INVENTORY")
        val totalDebt = getVal("TOTAL_DEBT") ?: getVal("TOTAL_LIABILITIES")
        val totalEquity = getVal("TOTAL_EQUITY")
        val netIncome = getVal("NET_INCOME")
        val totalAssets = getVal("TOTAL_ASSETS")
        val revenue = getVal("REVENUE") ?: getVal("TOTAL_REVENUE")
        val grossProfit = getVal("GROSS_PROFIT")
        val interestExpense = getVal("INTEREST_EXPENSE")
        val ebit = getVal("EBIT") ?: getVal("OPERATING_INCOME")
        val debtService = getVal("DEBT_SERVICE")

        safeDivide(currentAssets, currentLiabilities)?.let { ratios[RatioCode.CURRENT_RATIO] = it }
        safeDivide(currentAssets?.subtract(inventory ?: BigDecimal.ZERO), currentLiabilities)?.let { ratios[RatioCode.QUICK_RATIO] = it }
        safeDivide(totalDebt, totalEquity)?.let { ratios[RatioCode.DEBT_TO_EQUITY] = it }
        safeDivide(netIncome, totalEquity)?.let { ratios[RatioCode.RETURN_ON_EQUITY] = it }
        safeDivide(netIncome, totalAssets)?.let { ratios[RatioCode.RETURN_ON_ASSETS] = it }
        safeDivide(netIncome, revenue)?.let { ratios[RatioCode.NET_PROFIT_MARGIN] = it }
        safeDivide(grossProfit, revenue)?.let { ratios[RatioCode.GROSS_PROFIT_MARGIN] = it }
        safeDivide(revenue, totalAssets)?.let { ratios[RatioCode.ASSET_TURNOVER] = it }
        safeDivide(ebit, interestExpense)?.let { ratios[RatioCode.INTEREST_COVERAGE] = it }
        safeDivide(totalAssets, totalEquity)?.let { ratios[RatioCode.LEVERAGE_RATIO] = it }

        if (currentAssets != null && currentLiabilities != null) {
            ratios[RatioCode.WORKING_CAPITAL] = currentAssets.subtract(currentLiabilities)
        }

        if (debtService != null && ebit != null) {
            safeDivide(ebit, debtService)?.let { ratios[RatioCode.DEBT_SERVICE_COVERAGE] = it }
        }

        return ratios
    }

    private fun safeDivide(numerator: BigDecimal?, denominator: BigDecimal?): BigDecimal? {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return null
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP)
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
