package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.shared.domain.TenantAwareEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
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
) {

    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    /**
     * Recalculate breach probability for all active monitoring items
     * that have at least 2 historical data points.
     */
    @Transactional
    fun recalculateAllProbabilities() {
        val items = monitoringRepository.findByTenantId(tenantId)
            .filter { it.status !in listOf(CovenantStatus.CLOSED, CovenantStatus.MET) }

        items.forEach { item ->
            val history = monitoringRepository.findByCovenantId(item.covenant.id!!)
                .mapNotNull { it.calculatedValue ?: it.manualValue }
                .takeLast(8)

            if (history.size >= 2) {
                val probability = estimateBreachProbability(
                    values = history,
                    threshold = item.covenant.thresholdValue,
                    operator = item.covenant.operator?.name,
                )
                item.breachProbability = probability
            }
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
}
