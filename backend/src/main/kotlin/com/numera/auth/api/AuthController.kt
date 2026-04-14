package com.numera.auth.api

import com.numera.auth.application.AuthService
import com.numera.auth.application.MfaService
import com.numera.auth.application.SessionManagementService
import com.numera.auth.application.SsoService
import com.numera.auth.dto.AuthMeResponse
import com.numera.auth.dto.LoginRequest
import com.numera.auth.dto.LoginResponse
import com.numera.auth.dto.ChangePasswordRequest
import com.numera.auth.dto.MfaBackupCodesResponse
import com.numera.auth.dto.MfaSetupResponse
import com.numera.auth.dto.MfaVerifyRequest
import com.numera.auth.dto.PasswordChangeResponse
import com.numera.auth.dto.RefreshRequest
import com.numera.auth.dto.RegisterRequest
import com.numera.auth.dto.RegisterResponse
import com.numera.auth.dto.SsoCallbackRequest
import com.numera.auth.dto.SsoConfigResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val mfaService: MfaService,
    private val sessionManagementService: SessionManagementService,
    private val ssoService: SsoService,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.login(request)

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): RegisterResponse = authService.register(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): LoginResponse = authService.refresh(request)

    @GetMapping("/me")
    fun me(authentication: Authentication): AuthMeResponse = authService.me(authentication.name)

    @PostMapping("/logout")
    fun logout(authentication: Authentication, request: HttpServletRequest) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substringAfter("Bearer ")
        authService.logout(authentication.name, token)
    }

    @PostMapping("/force-logout/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forceLogout(@PathVariable userId: UUID) {
        sessionManagementService.forceLogout(userId)
    }

    @GetMapping("/sessions")
    fun getActiveSessions(authentication: Authentication): Map<String, Any?> {
        val userId = authService.getUserId(authentication.name)
        val sessions = sessionManagementService.getActiveSessions(userId)
        return mapOf(
            "userId" to userId.toString(),
            "activeSessions" to sessions,
            "sessionCount" to sessions.size,
        )
    }

    @PostMapping("/password")
    fun changePassword(
        authentication: Authentication,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): PasswordChangeResponse = authService.changePassword(authentication.name, request)

    // ── MFA Endpoints ───────────────────────────────────────────────

    @PostMapping("/mfa/setup")
    fun setupMfa(authentication: Authentication): MfaSetupResponse {
        val userId = authService.getUserId(authentication.name)
        return mfaService.setupMfa(userId)
    }

    @PostMapping("/mfa/verify")
    fun verifyMfa(
        authentication: Authentication,
        @Valid @RequestBody request: MfaVerifyRequest,
    ): MfaBackupCodesResponse {
        val userId = authService.getUserId(authentication.name)
        return mfaService.verifyAndEnable(userId, request)
    }

    @DeleteMapping("/mfa")
    fun disableMfa(authentication: Authentication) {
        val userId = authService.getUserId(authentication.name)
        mfaService.disableMfa(userId)
    }

    // ── SSO Endpoints ───────────────────────────────────────────────

    @GetMapping("/sso/providers")
    fun getSsoProviders(@RequestParam tenantId: UUID): List<SsoConfigResponse> =
        ssoService.getProviders(tenantId)

    @GetMapping("/sso/authorize")
    fun getSsoAuthorizeUrl(
        @RequestParam tenantId: UUID,
        @RequestParam provider: String,
        @RequestParam redirectUri: String,
    ): Map<String, String> = mapOf("url" to ssoService.getAuthorizationUrl(tenantId, provider, redirectUri))

    @PostMapping("/sso/callback")
    fun handleSsoCallback(
        @RequestParam tenantId: UUID,
        @Valid @RequestBody request: SsoCallbackRequest,
    ): LoginResponse = ssoService.handleOidcCallback(tenantId, request)
}
