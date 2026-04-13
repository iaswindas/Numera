package com.numera.admin.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "managed_zones")
class ManagedZone : TenantAwareEntity() {
    @Column(nullable = false, length = 100)
    var name: String = ""

    @Column(nullable = false, length = 50)
    var code: String = ""

    @Column(nullable = false, length = 20)
    var color: String = "#6366f1"

    @Column
    var description: String? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
