package com.numera.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128) val password: String,
    @field:NotBlank val fullName: String,
    val tenantId: java.util.UUID? = null,
)

data class RegisterResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val accountStatus: String,
    val message: String = "Registration successful. Please await admin approval.",
)
