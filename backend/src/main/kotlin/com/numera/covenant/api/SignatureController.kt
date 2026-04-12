package com.numera.covenant.api

import com.numera.covenant.application.EmailTemplateService
import com.numera.covenant.dto.SignatureRequest
import com.numera.covenant.dto.SignatureResponse
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

/**
 * REST controller for signature management.
 * Signatures are used to append to generated waiver letters and other covenant documents.
 */
@RestController
@RequestMapping("/api/covenants/signatures")
class SignatureController(
    private val emailTemplateService: EmailTemplateService,
) {

    /**
     * Get all active signatures for the current tenant.
     * GET /api/covenants/signatures
     */
    @GetMapping
    fun listSignatures(): List<SignatureResponse> = emailTemplateService.listSignatures()

    /**
     * Get a specific signature by ID.
     * GET /api/covenants/signatures/{id}
     */
    @GetMapping("/{id}")
    fun getSignature(@PathVariable id: UUID): SignatureResponse = emailTemplateService.getSignature(id)

    /**
     * Create a new signature.
     * POST /api/covenants/signatures
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSignature(
        @RequestBody request: SignatureRequest,
    ): SignatureResponse = emailTemplateService.createSignature(request, null)

    /**
     * Update an existing signature.
     * PUT /api/covenants/signatures/{id}
     */
    @PutMapping("/{id}")
    fun updateSignature(
        @PathVariable id: UUID,
        @RequestBody request: SignatureRequest,
    ): SignatureResponse = emailTemplateService.updateSignature(id, request)

    /**
     * Soft delete a signature (set active=false).
     * DELETE /api/covenants/signatures/{id}
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSignature(@PathVariable id: UUID) {
        emailTemplateService.toggleSignatureActive(id, false)
    }
}
