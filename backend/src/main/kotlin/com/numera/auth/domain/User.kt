package com.numera.auth.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

enum class AccountStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    REJECTED
}

@Entity
@Table(name = "users")
class User : TenantAwareEntity() {
    @Column(nullable = false)
    var email: String = ""

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = ""

    @Column(name = "password_changed_at")
    var passwordChangedAt: Instant? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "password_history", nullable = false, columnDefinition = "jsonb")
    var passwordHistory: String = "[]"

    @Column(nullable = false)
    var fullName: String = ""

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var accountStatus: AccountStatus = AccountStatus.PENDING

    @Column
    var lastLoginAt: Instant? = null

    // SSO fields
    @Column(name = "sso_provider")
    var ssoProvider: String? = null

    @Column(name = "sso_subject_id")
    var ssoSubjectId: String? = null

    // MFA fields
    @Column(name = "mfa_enabled", nullable = false)
    var mfaEnabled: Boolean = false

    @Column(name = "mfa_secret")
    var mfaSecret: String? = null

    @Column(name = "mfa_verified", nullable = false)
    var mfaVerified: Boolean = false

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
}
