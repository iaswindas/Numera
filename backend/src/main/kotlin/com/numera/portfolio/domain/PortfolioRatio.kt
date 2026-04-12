package com.numera.portfolio.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "portfolio_ratio_snapshots")
class PortfolioRatioSnapshot : TenantAwareEntity() {

    @Column(nullable = false)
    var customerId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var customerName: String = ""

    @Column(nullable = false)
    var spreadItemId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var statementDate: LocalDate = LocalDate.now()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var ratioCode: RatioCode = RatioCode.CURRENT_RATIO

    @Column(nullable = false)
    var ratioLabel: String = ""

    @Column(nullable = false, precision = 18, scale = 6)
    var value: BigDecimal = BigDecimal.ZERO

    @Column(precision = 18, scale = 6)
    var previousValue: BigDecimal? = null

    @Column(precision = 10, scale = 4)
    var changePercent: BigDecimal? = null

    @Column(nullable = false)
    var snapshotVersion: Int = 1
}

enum class RatioCode {
    CURRENT_RATIO,
    QUICK_RATIO,
    DEBT_TO_EQUITY,
    DEBT_SERVICE_COVERAGE,
    RETURN_ON_EQUITY,
    RETURN_ON_ASSETS,
    NET_PROFIT_MARGIN,
    GROSS_PROFIT_MARGIN,
    ASSET_TURNOVER,
    INTEREST_COVERAGE,
    LEVERAGE_RATIO,
    WORKING_CAPITAL
}

data class PortfolioRatioRow(
    val customerId: UUID,
    val customerName: String,
    val statementDate: LocalDate,
    val ratios: Map<RatioCode, BigDecimal>,
    val alerts: List<RatioAlert>,
)

data class RatioTrendPoint(
    val statementDate: LocalDate,
    val value: BigDecimal,
)

data class RatioAlert(
    val ratioCode: RatioCode,
    val ratioLabel: String,
    val previousValue: BigDecimal?,
    val currentValue: BigDecimal,
    val changePercent: BigDecimal?,
    val severity: AlertSeverity,
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }

data class PortfolioQueryRequest(
    val ratioCode: RatioCode? = null,
    val customerIds: List<UUID>? = null,
    val minValue: BigDecimal? = null,
    val maxValue: BigDecimal? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sortBy: String? = null,
    val sortDirection: String? = null,
    val page: Int = 0,
    val size: Int = 50,
)
