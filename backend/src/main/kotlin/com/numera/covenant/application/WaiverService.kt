package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.domain.WaiverLetter
import com.numera.covenant.domain.WaiverType
import com.numera.covenant.dto.WaiverGenerationRequest
import com.numera.covenant.dto.WaiverLetterResponse
import com.numera.covenant.dto.WaiverRequest
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.EmailTemplateRepository
import com.numera.covenant.infrastructure.SignatureRepository
import com.numera.covenant.infrastructure.WaiverLetterRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.pdf.PdfGenerator
import com.numera.shared.security.TenantContext
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class WaiverLetterResult(
    val monitoringItemId: UUID,
    val letterHtml: String,
    val deliveryMethod: String,
)

@Service
class WaiverService(
    private val monitoringRepository: CovenantMonitoringRepository,
    private val waiverLetterRepository: WaiverLetterRepository,
    private val emailTemplateRepository: EmailTemplateRepository,
    private val signatureRepository: SignatureRepository,
    private val mailSender: JavaMailSender,
    private val auditService: AuditService,
    private val pdfGenerator: PdfGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    /**
     * Generate a waiver letter for a monitoring item.
     * Persists the letter for later retrieval and PDF generation.
     */
    @Transactional
    fun generateWaiverLetter(request: WaiverGenerationRequest, actorId: UUID): WaiverLetterResponse {
        val item = monitoringRepository.findById(request.monitoringItemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: ${request.monitoringItemId}") }

        if (item.status !in listOf(CovenantStatus.BREACHED, CovenantStatus.OVERDUE, CovenantStatus.TRIGGER_ACTION)) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Waiver letter can only be generated for BREACHED, OVERDUE, or TRIGGER_ACTION items",
            )
        }

        val template = request.templateId
            ?.let { emailTemplateRepository.findById(it).orElse(null) }

        val signature = request.signatureId
            ?.let { signatureRepository.findById(it).orElse(null) }

        val letterContent = buildLetter(
            templateHtml = template?.bodyHtml ?: defaultLetterTemplate(request.waived),
            signatureHtml = signature?.htmlContent,
            covenantName = item.covenant.name,
            customerName = item.covenant.covenantCustomer.customer.name,
            periodEnd = item.periodEnd.toString(),
            calculatedValue = item.calculatedValue?.toPlainString() ?: item.manualValue?.toPlainString() ?: "N/A",
            waiverType = request.waiverType,
            comments = request.comments,
        )

        val letter = WaiverLetter().also {
            it.tenantId = resolvedTenantId()
            it.monitoringItem = item
            it.waiverType = WaiverType.valueOf(request.waiverType)
            it.waived = request.waived
            it.letterContent = letterContent
            it.templateId = request.templateId
            it.signatureId = request.signatureId
            it.comments = request.comments
            it.generatedBy = actorId
            it.generatedAt = Instant.now()
        }

        val saved = waiverLetterRepository.save(letter)
        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "WAIVER_LETTER_GENERATED",
            action = AuditAction.CREATE,
            entityType = "waiver_letter",
            entityId = saved.id.toString(),
            diffJson = """{"waiverType":"${request.waiverType}","waived":${request.waived}}""",
        )

        return saved.toResponse()
    }

    /**
     * Send a waiver letter to specified recipients via email.
     * Marks the letter as sent and closes the monitoring item.
     */
    @Transactional
    fun sendWaiverLetter(waiverLetterId: UUID, recipientEmails: List<String>, actorId: UUID): WaiverLetterResponse {
        val letter = waiverLetterRepository.findById(waiverLetterId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Waiver letter not found: $waiverLetterId") }

        val item = letter.monitoringItem
        var sentCount = 0

        for (email in recipientEmails) {
            try {
                val subject = if (letter.waived)
                    "Covenant Waiver: ${item.covenant.name}"
                else
                    "Covenant Non-Waiver Notice: ${item.covenant.name}"

                sendEmail(email, subject, letter.letterContent)
                sentCount++
                log.info("Sent waiver letter {} to {}", waiverLetterId, email)
            } catch (e: Exception) {
                log.error("Failed to send waiver letter to {}: {}", email, e.message)
            }
        }

        if (sentCount > 0) {
            letter.sentAt = Instant.now()
            letter.sentBy = actorId
            item.status = CovenantStatus.CLOSED
            waiverLetterRepository.save(letter)
            monitoringRepository.save(item)

            auditService.record(
                tenantId = resolvedTenantId().toString(),
                eventType = "WAIVER_LETTER_SENT",
                action = AuditAction.UPDATE,
                entityType = "waiver_letter",
                entityId = waiverLetterId.toString(),
                diffJson = """{"sentCount":$sentCount,"status":"CLOSED"}""",
            )
        }

        return letter.toResponse()
    }

    /**
     * Download waiver letter as PDF.
     * Returns ByteArray suitable for HTTP response with application/pdf content type.
     */
    @Transactional(readOnly = true)
    fun downloadWaiverLetter(waiverLetterId: UUID): ByteArray {
        val letter = waiverLetterRepository.findById(waiverLetterId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Waiver letter not found: $waiverLetterId") }

        return pdfGenerator.renderHtml(
            htmlContent = letter.letterContent,
            title = "Waiver Letter - ${letter.monitoringItem.covenant.name}",
        )
    }

    /**
     * Legacy waiver processing method (preserved for backward compatibility).
     */
    @Transactional
    fun processWaiver(request: WaiverRequest, actorId: UUID): WaiverLetterResult {
        val item = monitoringRepository.findById(request.monitoringItemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: ${request.monitoringItemId}") }

        if (item.status !in listOf(CovenantStatus.BREACHED, CovenantStatus.OVERDUE, CovenantStatus.TRIGGER_ACTION)) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Waiver can only be applied to BREACHED, OVERDUE, or TRIGGER_ACTION items",
            )
        }

        // Build letter from template
        val template = request.emailTemplateId
            ?.let { emailTemplateRepository.findById(it).orElse(null) }

        val signature = request.signatureId
            ?.let { signatureRepository.findById(it).orElse(null) }

        val letterHtml = buildLetter(
            templateHtml = template?.bodyHtml ?: defaultLetterTemplate(request.letterType == "WAIVE"),
            signatureHtml = signature?.htmlContent,
            covenantName = item.covenant.name,
            customerName = item.covenant.covenantCustomer.customer.name,
            periodEnd = item.periodEnd.toString(),
            calculatedValue = item.calculatedValue?.toPlainString() ?: item.manualValue?.toPlainString() ?: "N/A",
            waiverType = request.waiverType,
            comments = request.comments,
        )

        // Close the monitoring item
        item.status = CovenantStatus.CLOSED
        monitoringRepository.save(item)

        auditService.record(
            tenantId = resolvedTenantId().toString(),
            eventType = "${request.letterType}_LETTER_SENT",
            action = AuditAction.LOCK,
            entityType = "covenant_monitoring_item",
            entityId = item.id.toString(),
            diffJson = """{"waiverType":"${request.waiverType}","letterType":"${request.letterType}","delivery":"${request.deliveryMethod}"}""",
        )

        return WaiverLetterResult(
            monitoringItemId = item.id!!,
            letterHtml = letterHtml,
            deliveryMethod = request.deliveryMethod,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildLetter(
        templateHtml: String,
        signatureHtml: String?,
        covenantName: String,
        customerName: String,
        periodEnd: String,
        calculatedValue: String,
        waiverType: String,
        comments: String?,
    ): String {
        val body = templateHtml
            .replace("{{COVENANT_NAME}}", covenantName)
            .replace("{{CUSTOMER_NAME}}", customerName)
            .replace("{{PERIOD_END}}", periodEnd)
            .replace("{{CALCULATED_VALUE}}", calculatedValue)
            .replace("{{WAIVER_TYPE}}", waiverType)
            .replace("{{COMMENTS}}", comments ?: "")

        return if (signatureHtml != null) "$body\n$signatureHtml" else body
    }

    private fun defaultLetterTemplate(isWaiving: Boolean): String {
        val header = "<html><head><meta charset=\"UTF-8\"><style>body { font-family: Arial, sans-serif; }</style></head><body>"
        val footer = "</body></html>"

        val content = if (isWaiving)
            "<p>Dear {{CUSTOMER_NAME}},</p>" +
            "<p>We refer to the covenant <strong>{{COVENANT_NAME}}</strong> for the period ending {{PERIOD_END}}. " +
            "The computed value was {{CALCULATED_VALUE}}. " +
            "We hereby confirm a waiver of this covenant breach.</p>" +
            "<p>{{COMMENTS}}</p>"
        else
            "<p>Dear {{CUSTOMER_NAME}},</p>" +
            "<p>We refer to the covenant <strong>{{COVENANT_NAME}}</strong> for the period ending {{PERIOD_END}}. " +
            "The computed value was {{CALCULATED_VALUE}}. " +
            "We hereby inform you that we are not waiving this covenant breach and reserve all rights.</p>" +
            "<p>{{COMMENTS}}</p>"

        return header + content + footer
    }

    private fun sendEmail(to: String, subject: String, htmlBody: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)
        helper.setFrom("noreply@numera.io")
        mailSender.send(message)
    }

    private fun WaiverLetter.toResponse() = WaiverLetterResponse(
        id = id!!,
        monitoringItemId = monitoringItem.id!!,
        waiverType = waiverType.name,
        waived = waived,
        letterContent = letterContent,
        comments = comments,
        generatedBy = generatedBy,
        generatedAt = generatedAt,
        sentAt = sentAt,
    )
}
