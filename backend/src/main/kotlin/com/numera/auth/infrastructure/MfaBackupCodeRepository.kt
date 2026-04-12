package com.numera.auth.infrastructure

import com.numera.auth.domain.MfaBackupCode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MfaBackupCodeRepository : JpaRepository<MfaBackupCode, UUID> {
    fun findByUserIdAndUsedFalse(userId: UUID): List<MfaBackupCode>
    fun deleteByUserId(userId: UUID)
}
