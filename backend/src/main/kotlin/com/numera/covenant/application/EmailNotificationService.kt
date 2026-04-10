package com.numera.covenant.application

import com.numera.covenant.events.CovenantBreachedEvent
import com.numera.covenant.events.CovenantStatusChangedEvent
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.EmailTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EmailNotificationService(
    private val mailSender: JavaMailSender,
    private val emailTemplateRepository: EmailTemplateRepository,
    private val monitoringRepository: CovenantMonitoringRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onCovenantBreached(event: CovenantBreachedEvent) {
        log.info("Sending breach alert email for covenant {} customer {}", event.covenantName, event.customerName)

        val templates = emailTemplateRepository.findByTenantIdAndTemplateCategory(event.tenantId, "BREACH_ALERT")
        val template = templates.firstOrNull() ?: run {
            log.warn("No BREACH_ALERT email template found for tenant {}", event.tenantId)
            return
        }

        val subject = resolveVariables(template.subject ?: "Covenant Breach Alert: ${event.covenantName}", event)
        val body = resolveVariables(template.bodyHtml, event)

        // Fetch contacts from the monitoring item's customer
        val monitoringItem = monitoringRepository.findById(event.monitoringItemId).orElse(null) ?: return
        val contacts = monitoringItem.covenant.covenantCustomer.contacts

        if (contacts.isEmpty()) {
            log.warn("No contacts found for customer {}, skipping email", event.customerName)
            return
        }

        for (contact in contacts) {
            try {
                sendEmail(contact.email, subject, body)
                log.info("Breach alert sent to {} for covenant {}", contact.email, event.covenantName)
            } catch (e: Exception) {
                log.error("Failed to send breach alert email to {}: {}", contact.email, e.message)
            }
        }
    }

    @Async
    @EventListener
    fun onCovenantStatusChanged(event: CovenantStatusChangedEvent) {
        if (event.newStatus == "BREACHED") return // Handled by breach-specific handler

        val category = when (event.newStatus) {
            "DUE" -> "DUE_REMINDER"
            "OVERDUE" -> "OVERDUE_NOTICE"
            else -> return // No email for MET, CLOSED, etc.
        }

        val templates = emailTemplateRepository.findByTenantIdAndTemplateCategory(event.tenantId, category)
        val template = templates.firstOrNull() ?: run {
            log.debug("No {} email template found for tenant {}", category, event.tenantId)
            return
        }

        val monitoringItem = monitoringRepository.findById(event.monitoringItemId).orElse(null) ?: return
        val contacts = monitoringItem.covenant.covenantCustomer.contacts

        val vars = mapOf(
            "{{COVENANT_NAME}}" to monitoringItem.covenant.name,
            "{{CUSTOMER_NAME}}" to monitoringItem.covenant.covenantCustomer.customer.name,
            "{{PERIOD_END}}" to monitoringItem.periodEnd.toString(),
            "{{DUE_DATE}}" to monitoringItem.dueDate.toString(),
            "{{STATUS}}" to event.newStatus,
        )

        val subject = vars.entries.fold(template.subject ?: "Covenant $category") { s, (k, v) -> s.replace(k, v) }
        val body = vars.entries.fold(template.bodyHtml) { s, (k, v) -> s.replace(k, v) }

        for (contact in contacts) {
            try {
                sendEmail(contact.email, subject, body)
            } catch (e: Exception) {
                log.error("Failed to send {} email to {}: {}", category, contact.email, e.message)
            }
        }
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

    private fun resolveVariables(text: String, event: CovenantBreachedEvent): String =
        text.replace("{{COVENANT_NAME}}", event.covenantName)
            .replace("{{CUSTOMER_NAME}}", event.customerName)
            .replace("{{PERIOD_END}}", event.periodEnd.toString())
            .replace("{{CALCULATED_VALUE}}", event.calculatedValue?.toPlainString() ?: "N/A")
            .replace("{{THRESHOLD_VALUE}}", event.thresholdValue?.toPlainString() ?: "N/A")
}
