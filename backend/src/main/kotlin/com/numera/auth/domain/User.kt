package com.numera.auth.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User : TenantAwareEntity() {
    @Column(nullable = false)
    var email: String = ""

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = ""

    @Column(nullable = false)
    var fullName: String = ""

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column
    var lastLoginAt: Instant? = null

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
}
