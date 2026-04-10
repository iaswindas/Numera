package com.numera.auth.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

@Entity
@Table(name = "roles")
class Role : TenantAwareEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @ElementCollection
    @CollectionTable(name = "role_permissions", joinColumns = [JoinColumn(name = "role_id")])
    @Column(name = "permission")
    var permissions: MutableSet<Permission> = mutableSetOf()
}
