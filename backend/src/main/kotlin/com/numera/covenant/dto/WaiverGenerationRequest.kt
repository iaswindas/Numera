package com.numera.covenant.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class WaiverGenerationRequest(
    @field:NotNull val monitoringItemId: UUID,
    @field:NotBlank @field:Size(max = 50) val waiverType: String,           // INSTANCE or PERMANENT
    val waived: Boolean,               // true if waiving breach, false if not waiving
    val templateId: UUID?,             // Optional email template ID
    val signatureId: UUID?,            // Optional signature to append
    @field:Size(max = 2000) val comments: String? = null,
    val recipientIds: List<UUID> = emptyList(),  // User IDs to send to
)

data class WaiverLetterResponse(
    val id: UUID,
    val monitoringItemId: UUID,
    val waiverType: String,
    val waived: Boolean,
    val letterContent: String,
    val comments: String?,
    val generatedBy: UUID?,
    val generatedAt: java.time.Instant,
    val sentAt: java.time.Instant?,
)
