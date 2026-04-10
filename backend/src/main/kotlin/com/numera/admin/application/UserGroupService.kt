package com.numera.admin.application

import com.numera.admin.domain.GroupCustomerAccess
import com.numera.admin.domain.GroupMember
import com.numera.admin.domain.UserGroup
import com.numera.admin.infrastructure.GroupCustomerAccessRepository
import com.numera.admin.infrastructure.GroupMemberRepository
import com.numera.admin.infrastructure.UserGroupRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserGroupService(
    private val groupRepository: UserGroupRepository,
    private val memberRepository: GroupMemberRepository,
    private val accessRepository: GroupCustomerAccessRepository,
) {
    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    fun listGroups(): List<Map<String, Any?>> =
        groupRepository.findByTenantIdAndIsActiveTrue(tenantId).map { it.toMap() }

    fun getGroup(id: UUID): Map<String, Any?> {
        val group = groupRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Group not found: $id") }
        val members = memberRepository.findByGroupId(id)
        val customers = accessRepository.findByGroupId(id)
        return group.toMap() + mapOf(
            "memberUserIds" to members.map { it.userId },
            "customerIds" to customers.map { it.customerId },
        )
    }

    @Transactional
    fun createGroup(name: String, description: String?, createdBy: UUID?): Map<String, Any?> {
        val saved = groupRepository.save(UserGroup().also {
            it.tenantId = tenantId
            it.name = name
            it.description = description
            it.createdBy = createdBy
        })
        return saved.toMap()
    }

    @Transactional
    fun addMember(groupId: UUID, userId: UUID) {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Group not found: $groupId") }
        memberRepository.save(GroupMember().also {
            it.group = group
            it.userId = userId
        })
    }

    @Transactional
    fun removeMember(groupId: UUID, userId: UUID) {
        val members = memberRepository.findByGroupId(groupId)
        members.find { it.userId == userId }?.let { memberRepository.delete(it) }
    }

    @Transactional
    fun addCustomerAccess(groupId: UUID, customerId: UUID) {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Group not found: $groupId") }
        accessRepository.save(GroupCustomerAccess().also {
            it.group = group
            it.customerId = customerId
        })
    }

    @Transactional
    fun removeCustomerAccess(groupId: UUID, customerId: UUID) {
        val accesses = accessRepository.findByGroupId(groupId)
        accesses.find { it.customerId == customerId }?.let { accessRepository.delete(it) }
    }

    /** Returns the list of customer IDs visible to a user via their group memberships */
    fun getVisibleCustomerIds(userId: UUID): List<UUID>? {
        val groupIds = memberRepository.findGroupIdsByUserId(userId)
        if (groupIds.isEmpty()) return null // No group membership = see all customers (admin)
        return accessRepository.findCustomerIdsByGroupIds(groupIds)
    }

    private fun UserGroup.toMap() = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "isActive" to isActive,
        "createdAt" to createdAt,
    )
}
