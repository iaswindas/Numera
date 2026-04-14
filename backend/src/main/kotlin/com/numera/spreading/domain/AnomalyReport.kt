package com.numera.spreading.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "anomaly_reports")
class AnomalyReport : BaseEntity() {
    @Column(name = "spread_item_id", nullable = false)
    var spreadItemId: UUID = UUID.randomUUID()

    @Column(name = "overall_risk_score", nullable = false)
    var overallRiskScore: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false, columnDefinition = "text")
    var summary: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anomalies_json", nullable = false, columnDefinition = "jsonb")
    var anomaliesJson: String = "[]"

    @Column(name = "checked_at", nullable = false)
    var checkedAt: Instant = Instant.now()
}

@Repository
interface AnomalyReportRepository : JpaRepository<AnomalyReport, UUID> {
    fun findBySpreadItemId(spreadItemId: UUID): List<AnomalyReport>
    fun findTopBySpreadItemIdOrderByCheckedAtDesc(spreadItemId: UUID): AnomalyReport?
}
