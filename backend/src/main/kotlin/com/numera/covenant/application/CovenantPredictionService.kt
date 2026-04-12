package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.domain.CovenantThresholdOperator
import com.numera.covenant.dto.CovenantPredictionHistoryPoint
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.document.CovenantPredictionPort
import com.numera.document.CovenantPredictionRequest
import com.numera.document.RSBSNPredictionRequest
import com.numera.document.CovenantPredictionHistoryPoint as DocumentPredictionHistoryPoint
import com.numera.shared.config.FeatureFlagService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID

/**
 * Computes breach probability for financial covenant monitoring items.
 *
 * The current implementation uses a statistical trend analysis approach:
 * it looks at the last N calculated values and fits a linear regression
 * to extrapolate whether the covenant threshold will be crossed.
 *
 * This will be replaced by the dedicated ML prediction endpoint once
 * the ml-service exposes the /covenants/predict endpoint.
 */
@Service
class CovenantPredictionService(
    private val monitoringRepository: CovenantMonitoringRepository,
    private val covenantPredictionPort: CovenantPredictionPort,
    private val featureFlags: FeatureFlagService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    /**
     * Recalculate breach probability for all active monitoring items
     * that have at least 2 historical data points.
     */
    @Transactional
    fun recalculateAllProbabilities() {
        val items = monitoringRepository.findByTenantId(resolvedTenantId())
            .filter { it.status !in listOf(CovenantStatus.CLOSED, CovenantStatus.MET) }

        items.forEach { item ->
            val points = monitoringRepository.findByCovenantId(item.covenant.id!!)
                .sortedBy { it.periodEnd }
                .mapNotNull { monitoring ->
                    val value = monitoring.calculatedValue ?: monitoring.manualValue ?: return@mapNotNull null
                    CovenantPredictionHistoryPoint(
                        period = formatQuarter(monitoring.periodEnd),
                        value = value,
                    )
                }
                .takeLast(12)

            item.breachProbability = predictWithFallback(item.covenant.id!!, points, item.covenant.thresholdValue, item.covenant.operator)
        }

        monitoringRepository.saveAll(items)
    }

    /**
     * Simple linear trend extrapolation.
     * Returns probability in [0.0, 1.0] or null if insufficient data.
     */
    fun estimateBreachProbability(
        values: List<BigDecimal>,
        threshold: BigDecimal?,
        operator: String?,
    ): BigDecimal {
        if (threshold == null || operator == null || values.size < 2) return BigDecimal("0.5")

        val n = values.size
        val xMean = BigDecimal((n - 1).toDouble() / 2)
        val yMean = values.reduce { a, b -> a + b } / BigDecimal(n)

        // Slope via OLS
        var numerator = BigDecimal.ZERO
        var denominator = BigDecimal.ZERO
        values.forEachIndexed { i, y ->
            val x = BigDecimal(i) - xMean
            numerator += x * (y - yMean)
            denominator += x * x
        }

        val slope = if (denominator == BigDecimal.ZERO) BigDecimal.ZERO
                    else numerator.divide(denominator, MathContext.DECIMAL64)

        // Extrapolate 1 period ahead
        val projected = values.last() + slope

        val willBreach = when (operator) {
            "GTE" -> projected < threshold
            "LTE" -> projected > threshold
            "EQ"  -> (projected - threshold).abs() > BigDecimal("0.01")
            else  -> false
        }

        // Map trend strength to probability
        val trendStrength = if (denominator == BigDecimal.ZERO) BigDecimal.ZERO
                            else slope.abs().divide(threshold.abs().max(BigDecimal.ONE), MathContext.DECIMAL64)
                                .min(BigDecimal.ONE)

        return if (willBreach)
            (BigDecimal("0.5") + trendStrength.multiply(BigDecimal("0.5"))).min(BigDecimal.ONE)
        else
            (BigDecimal("0.5") - trendStrength.multiply(BigDecimal("0.5"))).max(BigDecimal.ZERO)
    }

    private fun predictWithFallback(
        covenantId: UUID,
        history: List<CovenantPredictionHistoryPoint>,
        threshold: BigDecimal?,
        operator: CovenantThresholdOperator?,
    ): BigDecimal {
        val values = history.map { it.value }
        if (threshold == null || operator == null || history.size < 3) {
            return estimateBreachProbability(values, threshold, operator?.name)
        }

        val direction = when (operator) {
            CovenantThresholdOperator.GTE -> "MIN"
            CovenantThresholdOperator.LTE -> "MAX"
            else -> null
        }

        if (direction == null) {
            return estimateBreachProbability(values, threshold, operator.name)
        }

        val tenantId = runCatching { resolvedTenantId().toString() }.getOrNull()

        // Try RS-BSN predictor first when feature flag is enabled
        if (featureFlags.isEnabled("rsBsnPredictor", tenantId)) {
            val rsbsnResult = runCatching {
                val request = RSBSNPredictionRequest(
                    covenantId = covenantId.toString(),
                    threshold = threshold,
                    direction = direction,
                    history = values.map { it.toFloat() },
                    periodsAhead = 4,
                )
                covenantPredictionPort.predictCovenantBreachRSBSN(request).breachProbability
                    .coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
                    .setScale(4, RoundingMode.HALF_UP)
            }
            rsbsnResult.onSuccess { return it }
            rsbsnResult.onFailure {
                logger.warn("RS-BSN prediction failed for covenantId={}, falling back to basic ML", covenantId, it)
            }
        }

        // Fallback: basic ML prediction
        val request = CovenantPredictionRequest(
            covenantId = covenantId.toString(),
            threshold = threshold,
            direction = direction,
            history = history.map { DocumentPredictionHistoryPoint(period = it.period, value = it.value) },
            periodsAhead = 4,
        )

        return runCatching {
            covenantPredictionPort.predictCovenantBreach(request).breachProbability
                .coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP)
        }.onFailure {
            logger.warn("ML covenant prediction unavailable for covenantId={}, using fallback", covenantId)
        }.getOrElse {
            estimateBreachProbability(values, threshold, operator.name)
        }
    }

    private fun formatQuarter(date: java.time.LocalDate): String {
        val quarter = ((date.monthValue - 1) / 3) + 1
        return "${date.year}-Q$quarter"
    }
}
