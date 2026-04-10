package com.numera.shared.infrastructure

import com.numera.shared.domain.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantRepository : JpaRepository<Tenant, UUID>
