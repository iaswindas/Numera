package com.numera.reporting.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "report_schedules")
class ReportSchedule : TenantAwareEntity() {

    @Column(nullable = false)
    var reportName: String = ""

    @Column(nullable = false)
    var reportType: String = "" // SPREADING, COVENANT, AUDIT

    @Column(nullable = false)
    var reportFormat: String = "XLSX" // XLSX, PDF, CSV

    @Column(nullable = false)
    var frequency: String = "DAILY" // DAILY, WEEKLY, MONTHLY, QUARTERLY

    @Column(nullable = false, columnDefinition = "text")
    var recipientEmails: String = "" // Comma-separated

    @Column
    var filterStartOffset: Long? = null // Days before today for start date filter

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column
    var nextRunAt: Instant? = null

    @Column
    var lastRunAt: Instant? = null

    @Column(columnDefinition = "text")
    var lastError: String? = null
}
