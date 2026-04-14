package com.numera.document.infrastructure

import com.numera.shared.config.NumeraProperties
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class MlServiceClient(
    webClientBuilder: WebClient.Builder,
    private val config: NumeraProperties,
) {
    private val ocrClient = webClientBuilder
        .baseUrl(config.ml.ocrServiceUrl)
        .defaultHeader("X-API-Key", config.ml.ocrApiKey)
        .build()

    private val mlClient = webClientBuilder
        .baseUrl(config.ml.mlServiceUrl)
        .defaultHeader("X-API-Key", config.ml.mlApiKey)
        .build()

    private val circuits = ConcurrentHashMap<String, CircuitState>()

    private data class CircuitState(
        var failures: Int = 0,
        var openedUntilEpochMs: Long = 0,
    )

    data class OcrRequest(
        val document_id: String,
        val storage_path: String,
        val language: String = "en",
        val password: String? = null,
    )
    data class OcrTextBlock(val text: String, val confidence: Float, val bbox: List<Float>, val page: Int)
    data class OcrResponse(
        val document_id: String,
        val total_pages: Int,
        val pages_processed: Int,
        val text_blocks: List<OcrTextBlock>,
        val full_text: String,
        val processing_time_ms: Int,
        val backend: String,
        val pdf_type: String,
        val pages_failed: Int = 0,
        val errors: List<Map<String, Any>> = emptyList(),
    )

    fun extractText(
        documentId: String,
        storagePath: String,
        language: String = "en",
        password: String? = null,
    ): OcrResponse =
        executeWithResilience("ocr") {
            ocrClient.post().uri("/ocr/extract")
                .bodyValue(OcrRequest(documentId, storagePath, language, password))
                .retrieve().bodyToMono(OcrResponse::class.java)
                .withMlResilience("ocr.extractText")
                .block()!!
        }

    data class TableDetectRequest(
        val document_id: String,
        val storage_path: String,
        val password: String? = null,
    )
    data class TableDetectResponse(
        val document_id: String,
        val total_pages: Int,
        val tables_detected: Int,
        val tables: List<Map<String, Any>>,
        val processing_time_ms: Int,
        val backend: String,
        val pdf_type: String,
        val tables_filtered: Int = 0,
        val errors: List<Map<String, Any>> = emptyList(),
    )

    fun detectTables(documentId: String, storagePath: String, password: String? = null): TableDetectResponse =
        executeWithResilience("ocr") {
            ocrClient.post().uri("/ocr/tables/detect")
                .bodyValue(TableDetectRequest(documentId, storagePath, password))
                .retrieve().bodyToMono(TableDetectResponse::class.java)
                .withMlResilience("ocr.detectTables")
                .block()!!
        }

    data class CovenantPredictionRequest(
        val covenantId: String,
        val threshold: java.math.BigDecimal,
        val direction: String,
        val history: List<CovenantPredictionHistoryPoint>,
        val periodsAhead: Int = 4,
    )

    data class CovenantPredictionHistoryPoint(
        val period: String,
        val value: java.math.BigDecimal,
    )

    data class CovenantPredictionResponse(
        val breachProbability: java.math.BigDecimal,
        val confidenceInterval: CovenantPredictionConfidenceInterval,
        val forecast: List<CovenantPredictionForecastPoint>,
        val factors: List<CovenantPredictionFactor>,
    )

    data class CovenantPredictionConfidenceInterval(
        val lower: java.math.BigDecimal,
        val upper: java.math.BigDecimal,
    )

    data class CovenantPredictionForecastPoint(
        val period: String,
        val expectedValue: java.math.BigDecimal,
        val breachRisk: java.math.BigDecimal,
    )

    data class CovenantPredictionFactor(
        val name: String,
        val impact: java.math.BigDecimal,
    )

    fun predictCovenantBreach(request: CovenantPredictionRequest): CovenantPredictionResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/covenant/predict")
                .bodyValue(request)
                .retrieve().bodyToMono(CovenantPredictionResponse::class.java)
                .withMlResilience("ml.predictCovenantBreach")
                .block()!!
        }

    data class ZoneClassifyRequest(val document_id: String, val tables: List<Map<String, Any>>)
    data class ClassifiedZone(
        val table_id: String,
        val zone_type: String,
        val zone_label: String,
        val confidence: Float,
        val classification_method: String,
        val detected_periods: List<String>,
        val detected_currency: String?,
        val detected_unit: String?,
    )

    data class ZoneClassifyResponse(
        val document_id: String,
        val zones: List<ClassifiedZone>,
        val processing_time_ms: Int,
    )

    fun classifyZones(documentId: String, tables: List<Map<String, Any>>): ZoneClassifyResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/zones/classify")
                .bodyValue(ZoneClassifyRequest(documentId, tables))
                .retrieve().bodyToMono(ZoneClassifyResponse::class.java)
                .withMlResilience("ml.classifyZones")
                .block()!!
        }

    data class MappingSuggestRequest(
        val document_id: String,
        val source_rows: List<Map<String, Any>>,
        val target_items: List<Map<String, Any>>,
        val tenant_id: String? = null,
    )

    data class MappingSuggestResponse(
        val document_id: String,
        val mappings: List<Map<String, Any>>,
        val summary: Map<String, Int>,
        val processing_time_ms: Int,
    )

    fun suggestMappings(request: MappingSuggestRequest): MappingSuggestResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/mapping/suggest")
                .bodyValue(request)
                .retrieve().bodyToMono(MappingSuggestResponse::class.java)
                .withMlResilience("ml.suggestMappings")
                .block()!!
        }

    data class ExpressionSource(
        val row_index: Int,
        val label: String,
        val value: Number?,
        val page: Int,
        val confidence: Float,
    )

    data class ExpressionRecord(
        val target_item_id: String,
        val target_label: String,
        val expression_type: String,
        val sources: List<ExpressionSource> = emptyList(),
        val scale_factor: Float = 1f,
        val computed_value: Number? = null,
        val confidence: Float = 0f,
        val explanation: String = "",
    )

    data class ExpressionBuildRequest(
        val document_id: String,
        val tenant_id: String,
        val customer_id: String,
        val template_id: String,
        val zone_type: String,
        val period_index: Int = 0,
        val extracted_rows: List<Map<String, Any>>,
        val semantic_matches: List<Map<String, Any>>,
        val use_autofill: Boolean = true,
    )

    data class ExpressionBuildRawResponse(
        val document_id: String,
        val template_id: String,
        val zone_type: String,
        val expressions: List<ExpressionRecord>,
        val total_mapped: Int,
        val total_items: Int,
        val coverage_pct: Float,
        val unit_scale: Float,
        val autofilled: Int,
        val validation_results: List<Map<String, Any>>,
    )

    data class ExpressionBuildResponse(
        val document_id: String,
        val template_id: String,
        val zone_type: String,
        val expressions: List<Map<String, Any>>,
        val total_mapped: Int,
        val total_items: Int,
        val coverage_pct: Float,
        val unit_scale: Float,
        val autofilled: Int,
        val validation_results: List<Map<String, Any>>,
    )

    fun buildExpressions(request: ExpressionBuildRequest): ExpressionBuildResponse {
        val response = executeWithResilience("ml") {
            mlClient.post().uri("/ml/expressions/build")
                .bodyValue(request)
                .retrieve().bodyToMono(ExpressionBuildRawResponse::class.java)
                .withMlResilience("ml.buildExpressions")
                .block()!!
        }

        val normalized = response.expressions.map { expression ->
            val firstSource = expression.sources.firstOrNull()
            mapOf(
                "item_code" to resolveItemCode(request, expression.target_item_id),
                "target_item_id" to expression.target_item_id,
                "target_label" to expression.target_label,
                "expression_type" to expression.expression_type,
                "scale_factor" to expression.scale_factor,
                "value" to (expression.computed_value ?: 0),
                "computed_value" to (expression.computed_value ?: 0),
                "confidence" to expression.confidence,
                "confidence_level" to confidenceLevel(expression.confidence),
                "source_text" to (firstSource?.label ?: ""),
                "page" to (firstSource?.page ?: 0),
                "autofilled" to (response.autofilled > 0),
                "explanation" to expression.explanation,
            )
        }

        return ExpressionBuildResponse(
            document_id = response.document_id,
            template_id = response.template_id,
            zone_type = response.zone_type,
            expressions = normalized,
            total_mapped = response.total_mapped,
            total_items = response.total_items,
            coverage_pct = response.coverage_pct,
            unit_scale = response.unit_scale,
            autofilled = response.autofilled,
            validation_results = response.validation_results,
        )
    }

    data class FeedbackItem(
        val source_text: String,
        val source_zone_type: String,
        val suggested_item_id: String,
        val corrected_item_id: String,
        val correction_type: String,
        val document_id: String,
        val customer_id: String?,
        val tenant_id: String?,
    )

    data class FeedbackRequest(val corrections: List<FeedbackItem>)
    data class FeedbackResponse(
        val accepted: Int,
        val total_stored: Int,
        val message: String? = null,
        val storage: String? = null,
    )

    fun submitFeedback(corrections: List<FeedbackItem>): FeedbackResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/feedback")
                .bodyValue(FeedbackRequest(corrections))
                .retrieve().bodyToMono(FeedbackResponse::class.java)
                .withMlResilience("ml.submitFeedback")
                .block()!!
        }

    // ── RS-BSN Covenant Prediction ──────────────────────────────────────

    data class RSBSNPredictionRequest(
        val covenant_id: String,
        val threshold: java.math.BigDecimal,
        val direction: String,
        val history: List<Float>,
        val periods_ahead: Int = 4,
    )

    data class RSBSNForecastPoint(
        val period: Int,
        val expected_value: java.math.BigDecimal,
        val breach_risk: java.math.BigDecimal,
    )

    data class RSBSNRegimeDetection(
        val regime: String,
        val probability: java.math.BigDecimal,
        val transition_matrix: List<List<Float>>,
    )

    data class RSBSNPredictionResponse(
        val breach_probability: java.math.BigDecimal,
        val confidence_interval: RSBSNConfidenceInterval,
        val forecasts: List<RSBSNForecastPoint>,
        val regime_detection: RSBSNRegimeDetection,
        val factors: List<String>,
    )

    data class RSBSNConfidenceInterval(
        val lower: java.math.BigDecimal,
        val upper: java.math.BigDecimal,
    )

    fun predictCovenantBreachRSBSN(request: RSBSNPredictionRequest): RSBSNPredictionResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/covenants/predict")
                .bodyValue(request)
                .retrieve().bodyToMono(RSBSNPredictionResponse::class.java)
                .withMlResilience("ml.predictCovenantBreachRSBSN")
                .block()!!
        }

    // ── OW-PGGR Anomaly Detection ──────────────────────────────────────

    data class SpreadValueDto(
        val line_item_id: String,
        val label: String,
        val value: java.math.BigDecimal?,
        val zone_type: String? = null,
    )

    data class AnomalyDetectionRequest(
        val spread_values: List<SpreadValueDto>,
        val historical_values: List<List<java.math.BigDecimal?>> = emptyList(),
        val template_validations: List<Map<String, Any>> = emptyList(),
    )

    data class AnomalyDto(
        val line_item_id: String,
        val label: String,
        val anomaly_type: String,
        val severity: String,
        val score: java.math.BigDecimal,
        val message: String,
    )

    data class AnomalyDetectionResponse(
        val anomalies: List<AnomalyDto>,
        val overall_risk_score: java.math.BigDecimal,
        val summary: String,
        val total_items_checked: Int,
        val flagged_count: Int,
    )

    fun detectAnomalies(request: AnomalyDetectionRequest): AnomalyDetectionResponse =
        executeWithResilience("ml") {
            mlClient.post().uri("/ml/anomaly/detect")
                .bodyValue(request)
                .retrieve().bodyToMono(AnomalyDetectionResponse::class.java)
                .withMlResilience("ml.detectAnomalies")
                .block()!!
        }

    private fun <T> executeWithResilience(service: String, action: () -> T): T {
        val state = circuits.computeIfAbsent(service) { CircuitState() }
        val now = System.currentTimeMillis()

        synchronized(state) {
            if (state.openedUntilEpochMs > now) {
                throw IllegalStateException("ML circuit open for $service until ${state.openedUntilEpochMs}")
            }
            if (state.openedUntilEpochMs != 0L && state.openedUntilEpochMs <= now) {
                state.openedUntilEpochMs = 0
                state.failures = 0
            }
        }

        return runCatching(action).onSuccess {
            synchronized(state) {
                state.failures = 0
            }
        }.getOrElse { ex ->
            synchronized(state) {
                state.failures += 1
                if (state.failures >= config.ml.circuitBreakerFailureThreshold) {
                    state.openedUntilEpochMs = now + config.ml.circuitBreakerOpenMs
                }
            }
            throw ex
        }
    }

    private fun <T> reactor.core.publisher.Mono<T>.withMlResilience(operation: String): reactor.core.publisher.Mono<T> {
        val attempts = config.ml.retryMaxAttempts.coerceAtLeast(1)
        val retries = (attempts - 1).coerceAtLeast(0).toLong()

        return this
            .timeout(Duration.ofMillis(config.ml.timeoutMs))
            .retryWhen(
                Retry.backoff(retries, Duration.ofMillis(config.ml.retryBackoffMs))
                    .filter { shouldRetry(it) }
                    .onRetryExhaustedThrow { _, signal ->
                        IllegalStateException("ML operation failed after retries: $operation", signal.failure())
                    }
            )
    }

    private fun shouldRetry(throwable: Throwable): Boolean =
        when (throwable) {
            is WebClientRequestException -> true
            is IOException -> true
            is java.util.concurrent.TimeoutException -> true
            is WebClientResponseException -> throwable.statusCode.is5xxServerError
            else -> false
        }

    private fun confidenceLevel(confidence: Float): String = when {
        confidence >= 0.85f -> "HIGH"
        confidence >= 0.65f -> "MEDIUM"
        else -> "LOW"
    }

    private fun resolveItemCode(request: ExpressionBuildRequest, targetItemId: String): String {
        val exact = request.semantic_matches.firstOrNull { it["target_item_id"]?.toString() == targetItemId }
        if (exact != null) {
            return exact["target_item_code"]?.toString()
                ?: exact["item_code"]?.toString()
                ?: exact["itemCode"]?.toString()
                ?: targetItemId
        }
        return targetItemId
    }
}