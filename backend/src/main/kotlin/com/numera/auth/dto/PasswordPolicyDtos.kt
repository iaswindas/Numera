package com.numera.auth.dto

import jakarta.validation.constraints.NotBlank

data class PasswordPolicyUpsertRequest(
    val minLength: Int = 12,
    val requireUppercase: Boolean = true,
    val requireLowercase: Boolean = true,
    val requireDigit: Boolean = true,
    val requireSpecialCharacter: Boolean = true,
    val expiryDays: Int = 90,
    val historySize: Int = 5,
    val enabled: Boolean = true,
)

data class PasswordPolicyResponse(
    val id: String? = null,
    val tenantId: String,
    val minLength: Int,
    val requireUppercase: Boolean,
    val requireLowercase: Boolean,
    val requireDigit: Boolean,
    val requireSpecialCharacter: Boolean,
    val expiryDays: Int,
    val historySize: Int,
    val enabled: Boolean,
    val updatedAt: String? = null,
)

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,

    @field:NotBlank
    val newPassword: String,
)

data class PasswordChangeResponse(
    val changed: Boolean = true,
)