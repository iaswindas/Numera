package com.numera.shared.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.core.MessagePropertiesBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.nio.charset.StandardCharsets
import java.util.UUID

class RabbitMqEventDispatcher(
    private val rabbitTemplate: RabbitTemplate,
    private val exchange: String,
) : BrokerEventDispatcher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun dispatch(message: BrokerEventMessage) {
        val routingKey = toKebabCase(message.eventType)
        val messageId = UUID.randomUUID().toString()

        try {
            val props = MessagePropertiesBuilder.newInstance()
                .setContentType("application/json")
                .setMessageId(messageId)
                .setHeader("eventType", message.eventType)
                .setHeader("tenantId", message.tenantId ?: "")
                .setHeader("timestamp", System.currentTimeMillis())
                .build()

            val amqpMessage = MessageBuilder
                .withBody(message.payloadJson.toByteArray(StandardCharsets.UTF_8))
                .andProperties(props)
                .build()

            rabbitTemplate.send(exchange, routingKey, amqpMessage)

            log.debug(
                "Dispatched event to exchange={} routingKey={} messageId={}",
                exchange, routingKey, messageId,
            )
        } catch (ex: Exception) {
            log.error(
                "Failed to dispatch event to RabbitMQ: exchange={} routingKey={} messageId={}",
                exchange, routingKey, messageId, ex,
            )
        }
    }

    private fun toKebabCase(eventType: String): String =
        eventType
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
            .lowercase()
}
