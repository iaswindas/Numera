package com.numera.admin.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "group_members")
class GroupMember : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    lateinit var group: UserGroup

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID()
}
