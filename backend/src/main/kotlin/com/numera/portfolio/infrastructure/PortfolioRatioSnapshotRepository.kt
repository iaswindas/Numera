package com.numera.portfolio.infrastructure

import com.numera.portfolio.domain.PortfolioRatioSnapshot
import com.numera.portfolio.domain.RatioCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface PortfolioRatioSnapshotRepository : JpaRepository<PortfolioRatioSnapshot, UUID> {

    fun findByTenantId(tenantId: UUID): List<PortfolioRatioSnapshot>

    fun findByTenantIdAndCustomerId(tenantId: UUID, customerId: UUID): List<PortfolioRatioSnapshot>

    fun findByTenantIdAndRatioCode(tenantId: UUID, ratioCode: RatioCode): List<PortfolioRatioSnapshot>

    @Query("""
        SELECT s FROM PortfolioRatioSnapshot s
        WHERE s.tenantId = :tenantId
        AND s.ratioCode = :ratioCode
        AND s.customerId = :customerId
        ORDER BY s.statementDate ASC
    """)
    fun findTrend(
        @Param("tenantId") tenantId: UUID,
        @Param("customerId") customerId: UUID,
        @Param("ratioCode") ratioCode: RatioCode,
    ): List<PortfolioRatioSnapshot>

    @Query("""
        SELECT s FROM PortfolioRatioSnapshot s
        WHERE s.tenantId = :tenantId
        AND (:ratioCode IS NULL OR s.ratioCode = :ratioCode)
        AND (:fromDate IS NULL OR s.statementDate >= :fromDate)
        AND (:toDate IS NULL OR s.statementDate <= :toDate)
    """)
    fun findByQuery(
        @Param("tenantId") tenantId: UUID,
        @Param("ratioCode") ratioCode: RatioCode?,
        @Param("fromDate") fromDate: LocalDate?,
        @Param("toDate") toDate: LocalDate?,
        pageable: Pageable,
    ): Page<PortfolioRatioSnapshot>

    @Query("""
        SELECT s FROM PortfolioRatioSnapshot s
        WHERE s.tenantId = :tenantId
        AND s.changePercent IS NOT NULL
        AND (s.changePercent > :threshold OR s.changePercent < -:threshold)
        ORDER BY ABS(s.changePercent) DESC
    """)
    fun findSignificantChanges(
        @Param("tenantId") tenantId: UUID,
        @Param("threshold") threshold: java.math.BigDecimal,
    ): List<PortfolioRatioSnapshot>

    fun findByTenantIdAndSpreadItemId(tenantId: UUID, spreadItemId: UUID): List<PortfolioRatioSnapshot>
}
