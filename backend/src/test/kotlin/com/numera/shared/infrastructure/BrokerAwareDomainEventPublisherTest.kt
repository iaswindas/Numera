package com.numera.shared.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.config.NumeraProperties
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrokerAwareDomainEventPublisherTest {

    @Test
    fun `publishes in-process only when broker is disabled`() {
        val inProcessPublisher = mockk<DomainEventPublisher>()
        val brokerEventDispatcher = mockk<BrokerEventDispatcher>()
        val publisher = BrokerAwareDomainEventPublisher(
            inProcessPublisher = inProcessPublisher,
            brokerEventDispatcher = brokerEventDispatcher,
            objectMapper = ObjectMapper(),
            eventProperties = NumeraProperties.Events(
                brokerEnabled = false,
                inProcessFallbackEnabled = true,
                topicPrefix = "numera",
            ),
        )

        every { inProcessPublisher.publish(any()) } just runs

        publisher.publish(TestDomainEvent(tenantId = "tenant-a", payload = "x"))

        verify(exactly = 1) { inProcessPublisher.publish(any()) }
        verify(exactly = 0) { brokerEventDispatcher.dispatch(any()) }
    }

    @Test
    fun `publishes to broker only when enabled and fallback is disabled`() {
        val inProcessPublisher = mockk<DomainEventPublisher>()
        val brokerEventDispatcher = mockk<BrokerEventDispatcher>()
        val publisher = BrokerAwareDomainEventPublisher(
            inProcessPublisher = inProcessPublisher,
            brokerEventDispatcher = brokerEventDispatcher,
            objectMapper = ObjectMapper(),
            eventProperties = NumeraProperties.Events(
                brokerEnabled = true,
                inProcessFallbackEnabled = false,
                topicPrefix = "numera",
            ),
        )

        val messageSlot = slot<BrokerEventMessage>()
        every { brokerEventDispatcher.dispatch(capture(messageSlot)) } just runs

        publisher.publish(TestDomainEvent(tenantId = "tenant-b", payload = "y"))

        verify(exactly = 1) { brokerEventDispatcher.dispatch(any()) }
        verify(exactly = 0) { inProcessPublisher.publish(any()) }
        assertEquals("numera.testdomainevent", messageSlot.captured.topic)
        assertEquals("tenant-b", messageSlot.captured.tenantId)
    }

    @Test
    fun `falls back to in-process when broker dispatch fails`() {
        val inProcessPublisher = mockk<DomainEventPublisher>()
        val brokerEventDispatcher = mockk<BrokerEventDispatcher>()
        val publisher = BrokerAwareDomainEventPublisher(
            inProcessPublisher = inProcessPublisher,
            brokerEventDispatcher = brokerEventDispatcher,
            objectMapper = ObjectMapper(),
            eventProperties = NumeraProperties.Events(
                brokerEnabled = true,
                inProcessFallbackEnabled = true,
                topicPrefix = "numera",
            ),
        )

        every { brokerEventDispatcher.dispatch(any()) } throws IllegalStateException("broker unavailable")
        every { inProcessPublisher.publish(any()) } just runs

        publisher.publish(TestDomainEvent(tenantId = "tenant-c", payload = "z"))

        verify(exactly = 1) { brokerEventDispatcher.dispatch(any()) }
        verify(exactly = 1) { inProcessPublisher.publish(any()) }
    }

    private data class TestDomainEvent(
        val tenantId: String,
        val payload: String,
    )
}
