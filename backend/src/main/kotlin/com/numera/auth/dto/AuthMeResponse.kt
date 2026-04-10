package com.numera.auth.dto

data class AuthMeResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val roles: List<String>,
    val tenantId: String,
    val tenantName: String,
    val lastLoginAt: String?,
)
