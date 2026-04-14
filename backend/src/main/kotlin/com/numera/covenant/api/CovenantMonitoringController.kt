package com.numera.covenant.api

import com.numera.covenant.application.CovenantMonitoringService
import com.numera.covenant.dto.CheckerDecisionRequest
import com.numera.covenant.dto.CovenantDocumentResponse
import com.numera.covenant.dto.CovenantMonitoringItemResponse
import com.numera.covenant.dto.ManualValueRequest
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/covenants/monitoring")
class CovenantMonitoringController(
    private val monitoringService: CovenantMonitoringService,
) {

    @GetMapping("/summary")
    fun summary(): Map<String, Any> {
        val items = monitoringService.listByTenant()
        val byStatus = items.groupingBy { it.status }.eachCount()
        return mapOf(
            "total" to items.size,
            "distribution" to byStatus,
            "breached" to byStatus.getOrDefault("BREACHED", 0),
            "overdue" to byStatus.getOrDefault("OVERDUE", 0),
            "due" to byStatus.getOrDefault("DUE", 0),
        )
    }

    @GetMapping
    fun listAll(): List<CovenantMonitoringItemResponse> = monitoringService.listByTenant()

    @GetMapping("/pending")
    fun listPending(): List<CovenantMonitoringItemResponse> = monitoringService.listPending()

    @GetMapping("/breached")
    fun listBreached(): List<CovenantMonitoringItemResponse> = monitoringService.listBreached()

    @GetMapping("/by-covenant/{covenantId}")
    fun listByCovenant(@PathVariable covenantId: UUID): List<CovenantMonitoringItemResponse> =
        monitoringService.listByCovenant(covenantId)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CovenantMonitoringItemResponse = monitoringService.get(id)

    /** Generate monitoring items for a covenant over a date range */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(
        @RequestParam covenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<CovenantMonitoringItemResponse> = monitoringService.generateMonitoringItems(covenantId, from, to)

    /** Analyst sets a manual value override with justification */
    @PutMapping("/{id}/manual-value")
    fun setManualValue(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ManualValueRequest,
    ): CovenantMonitoringItemResponse = monitoringService.setManualValue(id, request)

    /** Upload compliance document for non-financial covenant */
    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @PathVariable id: UUID,
        @RequestParam file: MultipartFile,
        @RequestParam actorId: UUID,
    ): CovenantDocumentResponse = monitoringService.uploadDocument(id, file, actorId)

    /** Checker approves or rejects a submitted non-financial item */
    @PostMapping("/{id}/checker-decision")
    fun checkerDecision(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CheckerDecisionRequest,
        @RequestParam actorId: UUID,
    ): CovenantMonitoringItemResponse = monitoringService.checkerDecision(id, request, actorId)

    /** Analyst triggers waiver action on a breached item */
    @PostMapping("/{id}/trigger-action")
    fun triggerAction(
        @PathVariable id: UUID,
        @RequestParam actorId: UUID,
    ): CovenantMonitoringItemResponse = monitoringService.triggerAction(id, actorId)

    /** Scheduled sweep — mark overdue items (called internally or by cron) */
    @PostMapping("/sweep-overdue")
    fun sweepOverdue(): Map<String, Int> =
        mapOf("markedOverdue" to monitoringService.markOverdue())
}
