package com.numera.covenant.api

import com.numera.covenant.application.EmailTemplateService
import com.numera.covenant.dto.EmailTemplateRequest
import com.numera.covenant.dto.EmailTemplateResponse
import com.numera.covenant.dto.SignatureRequest
import com.numera.covenant.dto.SignatureResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
@RequestMapping("/api/covenants/templates")
class EmailTemplateController(
    private val emailTemplateService: EmailTemplateService,
) {

    // ── Email Templates ───────────────────────────────────────────────────

    @GetMapping
    fun listTemplates(): List<EmailTemplateResponse> = emailTemplateService.listTemplates()

    @GetMapping("/{id}")
    fun getTemplate(@PathVariable id: UUID): EmailTemplateResponse = emailTemplateService.getTemplate(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTemplate(
        @Valid @RequestBody request: EmailTemplateRequest,
        @RequestParam(required = false) actorId: UUID?,
    ): EmailTemplateResponse = emailTemplateService.createTemplate(request, actorId)

    @PutMapping("/{id}")
    fun updateTemplate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: EmailTemplateRequest,
    ): EmailTemplateResponse = emailTemplateService.updateTemplate(id, request)

    @PatchMapping("/{id}/active")
    fun toggleTemplateActive(
        @PathVariable id: UUID,
        @RequestParam active: Boolean,
    ): EmailTemplateResponse = emailTemplateService.toggleTemplateActive(id, active)

    // ── Signatures ────────────────────────────────────────────────────────

    @GetMapping("/signatures")
    fun listSignatures(): List<SignatureResponse> = emailTemplateService.listSignatures()

    @GetMapping("/signatures/{id}")
    fun getSignature(@PathVariable id: UUID): SignatureResponse = emailTemplateService.getSignature(id)

    @PostMapping("/signatures")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSignature(
        @Valid @RequestBody request: SignatureRequest,
        @RequestParam(required = false) actorId: UUID?,
    ): SignatureResponse = emailTemplateService.createSignature(request, actorId)

    @PutMapping("/signatures/{id}")
    fun updateSignature(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SignatureRequest,
    ): SignatureResponse = emailTemplateService.updateSignature(id, request)

    @PatchMapping("/signatures/{id}/active")
    fun toggleSignatureActive(
        @PathVariable id: UUID,
        @RequestParam active: Boolean,
    ): SignatureResponse = emailTemplateService.toggleSignatureActive(id, active)
}
