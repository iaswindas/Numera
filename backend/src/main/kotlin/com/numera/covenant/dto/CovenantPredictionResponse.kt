package com.numera.covenant.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class CovenantPredictionResponse(
    @JsonProperty("breach_probability")
    val breachProbability: BigDecimal,
    @JsonProperty("confidence_interval")
    val confidenceInterval: CovenantPredictionConfidenceInterval,
    @JsonProperty("forecast")
    val forecast: List<CovenantPredictionForecastPoint>,
    @JsonProperty("factors")
    val factors: List<CovenantPredictionFactor>,
)

data class CovenantPredictionConfidenceInterval(
    @JsonProperty("lower")
    val lower: BigDecimal,
    @JsonProperty("upper")
    val upper: BigDecimal,
)

data class CovenantPredictionForecastPoint(
    @JsonProperty("period")
    val period: String,
    @JsonProperty("expected_value")
    val expectedValue: BigDecimal,
    @JsonProperty("breach_risk")
    val breachRisk: BigDecimal,
)

data class CovenantPredictionFactor(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("impact")
    val impact: BigDecimal,
)
