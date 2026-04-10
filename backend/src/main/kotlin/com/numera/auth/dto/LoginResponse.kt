package com.numera.auth.dto

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSec: Long,
    val user: UserProfile,
)