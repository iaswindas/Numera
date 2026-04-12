package com.numera.auth.infrastructure

import com.numera.auth.domain.SsoConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SsoConfigurationRepository : JpaRepository<SsoConfiguration, UUID> {
    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<SsoConfiguration>
    fun findByTenantIdAndProviderName(tenantId: UUID, providerName: String): Optional<SsoConfiguration>
}
