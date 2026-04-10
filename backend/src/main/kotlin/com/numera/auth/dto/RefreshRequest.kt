package com.numera.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)