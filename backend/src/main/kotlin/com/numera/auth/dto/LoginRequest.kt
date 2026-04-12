package com.numera.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank val password: String,
    val mfaCode: String? = null,
)