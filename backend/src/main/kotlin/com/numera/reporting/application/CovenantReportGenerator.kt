package com.numera.reporting.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.shared.pdf.PdfGenerator
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.UUID

@Component
class CovenantReportGenerator(
    private val monitoringRepository: CovenantMonitoringRepository,
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
        val items = loadItems(tenantId, filter)
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Covenant Report")

        val headerStyle = wb.createCellStyle().apply {
            val font = wb.createFont().apply { bold = true }
            setFont(font)
        }
        val headers = listOf(
            "Covenant", "Customer", "Type", "Period Start", "Period End",
            "Due Date", "Status", "Calculated Value", "Manual Value", "Breach Probability",
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            headerRow.createCell(idx).apply { setCellValue(h); cellStyle = headerStyle }
        }

        items.forEachIndexed { idx, item ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(item.covenant.name)
            row.createCell(1).setCellValue(item.covenant.covenantCustomer.customer.name)
            row.createCell(2).setCellValue(item.covenant.covenantType.name)
            row.createCell(3).setCellValue(item.periodStart.toString())
            row.createCell(4).setCellValue(item.periodEnd.toString())
            row.createCell(5).setCellValue(item.dueDate.toString())
            row.createCell(6).setCellValue(item.status.name)
            row.createCell(7).setCellValue(item.calculatedValue?.toDouble() ?: 0.0)
            row.createCell(8).setCellValue(item.manualValue?.toDouble() ?: 0.0)
            row.createCell(9).setCellValue(item.breachProbability?.toDouble() ?: 0.0)
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }

        log.info("Generated covenant Excel report: {} items", items.size)
        return toBytes(wb)
    }

    private fun generatePdf(tenantId: UUID, filter: ReportFilter): ByteArray {
        val items = loadItems(tenantId, filter)
        val html = buildString {
            append("<h1>Covenant Monitoring Report</h1>")
            append("<table><thead><tr>")
            listOf("Covenant", "Customer", "Period End", "Status", "Calculated Value", "Breach Prob.")
                .forEach { append("<th>$it</th>") }
            append("</tr></thead><tbody>")
            for (item in items) {
                append("<tr>")
                append("<td>${esc(item.covenant.name)}</td>")
                append("<td>${esc(item.covenant.covenantCustomer.customer.name)}</td>")
                append("<td>${item.periodEnd}</td>")
                append("<td>${item.status.name}</td>")
                append("<td>${item.calculatedValue?.toPlainString() ?: "-"}</td>")
                append("<td>${item.breachProbability?.toPlainString() ?: "-"}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
        }
        return pdfGenerator.renderHtml(html, PdfGenerator.PdfOptions(title = "Covenant Monitoring Report"))
    }

    private fun generateCsv(tenantId: UUID, filter: ReportFilter): ByteArray {
        val items = loadItems(tenantId, filter)
        val csv = buildString {
            appendLine("covenant,customer,type,period_start,period_end,due_date,status,calculated_value,manual_value")
            for (i in items) {
                appendLine("\"${i.covenant.name}\",\"${i.covenant.covenantCustomer.customer.name}\",${i.covenant.covenantType.name},${i.periodStart},${i.periodEnd},${i.dueDate},${i.status.name},${i.calculatedValue ?: ""},${i.manualValue ?: ""}")
            }
        }
        return csv.toByteArray(Charsets.UTF_8)
    }

    private fun loadItems(tenantId: UUID, filter: ReportFilter) =
        monitoringRepository.findByTenantId(tenantId).filter { item ->
            val matchStatus = filter.status == null ||
                runCatching { CovenantStatus.valueOf(filter.status!!) }.getOrNull() == item.status
            val matchCustomer = filter.customerId == null || item.covenant.covenantCustomer.customer.id == filter.customerId
            val matchDate = (filter.startDate == null || !item.periodEnd.isBefore(filter.startDate)) &&
                (filter.endDate == null || !item.periodEnd.isAfter(filter.endDate))
            matchStatus && matchCustomer && matchDate
        }

    private fun toBytes(wb: XSSFWorkbook): ByteArray {
        val out = ByteArrayOutputStream()
        wb.use { it.write(out) }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
