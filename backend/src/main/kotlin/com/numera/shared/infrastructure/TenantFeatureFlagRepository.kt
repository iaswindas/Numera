package com.numera.shared.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tenant_feature_flags")
data class TenantFeatureFlag(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "flag_name", nullable = false, length = 100)
    val flagName: String,

    @Column(nullable = false)
    val enabled: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

@Repository
interface TenantFeatureFlagRepository : JpaRepository<TenantFeatureFlag, UUID> {
    fun findByTenantId(tenantId: UUID): List<TenantFeatureFlag>
}
