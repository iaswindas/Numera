package com.numera.shared.copilot

import com.fasterxml.jackson.annotation.JsonProperty
import com.numera.shared.config.NumeraProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.util.retry.Retry
import java.time.Duration

@RestController
@RequestMapping("/api/copilot")
class CopilotController(
    webClientBuilder: WebClient.Builder,
    private val config: NumeraProperties,
) {
    private val log = LoggerFactory.getLogger(CopilotController::class.java)

    private val mlClient: WebClient = webClientBuilder
        .baseUrl(config.ml.mlServiceUrl)
        .build()

    // ── DTOs ────────────────────────────────────────────────────────

    data class CopilotQueryRequest(
        val question: String,
        @JsonProperty("collections") val collections: List<String>? = null,
        @JsonProperty("top_k") val topK: Int = 5,
        @JsonProperty("customer_id") val customerId: String? = null,
    )

    data class CitationDto(
        @JsonProperty("source_id") val sourceId: String,
        val text: String,
        val collection: String,
        val score: Double,
        val metadata: Map<String, Any?> = emptyMap(),
    )

    data class CopilotQueryResponse(
        val answer: String,
        val citations: List<CitationDto>,
        val model: String,
        val provider: String,
        @JsonProperty("latency_ms") val latencyMs: Int,
        @JsonProperty("context_tokens") val contextTokens: Int = 0,
    )

    data class NlQueryRequest(val query: String)

    data class NlQueryResponse(
        val intent: String,
        val filters: Map<String, Any?>,
        @JsonProperty("sort_by") val sortBy: String? = null,
        @JsonProperty("sort_order") val sortOrder: String = "desc",
        val limit: Int = 20,
        val confidence: Double = 0.0,
        @JsonProperty("raw_query") val rawQuery: String = "",
    )

    data class CopilotStatusResponse(
        val status: String,
        val provider: String,
        val collections: Map<String, Int>,
    )

    // ── Endpoints ───────────────────────────────────────────────────

    @PostMapping("/query")
    fun query(
        @RequestBody request: CopilotQueryRequest,
        @AuthenticationPrincipal user: UserDetails?,
    ): CopilotQueryResponse {
        log.info("Copilot query from user={}: {}", user?.username ?: "anonymous", request.question.take(80))
        return proxy<CopilotQueryResponse>(
            method = "POST",
            path = "/ml/copilot/query",
            body = request,
        )
    }

    @PostMapping("/query/parse")
    fun parseQuery(
        @RequestBody request: NlQueryRequest,
    ): NlQueryResponse {
        log.debug("NL query parse: {}", request.query.take(80))
        return proxy<NlQueryResponse>(
            method = "POST",
            path = "/ml/copilot/query/parse",
            body = request,
        )
    }

    @GetMapping("/status")
    fun status(): CopilotStatusResponse {
        return proxy<CopilotStatusResponse>(
            method = "GET",
            path = "/ml/copilot/status",
            body = null,
        )
    }

    // ── Proxy helper ────────────────────────────────────────────────

    private inline fun <reified T : Any> proxy(
        method: String,
        path: String,
        body: Any?,
    ): T {
        try {
            val spec = when (method) {
                "POST" -> mlClient.post().uri(path).let { b ->
                    if (body != null) b.bodyValue(body) else b
                }
                else -> mlClient.get().uri(path)
            }

            @Suppress("UNCHECKED_CAST")
            val responseSpec = (spec as WebClient.RequestHeadersSpec<*>)
                .header("Content-Type", "application/json")
                .retrieve()

            return responseSpec
                .bodyToMono(T::class.java)
                .retryWhen(
                    Retry.backoff(
                        config.ml.retryMaxAttempts.toLong(),
                        Duration.ofMillis(config.ml.retryBackoffMs),
                    ).filter { it is java.io.IOException }
                )
                .block(Duration.ofMillis(config.ml.timeoutMs))
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from ML service")
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            log.error("Copilot proxy failed for {} {}: {}", method, path, ex.message)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "ML service unavailable: ${ex.message}",
            )
        }
    }
}
