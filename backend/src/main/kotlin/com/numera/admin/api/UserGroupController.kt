package com.numera.admin.api

import com.numera.admin.application.UserGroupService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/groups")
class UserGroupController(
    private val userGroupService: UserGroupService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> = userGroupService.listGroups()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): Map<String, Any?> = userGroupService.getGroup(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateGroupRequest): Map<String, Any?> =
        userGroupService.createGroup(request.name, request.description, request.createdBy)

    @PostMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(@PathVariable groupId: UUID, @PathVariable userId: UUID) =
        userGroupService.addMember(groupId, userId)

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@PathVariable groupId: UUID, @PathVariable userId: UUID) =
        userGroupService.removeMember(groupId, userId)

    @PostMapping("/{groupId}/customers/{customerId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCustomerAccess(@PathVariable groupId: UUID, @PathVariable customerId: UUID) =
        userGroupService.addCustomerAccess(groupId, customerId)

    @DeleteMapping("/{groupId}/customers/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCustomerAccess(@PathVariable groupId: UUID, @PathVariable customerId: UUID) =
        userGroupService.removeCustomerAccess(groupId, customerId)
}

data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val createdBy: UUID? = null,
)
