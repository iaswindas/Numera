package com.numera.auth

import com.numera.auth.application.PasswordPolicyService
import com.numera.auth.domain.AccountStatus
import com.numera.auth.domain.User
import com.numera.auth.infrastructure.RoleRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for user administration exposed by the auth module.
 *
 * This service lives in the auth ROOT package so that the admin module can depend on it without
 * crossing into auth's private sub-packages (application / domain / infrastructure).
 */
@Service
class UserAdminFacade(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordPolicyService: PasswordPolicyService,
    private val auditService: AuditService,
) {
    fun findAll(): List<Map<String, Any?>> = userRepository.findAll().map { it.toSummary() }

    fun create(tenantId: UUID, request: UserAdminCreateRequest): Map<String, Any?> {
        if (userRepository.findByEmailIgnoreCase(request.email).isPresent) {
            throw ApiException(ErrorCode.CONFLICT, "User already exists")
        }
        val roleNames = request.roles.ifEmpty { listOf("ROLE_ANALYST") }
        val roles = roleNames.map { name ->
            roleRepository.findByTenantIdAndName(tenantId, name)
                ?: throw ApiException(ErrorCode.NOT_FOUND, "Role not found: $name")
        }.toMutableSet()

        val saved = userRepository.save(User().also {
            it.tenantId = tenantId
            it.email = request.email
            it.fullName = request.fullName
            it.enabled = request.enabled
            it.passwordHistory = "[]"
            passwordPolicyService.setPassword(it, request.password ?: "Password123!")
            it.roles = roles
        })
        return saved.toSummary()
    }

    fun update(id: UUID, tenantId: UUID, request: UserAdminUpdateRequest): Map<String, Any?> {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        user.fullName = request.fullName
        user.enabled = request.enabled
        if (!request.password.isNullOrBlank()) {
            passwordPolicyService.setPassword(user, request.password)
        }
        if (request.roles.isNotEmpty()) {
            user.roles = request.roles.map { name ->
                roleRepository.findByTenantIdAndName(tenantId, name)
                    ?: throw ApiException(ErrorCode.NOT_FOUND, "Role not found: $name")
            }.toMutableSet()
        }
        return userRepository.save(user).toSummary()
    }

    fun delete(id: UUID) {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        userRepository.delete(user)
    }

    fun approve(id: UUID): Map<String, Any?> {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        if (user.accountStatus != AccountStatus.PENDING) {
            throw ApiException(ErrorCode.BAD_REQUEST, "User is not in PENDING status")
        }
        user.accountStatus = AccountStatus.ACTIVE
        user.enabled = true
        val saved = userRepository.save(user)
        auditService.record(
            tenantId = user.tenantId.toString(),
            eventType = "USER_APPROVED",
            action = AuditAction.UPDATE,
            entityType = "user",
            entityId = saved.id.toString(),
        )
        return saved.toSummary() + mapOf("message" to "User approved successfully")
    }

    fun reject(id: UUID): Map<String, Any?> {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        if (user.accountStatus != AccountStatus.PENDING) {
            throw ApiException(ErrorCode.BAD_REQUEST, "User is not in PENDING status")
        }
        user.accountStatus = AccountStatus.REJECTED
        user.enabled = false
        val saved = userRepository.save(user)
        auditService.record(
            tenantId = user.tenantId.toString(),
            eventType = "USER_REJECTED",
            action = AuditAction.UPDATE,
            entityType = "user",
            entityId = saved.id.toString(),
        )
        return saved.toSummary() + mapOf("message" to "User rejected")
    }

    private fun User.toSummary(): Map<String, Any?> = mapOf(
        "id" to id.toString(),
        "email" to email,
        "fullName" to fullName,
        "enabled" to enabled,
        "accountStatus" to accountStatus.toString(),
        "roles" to roles.map { it.name.removePrefix("ROLE_") },
        "lastLoginAt" to lastLoginAt?.toString(),
        "createdAt" to createdAt.toString(),
    )
}

/** Request DTO for user creation — defined in auth root package so admin can use it without
 *  crossing into auth sub-packages. */
data class UserAdminCreateRequest(
    val email: String,
    val fullName: String,
    val password: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/** Request DTO for user update — defined in auth root package. */
data class UserAdminUpdateRequest(
    val fullName: String,
    val password: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
)
