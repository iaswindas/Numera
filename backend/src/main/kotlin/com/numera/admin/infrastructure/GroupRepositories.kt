package com.numera.admin.infrastructure

import com.numera.admin.domain.GroupCustomerAccess
import com.numera.admin.domain.GroupMember
import com.numera.admin.domain.UserGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserGroupRepository : JpaRepository<UserGroup, UUID> {
    fun findByTenantIdAndIsActiveTrue(tenantId: UUID): List<UserGroup>
    fun findByTenantId(tenantId: UUID): List<UserGroup>
}

@Repository
interface GroupMemberRepository : JpaRepository<GroupMember, UUID> {
    fun findByUserId(userId: UUID): List<GroupMember>
    fun findByGroupId(groupId: UUID): List<GroupMember>

    @Query("select gm.group.id from GroupMember gm where gm.userId = :userId")
    fun findGroupIdsByUserId(@Param("userId") userId: UUID): List<UUID>
}

@Repository
interface GroupCustomerAccessRepository : JpaRepository<GroupCustomerAccess, UUID> {
    fun findByGroupId(groupId: UUID): List<GroupCustomerAccess>

    @Query("select gca.customerId from GroupCustomerAccess gca where gca.group.id in :groupIds")
    fun findCustomerIdsByGroupIds(@Param("groupIds") groupIds: List<UUID>): List<UUID>
}
