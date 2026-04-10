package com.numera.shared.audit

import com.numera.shared.security.CurrentUserProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuditService(
    private val eventLogRepository: EventLogRepository,
    private val hashChainService: HashChainService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @Transactional
    fun record(
        tenantId: String,
        eventType: String,
        action: AuditAction,
        entityType: String,
        entityId: String,
        parentEntityType: String? = null,
        parentEntityId: String? = null,
        diffJson: String? = null,
    ): AuditEvent {
        val previous = eventLogRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
        val previousHash = previous?.currentHash ?: "GENESIS"

        val payload = mapOf(
            "eventType" to eventType,
            "action" to action.name,
            "entityType" to entityType,
            "entityId" to entityId,
            "parentEntityType" to parentEntityType,
            "parentEntityId" to parentEntityId,
            "diffJson" to diffJson,
        )
        val currentHash = hashChainService.computeHash(previousHash, payload)

        return eventLogRepository.save(AuditEvent().also {
            it.tenantId = tenantId
            it.eventType = eventType
            it.action = action.name
            it.actorEmail = currentUserProvider.email() ?: "system@numera.local"
            it.entityType = entityType
            it.entityId = entityId
            it.parentEntityType = parentEntityType
            it.parentEntityId = parentEntityId
            it.diffJson = diffJson
            it.previousHash = previousHash
            it.currentHash = currentHash
        })
    }

    fun verifyChain(tenantId: String): Boolean {
        var prev = "GENESIS"
        val events = eventLogRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)
        for (event in events) {
            val payload = mapOf(
                "eventType" to event.eventType,
                "action" to event.action,
                "entityType" to event.entityType,
                "entityId" to event.entityId,
                "parentEntityType" to event.parentEntityType,
                "parentEntityId" to event.parentEntityId,
                "diffJson" to event.diffJson,
            )
            val expected = hashChainService.computeHash(prev, payload)
            if (expected != event.currentHash || event.previousHash != prev) {
                return false
            }
            prev = event.currentHash
        }
        return true
    }
}