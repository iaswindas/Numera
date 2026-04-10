package com.numera.spreading.api

import com.numera.spreading.infrastructure.SpreadItemRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reports")
class SpreadingReportController(
    private val spreadItemRepository: SpreadItemRepository,
) {
    @GetMapping("/spreading-summary")
    fun spreadingSummary(): Map<String, Any> {
        val spreads = spreadItemRepository.findAll()
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
}
