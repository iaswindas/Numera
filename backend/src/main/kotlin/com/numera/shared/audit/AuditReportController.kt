package com.numera.shared.audit

import com.numera.shared.domain.TenantAwareEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/reports")
class AuditReportController(
    private val eventLogRepository: EventLogRepository,
) {
    @GetMapping("/audit-trail")
    fun auditTrail(
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) limit: Int?,
    ): Map<String, Any> {
        val events = eventLogRepository.findByTenantIdOrderByCreatedAtAsc(TenantAwareEntity.DEFAULT_TENANT.toString())
            .asReversed()
            .let { list -> if (entityType.isNullOrBlank()) list else list.filter { it.entityType == entityType } }
            .let { list -> if (limit == null || limit <= 0) list else list.take(limit) }
        return mapOf(
            "total" to events.size,
            "events" to events.map {
                mapOf(
                    "id" to it.id.toString(),
                    "eventType" to it.eventType,
                    "action" to it.action,
                    "entityType" to it.entityType,
                    "entityId" to it.entityId,
                    "actorEmail" to it.actorEmail,
                    "createdAt" to it.createdAt.toString(),
                )
            },
        )
    }

    @GetMapping("/export")
    fun export(@RequestParam(defaultValue = "csv") format: String): ResponseEntity<ByteArray> {
        val csv = buildString {
            appendLine("generatedAt,format")
            appendLine("${Instant.now()},$format")
            appendLine("report,spreading-summary")
            appendLine("report,covenant-summary")
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=numera-report.$format")
            .contentType(MediaType.TEXT_PLAIN)
            .body(csv.toByteArray())
    }
}
