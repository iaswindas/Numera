package com.numera.spreading.dto

data class VersionEntryResponse(
    val versionNumber: Int,
    val action: String,
    val comments: String?,
    val cellsChanged: Int,
    val createdBy: String,
    val createdAt: String,
)

data class VersionHistoryResponse(
    val spreadItemId: String,
    val versions: List<VersionEntryResponse>,
)