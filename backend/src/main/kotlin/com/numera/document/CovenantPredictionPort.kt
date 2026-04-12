package com.numera.document

import com.numera.document.infrastructure.MlServiceClient
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Public API for covenant-breach prediction requests exposed by the document module.
 *
 * Lives in the document ROOT package so the covenant module can invoke ML prediction without
 * crossing into document's private infrastructure package.
 */
@Service
class CovenantPredictionPort(
    private val mlServiceClient: MlServiceClient,
) {
    fun predictCovenantBreach(request: CovenantPredictionRequest): CovenantPredictionResponse {
        val response = mlServiceClient.predictCovenantBreach(
            MlServiceClient.CovenantPredictionRequest(
                covenantId = request.covenantId,
                threshold = request.threshold,
                direction = request.direction,
                history = request.history.map {
                    MlServiceClient.CovenantPredictionHistoryPoint(
                        period = it.period,
                        value = it.value,
                    )
                },
                periodsAhead = request.periodsAhead,
            )
        )
        return CovenantPredictionResponse(
            breachProbability = response.breachProbability,
            confidenceInterval = CovenantPredictionConfidenceInterval(
                lower = response.confidenceInterval.lower,
                upper = response.confidenceInterval.upper,
            ),
            forecast = response.forecast.map {
                CovenantPredictionForecastPoint(
                    period = it.period,
                    expectedValue = it.expectedValue,
                    breachRisk = it.breachRisk,
                )
            },
            factors = response.factors.map {
                CovenantPredictionFactor(
                    name = it.name,
                    impact = it.impact,
                )
            },
        )
    }

    fun predictCovenantBreachRSBSN(request: RSBSNPredictionRequest): RSBSNPredictionResponse {
        val response = mlServiceClient.predictCovenantBreachRSBSN(
            MlServiceClient.RSBSNPredictionRequest(
                covenant_id = request.covenantId,
                threshold = request.threshold,
                direction = request.direction,
                history = request.history,
                periods_ahead = request.periodsAhead,
            )
        )
        return RSBSNPredictionResponse(
            breachProbability = response.breach_probability,
            confidenceInterval = CovenantPredictionConfidenceInterval(
                lower = response.confidence_interval.lower,
                upper = response.confidence_interval.upper,
            ),
            forecasts = response.forecasts.map {
                CovenantPredictionForecastPoint(
                    period = it.period.toString(),
                    expectedValue = it.expected_value,
                    breachRisk = it.breach_risk,
                )
            },
            regimeDetection = RSBSNRegimeDetection(
                regime = response.regime_detection.regime,
                probability = response.regime_detection.probability,
                transitionMatrix = response.regime_detection.transition_matrix,
            ),
            factors = response.factors,
        )
    }
}

data class CovenantPredictionRequest(
    val covenantId: String,
    val threshold: BigDecimal,
    val direction: String,
    val history: List<CovenantPredictionHistoryPoint>,
    val periodsAhead: Int = 4,
)

data class CovenantPredictionHistoryPoint(
    val period: String,
    val value: BigDecimal,
)

data class CovenantPredictionResponse(
    val breachProbability: BigDecimal,
    val confidenceInterval: CovenantPredictionConfidenceInterval,
    val forecast: List<CovenantPredictionForecastPoint>,
    val factors: List<CovenantPredictionFactor>,
)

data class CovenantPredictionConfidenceInterval(
    val lower: BigDecimal,
    val upper: BigDecimal,
)

data class CovenantPredictionForecastPoint(
    val period: String,
    val expectedValue: BigDecimal,
    val breachRisk: BigDecimal,
)

data class CovenantPredictionFactor(
    val name: String,
    val impact: BigDecimal,
)

// ── RS-BSN DTOs ─────────────────────────────────────────────────────

data class RSBSNPredictionRequest(
    val covenantId: String,
    val threshold: BigDecimal,
    val direction: String,
    val history: List<Float>,
    val periodsAhead: Int = 4,
)

data class RSBSNPredictionResponse(
    val breachProbability: BigDecimal,
    val confidenceInterval: CovenantPredictionConfidenceInterval,
    val forecasts: List<CovenantPredictionForecastPoint>,
    val regimeDetection: RSBSNRegimeDetection,
    val factors: List<String>,
)

data class RSBSNRegimeDetection(
    val regime: String,
    val probability: BigDecimal,
    val transitionMatrix: List<List<Float>>,
)
