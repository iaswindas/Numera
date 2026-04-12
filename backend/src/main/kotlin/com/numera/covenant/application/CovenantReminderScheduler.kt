package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.EmailTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Scheduled task for sending automated covenant reminder emails.
 * Runs daily at 8 AM to check for:
 * - Items due within the configured reminder days
 * - Items overdue by the configured days
 */
@Component
class CovenantReminderScheduler(
    private val monitoringRepository: CovenantMonitoringRepository,
    private val emailTemplateRepository: EmailTemplateRepository,
    private val mailSender: JavaMailSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // In-memory dedup keys of reminders sent for a specific day/type/item.
    private val sentReminders: MutableMap<String, LocalDate> = ConcurrentHashMap()

    /**
     * Send due reminders (X days before due date).
     * Runs daily at 08:00 UTC.
     */
    @Scheduled(cron = "0 8 * * * ?")
    @Transactional
    fun sendDueReminders() {
        log.info("Starting sendDueReminders scheduler")
        try {
            val items = monitoringRepository.findByStatusIn(
                listOf(CovenantStatus.DUE, CovenantStatus.SUBMITTED)
            )

            var sentCount = 0
            for (item in items) {
                val covenant = item.covenant
                val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), item.dueDate)
                val shouldRemind = daysUntilDue in (0..covenant.reminderDaysBefore.toLong())

                if (shouldRemind && !alreadySentToday(item.id!!)) {
                    val template = emailTemplateRepository.findByTenantIdAndTemplateCategory(
                        item.covenant.tenantId,
                        "DUE_REMINDER"
                    ).firstOrNull()

                    if (template != null) {
                        val contacts = item.covenant.covenantCustomer.contacts
                        for (contact in contacts) {
                            try {
                                val subject = template.subject?.replace("{{COVENANT_NAME}}", item.covenant.name)
                                    ?: "Reminder: Covenant ${item.covenant.name} Due ${item.dueDate}"
                                val body = template.bodyHtml
                                    .replace("{{COVENANT_NAME}}", item.covenant.name)
                                    .replace("{{CUSTOMER_NAME}}", item.covenant.covenantCustomer.customer.name)
                                    .replace("{{PERIOD_END}}", item.periodEnd.toString())
                                    .replace("{{DUE_DATE}}", item.dueDate.toString())
                                    .replace("{{DAYS_UNTIL_DUE}}", daysUntilDue.toString())

                                sendEmail(contact.email, subject ?: "Covenant Reminder", body)
                                sentCount++
                                log.info("Sent due reminder for covenant {} to {}", item.covenant.name, contact.email)
                            } catch (e: Exception) {
                                log.error("Failed to send due reminder to {}: {}", contact.email, e.message)
                            }
                        }
                        sentReminders[dueReminderKey(item.id!!)] = LocalDate.now()
                    }
                }
            }
            log.info("Completed sendDueReminders: sent {} reminders", sentCount)
        } catch (e: Exception) {
            log.error("Error in sendDueReminders: {}", e.message, e)
        }
    }

    /**
     * Send overdue escalation emails (Y days after due date).
     * Runs daily at 08:15 UTC.
     */
    @Scheduled(cron = "0 15 8 * * ?")
    @Transactional
    fun sendOverdueReminders() {
        log.info("Starting sendOverdueReminders scheduler")
        try {
            val items = monitoringRepository.findByStatusIn(
                listOf(CovenantStatus.OVERDUE, CovenantStatus.SUBMITTED)
            )

            var sentCount = 0
            for (item in items) {
                val covenant = item.covenant
                val daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(item.dueDate, LocalDate.now())
                val shouldRemind = daysOverdue >= covenant.reminderDaysAfter

                if (shouldRemind && !wasSentToday(item.id!!, "OVERDUE")) {
                    val template = emailTemplateRepository.findByTenantIdAndTemplateCategory(
                        item.covenant.tenantId,
                        "OVERDUE_NOTICE"
                    ).firstOrNull()

                    if (template != null) {
                        val contacts = item.covenant.covenantCustomer.contacts
                        for (contact in contacts) {
                            try {
                                val subject = template.subject?.replace("{{COVENANT_NAME}}", item.covenant.name)
                                    ?: "URGENT: Covenant ${item.covenant.name} is Overdue"
                                val body = template.bodyHtml
                                    .replace("{{COVENANT_NAME}}", item.covenant.name)
                                    .replace("{{CUSTOMER_NAME}}", item.covenant.covenantCustomer.customer.name)
                                    .replace("{{PERIOD_END}}", item.periodEnd.toString())
                                    .replace("{{DUE_DATE}}", item.dueDate.toString())
                                    .replace("{{DAYS_OVERDUE}}", daysOverdue.toString())

                                sendEmail(contact.email, subject ?: "URGENT: Covenant Overdue", body)
                                sentCount++
                                log.info("Sent overdue notice for covenant {} to {}", item.covenant.name, contact.email)
                            } catch (e: Exception) {
                                log.error("Failed to send overdue notice to {}: {}", contact.email, e.message)
                            }
                        }
                        sentReminders[overdueReminderKey(item.id!!)] = LocalDate.now()
                    }
                }
            }
            log.info("Completed sendOverdueReminders: sent {} notices", sentCount)
        } catch (e: Exception) {
            log.error("Error in sendOverdueReminders: {}", e.message, e)
        }
    }

    /**
     * Check if a reminder was already sent for this item today.
     */
    private fun alreadySentToday(itemId: UUID): Boolean {
        val lastSent = sentReminders[dueReminderKey(itemId)]
        return lastSent != null && lastSent == LocalDate.now()
    }

    /**
     * Check if an overdue reminder was already sent for this item today.
     */
    private fun wasSentToday(itemId: UUID, type: String): Boolean {
        val key = when (type.uppercase()) {
            "OVERDUE" -> overdueReminderKey(itemId)
            else -> "${itemId}:${type.uppercase()}:${LocalDate.now()}"
        }
        val lastSent = sentReminders[key]
        return lastSent != null && lastSent == LocalDate.now()
    }

    private fun dueReminderKey(itemId: UUID): String = "${itemId}:DUE:${LocalDate.now()}"

    private fun overdueReminderKey(itemId: UUID): String = "${itemId}:OVERDUE:${LocalDate.now()}"

    /**
     * Send an email via JavaMailSender.
     */
    private fun sendEmail(to: String, subject: String, htmlBody: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)
        helper.setFrom("noreply@numera.io")
        mailSender.send(message)
    }
}
