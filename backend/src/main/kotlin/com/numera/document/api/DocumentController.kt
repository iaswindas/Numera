package com.numera.document.api

import com.numera.document.application.DocumentProcessingService
import com.numera.document.domain.DocumentStatus
import com.numera.document.dto.DocumentResponse
import com.numera.document.dto.DocumentStatusResponse
import com.numera.document.dto.DocumentUploadResponse
import com.numera.document.dto.ZoneResponse
import com.numera.document.dto.ZoneUpdateRequest
import com.numera.document.dto.ZonesResponse
import jakarta.validation.Valid
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api")
class DocumentController(
    private val documentProcessingService: DocumentProcessingService,
) {
    @GetMapping("/documents")
    fun list(
        @RequestParam(required = false) customerId: UUID?,
        @RequestParam(required = false) uploadedBy: String?,
        @RequestParam(required = false) status: DocumentStatus?,
    ): List<DocumentResponse> = documentProcessingService.list(customerId, uploadedBy, status)

    @PostMapping("/documents/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestPart("customerId") customerId: String,
        @RequestPart(value = "language", required = false) language: String?,
        @RequestPart(value = "password", required = false) password: String?,
    ): DocumentUploadResponse =
        documentProcessingService.upload(
            customerId = UUID.fromString(customerId),
            file = file,
            language = language ?: "en",
            password = password,
        )

    @PostMapping("/customers/{customerId}/documents/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun uploadForCustomer(
        @PathVariable customerId: UUID,
        @RequestPart("file") file: MultipartFile,
        @RequestPart(value = "language", required = false) language: String?,
        @RequestPart(value = "password", required = false) password: String?,
    ): DocumentUploadResponse = documentProcessingService.upload(customerId, file, language ?: "en", password)

    @PostMapping("/documents/{id}/process")
    fun process(
        @PathVariable id: UUID,
        @RequestParam(value = "password", required = false) password: String?,
    ): DocumentStatusResponse = documentProcessingService.process(id, password)

    @GetMapping("/documents/{id}")
    fun get(@PathVariable id: UUID): DocumentResponse = documentProcessingService.getDocument(id)

    @GetMapping("/documents/{id}/status")
    fun status(@PathVariable id: UUID): DocumentStatusResponse = documentProcessingService.getStatus(id)

    @GetMapping("/documents/{id}/zones")
    fun zones(@PathVariable id: UUID): ZonesResponse = documentProcessingService.zones(id)

    @PutMapping("/documents/{id}/zones/{zoneId}")
    fun updateZone(
        @PathVariable id: UUID,
        @PathVariable zoneId: UUID,
        @Valid @org.springframework.web.bind.annotation.RequestBody request: ZoneUpdateRequest,
    ): ZoneResponse = documentProcessingService.updateZone(id, zoneId, request)

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = documentProcessingService.delete(id)

    @GetMapping("/documents/{id}/download")
    fun download(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        val doc = documentProcessingService.getDocumentEntity(id)
        val stream = documentProcessingService.downloadFile(doc.storagePath)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${doc.originalFilename}\"")
            .contentType(MediaType.parseMediaType(doc.contentType))
            .body(InputStreamResource(stream))
    }
}
