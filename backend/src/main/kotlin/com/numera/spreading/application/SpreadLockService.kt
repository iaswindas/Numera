package com.numera.spreading.application

import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class SpreadLockService(
    private val redis: StringRedisTemplate,
) {
    companion object {
        private const val LOCK_PREFIX = "numera:spread:lock:"
        private val LOCK_TTL = Duration.ofMinutes(30)
    }

    data class LockInfo(
        val spreadItemId: UUID,
        val lockedBy: String,
        val lockedByName: String,
        val acquiredAt: Long,
    )

    fun acquire(spreadItemId: UUID, userEmail: String, userName: String): LockInfo {
        val key = LOCK_PREFIX + spreadItemId
        val value = "$userEmail|$userName|${System.currentTimeMillis()}"

        val existingLock = redis.opsForValue().get(key)
        if (existingLock != null) {
            val parts = existingLock.split("|")
            if (parts[0] == userEmail) {
                // Same user — extend lock
                redis.expire(key, LOCK_TTL)
                return LockInfo(spreadItemId, parts[0], parts[1], parts[2].toLong())
            }
            throw ApiException(
                ErrorCode.CONFLICT,
                "Spread is currently locked by ${parts[1]} (${parts[0]})"
            )
        }

        val acquired = redis.opsForValue().setIfAbsent(key, value, LOCK_TTL) ?: false
        if (!acquired) {
            val lock = redis.opsForValue().get(key)
            val parts = lock?.split("|") ?: listOf("unknown", "Unknown")
            throw ApiException(
                ErrorCode.CONFLICT,
                "Spread is currently locked by ${parts.getOrElse(1) { "Unknown" }}"
            )
        }

        return LockInfo(spreadItemId, userEmail, userName, System.currentTimeMillis())
    }

    fun release(spreadItemId: UUID, userEmail: String): Boolean {
        val key = LOCK_PREFIX + spreadItemId
        val value = redis.opsForValue().get(key) ?: return true  // Already unlocked

        val parts = value.split("|")
        if (parts[0] != userEmail) {
            throw ApiException(ErrorCode.FORBIDDEN, "Only the lock holder can release the lock")
        }

        return redis.delete(key)
    }

    fun getLockInfo(spreadItemId: UUID): LockInfo? {
        val key = LOCK_PREFIX + spreadItemId
        val value = redis.opsForValue().get(key) ?: return null
        val parts = value.split("|")
        return LockInfo(
            spreadItemId = spreadItemId,
            lockedBy = parts[0],
            lockedByName = parts.getOrElse(1) { "" },
            acquiredAt = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0,
        )
    }

    fun heartbeat(spreadItemId: UUID, userEmail: String): Boolean {
        val key = LOCK_PREFIX + spreadItemId
        val value = redis.opsForValue().get(key) ?: return false
        val parts = value.split("|")
        if (parts[0] != userEmail) return false
        return redis.expire(key, LOCK_TTL) ?: false
    }
}
