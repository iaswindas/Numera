package com.numera.covenant.application

import com.numera.covenant.domain.EmailTemplate
import com.numera.covenant.domain.Signature
import com.numera.covenant.dto.EmailTemplateRequest
import com.numera.covenant.dto.EmailTemplateResponse
import com.numera.covenant.dto.SignatureRequest
import com.numera.covenant.dto.SignatureResponse
import com.numera.covenant.infrastructure.EmailTemplateRepository
import com.numera.covenant.infrastructure.SignatureRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmailTemplateService(
    private val emailTemplateRepository: EmailTemplateRepository,
    private val signatureRepository: SignatureRepository,
    private val auditService: AuditService,
) {

    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    // ── Email Templates ───────────────────────────────────────────────────

    fun listTemplates(): List<EmailTemplateResponse> =
        emailTemplateRepository.findByTenantIdAndIsActiveTrue(resolvedTenantId()).map { it.toResponse() }

    fun getTemplate(id: UUID): EmailTemplateResponse =
        emailTemplateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Email template not found: $id") }
            .toResponse()

    @Transactional
    fun createTemplate(request: EmailTemplateRequest, actorId: UUID?): EmailTemplateResponse {
        val saved = emailTemplateRepository.save(EmailTemplate().also {
            it.tenantId = resolvedTenantId()
            it.name = request.name
            it.covenantType = request.covenantType
            it.templateCategory = request.templateCategory
            it.subject = request.subject
            it.bodyHtml = request.bodyHtml
            it.createdBy = actorId
        })
        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "EMAIL_TEMPLATE_CREATED",
            action = AuditAction.CREATE,
            entityType = "email_template",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateTemplate(id: UUID, request: EmailTemplateRequest): EmailTemplateResponse {
        val template = emailTemplateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Email template not found: $id") }

        template.name = request.name
        template.covenantType = request.covenantType
        template.templateCategory = request.templateCategory
        template.subject = request.subject
        template.bodyHtml = request.bodyHtml

        val saved = emailTemplateRepository.save(template)
        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "EMAIL_TEMPLATE_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "email_template",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun toggleTemplateActive(id: UUID, active: Boolean): EmailTemplateResponse {
        val template = emailTemplateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Email template not found: $id") }
        template.isActive = active
        return emailTemplateRepository.save(template).toResponse()
    }

    // ── Signatures ────────────────────────────────────────────────────────

    fun listSignatures(): List<SignatureResponse> =
        signatureRepository.findByTenantIdAndIsActiveTrue(resolvedTenantId()).map { it.toResponse() }

    fun getSignature(id: UUID): SignatureResponse =
        signatureRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Signature not found: $id") }
            .toResponse()

    @Transactional
    fun createSignature(request: SignatureRequest, actorId: UUID?): SignatureResponse {
        val saved = signatureRepository.save(Signature().also {
            it.tenantId = resolvedTenantId()
            it.name = request.name
            it.title = request.title
            it.htmlContent = request.htmlContent
            it.createdBy = actorId
        })
        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "SIGNATURE_CREATED",
            action = AuditAction.CREATE,
            entityType = "signature",
            entityId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateSignature(id: UUID, request: SignatureRequest): SignatureResponse {
        val sig = signatureRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Signature not found: $id") }
        sig.name = request.name
        sig.title = request.title
        sig.htmlContent = request.htmlContent
        return signatureRepository.save(sig).toResponse()
    }

    @Transactional
    fun toggleSignatureActive(id: UUID, active: Boolean): SignatureResponse {
        val sig = signatureRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Signature not found: $id") }
        sig.isActive = active
        return signatureRepository.save(sig).toResponse()
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private fun EmailTemplate.toResponse() = EmailTemplateResponse(
        id = id!!,
        name = name,
        covenantType = covenantType,
        templateCategory = templateCategory,
        subject = subject,
        bodyHtml = bodyHtml,
        isActive = isActive,
        createdAt = createdAt,
    )

    private fun Signature.toResponse() = SignatureResponse(
        id = id!!,
        name = name,
        title = title,
        htmlContent = htmlContent,
        isActive = isActive,
        createdAt = createdAt,
    )
}
