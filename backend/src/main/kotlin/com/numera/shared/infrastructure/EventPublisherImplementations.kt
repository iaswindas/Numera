package com.numera.shared.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.config.NumeraProperties
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

class SpringDomainEventPublisher(
    private val delegate: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: Any) {
        delegate.publishEvent(event)
    }
}

class NoopBrokerEventDispatcher : BrokerEventDispatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun dispatch(message: BrokerEventMessage) {
        log.debug(
            "Broker dispatcher not configured. Dropping broker event topic={} type={}",
            message.topic,
            message.eventType,
        )
    }
}

class BrokerAwareDomainEventPublisher(
    private val inProcessPublisher: DomainEventPublisher,
    private val brokerEventDispatcher: BrokerEventDispatcher,
    private val objectMapper: ObjectMapper,
    private val eventProperties: NumeraProperties.Events,
) : DomainEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: Any) {
        if (!eventProperties.brokerEnabled) {
            inProcessPublisher.publish(event)
            return
        }

        val delivered = runCatching {
            brokerEventDispatcher.dispatch(toBrokerMessage(event))
            true
        }.getOrElse { ex ->
            log.error("Failed to dispatch broker event for type={}", event.javaClass.name, ex)
            false
        }

        if (!delivered || eventProperties.inProcessFallbackEnabled) {
            inProcessPublisher.publish(event)
        }
    }

    private fun toBrokerMessage(event: Any): BrokerEventMessage {
        val eventType = event.javaClass.simpleName.ifBlank { "UnknownEvent" }
        val topicName = "${eventProperties.topicPrefix}.${eventType.lowercase()}"

        return BrokerEventMessage(
            topic = topicName,
            eventType = eventType,
            payloadJson = objectMapper.writeValueAsString(event),
            tenantId = extractTenantId(event),
        )
    }

    private fun extractTenantId(event: Any): String? {
        val tenantField = event.javaClass.declaredFields.firstOrNull { it.name == "tenantId" } ?: return null
        return runCatching {
            tenantField.isAccessible = true
            tenantField.get(event)?.toString()
        }.getOrNull()
    }
}
