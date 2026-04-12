package com.numera.shared.infrastructure

interface DomainEventPublisher {
    fun publish(event: Any)
}
