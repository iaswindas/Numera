package com.numera.admin

import com.numera.model.infrastructure.LineItemRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/taxonomy")
class TaxonomyController(
    private val lineItemRepository: LineItemRepository,
) {
    @GetMapping
    fun list(): Map<String, Any> {
        val items = lineItemRepository.findAll().take(500)
        return mapOf(
            "total" to items.size,
            "entries" to items.map {
                mapOf(
                    "itemCode" to it.itemCode,
                    "label" to it.label,
                    "zone" to it.zone,
                    "category" to it.category,
                )
            },
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upsert(@RequestBody payload: Map<String, Any?>): Map<String, Any?> = mapOf(
        "accepted" to true,
        "message" to "Taxonomy payload accepted",
        "payload" to payload,
    )
}
