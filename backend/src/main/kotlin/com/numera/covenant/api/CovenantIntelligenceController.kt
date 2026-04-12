package com.numera.covenant.api

import com.numera.covenant.application.CovenantIntelligenceService
import com.numera.covenant.dto.CalendarEntry
import com.numera.covenant.dto.PortfolioSummary
import com.numera.covenant.dto.RiskHeatmapData
import com.numera.covenant.dto.TrendlineData
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/covenants/intelligence")
class CovenantIntelligenceController(
    private val intelligenceService: CovenantIntelligenceService,
) {

    @GetMapping("/heatmap")
    fun getRiskHeatmap(): RiskHeatmapData =
        intelligenceService.getRiskHeatmap()

    @GetMapping("/trendline/{covenantId}")
    fun getTrendline(
        @PathVariable covenantId: UUID,
        @RequestParam(defaultValue = "12") periods: Int,
    ): TrendlineData = intelligenceService.getTrendlines(covenantId, periods)

    @GetMapping("/calendar")
    fun getCalendar(
        @RequestParam(defaultValue = "90") days: Int,
    ): List<CalendarEntry> = intelligenceService.getCalendar(days = days)

    @GetMapping("/summary")
    fun getPortfolioSummary(): PortfolioSummary =
        intelligenceService.getPortfolioSummary()

    @PostMapping("/recompute/{customerId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun recomputeForCustomer(
        @PathVariable customerId: UUID,
        @RequestParam tenantId: UUID,
    ): Map<String, String> {
        intelligenceService.recomputeForCustomer(customerId, tenantId)
        return mapOf("status" to "accepted", "customerId" to customerId.toString())
    }
}
