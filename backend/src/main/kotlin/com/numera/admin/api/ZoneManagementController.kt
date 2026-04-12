package com.numera.admin.api

import com.numera.admin.application.ZoneManagementService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/zones")
@PreAuthorize("hasRole('ADMIN')")
class ZoneManagementController(
    private val zoneService: ZoneManagementService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> = zoneService.findAll()

    @GetMapping("/active")
    fun listActive(): List<Map<String, Any?>> = zoneService.findActive()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: Map<String, Any?>): Map<String, Any?> = zoneService.create(body)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any?>): Map<String, Any?> =
        zoneService.update(id, body)

    @PatchMapping("/{id}/active")
    fun toggleActive(@PathVariable id: UUID, @RequestBody body: Map<String, Boolean>): Map<String, Any?> =
        zoneService.toggleActive(id, body["active"] ?: true)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = zoneService.delete(id)
}
