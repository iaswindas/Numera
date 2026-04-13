package com.numera.shared.compliance

import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ConsentService(
    private val jdbc: JdbcTemplate,
    private val auditService: AuditService,
) {
    private val log = LoggerFactory.getLogger(ConsentService::class.java)

    companion object {
        const val CONSENT_DATA_PROCESSING = "DATA_PROCESSING"
        const val CONSENT_MARKETING = "MARKETING"
        const val CONSENT_ANALYTICS = "ANALYTICS"
        const val CONSENT_THIRD_PARTY_SHARING = "THIRD_PARTY_SHARING"
    }

    @Transactional
    fun grantConsent(tenantId: String, userId: String, consentType: String, ipAddress: String? = null): ConsentRecord {
        log.info("Granting consent type={} for user={} tenant={}", consentType, userId, tenantId)

        val id = UUID.randomUUID()
        val now = Instant.now()

        // Revoke any existing active consent of the same type (idempotent grant)
        jdbc.update(
            "UPDATE gdpr_consents SET revoked_at = NOW() WHERE user_id = ?::uuid AND tenant_id = ? AND consent_type = ? AND revoked_at IS NULL",
            userId, tenantId, consentType,
        )

        jdbc.update(
            """INSERT INTO gdpr_consents (id, tenant_id, user_id, consent_type, granted, granted_at, ip_address)
            VALUES (?::uuid, ?, ?::uuid, ?, true, NOW(), ?)""",
            id.toString(), tenantId, userId, consentType, ipAddress,
        )

        auditService.record(
            tenantId = tenantId,
            eventType = "GDPR",
            action = AuditAction.CREATE,
            entityType = "CONSENT",
            entityId = id.toString(),
        )

        return ConsentRecord(id, consentType, true, now, null)
    }

    @Transactional
    fun revokeConsent(tenantId: String, userId: String, consentType: String): Int {
        log.info("Revoking consent type={} for user={} tenant={}", consentType, userId, tenantId)

        val rows = jdbc.update(
            "UPDATE gdpr_consents SET revoked_at = NOW(), granted = false WHERE user_id = ?::uuid AND tenant_id = ? AND consent_type = ? AND revoked_at IS NULL",
            userId, tenantId, consentType,
        )

        auditService.record(
            tenantId = tenantId,
            eventType = "GDPR",
            action = AuditAction.UPDATE,
            entityType = "CONSENT_REVOCATION",
            entityId = "$userId:$consentType",
        )

        return rows
    }

    @Transactional(readOnly = true)
    fun getActiveConsents(tenantId: String, userId: String): List<ConsentRecord> {
        return jdbc.query(
            "SELECT id, consent_type, granted, granted_at, revoked_at FROM gdpr_consents WHERE user_id = ?::uuid AND tenant_id = ? AND revoked_at IS NULL ORDER BY granted_at",
            { rs, _ ->
                ConsentRecord(
                    id = UUID.fromString(rs.getString("id")),
                    consentType = rs.getString("consent_type"),
                    granted = rs.getBoolean("granted"),
                    grantedAt = rs.getTimestamp("granted_at").toInstant(),
                    revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
                )
            },
            userId, tenantId,
        )
    }

    @Transactional(readOnly = true)
    fun hasConsent(tenantId: String, userId: String, consentType: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gdpr_consents WHERE user_id = ?::uuid AND tenant_id = ? AND consent_type = ? AND granted = true AND revoked_at IS NULL",
            Int::class.java,
            userId, tenantId, consentType,
        ) ?: 0
        return count > 0
    }

    data class ConsentRecord(
        val id: UUID,
        val consentType: String,
        val granted: Boolean,
        val grantedAt: Instant,
        val revokedAt: Instant?,
    )
}
