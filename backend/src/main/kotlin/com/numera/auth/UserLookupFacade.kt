package com.numera.auth

import com.numera.auth.infrastructure.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for user look-up by email, exposed by the auth module.
 *
 * Lives in the auth ROOT package so other modules (customer, document) can look up users by email
 * without crossing into auth's private infrastructure package.
 */
@Service
class UserLookupFacade(
    private val userRepository: UserRepository,
) {
    /** Returns the user's UUID if found, null otherwise. */
    fun findIdByEmail(email: String): UUID? =
        userRepository.findByEmailIgnoreCase(email).map { it.id }.orElse(null)

    /** Returns the user's role names (e.g. "ROLE_ADMIN") if found, empty list otherwise. */
    fun findRolesByEmail(email: String): List<String> =
        userRepository.findByEmailIgnoreCase(email)
            .map { it.roles.map { r -> r.name } }
            .orElse(emptyList())

    /** Returns name + id for upload attribution, null if the user cannot be found. */
    fun findUploadedByInfo(email: String): UserUploadedByInfo? =
        userRepository.findByEmailIgnoreCase(email).map {
            UserUploadedByInfo(id = it.id!!, fullName = it.fullName ?: it.email)
        }.orElse(null)
}

/** Simple projection for file-upload attribution — lives in auth root so other modules can use
 *  it without importing from auth sub-packages. */
data class UserUploadedByInfo(
    val id: UUID,
    val fullName: String,
)
