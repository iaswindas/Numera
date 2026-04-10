package com.numera.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "numera")
data class NumeraProperties(
    val jwt: Jwt = Jwt(),
    val ml: Ml = Ml(),
    val storage: Storage = Storage(),
) {
    data class Jwt(
        val secret: String = "change-me-please-change-me-please-change-me",
        val accessExpirationMs: Long = 3600000,
        val refreshExpirationMs: Long = 604800000,
    )

    data class Ml(
        val ocrServiceUrl: String = "http://localhost:8001/api",
        val mlServiceUrl: String = "http://localhost:8002/api",
        val timeoutMs: Long = 120000,
        val retryMaxAttempts: Int = 3,
        val retryBackoffMs: Long = 500,
        val circuitBreakerFailureThreshold: Int = 3,
        val circuitBreakerOpenMs: Long = 15000,
    )

    data class Storage(
        val endpoint: String = "http://localhost:9000",
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
        val bucket: String = "numera-docs",
    )
}