package com.numera.shared.compliance

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.observability.MetricsConfig
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * GDPR Article 20 — Right to data portability.
 * Exports all personal data for a given user across every tenant-scoped table.
 */
@Service
class DataExportService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService,
    private val metricsConfig: MetricsConfig,
) {
    private val log = LoggerFactory.getLogger(DataExportService::class.java)

    @Transactional(readOnly = true)
    fun exportUserData(tenantId: String, userId: String): Map<String, Any> {
        metricsConfig.gdprExportRequests.increment()
        log.info("GDPR data export requested for user={} tenant={}", userId, tenantId)

        val export = mutableMapOf<String, Any>(
            "exportedAt" to Instant.now().toString(),
            "userId" to userId,
            "tenantId" to tenantId,
        )

        // User profile
        val user = jdbc.queryForList(
            "SELECT id, email, first_name, last_name, role, created_at, last_login_at FROM users WHERE id = ?::uuid AND tenant_id = ?",
            userId, tenantId,
        )
        export["profile"] = user

        // Audit events authored by user
        val auditEvents = jdbc.queryForList(
            "SELECT id, event_type, action, entity_type, entity_id, created_at FROM audit_event_log WHERE user_email = (SELECT email FROM users WHERE id = ?::uuid) AND tenant_id = ? ORDER BY created_at",
            userId, tenantId,
        )
        export["auditEvents"] = auditEvents

        // Spreading sessions
        val sessions = jdbc.queryForList(
            "SELECT id, document_id, status, created_at, updated_at FROM spreading_sessions WHERE analyst_id = ?::uuid AND tenant_id = ? ORDER BY created_at",
            userId, tenantId,
        )
        export["spreadingSessions"] = sessions

        // Comments
        val comments = jdbc.queryForList(
            "SELECT id, content, created_at FROM comments WHERE author_id = ?::uuid AND tenant_id = ? ORDER BY created_at",
            userId, tenantId,
        )
        export["comments"] = comments

        // Consent records
        val consents = jdbc.queryForList(
            "SELECT id, consent_type, granted, granted_at, revoked_at FROM gdpr_consents WHERE user_id = ?::uuid AND tenant_id = ? ORDER BY granted_at",
            userId, tenantId,
        )
        export["consents"] = consents

        auditService.record(
            tenantId = tenantId,
            eventType = "GDPR",
            action = AuditAction.PROCESS,
            entityType = "USER_DATA_EXPORT",
            entityId = userId,
        )

        return export
    }

    fun exportAsJson(tenantId: String, userId: String): ByteArray {
        val data = exportUserData(tenantId, userId)
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data)
    }
}
