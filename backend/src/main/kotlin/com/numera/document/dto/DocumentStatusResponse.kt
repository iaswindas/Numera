package com.numera.document.dto

import com.numera.document.domain.DocumentStatus

data class DocumentStatusResponse(
    val id: String,
    val status: DocumentStatus,
    val errorMessage: String?,
)