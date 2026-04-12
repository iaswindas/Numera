package com.numera.shared.notification

import com.numera.shared.events.CovenantBreachedEvent
import com.numera.shared.events.CovenantStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CovenantWebSocketListener(
    private val wsNotificationService: WebSocketNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onCovenantStatusChanged(event: CovenantStatusChangedEvent) {
        wsNotificationService.notifyCovenantStatusChanged(
            tenantId = event.tenantId,
            monitoringItemId = event.monitoringItemId,
            covenantName = "Covenant ${event.covenantId}",
            newStatus = event.newStatus,
        )
    }

    @EventListener
    fun onCovenantBreached(event: CovenantBreachedEvent) {
        log.warn("Covenant breach detected: {} for customer {}", event.covenantName, event.customerName)
        wsNotificationService.notifyCovenantStatusChanged(
            tenantId = event.tenantId,
            monitoringItemId = event.monitoringItemId,
            covenantName = event.covenantName,
            newStatus = "BREACHED",
        )
    }
}
