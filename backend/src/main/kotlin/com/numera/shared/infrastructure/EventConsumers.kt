package com.numera.shared.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.covenant.application.CovenantIntelligenceService
import com.numera.shared.config.NumeraProperties
import com.numera.shared.config.RabbitMqConfig
import com.numera.shared.events.CovenantBreachedEvent
import com.numera.shared.events.CovenantStatusChangedEvent
import com.numera.shared.events.SpreadSubmittedEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["numera.events.broker-enabled"], havingValue = "true")
class EventConsumers(
    private val eventPublisher: ApplicationEventPublisher,
    private val features: NumeraProperties.Features,
    private val objectMapper: ObjectMapper,
    private val covenantIntelligenceService: CovenantIntelligenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMqConfig.COVENANT_RECALCULATION_QUEUE])
    fun onCovenantRecalculation(message: Message) {
        if (!features.eventBroker) {
            log.debug("Event broker feature flag disabled, skipping covenant recalculation message")
            return
        }

        val eventType = message.messageProperties.getHeader<String>("eventType")
        val messageId = message.messageProperties.messageId

        try {
            log.info("Processing covenant recalculation: eventType={} messageId={}", eventType, messageId)
            val payload = String(message.body)
            log.debug("Covenant recalculation payload: {}", payload)
            // The existing SpreadCovenantEventListener handles recalculation via Spring events.
            // This consumer logs receipt for broker-based delivery tracing.
        } catch (ex: Exception) {
            log.error(
                "Failed to process covenant recalculation: eventType={} messageId={}",
                eventType, messageId, ex,
            )
            throw ex // rethrow so RabbitMQ can retry / route to DLQ
        }
    }

    @RabbitListener(queues = [RabbitMqConfig.NOTIFICATION_DISPATCH_QUEUE])
    fun onNotificationDispatch(message: Message) {
        if (!features.eventBroker) {
            log.debug("Event broker feature flag disabled, skipping notification dispatch message")
            return
        }

        val eventType = message.messageProperties.getHeader<String>("eventType")
        val messageId = message.messageProperties.messageId

        try {
            log.info("Processing notification dispatch: eventType={} messageId={}", eventType, messageId)
            val payload = String(message.body)
            log.debug("Notification dispatch payload: {}", payload)
            // Notification routing is handled by the existing WebSocket/email listeners.
            // This consumer logs receipt for broker-based delivery tracing.
        } catch (ex: Exception) {
            log.error(
                "Failed to process notification dispatch: eventType={} messageId={}",
                eventType, messageId, ex,
            )
            throw ex
        }
    }

    @RabbitListener(queues = [RabbitMqConfig.ANALYTICS_MATERIALIZATION_QUEUE])
    fun onAnalyticsMaterialization(message: Message) {
        if (!features.eventBroker) {
            log.debug("Event broker feature flag disabled, skipping analytics materialization message")
            return
        }

        val eventType = message.messageProperties.getHeader<String>("eventType")
        val messageId = message.messageProperties.messageId

        try {
            val payload = String(message.body)
            when (eventType) {
                "CovenantStatusChangedEvent" -> {
                    val event = objectMapper.readValue(payload, CovenantStatusChangedEvent::class.java)
                    covenantIntelligenceService.materializeRiskHeatmap(event.tenantId)
                }
                "CovenantBreachedEvent" -> {
                    val event = objectMapper.readValue(payload, CovenantBreachedEvent::class.java)
                    covenantIntelligenceService.materializeRiskHeatmap(event.tenantId)
                }
                "SpreadSubmittedEvent" -> {
                    val event = objectMapper.readValue(payload, SpreadSubmittedEvent::class.java)
                    covenantIntelligenceService.materializeCustomerHeatmap(event.tenantId, event.customerId)
                }
                else -> {
                    log.debug(
                        "Skipping analytics materialization for unsupported eventType={} messageId={}",
                        eventType, messageId,
                    )
                }
            }

            log.info(
                "Analytics materialization processed: eventType={} messageId={}",
                eventType, messageId,
            )
        } catch (ex: Exception) {
            log.error(
                "Failed to process analytics materialization: eventType={} messageId={}",
                eventType, messageId, ex,
            )
            throw ex
        }
    }
}
