package com.numera.auth.infrastructure

import com.numera.auth.domain.PasswordPolicy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PasswordPolicyRepository : JpaRepository<PasswordPolicy, UUID> {
    fun findByTenantId(tenantId: UUID): PasswordPolicy?
}