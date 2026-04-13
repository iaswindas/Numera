package com.numera.reporting.application

import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.security.TenantContext
import com.numera.shared.domain.TenantAwareEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

data class ReportFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val status: String? = null,
    val customerId: UUID? = null,
    val groupId: UUID? = null,
)

enum class ReportType { SPREADING, COVENANT, AUDIT }
enum class ReportFormat { XLSX, PDF, CSV }

/**
 * Orchestrates report generation by delegating to specialised generators.
 */
@Service
class ReportService(
    private val spreadingReportGenerator: SpreadingReportGenerator,
    private val covenantReportGenerator: CovenantReportGenerator,
    private val auditReportGenerator: AuditReportGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun generate(report: ReportType, format: ReportFormat, filter: ReportFilter): ByteArray {
        val tenantId = resolvedTenantId()
        log.info("Generating {} report as {} for tenant {}", report, format, tenantId)

        return when (report) {
            ReportType.SPREADING -> spreadingReportGenerator.generate(tenantId, format, filter)
            ReportType.COVENANT -> covenantReportGenerator.generate(tenantId, format, filter)
            ReportType.AUDIT -> auditReportGenerator.generate(tenantId, format, filter)
        }
    }

    fun contentType(format: ReportFormat): String = when (format) {
        ReportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ReportFormat.PDF -> "application/pdf"
        ReportFormat.CSV -> "text/csv"
    }

    fun fileExtension(format: ReportFormat): String = format.name.lowercase()
}
