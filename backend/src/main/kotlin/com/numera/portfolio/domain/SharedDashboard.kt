package com.numera.portfolio.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "shared_dashboards")
class SharedDashboard : TenantAwareEntity() {

    @Column(nullable = false, unique = true)
    var token: String = ""

    @Column(nullable = false)
    var createdBy: UUID = UUID.randomUUID()

    @Column(nullable = false, columnDefinition = "text")
    var dashboardConfigJson: String = "{}"

    @Column
    var title: String? = null

    @Column(nullable = false)
    var expiresAt: Instant = Instant.now()

    @Column(nullable = false)
    var viewCount: Int = 0

    @Column(nullable = false)
    var active: Boolean = true
}
