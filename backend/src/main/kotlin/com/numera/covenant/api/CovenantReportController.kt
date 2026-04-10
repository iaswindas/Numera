package com.numera.covenant.api

import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.shared.domain.TenantAwareEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reports")
class CovenantReportController(
    private val covenantMonitoringRepository: CovenantMonitoringRepository,
) {
    @GetMapping("/covenant-summary")
    fun covenantSummary(): Map<String, Any> {
        val items = covenantMonitoringRepository.findByTenantId(TenantAwareEntity.DEFAULT_TENANT)
        return mapOf(
            "total" to items.size,
            "byStatus" to items.groupingBy { it.status.name }.eachCount(),
            "items" to items.map {
                mapOf(
                    "id" to it.id.toString(),
                    "covenantName" to it.covenant.name,
                    "customerName" to it.covenant.covenantCustomer.customer.name,
                    "status" to it.status.name,
                    "periodEnd" to it.periodEnd.toString(),
                    "dueDate" to it.dueDate.toString(),
                )
            },
        )
    }
}
