package com.numera.admin

import com.numera.auth.UserAdminCreateRequest
import com.numera.auth.UserAdminFacade
import com.numera.auth.UserAdminUpdateRequest
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader
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

    @PostMapping("/bulk-upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun bulkUpload(@RequestParam("file") file: MultipartFile): Map<String, Any> {
        val tenantId = resolvedTenantId()
        var created = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        BufferedReader(InputStreamReader(file.inputStream)).use { reader ->
            val header = reader.readLine() ?: return mapOf("created" to 0, "skipped" to 0, "errors" to listOf("Empty file"))
            val cols = header.split(",").map { it.trim().lowercase() }
            val emailIdx = cols.indexOf("email")
            val nameIdx = cols.indexOf("fullname").takeIf { it >= 0 } ?: cols.indexOf("full_name").takeIf { it >= 0 } ?: cols.indexOf("name")
            val roleIdx = cols.indexOf("role").takeIf { it >= 0 } ?: cols.indexOf("roles")

            if (emailIdx < 0 || nameIdx < 0) {
                return mapOf("created" to 0, "skipped" to 0, "errors" to listOf("CSV must have 'email' and 'fullname' columns"))
            }

            var lineNum = 1
            reader.forEachLine { line ->
                lineNum++
                try {
                    val parts = line.split(",").map { it.trim() }
                    val email = parts.getOrNull(emailIdx) ?: return@forEachLine
                    val fullName = parts.getOrNull(nameIdx) ?: return@forEachLine
                    if (email.isBlank() || fullName.isBlank()) {
                        skipped++
                        return@forEachLine
                    }
                    val role = if (roleIdx >= 0) parts.getOrNull(roleIdx)?.takeIf { it.isNotBlank() } ?: "ROLE_ANALYST" else "ROLE_ANALYST"
                    userAdminFacade.create(tenantId, UserAdminCreateRequest(email, fullName, null, listOf(role), true))
                    created++
                } catch (e: Exception) {
                    errors.add("Line $lineNum: ${e.message}")
                    skipped++
                }
            }
        }

        return mapOf("created" to created, "skipped" to skipped, "errors" to errors)
    }
}

data class UserUpsertRequest(
    val email: String,
    val fullName: String,
    val password: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
)
