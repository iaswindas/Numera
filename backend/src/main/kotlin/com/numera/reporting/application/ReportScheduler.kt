package com.numera.reporting.application

import com.numera.reporting.domain.ReportSchedule
import com.numera.reporting.infrastructure.ReportScheduleRepository
import com.numera.shared.security.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * Checks for due report schedules and generates/emails them automatically.
 */
@Service
class ReportScheduler(
    private val scheduleRepository: ReportScheduleRepository,
    private val reportService: ReportService,
    private val mailSender: JavaMailSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${numera.reports.scheduler-interval-ms:300000}")
    @Transactional
    fun processScheduledReports() {
        val now = Instant.now()
        val due = scheduleRepository.findByEnabledTrueAndNextRunAtBefore(now)

        if (due.isEmpty()) return
        log.info("Processing {} scheduled reports", due.size)

        for (schedule in due) {
            try {
                TenantContext.set(schedule.tenantId.toString())
                processOne(schedule)
            } catch (ex: Exception) {
                log.error("Failed to process scheduled report {}: {}", schedule.id, ex.message, ex)
                schedule.lastError = ex.message?.take(500)
                scheduleRepository.save(schedule)
            } finally {
                TenantContext.clear()
            }
        }
    }

    private fun processOne(schedule: ReportSchedule) {
        val reportType = ReportType.valueOf(schedule.reportType)
        val format = ReportFormat.valueOf(schedule.reportFormat)
        val today = LocalDate.now()
        val filter = ReportFilter(
            startDate = schedule.filterStartOffset?.let { today.minusDays(it) },
            endDate = today,
        )

        val bytes = reportService.generate(reportType, format, filter)
        val filename = "${schedule.reportType.lowercase()}-report.${reportService.fileExtension(format)}"

        val recipients = schedule.recipientEmails.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (recipients.isEmpty()) {
            log.warn("No recipients for scheduled report {}", schedule.id)
        } else {
            for (recipient in recipients) {
                sendReportEmail(recipient, schedule.reportName, filename, bytes, reportService.contentType(format))
            }
            log.info("Sent scheduled report {} to {} recipients", schedule.reportName, recipients.size)
        }

        // Advance next run
        schedule.lastRunAt = Instant.now()
        schedule.lastError = null
        schedule.nextRunAt = computeNextRun(schedule)
        scheduleRepository.save(schedule)
    }

    private fun computeNextRun(schedule: ReportSchedule): Instant {
        val base = schedule.lastRunAt ?: Instant.now()
        val intervalMs = when (schedule.frequency.uppercase()) {
            "DAILY" -> 86_400_000L
            "WEEKLY" -> 604_800_000L
            "MONTHLY" -> 2_592_000_000L // ~30 days
            "QUARTERLY" -> 7_776_000_000L // ~90 days
            else -> 86_400_000L
        }
        return base.plusMillis(intervalMs)
    }

    private fun sendReportEmail(to: String, reportName: String, filename: String, data: ByteArray, contentType: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setTo(to)
        helper.setSubject("Scheduled Report: $reportName")
        helper.setText(
            "<p>Please find attached the scheduled report <strong>$reportName</strong>.</p>" +
                "<p>This report was generated automatically by Numera.</p>",
            true,
        )
        helper.setFrom("noreply@numera.io")
        helper.addAttachment(filename, org.springframework.core.io.ByteArrayResource(data), contentType)
        mailSender.send(message)
    }
}
