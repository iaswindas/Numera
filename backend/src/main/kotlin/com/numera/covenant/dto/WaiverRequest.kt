package com.numera.covenant.dto

import java.util.UUID

data class WaiverRequest(
    val monitoringItemId: UUID,
    val waiverType: String,           // INSTANCE or PERMANENT
    val letterType: String,           // WAIVE or NOT_WAIVE
    val signatureId: UUID?,
    val emailTemplateId: UUID?,
    val deliveryMethod: String,       // EMAIL or DOWNLOAD
    val comments: String? = null,
)
