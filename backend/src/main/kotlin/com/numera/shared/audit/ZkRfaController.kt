package com.numera.shared.audit

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/audit")
class ZkRfaController(
    private val zkRfaAuditService: ZkRfaAuditService,
    private val eventLogRepository: EventLogRepository,
) {

    data class RedactRequest(val redactedPayload: String)

    @PostMapping("/verify-inclusion/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun verifyInclusion(@PathVariable eventId: UUID): ResponseEntity<Map<String, Any?>> {
        val included = zkRfaAuditService.verifyInclusion(eventId)
        return ResponseEntity.ok(mapOf(
            "eventId" to eventId.toString(),
            "included" to included,
        ))
    }

    @PostMapping("/verify-chain")
    @PreAuthorize("hasRole('ADMIN')")
    fun verifyChain(@RequestParam tenantId: String): ResponseEntity<ChainVerificationResult> {
        val result = zkRfaAuditService.verifyChain(tenantId)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/redact/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun redactEvent(
        @PathVariable eventId: UUID,
        @RequestBody request: RedactRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val event = zkRfaAuditService.redactEvent(eventId, request.redactedPayload)
        return ResponseEntity.ok(mapOf(
            "eventId" to event.id.toString(),
            "redacted" to true,
        ))
    }

    @GetMapping("/proof/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getProof(@PathVariable eventId: UUID): ResponseEntity<Map<String, Any?>> {
        val event = eventLogRepository.findById(eventId)
            .orElseThrow { IllegalArgumentException("Event $eventId not found") }

        return ResponseEntity.ok(mapOf(
            "eventId" to eventId.toString(),
            "mmrIndex" to event.mmrIndex,
            "mmrRoot" to event.mmrRoot,
            "mmrProof" to event.mmrProofJson,
            "hasChameleonHash" to (event.chameleonRandomness != null),
        ))
    }
}
