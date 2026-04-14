package com.numera.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
    val mfaCode: String? = null,
)