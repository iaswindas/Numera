package com.numera.covenant.dto

import java.time.Instant
import java.util.UUID

data class EmailTemplateResponse(
    val id: UUID,
    val name: String,
    val covenantType: String?,
    val templateCategory: String?,
    val subject: String?,
    val bodyHtml: String,
    val isActive: Boolean,
    val createdAt: Instant,
)

data class SignatureRequest(
    val name: String,
    val htmlContent: String,
)

data class SignatureResponse(
    val id: UUID,
    val name: String,
    val htmlContent: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
