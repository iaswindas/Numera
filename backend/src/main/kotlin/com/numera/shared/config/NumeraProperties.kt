package com.numera.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "numera")
data class NumeraProperties(
    val jwt: Jwt = Jwt(),
    val ml: Ml = Ml(),
    val storage: Storage = Storage(),
    val events: Events = Events(),
    val features: Features = Features(),
) {
    data class Features(
        val workflowEngine: Boolean = false,
        val eventBroker: Boolean = false,
        val zkRfaAudit: Boolean = false,
        val rsBsnPredictor: Boolean = false,
        val ngMilpSolver: Boolean = true,
        val hsparKnowledgeGraph: Boolean = false,
        val owpggrAnomaly: Boolean = false,
        val fsoFederatedLearning: Boolean = false,
        val stghFingerprinting: Boolean = true,
        val externalIntegrations: Boolean = false,
    )

    data class Jwt(
        val secret: String = "",
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
        val accessKey: String = "",
        val secretKey: String = "",
        val bucket: String = "numera-docs",
    )

    data class Events(
        val brokerEnabled: Boolean = false,
        val inProcessFallbackEnabled: Boolean = true,
        val topicPrefix: String = "numera",
    )
}