package com.numera.admin.api

import com.numera.admin.application.ExclusionService
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/admin/exclusion-rules")
class ExclusionController(
    private val exclusionService: ExclusionService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> = exclusionService.list()

    @GetMapping("/active")
    fun listActive(): List<Map<String, Any?>> = exclusionService.listActive()

    @GetMapping("/categories")
    fun categories(): List<String> = exclusionService.getCategories()

    @GetMapping("/category/{category}")
    fun listByCategory(@PathVariable category: String): List<Map<String, Any?>> =
        exclusionService.listByCategory(category)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: Map<String, String?>): Map<String, Any?> =
        exclusionService.create(
            category = request["category"] ?: throw IllegalArgumentException("category required"),
            pattern = request["pattern"] ?: throw IllegalArgumentException("pattern required"),
            patternType = request["patternType"] ?: "EXACT",
            description = request["description"],
        )

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: Map<String, Any?>,
    ): Map<String, Any?> = exclusionService.update(
        id = id,
        category = request["category"] as? String,
        pattern = request["pattern"] as? String,
        patternType = request["patternType"] as? String,
        description = request["description"] as? String,
        isActive = request["isActive"] as? Boolean,
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = exclusionService.delete(id)

    @PostMapping("/apply")
    fun applyRules(@RequestBody request: Map<String, String>): Map<String, String> {
        val text = request["text"] ?: throw IllegalArgumentException("text required")
        return mapOf("cleanedText" to exclusionService.applyRules(text))
    }
}
