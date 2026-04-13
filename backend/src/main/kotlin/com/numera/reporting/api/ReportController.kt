package com.numera.reporting.api

import com.numera.reporting.application.ReportFilter
import com.numera.reporting.application.ReportFormat
import com.numera.reporting.application.ReportService
import com.numera.reporting.application.ReportType
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/reports")
class ReportController(
    private val reportService: ReportService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/generate")
    fun export(
        @RequestParam report: String,
        @RequestParam(defaultValue = "xlsx") format: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) customerId: UUID?,
        @RequestParam(required = false) groupId: UUID?,
    ): ResponseEntity<ByteArray> {
        val reportType = runCatching { ReportType.valueOf(report.uppercase()) }.getOrElse {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Invalid report type: $report. Valid: ${ReportType.entries.joinToString()}")
        }
        val reportFormat = runCatching { ReportFormat.valueOf(format.uppercase()) }.getOrElse {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Invalid format: $format. Valid: ${ReportFormat.entries.joinToString()}")
        }

        val filter = ReportFilter(
            startDate = startDate,
            endDate = endDate,
            status = status,
            customerId = customerId,
            groupId = groupId,
        )

        val bytes = reportService.generate(reportType, reportFormat, filter)
        val filename = "${report.lowercase()}-report.${reportService.fileExtension(reportFormat)}"

        log.info("Serving report {} as {} ({} bytes)", reportType, reportFormat, bytes.size)

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(reportService.contentType(reportFormat)))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .body(bytes)
    }
}
