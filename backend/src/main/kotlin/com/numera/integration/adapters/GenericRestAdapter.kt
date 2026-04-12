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
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal

@Component
class GenericRestAdapter(
    webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
) : ExternalAdapter {

    private val log = LoggerFactory.getLogger(GenericRestAdapter::class.java)
    private val webClientBuilder = webClientBuilder

    override fun systemType(): ExternalSystemType = ExternalSystemType.GENERIC_REST

    override fun pushSpread(system: ExternalSystem, spreadData: CanonicalSpreadPayload): AdapterResponse {
        val client = buildClient(system)
        val config = parseConfig(system)
        val endpoint = config["pushEndpoint"]?.toString() ?: "/spreads"

        return try {
            val body = mapFromCanonical(spreadData)
            val response = client.post()
                .uri(endpoint)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            val externalId = response?.get("id")?.toString()
            AdapterResponse(success = true, externalId = externalId, message = "Spread pushed via generic REST")
        } catch (ex: WebClientResponseException) {
            AdapterResponse(
                success = false,
                errorCode = "HTTP_${ex.statusCode.value()}",
                message = ex.responseBodyAsString,
            )
        } catch (ex: Exception) {
            log.error("Generic REST push failed: {}", ex.message, ex)
            AdapterResponse(success = false, errorCode = "TRANSPORT_ERROR", message = ex.message)
        }
    }

    override fun pullMetadata(system: ExternalSystem, externalRef: String): CanonicalSpreadPayload? {
        val client = buildClient(system)
        val config = parseConfig(system)
        val endpoint = config["pullEndpoint"]?.toString() ?: "/spreads/{ref}"

        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri(endpoint, externalRef)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any> ?: return null

            mapToCanonical(response)
        } catch (ex: Exception) {
            log.error("Generic REST pull failed: {}", ex.message)
            null
        }
    }

    override fun validateConnection(system: ExternalSystem): Boolean {
        val client = buildClient(system)
        val config = parseConfig(system)
        val healthEndpoint = config["healthEndpoint"]?.toString() ?: "/health"

        return try {
            client.get()
                .uri(healthEndpoint)
                .retrieve()
                .toBodilessEntity()
                .block()
            true
        } catch (ex: Exception) {
            log.warn("Generic REST connection test failed for {}: {}", system.name, ex.message)
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun mapToCanonical(externalData: Map<String, Any>): CanonicalSpreadPayload {
        val lineItems = (externalData["lineItems"] as? List<Map<String, Any>>)?.map { item ->
            CanonicalLineItem(
                lineItemCode = item["code"]?.toString() ?: "",
                label = item["label"]?.toString() ?: "",
                value = BigDecimal(item["value"]?.toString() ?: "0"),
                source = CanonicalSource.MANUAL,
                confidence = null,
            )
        } ?: emptyList()

        return CanonicalSpreadPayload(
            customerId = externalData["customerId"]?.toString() ?: "",
            customerName = externalData["customerName"]?.toString() ?: "",
            period = externalData["period"]?.toString() ?: "",
            periodType = externalData["periodType"]?.toString() ?: "",
            templateName = externalData["templateName"]?.toString() ?: "",
            lineItems = lineItems,
        )
    }

    override fun mapFromCanonical(canonicalData: CanonicalSpreadPayload): Map<String, Any> {
        return mapOf(
            "customerId" to canonicalData.customerId,
            "customerName" to canonicalData.customerName,
            "period" to canonicalData.period,
            "periodType" to canonicalData.periodType,
            "templateName" to canonicalData.templateName,
            "lineItems" to canonicalData.lineItems.map { item ->
                mapOf(
                    "code" to item.lineItemCode,
                    "label" to item.label,
                    "value" to item.value.toPlainString(),
                )
            },
        )
    }

    private fun buildClient(system: ExternalSystem): WebClient {
        val builder = webClientBuilder
            .baseUrl(system.baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        if (!system.apiKey.isNullOrBlank()) {
            builder.defaultHeader("Authorization", "Bearer ${system.apiKey}")
        }
        return builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConfig(system: ExternalSystem): Map<String, Any> {
        if (system.configJson.isNullOrBlank()) return emptyMap()
        return try {
            objectMapper.readValue(system.configJson, Map::class.java) as Map<String, Any>
        } catch (ex: Exception) {
            log.warn("Failed to parse configJson for system {}: {}", system.id, ex.message)
            emptyMap()
        }
    }
}
