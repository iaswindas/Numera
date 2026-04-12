package com.numera.shared.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.infrastructure.BrokerEventDispatcher
import com.numera.shared.infrastructure.RabbitMqEventDispatcher
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["numera.events.broker-enabled"], havingValue = "true")
class RabbitMqConfig(
    private val numeraProperties: NumeraProperties,
) {
    private val exchangeName = "${numeraProperties.events.topicPrefix}.events"

    companion object {
        const val COVENANT_RECALCULATION_QUEUE = "numera.covenant-recalculation"
        const val NOTIFICATION_DISPATCH_QUEUE = "numera.notification-dispatch"
        const val ANALYTICS_MATERIALIZATION_QUEUE = "numera.analytics-materialization"

        private const val DLX_SUFFIX = ".dlx"
        private const val DLQ_SUFFIX = ".dlq"
        private const val MAX_RETRY_COUNT = 3
    }

    // --- Exchange ---

    @Bean
    fun numeraEventsExchange(): TopicExchange = TopicExchange(exchangeName)

    // --- Dead-letter exchange & queues ---

    @Bean
    fun deadLetterExchange(): TopicExchange = TopicExchange("$exchangeName$DLX_SUFFIX")

    @Bean
    fun covenantRecalculationDlq(): Queue =
        QueueBuilder.durable("$COVENANT_RECALCULATION_QUEUE$DLQ_SUFFIX").build()

    @Bean
    fun notificationDispatchDlq(): Queue =
        QueueBuilder.durable("$NOTIFICATION_DISPATCH_QUEUE$DLQ_SUFFIX").build()

    @Bean
    fun analyticsMaterializationDlq(): Queue =
        QueueBuilder.durable("$ANALYTICS_MATERIALIZATION_QUEUE$DLQ_SUFFIX").build()

    @Bean
    fun dlqCovenantBinding(): Binding =
        BindingBuilder.bind(covenantRecalculationDlq()).to(deadLetterExchange()).with("#")

    @Bean
    fun dlqNotificationBinding(): Binding =
        BindingBuilder.bind(notificationDispatchDlq()).to(deadLetterExchange()).with("#")

    @Bean
    fun dlqAnalyticsBinding(): Binding =
        BindingBuilder.bind(analyticsMaterializationDlq()).to(deadLetterExchange()).with("#")

    // --- Queues ---

    @Bean
    fun covenantRecalculationQueue(): Queue =
        QueueBuilder.durable(COVENANT_RECALCULATION_QUEUE)
            .withArgument("x-dead-letter-exchange", "$exchangeName$DLX_SUFFIX")
            .withArgument("x-dead-letter-routing-key", COVENANT_RECALCULATION_QUEUE)
            .build()

    @Bean
    fun notificationDispatchQueue(): Queue =
        QueueBuilder.durable(NOTIFICATION_DISPATCH_QUEUE)
            .withArgument("x-dead-letter-exchange", "$exchangeName$DLX_SUFFIX")
            .withArgument("x-dead-letter-routing-key", NOTIFICATION_DISPATCH_QUEUE)
            .build()

    @Bean
    fun analyticsMaterializationQueue(): Queue =
        QueueBuilder.durable(ANALYTICS_MATERIALIZATION_QUEUE)
            .withArgument("x-dead-letter-exchange", "$exchangeName$DLX_SUFFIX")
            .withArgument("x-dead-letter-routing-key", ANALYTICS_MATERIALIZATION_QUEUE)
            .build()

    // --- Bindings ---

    @Bean
    fun covenantRecalculationBinding(): Binding =
        BindingBuilder.bind(covenantRecalculationQueue()).to(numeraEventsExchange()).with("spread-submitted-event")

    @Bean
    fun notificationCovenantBreachedBinding(): Binding =
        BindingBuilder.bind(notificationDispatchQueue()).to(numeraEventsExchange()).with("covenant-breached-event")

    @Bean
    fun notificationCovenantStatusBinding(): Binding =
        BindingBuilder.bind(notificationDispatchQueue()).to(numeraEventsExchange()).with("covenant-status-changed-event")

    @Bean
    fun notificationSpreadApprovedBinding(): Binding =
        BindingBuilder.bind(notificationDispatchQueue()).to(numeraEventsExchange()).with("spread-approved-event")

    @Bean
    fun notificationSpreadRejectedBinding(): Binding =
        BindingBuilder.bind(notificationDispatchQueue()).to(numeraEventsExchange()).with("spread-rejected-event")

    @Bean
    fun analyticsAllBinding(): Binding =
        BindingBuilder.bind(analyticsMaterializationQueue()).to(numeraEventsExchange()).with("#")

    // --- Message converter & template ---

    @Bean
    fun jackson2JsonMessageConverter(objectMapper: ObjectMapper): Jackson2JsonMessageConverter =
        Jackson2JsonMessageConverter(objectMapper)

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: Jackson2JsonMessageConverter,
    ): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter
        return template
    }

    // --- Dispatcher bean (takes precedence over NoopBrokerEventDispatcher) ---

    @Bean
    fun brokerEventDispatcher(rabbitTemplate: RabbitTemplate): BrokerEventDispatcher =
        RabbitMqEventDispatcher(rabbitTemplate, exchangeName)
}
