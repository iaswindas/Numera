package com.numera.covenant.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class CovenantPredictionRequest(
    @JsonProperty("covenantId")
    val covenantId: String,
    @JsonProperty("threshold")
    val threshold: BigDecimal,
    @JsonProperty("direction")
    val direction: String,
    @JsonProperty("history")
    val history: List<CovenantPredictionHistoryPoint>,
    @JsonProperty("periodsAhead")
    val periodsAhead: Int = 4,
)

data class CovenantPredictionHistoryPoint(
    @JsonProperty("period")
    val period: String,
    @JsonProperty("value")
    val value: BigDecimal,
)
