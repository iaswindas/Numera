package com.numera.document.dto

data class ZoneResponse(
    val id: String,
    val pageNumber: Int?,
    val zoneType: String,
    val zoneLabel: String?,
    val confidenceScore: Double?,
    val classificationMethod: String?,
    val detectedPeriods: List<String>,
    val detectedCurrency: String?,
    val detectedUnit: String?,
    val status: String,
    val rowCount: Int?,
)
