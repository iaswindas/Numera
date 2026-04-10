package com.numera.admin

import com.numera.auth.domain.User
import com.numera.auth.infrastructure.RoleRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users")
class UserManagementController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    @GetMapping
    fun list(): List<Map<String, Any?>> = userRepository.findAll().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: UserUpsertRequest): Map<String, Any?> {
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
            it.passwordHash = passwordEncoder.encode(request.password ?: "Password123!")
            it.roles = roles
        })
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UserUpsertRequest): Map<String, Any?> {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        user.fullName = request.fullName
        user.enabled = request.enabled
        if (!request.password.isNullOrBlank()) {
            user.passwordHash = passwordEncoder.encode(request.password)
        }
        if (request.roles.isNotEmpty()) {
            user.roles = request.roles.map { name ->
                roleRepository.findByTenantIdAndName(tenantId, name)
                    ?: throw ApiException(ErrorCode.NOT_FOUND, "Role not found: $name")
            }.toMutableSet()
        }
        return userRepository.save(user).toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }
        userRepository.delete(user)
    }

    private fun User.toResponse(): Map<String, Any?> = mapOf(
        "id" to id.toString(),
        "email" to email,
        "fullName" to fullName,
        "enabled" to enabled,
        "roles" to roles.map { it.name.removePrefix("ROLE_") },
        "lastLoginAt" to lastLoginAt?.toString(),
        "createdAt" to createdAt.toString(),
    )
}

data class UserUpsertRequest(
    val email: String,
    val fullName: String,
    val password: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
)
