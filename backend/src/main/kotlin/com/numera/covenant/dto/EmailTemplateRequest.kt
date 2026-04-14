package com.numera.covenant.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class EmailTemplateRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 50) val covenantType: String? = null,
    @field:Size(max = 50) val templateCategory: String? = null,
    @field:Size(max = 200) val subject: String? = null,
    @field:NotBlank val bodyHtml: String,
)
