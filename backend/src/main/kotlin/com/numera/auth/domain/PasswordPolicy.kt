package com.numera.auth.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "password_policies")
class PasswordPolicy : TenantAwareEntity() {
    @Column(name = "min_length", nullable = false)
    var minLength: Int = 12

    @Column(name = "require_uppercase", nullable = false)
    var requireUppercase: Boolean = true

    @Column(name = "require_lowercase", nullable = false)
    var requireLowercase: Boolean = true

    @Column(name = "require_digit", nullable = false)
    var requireDigit: Boolean = true

    @Column(name = "require_special_character", nullable = false)
    var requireSpecialCharacter: Boolean = true

    @Column(name = "expiry_days", nullable = false)
    var expiryDays: Int = 90

    @Column(name = "history_size", nullable = false)
    var historySize: Int = 5

    @Column(nullable = false)
    var enabled: Boolean = true
}