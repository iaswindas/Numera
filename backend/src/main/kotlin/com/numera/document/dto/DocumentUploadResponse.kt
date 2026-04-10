package com.numera.document.dto

import com.numera.document.domain.DocumentStatus

data class DocumentUploadResponse(
    val documentId: String,
    val filename: String,
    val status: DocumentStatus,
    val message: String,
)
