package com.numera.admin

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/workflows")
@PreAuthorize("hasRole('ADMIN')")
class WorkflowConfigController {
    private val workflows = mutableListOf(
        mutableMapOf<String, Any?>(
            "id" to UUID.randomUUID().toString(),
            "name" to "Spread Approval - Standard",
            "type" to "SPREADING",
            "steps" to listOf("SUBMIT", "MANAGER_REVIEW", "APPROVE"),
            "active" to true,
        ),
        mutableMapOf<String, Any?>(
            "id" to UUID.randomUUID().toString(),
            "name" to "Covenant Waiver Workflow",
            "type" to "COVENANT",
            "steps" to listOf("TRIGGER", "REVIEW", "DECIDE", "CLOSE"),
            "active" to true,
        ),
    )

    @GetMapping
    fun list(): List<Map<String, Any?>> = workflows

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val item = body.toMutableMap()
        item["id"] = UUID.randomUUID().toString()
        workflows.add(item)
        return item
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val idx = workflows.indexOfFirst { it["id"] == id }
        if (idx < 0) return mapOf("updated" to false)
        val merged = workflows[idx].toMutableMap().also { it.putAll(body) }
        workflows[idx] = merged
        return merged
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        workflows.removeIf { it["id"] == id }
    }
}
