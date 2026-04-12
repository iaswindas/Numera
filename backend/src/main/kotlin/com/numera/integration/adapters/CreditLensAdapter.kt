package com.numera.integration.adapters

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.integration.domain.ExternalSystem
import com.numera.integration.domain.ExternalSystemType
import com.numera.integration.spi.AdapterResponse
import com.numera.integration.spi.CanonicalLineItem
import com.numera.integration.spi.CanonicalSource
import com.numera.integration.spi.CanonicalSpreadPayload
import com.numera.integration.spi.ExternalAdapter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Component
class CreditLensAdapter(
    webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
) : ExternalAdapter {

    private val log = LoggerFactory.getLogger(CreditLensAdapter::class.java)
    private val webClientBuilder = webClientBuilder

    // ── OAuth2 token cache per system ────────────────────────────────────
    private data class CachedToken(val accessToken: String, val expiresAt: Instant)

    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    // ── Circuit breaker state per system ─────────────────────────────────
    private data class CircuitState(
        val failures: AtomicInteger = AtomicInteger(0),
        val lastFailure: AtomicReference<Instant> = AtomicReference(Instant.EPOCH),
        val state: AtomicReference<BreakerStatus> = AtomicReference(BreakerStatus.CLOSED),
    )

    private enum class BreakerStatus { CLOSED, OPEN, HALF_OPEN }

    private val circuitBreakers = ConcurrentHashMap<String, CircuitState>()

    companion object {
        private const val FAILURE_THRESHOLD = 5
        private val OPEN_DURATION: Duration = Duration.ofSeconds(60)
        private val TOKEN_EXPIRY_BUFFER: Duration = Duration.ofSeconds(30)
    }

    override fun systemType(): ExternalSystemType = ExternalSystemType.CREDITLENS

    // ── Push ─────────────────────────────────────────────────────────────

    override fun pushSpread(system: ExternalSystem, spreadData: CanonicalSpreadPayload): AdapterResponse {
        checkCircuitBreaker(system)
        val client = buildClient(system)
        return try {
            val body = mapFromCanonical(spreadData)
            val response = client.post()
                .uri("/api/v1/spreads")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            val externalId = response?.get("spreadId")?.toString()
            recordSuccess(system)
            AdapterResponse(success = true, externalId = externalId, message = "Spread pushed to CreditLens")
        } catch (ex: WebClientResponseException) {
            recordFailure(system)
            handleHttpError(ex)
        } catch (ex: Exception) {
            recordFailure(system)
            log.error("CreditLens push failed: {}", ex.message, ex)
            AdapterResponse(success = false, errorCode = "TRANSPORT_ERROR", message = ex.message)
        }
    }

    // ── Pull metadata ────────────────────────────────────────────────────

    override fun pullMetadata(system: ExternalSystem, externalRef: String): CanonicalSpreadPayload? {
        checkCircuitBreaker(system)
        val client = buildClient(system)
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/api/v1/spreads/{ref}", externalRef)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any> ?: return null

            recordSuccess(system)
            mapToCanonical(response)
        } catch (ex: WebClientResponseException) {
            recordFailure(system)
            if (ex.statusCode == HttpStatusCode.valueOf(404)) return null
            log.error("CreditLens pull failed: {}", ex.message)
            null
        }
    }

    // ── Pull model templates ─────────────────────────────────────────────

    fun pullModel(system: ExternalSystem, modelId: String): Map<String, Any>? {
        checkCircuitBreaker(system)
        val client = buildClient(system)
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/api/v1/models/{id}", modelId)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            recordSuccess(system)
            response
        } catch (ex: WebClientResponseException) {
            recordFailure(system)
            if (ex.statusCode == HttpStatusCode.valueOf(404)) return null
            log.error("CreditLens model pull failed for {}: {}", modelId, ex.message)
            null
        } catch (ex: Exception) {
            recordFailure(system)
            log.error("CreditLens model pull transport error: {}", ex.message, ex)
            null
        }
    }

    // ── Pull historical spreads ──────────────────────────────────────────

    fun pullHistoricalSpreads(system: ExternalSystem, borrowerId: String, limit: Int = 20): List<CanonicalSpreadPayload> {
        checkCircuitBreaker(system)
        val client = buildClient(system)
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri { uri ->
                    uri.path("/api/v1/borrowers/{id}/spreads")
                        .queryParam("limit", limit)
                        .build(borrowerId)
                }
                .retrieve()
                .bodyToMono(List::class.java)
                .block() as? List<Map<String, Any>> ?: emptyList()

            recordSuccess(system)
            response.map { mapToCanonical(it) }
        } catch (ex: Exception) {
            recordFailure(system)
            log.error("CreditLens historical spreads pull failed for borrower {}: {}", borrowerId, ex.message)
            emptyList()
        }
    }

    // ── Sync metadata (bidirectional) ────────────────────────────────────

    fun syncMetadata(system: ExternalSystem, entityType: String, externalRef: String): AdapterResponse {
        checkCircuitBreaker(system)
        val client = buildClient(system)
        return try {
            val response = client.get()
                .uri("/api/v1/metadata/{type}/{ref}", entityType, externalRef)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            recordSuccess(system)
            AdapterResponse(
                success = true,
                externalId = response?.get("id")?.toString(),
                message = "Metadata synced from CreditLens for $entityType/$externalRef",
            )
        } catch (ex: WebClientResponseException) {
            recordFailure(system)
            handleHttpError(ex)
        } catch (ex: Exception) {
            recordFailure(system)
            log.error("CreditLens metadata sync failed: {}", ex.message, ex)
            AdapterResponse(success = false, errorCode = "TRANSPORT_ERROR", message = ex.message)
        }
    }

    // ── Connection validation ────────────────────────────────────────────

    override fun validateConnection(system: ExternalSystem): Boolean {
        val client = buildClient(system)
        return try {
            client.get()
                .uri("/api/v1/health")
                .retrieve()
                .toBodilessEntity()
                .block()
            true
        } catch (ex: Exception) {
            log.warn("CreditLens connection test failed for {}: {}", system.name, ex.message)
            false
        }
    }

    // ── Canonical mapping ────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override fun mapToCanonical(externalData: Map<String, Any>): CanonicalSpreadPayload {
        val lineItems = (externalData["lineItems"] as? List<Map<String, Any>>)?.map { item ->
            CanonicalLineItem(
                lineItemCode = item["code"]?.toString() ?: "",
                label = item["description"]?.toString() ?: "",
                value = BigDecimal(item["amount"]?.toString() ?: "0"),
                source = CanonicalSource.MANUAL,
                confidence = null,
            )
        } ?: emptyList()

        return CanonicalSpreadPayload(
            customerId = externalData["borrowerId"]?.toString() ?: "",
            customerName = externalData["borrowerName"]?.toString() ?: "",
            period = externalData["statementDate"]?.toString() ?: "",
            periodType = externalData["periodType"]?.toString() ?: "ANNUAL",
            templateName = externalData["templateName"]?.toString() ?: "",
            lineItems = lineItems,
            metadata = mapOf("creditLensId" to (externalData["spreadId"]?.toString() ?: "")),
        )
    }

    override fun mapFromCanonical(canonicalData: CanonicalSpreadPayload): Map<String, Any> {
        return mapOf(
            "borrowerId" to canonicalData.customerId,
            "borrowerName" to canonicalData.customerName,
            "statementDate" to canonicalData.period,
            "periodType" to canonicalData.periodType,
            "templateName" to canonicalData.templateName,
            "lineItems" to canonicalData.lineItems.map { item ->
                mapOf(
                    "code" to item.lineItemCode,
                    "description" to item.label,
                    "amount" to item.value.toPlainString(),
                )
            },
        )
    }

    // ── OAuth2 client credentials flow ───────────────────────────────────

    private fun obtainAccessToken(system: ExternalSystem): String? {
        val config = parseConfig(system)
        val clientId = config["clientId"] ?: return null
        val clientSecret = config["clientSecret"] ?: return null
        val tokenUrl = config["tokenUrl"] ?: "${system.baseUrl}/oauth/token"
        val scopes = config["scopes"] ?: "spreads.read spreads.write"

        val cacheKey = "${system.id}:$clientId"
        tokenCache[cacheKey]?.let { cached ->
            if (cached.expiresAt.isAfter(Instant.now().plus(TOKEN_EXPIRY_BUFFER))) {
                return cached.accessToken
            }
        }

        val tokenClient = webClientBuilder.baseUrl(tokenUrl).build()
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = tokenClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("scope", scopes),
                )
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            val accessToken = response?.get("access_token")?.toString()
                ?: throw IllegalStateException("No access_token in OAuth response")
            val expiresIn = (response["expires_in"] as? Number)?.toLong() ?: 3600L

            tokenCache[cacheKey] = CachedToken(accessToken, Instant.now().plusSeconds(expiresIn))
            accessToken
        } catch (ex: Exception) {
            log.error("OAuth2 token acquisition failed for system {}: {}", system.name, ex.message)
            null
        }
    }

    // ── WebClient builder ────────────────────────────────────────────────

    private fun buildClient(system: ExternalSystem): WebClient {
        val builder = webClientBuilder
            .baseUrl(system.baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")

        // Prefer OAuth2; fall back to API key
        val token = obtainAccessToken(system)
        if (token != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } else if (!system.apiKey.isNullOrBlank()) {
            builder.defaultHeader("X-Api-Key", system.apiKey!!)
        }

        return builder.build()
    }

    // ── Circuit breaker ──────────────────────────────────────────────────

    private fun circuitFor(system: ExternalSystem): CircuitState =
        circuitBreakers.computeIfAbsent(system.id.toString()) { CircuitState() }

    private fun checkCircuitBreaker(system: ExternalSystem) {
        val circuit = circuitFor(system)
        when (circuit.state.get()) {
            BreakerStatus.OPEN -> {
                val elapsed = Duration.between(circuit.lastFailure.get(), Instant.now())
                if (elapsed >= OPEN_DURATION) {
                    circuit.state.set(BreakerStatus.HALF_OPEN)
                    log.info("Circuit breaker half-open for system {}", system.name)
                } else {
                    throw IllegalStateException("Circuit breaker OPEN for system ${system.name}; retry after ${OPEN_DURATION.minus(elapsed).seconds}s")
                }
            }
            BreakerStatus.HALF_OPEN, BreakerStatus.CLOSED -> { /* allow */ }
        }
    }

    private fun recordSuccess(system: ExternalSystem) {
        val circuit = circuitFor(system)
        circuit.failures.set(0)
        if (circuit.state.get() == BreakerStatus.HALF_OPEN) {
            circuit.state.set(BreakerStatus.CLOSED)
            log.info("Circuit breaker closed for system {}", system.name)
        }
    }

    private fun recordFailure(system: ExternalSystem) {
        val circuit = circuitFor(system)
        val count = circuit.failures.incrementAndGet()
        circuit.lastFailure.set(Instant.now())
        if (count >= FAILURE_THRESHOLD) {
            circuit.state.set(BreakerStatus.OPEN)
            log.warn("Circuit breaker OPEN for system {} after {} consecutive failures", system.name, count)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun parseConfig(system: ExternalSystem): Map<String, String> {
        if (system.configJson.isNullOrBlank()) return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(system.configJson, Map::class.java) as Map<String, String>
        } catch (ex: Exception) {
            log.warn("Failed to parse configJson for system {}: {}", system.name, ex.message)
            emptyMap()
        }
    }

    private fun handleHttpError(ex: WebClientResponseException): AdapterResponse {
        val code = ex.statusCode.value()
        return if (code in 400..499) {
            AdapterResponse(success = false, errorCode = "CLIENT_ERROR_$code", message = ex.responseBodyAsString)
        } else {
            AdapterResponse(success = false, errorCode = "SERVER_ERROR_$code", message = "CreditLens server error")
        }
    }
}
