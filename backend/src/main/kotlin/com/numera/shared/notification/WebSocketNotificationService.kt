package com.numera.shared.notification

import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class WebSocketNotificationService(
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Broadcast lock status changes for a spread item */
    fun notifyLockChanged(spreadItemId: UUID, lockedBy: String?, lockedByName: String?, locked: Boolean) {
        val payload = mapOf(
            "type" to "LOCK_CHANGED",
            "spreadItemId" to spreadItemId.toString(),
            "locked" to locked,
            "lockedBy" to lockedBy,
            "lockedByName" to lockedByName,
            "timestamp" to Instant.now().toString(),
        )
        messagingTemplate.convertAndSend("/topic/spread/$spreadItemId/lock", payload)
        log.debug("Sent LOCK_CHANGED for spread {}", spreadItemId)
    }

    /** Broadcast document processing updates */
    fun notifyDocumentProcessing(tenantId: UUID, documentId: UUID, status: String, progress: Int? = null) {
        val payload = mapOf(
            "type" to "DOCUMENT_PROCESSING",
            "documentId" to documentId.toString(),
            "status" to status,
            "progress" to progress,
            "timestamp" to Instant.now().toString(),
        )
        messagingTemplate.convertAndSend("/topic/tenant/$tenantId/documents", payload)
        log.debug("Sent DOCUMENT_PROCESSING for doc {} status={}", documentId, status)
    }

    /** Broadcast spread value changes for real-time collaboration awareness */
    fun notifySpreadValueChanged(spreadItemId: UUID, changedBy: String, itemCode: String) {
        val payload = mapOf(
            "type" to "SPREAD_VALUE_CHANGED",
            "spreadItemId" to spreadItemId.toString(),
            "changedBy" to changedBy,
            "itemCode" to itemCode,
            "timestamp" to Instant.now().toString(),
        )
        messagingTemplate.convertAndSend("/topic/spread/$spreadItemId/values", payload)
    }

    /** Broadcast covenant status changes */
    fun notifyCovenantStatusChanged(tenantId: UUID, monitoringItemId: UUID, covenantName: String, newStatus: String) {
        val payload = mapOf(
            "type" to "COVENANT_STATUS_CHANGED",
            "monitoringItemId" to monitoringItemId.toString(),
            "covenantName" to covenantName,
            "newStatus" to newStatus,
            "timestamp" to Instant.now().toString(),
        )
        messagingTemplate.convertAndSend("/topic/tenant/$tenantId/covenants", payload)
        log.debug("Sent COVENANT_STATUS_CHANGED for {} → {}", covenantName, newStatus)
    }

    /** Send notification to a specific user */
    fun notifyUser(userEmail: String, notification: Map<String, Any?>) {
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/notifications", notification)
    }
}
