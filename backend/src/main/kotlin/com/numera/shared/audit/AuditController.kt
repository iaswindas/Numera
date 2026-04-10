package com.numera.shared.audit

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit")
class AuditController(
    private val eventLogRepository: EventLogRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    @GetMapping("/events")
    fun events(
        @RequestParam entityType: String,
        @RequestParam entityId: String,
    ): Map<String, Any?> {
        val events = eventLogRepository.findByEntityOrParent(entityType, entityId)
        return mapOf(
            "events" to events.map {
                mapOf(
                    "id" to it.id.toString(),
                    "eventType" to it.eventType,
                    "action" to it.action,
                    "actorEmail" to it.actorEmail,
                    "entityType" to it.entityType,
                    "entityId" to it.entityId,
                    "parentEntityType" to it.parentEntityType,
                    "parentEntityId" to it.parentEntityId,
                    "diff" to it.diffJson?.let { json ->
                        objectMapper.readValue(json, object : TypeReference<List<Map<String, Any?>>>() {})
                    },
                    "createdAt" to it.createdAt.toString(),
                )
            },
            "total" to events.size,
            "page" to 0,
            "size" to 50,
        )
    }

    @GetMapping("/verify/{tenantId}")
    fun verify(@PathVariable tenantId: String): Map<String, Any?> {
        val started = System.currentTimeMillis()
        val events = eventLogRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)
        val valid = auditService.verifyChain(tenantId)
        return mapOf(
            "status" to if (valid) "VALID" else "INVALID",
            "eventsVerified" to events.size,
            "chainStartedAt" to events.firstOrNull()?.createdAt?.toString(),
            "lastEventAt" to events.lastOrNull()?.createdAt?.toString(),
            "verificationTimeMs" to (System.currentTimeMillis() - started),
        )
    }
}
