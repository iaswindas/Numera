package com.numera.auth

import com.numera.auth.application.PasswordPolicyService
import com.numera.auth.dto.PasswordPolicyUpsertRequest
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for password-policy administration.
 *
 * Moved from the admin module to the auth module because this endpoint orchestrates purely auth
 * concerns (PasswordPolicyService + PasswordPolicy domain). Placing it in the auth ROOT package
 * means it can freely access auth sub-packages without crossing module boundaries.
 *
 * The URL mapping (/api/admin/password-policy) is intentionally kept unchanged for API compatibility.
 */
@RestController
@RequestMapping("/api/admin/password-policy")
@PreAuthorize("hasRole('ADMIN')")
class PasswordPolicyController(
    private val passwordPolicyService: PasswordPolicyService,
) {
    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    @GetMapping
    fun current() = passwordPolicyService.toResponse(passwordPolicyService.getPolicy(resolvedTenantId()))

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun update(@RequestBody request: PasswordPolicyUpsertRequest) =
        passwordPolicyService.toResponse(passwordPolicyService.upsertPolicy(resolvedTenantId(), request))
}
