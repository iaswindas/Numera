package com.numera.spreading.api

import com.numera.spreading.domain.SpreadStatus
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import com.numera.shared.security.TenantContext
import com.numera.shared.domain.TenantAwareEntity
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/reports")
class SpreadingReportController(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    @GetMapping("/spreading-summary")
    fun spreadingSummary(): Map<String, Any> {
        val tenantId = resolvedTenantId()
        val spreads = spreadItemRepository.findByTenantId(tenantId)
        return mapOf(
            "total" to spreads.size,
            "byStatus" to spreads.groupingBy { it.status.name }.eachCount(),
            "items" to spreads.map {
                mapOf(
                    "id" to it.id.toString(),
                    "customer" to it.customer.name,
                    "statementDate" to it.statementDate.toString(),
                    "status" to it.status.name,
                    "version" to it.currentVersion,
                    "updatedAt" to it.updatedAt.toString(),
                )
            },
        )
    }

    @GetMapping("/export", produces = ["text/csv"])
    fun export(
        @RequestParam(required = false) status: SpreadStatus?,
    ): ResponseEntity<String> {
        val tenantId = resolvedTenantId()
        val spreads = spreadItemRepository.findByTenantId(tenantId)
            .filter { status == null || it.status == status }

        log.info("Exporting spreading report: tenant={}, status={}, spreadCount={}", tenantId, status, spreads.size)

        val csv = buildString {
            appendLine(
                listOf(
                    "spread_id",
                    "customer_name",
                    "statement_date",
                    "status",
                    "version",
                    "frequency",
                    "source_currency",
                    "line_item_code",
                    "line_item_label",
                    "mapped_value",
                    "confidence_score",
                    "confidence_level",
                    "source_page",
                    "source_text",
                    "expression_type",
                    "is_manual_override",
                    "is_autofilled",
                    "notes",
                ).joinToString(",")
            )

            spreads.forEach { spread ->
                val values = spreadValueRepository.findBySpreadItemId(spread.id!!)
                if (values.isEmpty()) {
                    appendLine(
                        listOf(
                            csvEscape(spread.id),
                            csvEscape(spread.customer.name),
                            csvEscape(spread.statementDate),
                            csvEscape(spread.status.name),
                            csvEscape(spread.currentVersion),
                            csvEscape(spread.frequency),
                            csvEscape(spread.sourceCurrency),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                        ).joinToString(",")
                    )
                } else {
                    values.forEach { value ->
                        appendLine(
                            listOf(
                                csvEscape(spread.id),
                                csvEscape(spread.customer.name),
                                csvEscape(spread.statementDate),
                                csvEscape(spread.status.name),
                                csvEscape(spread.currentVersion),
                                csvEscape(spread.frequency),
                                csvEscape(spread.sourceCurrency),
                                csvEscape(value.itemCode),
                                csvEscape(value.label),
                                csvEscape(value.mappedValue),
                                csvEscape(value.confidenceScore),
                                csvEscape(value.confidenceLevel),
                                csvEscape(value.sourcePage),
                                csvEscape(value.sourceText),
                                csvEscape(value.expressionType),
                                csvEscape(value.manualOverride),
                                csvEscape(value.autofilled),
                                csvEscape(value.notes),
                            ).joinToString(",")
                        )
                    }
                }
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=spreading-report.csv")
            .body(csv)
    }

    private fun csvEscape(value: Any?): String {
        if (value == null) return ""
        val text = value.toString().replace("\"", "\"\"")
        return "\"$text\""
    }
}
