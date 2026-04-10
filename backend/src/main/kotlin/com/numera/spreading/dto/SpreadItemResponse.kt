package com.numera.spreading.dto

import com.numera.spreading.domain.SpreadStatus

data class SpreadItemResponse(
    val id: String,
    val customerId: String,
    val documentId: String,
    val statementDate: String,
    val status: SpreadStatus,
    val currentVersion: Int,
    val createdAt: String,
)