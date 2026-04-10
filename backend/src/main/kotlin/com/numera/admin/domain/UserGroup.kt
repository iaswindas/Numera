package com.numera.admin.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "user_groups")
class UserGroup : TenantAwareEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column
    var description: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    var createdBy: UUID? = null
}
