package com.numera.admin

import com.numera.admin.application.UserGroupService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for user-group visibility queries exposed by the admin module.
 *
 * Lives in the admin ROOT package so the customer module can depend on it without crossing into
 * admin's private application sub-package.
 */
@Service
class UserGroupPort(
    private val userGroupService: UserGroupService,
) {
    /**
     * Returns the list of customer IDs the given user is permitted to see via their group memberships,
     * or null if the user belongs to no groups (meaning they have global visibility).
     */
    fun getVisibleCustomerIds(userId: UUID): List<UUID>? =
        userGroupService.getVisibleCustomerIds(userId)
}
