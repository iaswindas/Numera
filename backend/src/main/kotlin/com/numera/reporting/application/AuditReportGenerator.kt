package com.numera.reporting.application

import com.numera.shared.audit.EventLogRepository
import com.numera.shared.pdf.PdfGenerator
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.UUID

@Component
class AuditReportGenerator(
    private val eventLogRepository: EventLogRepository,
    private val pdfGenerator: PdfGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(tenantId: UUID, format: ReportFormat, filter: ReportFilter): ByteArray {
        return when (format) {
            ReportFormat.XLSX -> generateExcel(tenantId, filter)
            ReportFormat.PDF -> generatePdf(tenantId, filter)
            ReportFormat.CSV -> generateCsv(tenantId, filter)
        }
    }

    private fun generateExcel(tenantId: UUID, filter: ReportFilter): ByteArray {
        val events = loadEvents(tenantId, filter)
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Audit Trail")

        val headerStyle = wb.createCellStyle().apply {
            val font = wb.createFont().apply { bold = true }
            setFont(font)
        }
        val headers = listOf(
            "Timestamp", "Event Type", "Action", "Actor", "Entity Type",
            "Entity ID", "Parent Entity", "Hash Verified",
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            headerRow.createCell(idx).apply { setCellValue(h); cellStyle = headerStyle }
        }

        events.forEachIndexed { idx, event ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(event.createdAt.toString())
            row.createCell(1).setCellValue(event.eventType)
            row.createCell(2).setCellValue(event.action)
            row.createCell(3).setCellValue(event.actorEmail)
            row.createCell(4).setCellValue(event.entityType)
            row.createCell(5).setCellValue(event.entityId)
            row.createCell(6).setCellValue(event.parentEntityType ?: "")
            row.createCell(7).setCellValue(event.currentHash.isNotBlank())
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }

        log.info("Generated audit Excel report: {} events", events.size)
        return toBytes(wb)
    }

    private fun generatePdf(tenantId: UUID, filter: ReportFilter): ByteArray {
        val events = loadEvents(tenantId, filter)
        val html = buildString {
            append("<h1>Audit Trail Report</h1>")
            append("<table><thead><tr>")
            listOf("Timestamp", "Event", "Action", "Actor", "Entity", "Entity ID")
                .forEach { append("<th>$it</th>") }
            append("</tr></thead><tbody>")
            for (event in events) {
                append("<tr>")
                append("<td>${event.createdAt}</td>")
                append("<td>${esc(event.eventType)}</td>")
                append("<td>${esc(event.action)}</td>")
                append("<td>${esc(event.actorEmail)}</td>")
                append("<td>${esc(event.entityType)}</td>")
                append("<td>${esc(event.entityId)}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
        }
        return pdfGenerator.renderHtml(html, PdfGenerator.PdfOptions(title = "Audit Trail Report"))
    }

    private fun generateCsv(tenantId: UUID, filter: ReportFilter): ByteArray {
        val events = loadEvents(tenantId, filter)
        val csv = buildString {
            appendLine("timestamp,event_type,action,actor,entity_type,entity_id,hash")
            for (e in events) {
                appendLine("${e.createdAt ?: ""},${e.eventType},${e.action},\"${e.actorEmail}\",${e.entityType},${e.entityId},${e.currentHash}")
            }
        }
        return csv.toByteArray(Charsets.UTF_8)
    }

    private fun loadEvents(tenantId: UUID, filter: ReportFilter) =
        eventLogRepository.findByTenantIdOrderByCreatedAtAsc(tenantId.toString()).filter { event ->
            val matchDate = (filter.startDate == null ||
                !event.createdAt.isBefore(filter.startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC))) &&
                (filter.endDate == null ||
                    !event.createdAt.isAfter(filter.endDate.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))
            val matchEntity = filter.status == null || event.entityType == filter.status
            matchDate && matchEntity
        }

    private fun toBytes(wb: XSSFWorkbook): ByteArray {
        val out = ByteArrayOutputStream()
        wb.use { it.write(out) }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
