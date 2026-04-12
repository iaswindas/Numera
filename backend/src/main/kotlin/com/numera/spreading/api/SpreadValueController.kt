package com.numera.spreading.api

import com.numera.spreading.application.SpreadService
import com.numera.spreading.dto.BulkAcceptRequest
import com.numera.spreading.dto.BulkAcceptResponse
import com.numera.spreading.dto.SpreadValueResponse
import com.numera.spreading.dto.SpreadValueUpdateRequest
import com.numera.shared.notification.WebSocketNotificationService
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/spread-items/{id}/values")
class SpreadValueController(
    private val spreadService: SpreadService,
    private val wsNotificationService: WebSocketNotificationService,
) {
    @GetMapping
    fun list(@PathVariable id: UUID): List<SpreadValueResponse> = spreadService.values(id)

    @PutMapping("/{valueId}")
    fun update(
        @PathVariable id: UUID,
        @PathVariable valueId: UUID,
        @Valid @RequestBody request: SpreadValueUpdateRequest,
    ): SpreadValueResponse {
        val updated = spreadService.updateValue(id, valueId, request)
        val changedBy = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        wsNotificationService.notifySpreadValueChanged(id, changedBy, updated.itemCode)
        return updated
    }

    @PutMapping("/{valueId}/notes")
    fun updateNotes(
        @PathVariable id: UUID,
        @PathVariable valueId: UUID,
        @Valid @RequestBody request: Map<String, String>,
    ): SpreadValueResponse {
        val notes = request["notes"] ?: ""
        val updated = spreadService.updateNotes(id, valueId, notes)
        val changedBy = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        wsNotificationService.notifySpreadValueChanged(id, changedBy, updated.itemCode)
        return updated
    }

    @PostMapping("/bulk-accept")
    fun bulkAccept(@PathVariable id: UUID, @Valid @RequestBody request: BulkAcceptRequest): BulkAcceptResponse =
        spreadService.bulkAccept(id, request)
}