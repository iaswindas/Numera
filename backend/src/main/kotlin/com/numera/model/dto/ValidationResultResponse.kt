package com.numera.model.dto

data class ValidationResultResponse(
    val id: String,
    val name: String,
    val expression: String,
    val severity: String,
)