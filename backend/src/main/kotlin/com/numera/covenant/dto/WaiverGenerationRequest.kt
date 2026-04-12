package com.numera.covenant.dto

import java.util.UUID

data class WaiverGenerationRequest(
    val monitoringItemId: UUID,
    val waiverType: String,           // INSTANCE or PERMANENT
    val waived: Boolean,               // true if waiving breach, false if not waiving
    val templateId: UUID?,             // Optional email template ID
    val signatureId: UUID?,            // Optional signature to append
    val comments: String? = null,
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
