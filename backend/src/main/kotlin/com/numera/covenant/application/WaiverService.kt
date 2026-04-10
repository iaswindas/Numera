package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.dto.WaiverRequest
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.EmailTemplateRepository
import com.numera.covenant.infrastructure.SignatureRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class WaiverLetterResult(
    val monitoringItemId: UUID,
    val letterHtml: String,
    val deliveryMethod: String,
)

@Service
class WaiverService(
    private val monitoringRepository: CovenantMonitoringRepository,
    private val emailTemplateRepository: EmailTemplateRepository,
    private val signatureRepository: SignatureRepository,
    private val auditService: AuditService,
) {

    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

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
            templateHtml = template?.bodyHtml ?: defaultLetterTemplate(request.letterType),
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
            tenantId = tenantId.toString(),
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

    private fun defaultLetterTemplate(letterType: String): String =
        if (letterType == "WAIVE")
            "<p>Dear {{CUSTOMER_NAME}},</p><p>We refer to the covenant <strong>{{COVENANT_NAME}}</strong> for the period ending {{PERIOD_END}}. The computed value was {{CALCULATED_VALUE}}. We hereby confirm a waiver of this covenant breach.</p><p>{{COMMENTS}}</p>"
        else
            "<p>Dear {{CUSTOMER_NAME}},</p><p>We refer to the covenant <strong>{{COVENANT_NAME}}</strong> for the period ending {{PERIOD_END}}. The computed value was {{CALCULATED_VALUE}}. We hereby inform you that we are not waiving this covenant breach and reserve all rights.</p><p>{{COMMENTS}}</p>"
}
