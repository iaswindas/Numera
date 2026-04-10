package com.numera.model.dto

data class TemplateItemsResponse(
    val templateId: String,
    val templateName: String,
    val zone: String,
    val items: List<TemplateZoneItemResponse>,
)

data class TemplateZoneItemResponse(
    val id: String,
    val itemCode: String,
    val label: String,
    val category: String?,
    val itemType: String,
    val formula: String?,
    val displayOrder: Int,
    val indentLevel: Int,
    val isTotal: Boolean,
    val isRequired: Boolean,
    val signConvention: String,
    val synonyms: List<String>,
)
