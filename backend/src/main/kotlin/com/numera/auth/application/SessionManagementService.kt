package com.numera.auth.application

import com.numera.shared.config.FeatureFlagService
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class SessionManagementService(
    private val redis: StringRedisTemplate,
    private val featureFlagService: FeatureFlagService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    companion object {
        private const val SESSION_PREFIX = "numera:session:"
        private const val USER_SESSIONS_PREFIX = "numera:user_sessions:"
        private val DEFAULT_TIMEOUT = Duration.ofMinutes(30)
        private const val MAX_CONCURRENT_SESSIONS = 1
    }

    /**
     * Track a new session for a user in a tenant.
     * Enforces max concurrent sessions limit.
     */
    fun trackSession(userId: UUID, sessionId: String, tenantId: UUID) {
        val userSessionsKey = USER_SESSIONS_PREFIX + userId
        val currentSessions = redis.opsForSet().members(userSessionsKey) ?: emptySet()

        // Check if max sessions exceeded
        if (currentSessions.size >= MAX_CONCURRENT_SESSIONS && !currentSessions.contains(sessionId)) {
            throw ApiException(
                ErrorCode.CONFLICT,
                "Maximum concurrent sessions ($MAX_CONCURRENT_SESSIONS) exceeded. Please logout from another session."
            )
        }

        // Store session metadata
        val sessionKey = SESSION_PREFIX + sessionId
        val sessionData = "$userId|$tenantId|${System.currentTimeMillis()}"
        redis.opsForValue().set(sessionKey, sessionData, configurableTimeout(tenantId))

        // Add session to user's session set
        redis.opsForSet().add(userSessionsKey, sessionId)
        redis.expire(userSessionsKey, configurableTimeout(tenantId))
    }

    /**
     * Invalidate a specific session.
     */
    fun invalidateSession(sessionId: String): Boolean {
        val sessionKey = SESSION_PREFIX + sessionId
        val sessionData = redis.opsForValue().get(sessionKey) ?: return false

        val parts = sessionData.split("|")
        if (parts.isNotEmpty()) {
            val userId = parts[0]
            val userSessionsKey = USER_SESSIONS_PREFIX + userId
            redis.opsForSet().remove(userSessionsKey, sessionId)
        }

        return redis.delete(sessionKey) ?: false
    }

    /**
     * Force logout all sessions for a user.
     */
    fun forceLogout(userId: UUID) {
        val userSessionsKey = USER_SESSIONS_PREFIX + userId
        val sessions = redis.opsForSet().members(userSessionsKey) ?: emptySet()

        sessions.forEach { sessionId ->
            val sessionKey = SESSION_PREFIX + sessionId
            redis.delete(sessionKey)
        }

        redis.delete(userSessionsKey)
    }

    /**
     * Get all active sessions for a user.
     */
    fun getActiveSessions(userId: UUID): List<String> {
        val userSessionsKey = USER_SESSIONS_PREFIX + userId
        return (redis.opsForSet().members(userSessionsKey) ?: emptySet()).toList()
    }

    /**
     * Get configurable timeout per tenant via feature flag service.
     * Falls back to DEFAULT_TIMEOUT (30 minutes) when no tenant-specific override exists.
     */
    fun configurableTimeout(tenantId: UUID): Duration {
        val minutes = featureFlagService.getLong(tenantId, "session.timeout.minutes", 30L)
        log.debug("Session timeout for tenant {}: {} minutes", tenantId, minutes)
        return Duration.ofMinutes(minutes)
    }
}
