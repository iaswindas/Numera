package com.numera.covenant.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class WaiverRequest(
    @field:NotNull val monitoringItemId: UUID,
    @field:NotBlank @field:Size(max = 50) val waiverType: String,           // INSTANCE or PERMANENT
    @field:NotBlank @field:Size(max = 50) val letterType: String,           // WAIVE or NOT_WAIVE
    val signatureId: UUID?,
    val emailTemplateId: UUID?,
    @field:NotBlank @field:Size(max = 50) val deliveryMethod: String,       // EMAIL or DOWNLOAD
    @field:Size(max = 2000) val comments: String? = null,
)
