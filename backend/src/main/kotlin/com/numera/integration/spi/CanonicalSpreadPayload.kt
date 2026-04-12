package com.numera.integration.spi

import java.math.BigDecimal

/**
 * Canonical representation of spread data for external system exchange.
 * Adapters map to/from this format.
 */
data class CanonicalSpreadPayload(
    val customerId: String,
    val customerName: String,
    val period: String,
    val periodType: String,
    val templateName: String,
    val lineItems: List<CanonicalLineItem>,
    val metadata: Map<String, String> = emptyMap(),
)

data class CanonicalLineItem(
    val lineItemCode: String,
    val label: String,
    val value: BigDecimal,
    val source: CanonicalSource = CanonicalSource.MANUAL,
    val confidence: BigDecimal? = null,
)

enum class CanonicalSource {
    AI,
    MANUAL,
    OVERRIDE,
}
