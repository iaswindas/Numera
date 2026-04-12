package com.numera.admin

import com.numera.admin.application.TaxonomyService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
import java.util.UUID

@RestController
@RequestMapping("/api/admin/taxonomy")
@PreAuthorize("hasRole('ADMIN')")
class TaxonomyController(
    private val taxonomyService: TaxonomyService,
) {
    @GetMapping
    fun list(@RequestParam(required = false) language: String?): Map<String, Any> {
        val entries = taxonomyService.list(language)
        return mapOf("total" to entries.size, "entries" to entries)
    }

    @GetMapping("/category/{category}")
    fun listByCategory(@PathVariable category: String): List<Map<String, Any?>> =
        taxonomyService.getByCategory(category)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upsert(@RequestBody request: Map<String, Any?>): Map<String, Any?> {
        return taxonomyService.upsert(
            itemCode = request["itemCode"] as? String ?: throw IllegalArgumentException("itemCode required"),
            label = request["label"] as? String ?: throw IllegalArgumentException("label required"),
            category = request["category"] as? String,
            parentCode = request["parentCode"] as? String,
            synonyms = (request["synonyms"] as? List<*>)?.map { it.toString() },
            language = request["language"] as? String ?: "en",
        )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = taxonomyService.delete(id)

    @PostMapping("/bulk-upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun bulkUpload(@RequestParam("file") file: MultipartFile): Map<String, Any> {
        return taxonomyService.bulkImport(file.inputStream)
    }

    @GetMapping("/export")
    fun export(): List<Map<String, Any?>> = taxonomyService.exportData()
}
