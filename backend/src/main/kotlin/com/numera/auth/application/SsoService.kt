package com.numera.auth.application

import com.numera.auth.domain.SsoConfiguration
import com.numera.auth.domain.User
import com.numera.auth.dto.LoginResponse
import com.numera.auth.dto.SsoCallbackRequest
import com.numera.auth.dto.SsoConfigRequest
import com.numera.auth.dto.SsoConfigResponse
import com.numera.auth.dto.UserProfile
import com.numera.auth.infrastructure.SsoConfigurationRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.config.NumeraProperties
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class SsoService(
    private val ssoConfigRepository: SsoConfigurationRepository,
    private val userRepository: UserRepository,
    private val tokenProvider: JwtTokenProvider,
    private val config: NumeraProperties,
    private val auditService: AuditService,
    private val webClient: WebClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // State parameter store for CSRF protection (V-07)
    // Maps state -> (tenantId, provider, expiry)
    private val pendingStates = ConcurrentHashMap<String, Triple<UUID, String, Instant>>()
    private val stateExpirySeconds = 300L // 5 minutes

    fun getProviders(tenantId: UUID): List<SsoConfigResponse> =
        ssoConfigRepository.findByTenantIdAndIsActiveTrue(tenantId).map { it.toResponse() }

    @Transactional
    fun createProvider(tenantId: UUID, request: SsoConfigRequest): SsoConfigResponse {
        val config = SsoConfiguration().also {
            it.tenantId = tenantId
            it.providerType = request.providerType
            it.providerName = request.providerName
            it.clientId = request.clientId
            it.clientSecret = request.clientSecret
            it.issuerUri = request.issuerUri
            it.authorizationUri = request.authorizationUri
            it.tokenUri = request.tokenUri
            it.userinfoUri = request.userinfoUri
            it.jwksUri = request.jwksUri
            it.samlMetadataUrl = request.samlMetadataUrl
            it.samlEntityId = request.samlEntityId
            it.samlAcsUrl = request.samlAcsUrl
        }
        return ssoConfigRepository.save(config).toResponse()
    }

    /**
     * Handle OAuth2/OIDC authorization code callback.
     * Exchanges code for tokens, extracts user info, creates/links user, and returns JWT.
     */
    @Transactional
    fun handleOidcCallback(tenantId: UUID, request: SsoCallbackRequest): LoginResponse {
        // Validate state parameter (V-07)
        val state = request.state
        if (state.isNullOrBlank()) {
            throw ApiException(ErrorCode.BAD_REQUEST, "Missing OAuth state parameter")
        }
        val pendingState = pendingStates.remove(state)
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Invalid or expired OAuth state")
        val (stateTenantId, stateProvider, stateExpiry) = pendingState
        if (Instant.now().isAfter(stateExpiry)) {
            throw ApiException(ErrorCode.BAD_REQUEST, "OAuth state expired")
        }
        if (stateTenantId != tenantId) {
            throw ApiException(ErrorCode.BAD_REQUEST, "Tenant ID mismatch")
        }

        val ssoConfig = ssoConfigRepository.findByTenantIdAndProviderName(tenantId, request.provider)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "SSO provider '\${request.provider}' not found") }

        if (ssoConfig.providerType != "OIDC") {
            throw ApiException(ErrorCode.BAD_REQUEST, "Provider is not OIDC type")
        }

        val tokenEndpoint = ssoConfig.tokenUri
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Token URI not configured")

        // Exchange authorization code for tokens
        val tokenResponse = webClient.build()
            .post()
            .uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "authorization_code")
                    .with("code", request.code)
                    .with("redirect_uri", request.redirectUri)
                    .with("client_id", ssoConfig.clientId ?: "")
                    .with("client_secret", ssoConfig.clientSecret ?: "")
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw ApiException(ErrorCode.BAD_REQUEST, "Failed to exchange authorization code")

        // Fetch user info
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "No access token in response")

        val userinfoEndpoint = ssoConfig.userinfoUri
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Userinfo URI not configured")

        @Suppress("UNCHECKED_CAST")
        val userInfo = webClient.build()
            .get()
            .uri(userinfoEndpoint)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as? Map<String, Any>
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Failed to fetch user info")

        val email = userInfo["email"] as? String
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Email not found in SSO response")
        val name = userInfo["name"] as? String ?: email.substringBefore("@")
        val sub = userInfo["sub"] as? String ?: email

        // Find or create user
        val user = userRepository.findByEmailIgnoreCase(email).orElseGet {
            log.info("Creating new SSO user: {} (provider: {})", email, request.provider)
            userRepository.save(User().also {
                it.tenantId = tenantId
                it.email = email
                it.fullName = name
                it.passwordHash = "" // SSO users don't need passwords
                it.ssoProvider = request.provider
                it.ssoSubjectId = sub
                it.enabled = true
            })
        }

        // Link SSO if not already linked
        if (user.ssoProvider == null) {
            user.ssoProvider = request.provider
            user.ssoSubjectId = sub
            userRepository.save(user)
        }

        user.lastLoginAt = Instant.now()

        val roles = user.roles.map { it.name.removePrefix("ROLE_") }
        val jwtAccess = tokenProvider.generateAccessToken(user.email, user.tenantId.toString(), roles)
        val jwtRefresh = tokenProvider.generateRefreshToken(user.email)

        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "SSO_LOGIN",
            action = AuditAction.LOGIN,
            entityType = "user",
            entityId = user.id.toString(),
        )

        return LoginResponse(
            accessToken = jwtAccess,
            refreshToken = jwtRefresh,
            expiresInSec = config.jwt.accessExpirationMs / 1000,
            passwordExpired = false,
            user = UserProfile(
                id = user.id.toString(),
                email = user.email,
                fullName = user.fullName,
                tenantId = user.tenantId.toString(),
                roles = roles,
            )
        )
    }

    /**
     * Get the OIDC authorization URL to redirect the user to.
     */
    fun getAuthorizationUrl(tenantId: UUID, providerName: String, redirectUri: String): String {
        val ssoConfig = ssoConfigRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "SSO provider '$providerName' not found") }

        val authUri = ssoConfig.authorizationUri
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "Authorization URI not configured")

        val state = UUID.randomUUID().toString()
        // Store state for validation on callback (V-07)
        pendingStates[state] = Triple(tenantId, providerName, Instant.now().plusSeconds(stateExpirySeconds))
        // Cleanup expired states
        val now = Instant.now()
        pendingStates.entries.removeIf { it.value.third.isBefore(now) }

        return "$authUri?" +
            "response_type=code" +
            "&client_id=${ssoConfig.clientId}" +
            "&redirect_uri=$redirectUri" +
            "&scope=openid email profile" +
            "&state=$state"
    }

    private fun SsoConfiguration.toResponse() = SsoConfigResponse(
        id = id.toString(),
        providerType = providerType,
        providerName = providerName,
        issuerUri = issuerUri,
        isActive = isActive,
    )
}
