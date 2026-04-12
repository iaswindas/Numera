package com.numera.reporting.application

import com.numera.shared.pdf.PdfGenerator
import com.numera.spreading.domain.SpreadStatus
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.UUID

@Component
class SpreadingReportGenerator(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
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
        val spreads = loadSpreads(tenantId, filter)
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Spreading Report")

        // Header row
        val headerStyle = wb.createCellStyle().apply {
            val font = wb.createFont().apply { bold = true }
            setFont(font)
        }
        val headers = listOf(
            "Spread ID", "Customer", "Statement Date", "Status", "Version",
            "Frequency", "Currency", "Line Item Code", "Label", "Mapped Value",
            "Confidence", "Source Page", "Manual Override", "Notes",
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            headerRow.createCell(idx).apply { setCellValue(h); cellStyle = headerStyle }
        }

        var rowIdx = 1
        for (spread in spreads) {
            val values = spreadValueRepository.findBySpreadItemId(spread.id!!)
            if (values.isEmpty()) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(spread.id.toString())
                row.createCell(1).setCellValue(spread.customer.name)
                row.createCell(2).setCellValue(spread.statementDate.toString())
                row.createCell(3).setCellValue(spread.status.name)
                row.createCell(4).setCellValue(spread.currentVersion.toDouble())
                row.createCell(5).setCellValue(spread.frequency)
                row.createCell(6).setCellValue(spread.sourceCurrency ?: "")
            } else {
                for (v in values) {
                    val row = sheet.createRow(rowIdx++)
                    row.createCell(0).setCellValue(spread.id.toString())
                    row.createCell(1).setCellValue(spread.customer.name)
                    row.createCell(2).setCellValue(spread.statementDate.toString())
                    row.createCell(3).setCellValue(spread.status.name)
                    row.createCell(4).setCellValue(spread.currentVersion.toDouble())
                    row.createCell(5).setCellValue(spread.frequency)
                    row.createCell(6).setCellValue(spread.sourceCurrency ?: "")
                    row.createCell(7).setCellValue(v.itemCode)
                    row.createCell(8).setCellValue(v.label)
                    row.createCell(9).setCellValue(v.mappedValue?.toDouble() ?: 0.0)
                    row.createCell(10).setCellValue(v.confidenceLevel ?: "")
                    row.createCell(11).setCellValue(v.sourcePage?.toDouble() ?: 0.0)
                    row.createCell(12).setCellValue(v.manualOverride)
                    row.createCell(13).setCellValue(v.notes ?: "")
                }
            }
        }

        // Auto-size columns
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        log.info("Generated spreading Excel report: {} spreads", spreads.size)
        return toBytes(wb)
    }

    private fun generatePdf(tenantId: UUID, filter: ReportFilter): ByteArray {
        val spreads = loadSpreads(tenantId, filter)
        val html = buildString {
            append("<h1>Spreading Report</h1>")
            append("<table><thead><tr>")
            listOf("Customer", "Statement Date", "Status", "Version", "Frequency", "Currency")
                .forEach { append("<th>$it</th>") }
            append("</tr></thead><tbody>")
            for (spread in spreads) {
                append("<tr>")
                append("<td>${esc(spread.customer.name)}</td>")
                append("<td>${spread.statementDate}</td>")
                append("<td>${spread.status.name}</td>")
                append("<td>${spread.currentVersion}</td>")
                append("<td>${spread.frequency}</td>")
                append("<td>${spread.sourceCurrency ?: ""}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
        }
        return pdfGenerator.renderHtml(html, PdfGenerator.PdfOptions(title = "Spreading Report"))
    }

    private fun generateCsv(tenantId: UUID, filter: ReportFilter): ByteArray {
        val spreads = loadSpreads(tenantId, filter)
        val csv = buildString {
            appendLine("spread_id,customer_name,statement_date,status,version,frequency,currency")
            for (s in spreads) {
                appendLine("${s.id},\"${s.customer.name}\",${s.statementDate},${s.status.name},${s.currentVersion},${s.frequency},${s.sourceCurrency ?: ""}")
            }
        }
        return csv.toByteArray(Charsets.UTF_8)
    }

    private fun loadSpreads(tenantId: UUID, filter: ReportFilter) =
        spreadItemRepository.findByTenantId(tenantId).filter { spread ->
            val matchStatus = filter.status == null ||
                runCatching { SpreadStatus.valueOf(filter.status!!) }.getOrNull() == spread.status
            val matchCustomer = filter.customerId == null || spread.customer.id == filter.customerId
            val matchDate = (filter.startDate == null || !spread.statementDate.isBefore(filter.startDate)) &&
                (filter.endDate == null || !spread.statementDate.isAfter(filter.endDate))
            matchStatus && matchCustomer && matchDate
        }

    private fun toBytes(wb: XSSFWorkbook): ByteArray {
        val out = ByteArrayOutputStream()
        wb.use { it.write(out) }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
