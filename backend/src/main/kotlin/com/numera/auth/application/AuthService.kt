package com.numera.auth.application

import com.numera.auth.domain.RefreshToken
import com.numera.auth.dto.LoginRequest
import com.numera.auth.dto.LoginResponse
import com.numera.auth.dto.RefreshRequest
import com.numera.auth.dto.AuthMeResponse
import com.numera.auth.dto.UserProfile
import com.numera.auth.infrastructure.RefreshTokenRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.config.NumeraProperties
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.infrastructure.TenantRepository
import com.numera.shared.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
    private val config: NumeraProperties,
    private val auditService: AuditService,
    private val tenantRepository: TenantRepository,
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmailIgnoreCase(request.email)
            .orElseThrow { ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials") }

        if (!user.enabled || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials")
        }

        user.lastLoginAt = Instant.now()
        val roles = user.roles.map { it.name.toApiRole() }
        val access = tokenProvider.generateAccessToken(user.email, user.tenantId.toString(), roles)
        val refresh = tokenProvider.generateRefreshToken(user.email)

        refreshTokenRepository.save(RefreshToken().also {
            it.user = user
            it.token = refresh
            it.expiresAt = Instant.now().plusMillis(config.jwt.refreshExpirationMs)
        })

        auditService.record(
            tenantId = user.tenantId.toString(),
            eventType = "USER_LOGIN",
            action = AuditAction.LOGIN,
            entityType = "user",
            entityId = user.id.toString(),
        )

        return LoginResponse(
            accessToken = access,
            refreshToken = refresh,
            expiresInSec = config.jwt.accessExpirationMs / 1000,
            user = UserProfile(
                id = user.id.toString(),
                email = user.email,
                fullName = user.fullName,
                tenantId = user.tenantId.toString(),
                roles = roles,
            )
        )
    }

    @Transactional
    fun refresh(request: RefreshRequest): LoginResponse {
        val record = refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken)
            .orElseThrow { ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token") }

        if (record.expiresAt.isBefore(Instant.now())) {
            record.revoked = true
            throw ApiException(ErrorCode.UNAUTHORIZED, "Refresh token expired")
        }

        val user = record.user
        val roles = user.roles.map { it.name.toApiRole() }
        val newAccess = tokenProvider.generateAccessToken(user.email, user.tenantId.toString(), roles)
        val newRefresh = tokenProvider.generateRefreshToken(user.email)

        record.revoked = true

        refreshTokenRepository.save(RefreshToken().also {
            it.user = user
            it.token = newRefresh
            it.expiresAt = Instant.now().plusMillis(config.jwt.refreshExpirationMs)
        })

        return LoginResponse(
            accessToken = newAccess,
            refreshToken = newRefresh,
            expiresInSec = config.jwt.accessExpirationMs / 1000,
            user = UserProfile(
                id = user.id.toString(),
                email = user.email,
                fullName = user.fullName,
                tenantId = user.tenantId.toString(),
                roles = roles,
            )
        )
    }

    @Transactional(readOnly = true)
    fun me(email: String): AuthMeResponse {
        val user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow { ApiException(ErrorCode.UNAUTHORIZED, "User not found") }
        val tenant = tenantRepository.findById(user.tenantId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Tenant not found") }

        return AuthMeResponse(
            id = user.id.toString(),
            email = user.email,
            fullName = user.fullName,
            roles = user.roles.map { it.name.toApiRole() },
            tenantId = tenant.id.toString(),
            tenantName = tenant.code,
            lastLoginAt = user.lastLoginAt?.toString(),
        )
    }

    private fun String.toApiRole(): String = removePrefix("ROLE_")
}
