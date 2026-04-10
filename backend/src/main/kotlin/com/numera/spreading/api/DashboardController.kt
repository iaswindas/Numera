package com.numera.spreading.api

import com.numera.customer.infrastructure.CustomerRepository
import com.numera.document.infrastructure.DocumentRepository
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val customerRepository: CustomerRepository,
    private val documentRepository: DocumentRepository,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
) {
    @GetMapping("/stats")
    fun stats(): Map<String, Any> {
        val spreads = spreadItemRepository.findAll().sortedByDescending { it.updatedAt }
        val documents = documentRepository.findAll()
        val allValues = spreads.flatMap { spreadValueRepository.findBySpreadItemId(it.id!!) }
        val avgAccuracy = allValues
            .mapNotNull { it.confidenceScore }
            .takeIf { it.isNotEmpty() }
            ?.let { values -> values.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP) }
            ?: BigDecimal.ZERO
        val avgProcessingMs = documents.mapNotNull { it.processingTimeMs }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val recentSpreads = spreads.take(5).map {
            val vals = spreadValueRepository.findBySpreadItemId(it.id!!)
            val spreadAcc = vals.mapNotNull { value -> value.confidenceScore }.takeIf { conf -> conf.isNotEmpty() }
                ?.let { conf -> conf.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(conf.size), 2, RoundingMode.HALF_UP) }
                ?: BigDecimal.ZERO
            mapOf(
                "id" to it.id.toString(),
                "customerId" to it.customer.id.toString(),
                "customerName" to it.customer.name,
                "status" to it.status.name,
                "statementDate" to it.statementDate.toString(),
                "accuracy" to spreadAcc,
                "processingTimeMs" to it.document.processingTimeMs,
                "updatedAt" to it.updatedAt.toString(),
            )
        }
        val trend = (0..6).map { idx ->
            val month = YearMonth.now().minusMonths((6 - idx).toLong())
            val count = spreads.count { YearMonth.from(it.createdAt.atZone(java.time.ZoneOffset.UTC)) == month }
            mapOf("month" to month.toString(), "count" to count)
        }
        return mapOf(
            "customers" to customerRepository.count(),
            "documents" to documentRepository.count(),
            "spreads" to spreadItemRepository.count(),
            "aiAccuracy" to avgAccuracy,
            "avgProcessingTimeMs" to avgProcessingMs.toLong(),
            "covenantRiskCount" to 0,
            "recentSpreads" to recentSpreads,
            "spreadTrend" to trend,
            "covenantStatusDistribution" to emptyMap<String, Long>(),
        )
    }
}