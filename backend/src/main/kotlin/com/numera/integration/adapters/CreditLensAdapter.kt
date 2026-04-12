package com.numera.integration.adapters

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
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal

@Component
class CreditLensAdapter(
    webClientBuilder: WebClient.Builder,
) : ExternalAdapter {

    private val log = LoggerFactory.getLogger(CreditLensAdapter::class.java)
    private val webClientBuilder = webClientBuilder

    override fun systemType(): ExternalSystemType = ExternalSystemType.CREDITLENS

    override fun pushSpread(system: ExternalSystem, spreadData: CanonicalSpreadPayload): AdapterResponse {
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
            AdapterResponse(success = true, externalId = externalId, message = "Spread pushed to CreditLens")
        } catch (ex: WebClientResponseException) {
            handleHttpError(ex)
        } catch (ex: Exception) {
            log.error("CreditLens push failed: {}", ex.message, ex)
            AdapterResponse(success = false, errorCode = "TRANSPORT_ERROR", message = ex.message)
        }
    }

    override fun pullMetadata(system: ExternalSystem, externalRef: String): CanonicalSpreadPayload? {
        val client = buildClient(system)
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/api/v1/spreads/{ref}", externalRef)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any> ?: return null

            mapToCanonical(response)
        } catch (ex: WebClientResponseException) {
            if (ex.statusCode == HttpStatusCode.valueOf(404)) return null
            log.error("CreditLens pull failed: {}", ex.message)
            null
        }
    }

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

    private fun buildClient(system: ExternalSystem): WebClient =
        webClientBuilder
            .baseUrl(system.baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .defaultHeader("X-Api-Key", system.apiKey ?: "")
            .build()

    private fun handleHttpError(ex: WebClientResponseException): AdapterResponse {
        val code = ex.statusCode.value()
        return if (code in 400..499) {
            AdapterResponse(success = false, errorCode = "CLIENT_ERROR_$code", message = ex.responseBodyAsString)
        } else {
            AdapterResponse(success = false, errorCode = "SERVER_ERROR_$code", message = "CreditLens server error")
        }
    }
}
