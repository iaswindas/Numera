package com.numera.shared.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.infrastructure.BrokerAwareDomainEventPublisher
import com.numera.shared.infrastructure.BrokerEventDispatcher
import com.numera.shared.infrastructure.DomainEventPublisher
import com.numera.shared.infrastructure.NoopBrokerEventDispatcher
import com.numera.shared.infrastructure.SpringDomainEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventPublisherConfig {
    @Bean
    @ConditionalOnMissingBean(BrokerEventDispatcher::class)
    fun brokerEventDispatcher(): BrokerEventDispatcher = NoopBrokerEventDispatcher()

    @Bean
    fun domainEventPublisher(
        springEventPublisher: ApplicationEventPublisher,
        brokerEventDispatcher: BrokerEventDispatcher,
        objectMapper: ObjectMapper,
        properties: NumeraProperties,
    ): DomainEventPublisher =
        BrokerAwareDomainEventPublisher(
            inProcessPublisher = SpringDomainEventPublisher(springEventPublisher),
            brokerEventDispatcher = brokerEventDispatcher,
            objectMapper = objectMapper,
            eventProperties = properties.events,
        )
}
