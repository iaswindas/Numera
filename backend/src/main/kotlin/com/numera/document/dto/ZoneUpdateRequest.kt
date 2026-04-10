package com.numera.document.dto

import jakarta.validation.constraints.NotBlank

data class ZoneUpdateRequest(
    @field:NotBlank val zoneType: String,
    val zoneLabel: String? = null,
)