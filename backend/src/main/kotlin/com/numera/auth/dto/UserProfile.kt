package com.numera.auth.dto

data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val tenantId: String,
    val roles: List<String>,
)