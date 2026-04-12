package com.numera.auth.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.auth.domain.PasswordPolicy
import com.numera.auth.domain.User
import com.numera.auth.dto.PasswordPolicyResponse
import com.numera.auth.dto.PasswordPolicyUpsertRequest
import com.numera.auth.infrastructure.PasswordPolicyRepository
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PasswordPolicyService(
    private val passwordPolicyRepository: PasswordPolicyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper,
) {
    private val passwordHistoryType = object : TypeReference<List<String>>() {}

    @Transactional(readOnly = true)
    fun getPolicy(tenantId: UUID): PasswordPolicy =
        passwordPolicyRepository.findByTenantId(tenantId) ?: defaultPolicy(tenantId)

    @Transactional
    fun upsertPolicy(tenantId: UUID, request: PasswordPolicyUpsertRequest): PasswordPolicy {
        val policy = passwordPolicyRepository.findByTenantId(tenantId) ?: PasswordPolicy().also {
            it.tenantId = tenantId
        }

        policy.minLength = request.minLength.coerceAtLeast(1)
        policy.requireUppercase = request.requireUppercase
        policy.requireLowercase = request.requireLowercase
        policy.requireDigit = request.requireDigit
        policy.requireSpecialCharacter = request.requireSpecialCharacter
        policy.expiryDays = request.expiryDays.coerceAtLeast(0)
        policy.historySize = request.historySize.coerceAtLeast(0)
        policy.enabled = request.enabled

        return passwordPolicyRepository.save(policy)
    }

    fun isPasswordExpired(user: User): Boolean {
        val policy = getPolicy(user.tenantId)
        if (!policy.enabled || policy.expiryDays <= 0 || user.passwordHash.isBlank()) {
            return false
        }

        val changedAt = user.passwordChangedAt ?: user.createdAt
        return changedAt.plus(policy.expiryDays.toLong(), ChronoUnit.DAYS).isBefore(Instant.now())
    }

    @Transactional
    fun setPassword(user: User, rawPassword: String) {
        validatePassword(user, rawPassword)

        val currentHistory = readHistory(user.passwordHistory)
        val currentHash = user.passwordHash.takeIf { it.isNotBlank() }
        val nextHistory = if (currentHash == null) {
            currentHistory
        } else {
            (currentHistory + currentHash).takeLast(getPolicy(user.tenantId).historySize)
        }

        user.passwordHash = passwordEncoder.encode(rawPassword)
        user.passwordChangedAt = Instant.now()
        user.passwordHistory = writeHistory(nextHistory)
    }

    fun validatePassword(user: User, rawPassword: String) {
        val policy = getPolicy(user.tenantId)
        if (!policy.enabled) {
            return
        }

        val violations = mutableListOf<Map<String, Any?>>()

        if (rawPassword.length < policy.minLength) {
            violations += mapOf(
                "code" to "MIN_LENGTH",
                "message" to "Password must be at least ${policy.minLength} characters long",
            )
        }
        if (policy.requireUppercase && rawPassword.none { it.isUpperCase() }) {
            violations += mapOf(
                "code" to "UPPERCASE",
                "message" to "Password must contain at least one uppercase letter",
            )
        }
        if (policy.requireLowercase && rawPassword.none { it.isLowerCase() }) {
            violations += mapOf(
                "code" to "LOWERCASE",
                "message" to "Password must contain at least one lowercase letter",
            )
        }
        if (policy.requireDigit && rawPassword.none { it.isDigit() }) {
            violations += mapOf(
                "code" to "DIGIT",
                "message" to "Password must contain at least one digit",
            )
        }
        if (policy.requireSpecialCharacter && rawPassword.none { !it.isLetterOrDigit() }) {
            violations += mapOf(
                "code" to "SPECIAL_CHARACTER",
                "message" to "Password must contain at least one special character",
            )
        }

        if (isReusedPassword(user, rawPassword)) {
            violations += mapOf(
                "code" to "PASSWORD_HISTORY",
                "message" to "Password cannot match any of the last ${policy.historySize} passwords",
            )
        }

        if (violations.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Password does not meet policy requirements",
                validations = violations,
            )
        }
    }

    fun toResponse(policy: PasswordPolicy): PasswordPolicyResponse = PasswordPolicyResponse(
        id = policy.id?.toString(),
        tenantId = policy.tenantId.toString(),
        minLength = policy.minLength,
        requireUppercase = policy.requireUppercase,
        requireLowercase = policy.requireLowercase,
        requireDigit = policy.requireDigit,
        requireSpecialCharacter = policy.requireSpecialCharacter,
        expiryDays = policy.expiryDays,
        historySize = policy.historySize,
        enabled = policy.enabled,
        updatedAt = policy.updatedAt.toString(),
    )

    private fun isReusedPassword(user: User, rawPassword: String): Boolean {
        if (user.passwordHash.isBlank()) {
            return false
        }

        val policy = getPolicy(user.tenantId)
        if (policy.historySize <= 0) {
            return false
        }

        val historyHashes = readHistory(user.passwordHistory).takeLast(policy.historySize)
        val historicalHashes = sequenceOf(user.passwordHash) + historyHashes.asSequence()
        return historicalHashes.any { isPasswordMatch(rawPassword, it) }
    }

    private fun isPasswordMatch(rawPassword: String, storedHash: String): Boolean =
        try {
            passwordEncoder.matches(rawPassword, storedHash)
        } catch (_: Exception) {
            false
        }

    private fun readHistory(historyJson: String?): List<String> =
        try {
            if (historyJson.isNullOrBlank()) emptyList() else objectMapper.readValue(historyJson, passwordHistoryType)
        } catch (_: Exception) {
            emptyList()
        }

    private fun writeHistory(history: List<String>): String =
        objectMapper.writeValueAsString(history)

    private fun defaultPolicy(tenantId: UUID): PasswordPolicy = PasswordPolicy().also {
        it.tenantId = tenantId
    }
}