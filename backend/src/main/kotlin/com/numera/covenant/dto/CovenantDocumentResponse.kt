package com.numera.covenant.dto

import java.time.Instant
import java.util.UUID

data class CovenantDocumentResponse(
    val id: UUID,
    val fileName: String,
    val fileSize: Long?,
    val contentType: String?,
    val uploadedBy: UUID?,
    val uploadedAt: Instant,
)
