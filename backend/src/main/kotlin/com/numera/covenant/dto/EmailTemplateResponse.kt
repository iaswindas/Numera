package com.numera.covenant.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 200) val title: String? = null,
    @field:NotBlank val htmlContent: String,
)

data class SignatureResponse(
    val id: UUID,
    val name: String,
    val title: String?,
    val htmlContent: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
