package com.numera.covenant.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── Trendline ─────────────────────────────────────────────────────────

data class TrendlinePoint(
    val period: String,
    val value: BigDecimal?,
    val predictedValue: BigDecimal?,
    val lowerBand: BigDecimal?,
    val upperBand: BigDecimal?,
)

data class TrendlineData(
    val covenantId: UUID,
    val covenantName: String,
    val threshold: BigDecimal?,
    val direction: String?,
    val points: List<TrendlinePoint>,
    val regime: String?,
)

// ── Risk Heatmap ──────────────────────────────────────────────────────

data class RiskHeatmapRow(
    val customerId: UUID,
    val customerName: String,
    val cells: List<RiskHeatmapCell>,
)

data class RiskHeatmapCell(
    val covenantId: UUID,
    val covenantName: String,
    val breachProbability: BigDecimal,
    val currentValue: BigDecimal?,
    val threshold: BigDecimal?,
    val status: String,
    val lastUpdated: Instant,
)

data class RiskHeatmapData(
    val rows: List<RiskHeatmapRow>,
    val covenantColumns: List<CovenantColumn>,
)

data class CovenantColumn(
    val covenantId: UUID,
    val covenantName: String,
)

// ── Calendar ──────────────────────────────────────────────────────────

data class CalendarEntry(
    val covenantId: UUID,
    val monitoringItemId: UUID,
    val customerName: String,
    val covenantName: String,
    val dueDate: LocalDate,
    val status: String,
    val daysUntilDue: Int,
)

// ── Portfolio Summary ─────────────────────────────────────────────────

data class PortfolioSummary(
    val totalCustomers: Int,
    val totalCovenants: Int,
    val activeMonitoringItems: Int,
    val breachedCount: Int,
    val overdueCount: Int,
    val averageBreachProbability: Double,
    val highRiskCount: Int,
    val statusDistribution: Map<String, Int>,
)
