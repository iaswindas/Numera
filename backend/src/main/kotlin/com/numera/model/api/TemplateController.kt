package com.numera.model.api

import com.numera.model.application.TemplateService
import com.numera.model.dto.TemplateItemsResponse
import com.numera.model.dto.TemplateResponse
import com.numera.model.dto.TemplateUpsertRequest
import jakarta.validation.Valid
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
import java.util.UUID

@RestController
@RequestMapping("/api/model-templates")
class TemplateController(
    private val templateService: TemplateService,
) {
    @GetMapping
    fun list(): List<TemplateResponse> = templateService.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): TemplateResponse = templateService.findById(id)

    @GetMapping("/{id}/items")
    fun items(@PathVariable id: UUID, @RequestParam zone: String): TemplateItemsResponse =
        templateService.findItemsByZone(id, zone)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TemplateUpsertRequest): TemplateResponse = templateService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: TemplateUpsertRequest): TemplateResponse =
        templateService.update(id, request)
}
