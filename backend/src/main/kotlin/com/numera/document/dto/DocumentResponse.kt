package com.numera.document.dto

import com.numera.document.domain.DocumentStatus

data class DocumentResponse(
    val id: String,
    val filename: String,
    val originalFilename: String,
    val fileType: String,
    val fileSize: Long,
    val language: String,
    val processingStatus: DocumentStatus,
    val pdfType: String?,
    val backendUsed: String?,
    val totalPages: Int?,
    val processingTimeMs: Int?,
    val uploadedBy: String,
    val uploadedByName: String,
    val zonesDetected: Int,
    val createdAt: String,
)
