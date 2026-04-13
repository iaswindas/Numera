package com.numera.portfolio.api

import com.numera.portfolio.application.PortfolioService
import com.numera.portfolio.domain.PortfolioQueryRequest
import com.numera.portfolio.domain.PortfolioRatioRow
import com.numera.portfolio.domain.PortfolioRatioSnapshot
import com.numera.portfolio.domain.RatioAlert
import com.numera.portfolio.domain.RatioCode
import com.numera.portfolio.domain.RatioTrendPoint
import com.numera.portfolio.domain.SharedDashboard
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api")
class PortfolioController(
    private val portfolioService: PortfolioService,
) {
    // ── Portfolio Analytics ──

    @GetMapping("/portfolio/ratios")
    fun getPortfolioRatios(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<List<PortfolioRatioRow>> {
        val tenantId = extractTenantId(jwt)
        return ResponseEntity.ok(portfolioService.getPortfolioRatios(tenantId))
    }

    @GetMapping("/portfolio/ratios/trends")
    fun getRatioTrends(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam customerId: UUID,
        @RequestParam ratioCode: RatioCode,
    ): ResponseEntity<List<RatioTrendPoint>> {
        val tenantId = extractTenantId(jwt)
        return ResponseEntity.ok(portfolioService.getRatioTrends(tenantId, customerId, ratioCode))
    }

    @PostMapping("/portfolio/query")
    fun queryPortfolio(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PortfolioQueryRequest,
    ): ResponseEntity<Page<PortfolioRatioSnapshot>> {
        val tenantId = extractTenantId(jwt)
        return ResponseEntity.ok(portfolioService.queryPortfolio(tenantId, request))
    }

    @GetMapping("/portfolio/alerts")
    fun getAlerts(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "15") threshold: BigDecimal,
    ): ResponseEntity<List<RatioAlert>> {
        val tenantId = extractTenantId(jwt)
        return ResponseEntity.ok(portfolioService.getAlerts(tenantId, threshold))
    }

    @PostMapping("/portfolio/materialize")
    fun materializeSnapshots(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, String>> {
        val tenantId = extractTenantId(jwt)
        portfolioService.materializeSnapshots(tenantId)
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }

    // ── Dashboard Sharing ──

    @PostMapping("/dashboard/share")
    fun createSharedDashboard(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ShareDashboardRequest,
    ): ResponseEntity<ShareDashboardResponse> {
        val tenantId = extractTenantId(jwt)
        val userId = UUID.fromString(jwt.subject)
        val shared = portfolioService.createSharedDashboard(
            tenantId = tenantId,
            userId = userId,
            configJson = request.configJson,
            title = request.title,
            expiryHours = request.expiryHours ?: 168,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ShareDashboardResponse(
                token = shared.token,
                expiresAt = shared.expiresAt.toString(),
                shareUrl = "/dashboard/shared/${shared.token}",
            )
        )
    }

    @GetMapping("/dashboard/shared/{token}")
    fun getSharedDashboard(
        @PathVariable token: String,
    ): ResponseEntity<SharedDashboard> {
        val shared = portfolioService.getSharedDashboard(token)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(shared)
    }

    private fun extractTenantId(jwt: Jwt): UUID {
        val tenantClaim = jwt.claims["tenantId"]?.toString()
            ?: jwt.claims["tenant_id"]?.toString()
        return tenantClaim?.let { UUID.fromString(it) }
            ?: com.numera.shared.domain.TenantAwareEntity.DEFAULT_TENANT
    }
}

data class ShareDashboardRequest(
    val configJson: String,
    val title: String? = null,
    val expiryHours: Long? = null,
)

data class ShareDashboardResponse(
    val token: String,
    val expiresAt: String,
    val shareUrl: String,
)
