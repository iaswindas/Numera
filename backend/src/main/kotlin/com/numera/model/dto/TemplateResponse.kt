package com.numera.model.dto

data class TemplateResponse(
    val id: String,
    val name: String,
    val version: Int,
    val currency: String,
    val active: Boolean,
    val lineItems: List<LineItemResponse>,
    val validations: List<ValidationResultResponse>,
)