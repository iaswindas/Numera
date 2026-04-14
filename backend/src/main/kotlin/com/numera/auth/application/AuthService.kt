package com.numera.auth.application

import com.numera.auth.domain.RefreshToken
import com.numera.auth.domain.AccountStatus
import com.numera.auth.domain.User
import com.numera.auth.dto.LoginRequest
import com.numera.auth.dto.LoginResponse
import com.numera.auth.dto.ChangePasswordRequest
import com.numera.auth.dto.PasswordChangeResponse
import com.numera.auth.dto.RefreshRequest
import com.numera.auth.dto.AuthMeResponse
import com.numera.auth.dto.UserProfile
import com.numera.auth.dto.RegisterRequest
import com.numera.auth.dto.RegisterResponse
import com.numera.auth.infrastructure.RefreshTokenRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.auth.infrastructure.RoleRepository
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
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
    private val config: NumeraProperties,
    private val auditService: AuditService,
    private val tenantRepository: TenantRepository,
    private val mfaService: MfaService,
    private val passwordPolicyService: PasswordPolicyService,
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmailIgnoreCase(request.email)
            .orElseThrow { ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials") }

        if (!user.enabled || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials")
        }

        // MFA validation
        if (user.mfaEnabled && user.mfaVerified) {
            val mfaCode = request.mfaCode
            if (mfaCode.isNullOrBlank()) {
                throw ApiException(ErrorCode.MFA_REQUIRED, "MFA code required")
            }
            if (!mfaService.validateCode(user.id!!, mfaCode)) {
                throw ApiException(ErrorCode.UNAUTHORIZED, "Invalid MFA code")
            }
        }

        user.lastLoginAt = Instant.now()
        val passwordExpired = passwordPolicyService.isPasswordExpired(user)
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
            passwordExpired = passwordExpired,
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
        if (!user.enabled || user.accountStatus != AccountStatus.ACTIVE) {
            record.revoked = true
            throw ApiException(ErrorCode.UNAUTHORIZED, "User account is not active")
        }
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
            mfaEnabled = user.mfaEnabled && user.mfaVerified,
        )
    }

    fun getUserId(email: String): java.util.UUID {
        val user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        return user.id!!
    }

    private fun String.toApiRole(): String = removePrefix("ROLE_")

    @Transactional
    fun logout(email: String, accessToken: String? = null) {
        val user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        refreshTokenRepository.revokeAllByUserId(user.id!!)
        if (accessToken != null) {
            tokenProvider.blacklistToken(accessToken)
        }
    }

    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        // Check if user already exists
        if (userRepository.findByEmailIgnoreCase(request.email).isPresent) {
            throw ApiException(ErrorCode.CONFLICT, "Email already registered")
        }

        // Determine tenant - use provided tenantId or default
        val tenantId = request.tenantId ?: com.numera.shared.domain.TenantAwareEntity.DEFAULT_TENANT
        
        // Verify tenant exists
        tenantRepository.findById(tenantId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Tenant not found") }

        // Get default ANALYST role
        val role = roleRepository.findByTenantIdAndName(tenantId, "ROLE_ANALYST")
            ?: throw ApiException(ErrorCode.NOT_FOUND, "Default role not found")

        // Create new user in PENDING status
        val user = User().also {
            it.tenantId = tenantId
            it.email = request.email
            it.fullName = request.fullName
            it.enabled = false
            it.accountStatus = AccountStatus.PENDING
            it.passwordHistory = "[]"
            passwordPolicyService.setPassword(it, request.password)
            it.roles = mutableSetOf(role)
        }

        val saved = userRepository.save(user)

        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "USER_REGISTERED",
            action = AuditAction.CREATE,
            entityType = "user",
            entityId = saved.id.toString(),
        )

        return RegisterResponse(
            id = saved.id.toString(),
            email = saved.email,
            fullName = saved.fullName,
            accountStatus = saved.accountStatus.toString(),
        )
    }

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest): PasswordChangeResponse {
        val user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }

        if (!user.enabled || !passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials")
        }

        passwordPolicyService.setPassword(user, request.newPassword)
        userRepository.save(user)
        refreshTokenRepository.revokeAllByUserId(user.id!!)

        auditService.record(
            tenantId = user.tenantId.toString(),
            eventType = "PASSWORD_CHANGE",
            action = AuditAction.UPDATE,
            entityType = "user",
            entityId = user.id.toString(),
        )

        return PasswordChangeResponse(changed = true)
    }
}
