package com.numera.shared.infrastructure

data class BrokerEventMessage(
    val topic: String,
    val eventType: String,
    val payloadJson: String,
    val tenantId: String?,
)

interface BrokerEventDispatcher {
    fun dispatch(message: BrokerEventMessage)
}
