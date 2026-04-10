package com.numera.spreading.api

import com.numera.spreading.application.SpreadLockService
import com.numera.spreading.application.SpreadService
import com.numera.spreading.dto.DiffResponse
import com.numera.spreading.dto.MappingResultResponse
import com.numera.spreading.dto.SpreadItemRequest
import com.numera.spreading.dto.SpreadItemResponse
import com.numera.spreading.dto.SubmitSpreadRequest
import com.numera.spreading.dto.SubmitSpreadResponse
import com.numera.spreading.dto.VersionHistoryResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class SpreadController(
    private val spreadService: SpreadService,
    private val lockService: SpreadLockService,
) {
    @GetMapping("/spread-items/{id}")
    fun get(@PathVariable id: UUID): SpreadItemResponse = spreadService.get(id)

    @PostMapping("/customers/{customerId}/spread-items")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable customerId: UUID, @Valid @RequestBody request: SpreadItemRequest): SpreadItemResponse =
        spreadService.create(customerId, request)

    @GetMapping("/customers/{customerId}/spread-items")
    fun list(@PathVariable customerId: UUID): List<SpreadItemResponse> = spreadService.listByCustomer(customerId)

    @PostMapping("/spread-items/{id}/process")
    fun process(@PathVariable id: UUID): MappingResultResponse = spreadService.process(id)

    @PostMapping("/spread-items/{id}/submit")
    fun submit(@PathVariable id: UUID, @Valid @RequestBody request: SubmitSpreadRequest): SubmitSpreadResponse =
        spreadService.submit(id, request)

    @GetMapping("/spread-items/{id}/history")
    fun history(@PathVariable id: UUID): VersionHistoryResponse = spreadService.history(id)

    @GetMapping("/spread-items/{id}/diff/{v1}/{v2}")
    fun diff(@PathVariable id: UUID, @PathVariable v1: Int, @PathVariable v2: Int): DiffResponse =
        spreadService.diff(id, v1, v2)

    @PostMapping("/spread-items/{id}/rollback/{version}")
    fun rollback(
        @PathVariable id: UUID,
        @PathVariable version: Int,
        @RequestBody request: com.numera.spreading.dto.RollbackRequest,
    ): Map<String, Any> = spreadService.rollback(id, version, request.comments)

    // ── Exclusive Locking ─────────────────────────────────────────────

    @PostMapping("/spread-items/{id}/lock")
    fun acquireLock(@PathVariable id: UUID): Map<String, Any?> {
        val email = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        val lock = lockService.acquire(id, email, email)
        return mapOf("spreadItemId" to lock.spreadItemId, "lockedBy" to lock.lockedBy, "lockedByName" to lock.lockedByName, "acquiredAt" to lock.acquiredAt)
    }

    @DeleteMapping("/spread-items/{id}/lock")
    fun releaseLock(@PathVariable id: UUID): Map<String, Any> {
        val email = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        lockService.release(id, email)
        return mapOf("released" to true)
    }

    @GetMapping("/spread-items/{id}/lock")
    fun getLock(@PathVariable id: UUID): Map<String, Any?> {
        val lock = lockService.getLockInfo(id)
        return if (lock != null) {
            mapOf("locked" to true, "lockedBy" to lock.lockedBy, "lockedByName" to lock.lockedByName, "acquiredAt" to lock.acquiredAt)
        } else {
            mapOf("locked" to false)
        }
    }

    @PostMapping("/spread-items/{id}/lock/heartbeat")
    fun heartbeat(@PathVariable id: UUID): Map<String, Boolean> {
        val email = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        return mapOf("extended" to lockService.heartbeat(id, email))
    }
}