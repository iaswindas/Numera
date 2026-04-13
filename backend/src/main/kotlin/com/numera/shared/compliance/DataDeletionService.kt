package com.numera.shared.compliance

import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.observability.MetricsConfig
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * GDPR Article 17 — Right to erasure ("right to be forgotten").
 * Anonymises or deletes all personal data for a given user.
 *
 * Strategy: pseudonymise audit-critical records (keep event skeleton, strip PII),
 * hard-delete non-essential personal data.
 */
@Service
class DataDeletionService(
    private val jdbc: JdbcTemplate,
    private val auditService: AuditService,
    private val metricsConfig: MetricsConfig,
) {
    private val log = LoggerFactory.getLogger(DataDeletionService::class.java)

    @Transactional
    fun eraseUserData(tenantId: String, userId: String): DeletionResult {
        metricsConfig.gdprDeletionRequests.increment()
        log.warn("GDPR data erasure initiated for user={} tenant={}", userId, tenantId)

        val result = DeletionResult(userId = userId, tenantId = tenantId)

        // Resolve email before anonymising
        val emails = jdbc.queryForList(
            "SELECT email FROM users WHERE id = ?::uuid AND tenant_id = ?",
            String::class.java,
            userId, tenantId,
        )
        val email = emails.firstOrNull() ?: run {
            log.warn("User {} not found in tenant {}", userId, tenantId)
            return result.copy(status = "USER_NOT_FOUND")
        }

        // 1. Anonymise user profile
        val userRows = jdbc.update(
            """UPDATE users SET
                email = 'deleted_' || id || '@redacted.numera',
                first_name = 'REDACTED',
                last_name = 'REDACTED',
                password_hash = '',
                mfa_secret = NULL,
                deleted = true,
                deleted_at = NOW()
            WHERE id = ?::uuid AND tenant_id = ?""",
            userId, tenantId,
        )
        result.recordsAnonymised += userRows

        // 2. Anonymise audit trail (preserve event structure, strip PII)
        val auditRows = jdbc.update(
            """UPDATE audit_event_log SET
                user_email = 'redacted@numera'
            WHERE user_email = ? AND tenant_id = ?""",
            email, tenantId,
        )
        result.recordsAnonymised += auditRows

        // 3. Delete comments
        val commentRows = jdbc.update(
            "DELETE FROM comments WHERE author_id = ?::uuid AND tenant_id = ?",
            userId, tenantId,
        )
        result.recordsDeleted += commentRows

        // 4. Delete consent records (no longer needed once erased)
        val consentRows = jdbc.update(
            "DELETE FROM gdpr_consents WHERE user_id = ?::uuid AND tenant_id = ?",
            userId, tenantId,
        )
        result.recordsDeleted += consentRows

        // 5. Invalidate sessions
        val sessionRows = jdbc.update(
            "DELETE FROM user_sessions WHERE user_id = ?::uuid AND tenant_id = ?",
            userId, tenantId,
        )
        result.recordsDeleted += sessionRows

        // Record the erasure itself in audit log (with anonymised reference)
        auditService.record(
            tenantId = tenantId,
            eventType = "GDPR",
            action = AuditAction.DELETE,
            entityType = "USER_ERASURE",
            entityId = userId,
        )

        log.info("GDPR erasure complete for user={}: anonymised={}, deleted={}",
            userId, result.recordsAnonymised, result.recordsDeleted)

        return result.copy(status = "COMPLETED")
    }

    data class DeletionResult(
        val userId: String,
        val tenantId: String,
        val status: String = "PENDING",
        var recordsAnonymised: Int = 0,
        var recordsDeleted: Int = 0,
    )
}
