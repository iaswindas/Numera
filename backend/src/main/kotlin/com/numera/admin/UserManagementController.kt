package com.numera.admin

import com.numera.auth.UserAdminCreateRequest
import com.numera.auth.UserAdminFacade
import com.numera.auth.UserAdminUpdateRequest
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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
@PreAuthorize("hasRole('ADMIN')")
class UserManagementController(
    private val userAdminFacade: UserAdminFacade,
) {
    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    @GetMapping
    fun list(): List<Map<String, Any?>> = userAdminFacade.findAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: UserUpsertRequest): Map<String, Any?> {
        return userAdminFacade.create(
            resolvedTenantId(),
            UserAdminCreateRequest(request.email, request.fullName, request.password, request.roles, request.enabled),
        )
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UserUpsertRequest): Map<String, Any?> {
        return userAdminFacade.update(
            id,
            resolvedTenantId(),
            UserAdminUpdateRequest(request.fullName, request.password, request.roles, request.enabled),
        )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        userAdminFacade.delete(id)
    }

    @PostMapping("/{id}/approve")
    fun approveUser(@PathVariable id: UUID): Map<String, Any?> = userAdminFacade.approve(id)

    @PostMapping("/{id}/reject")
    fun rejectUser(@PathVariable id: UUID): Map<String, Any?> = userAdminFacade.reject(id)
}

data class UserUpsertRequest(
    val email: String,
    val fullName: String,
    val password: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
)
