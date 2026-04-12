package com.numera.integration.spi

/**
 * Standard response returned by every adapter operation.
 */
data class AdapterResponse(
    val success: Boolean,
    val externalId: String? = null,
    val message: String? = null,
    val errorCode: String? = null,
)
