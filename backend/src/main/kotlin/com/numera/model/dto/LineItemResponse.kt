package com.numera.model.dto

import com.numera.model.domain.ModelItemType

data class LineItemResponse(
    val id: String,
    val itemCode: String,
    val label: String,
    val zone: String,
    val category: String?,
    val itemType: ModelItemType,
    val formula: String?,
    val required: Boolean,
    val isTotal: Boolean,
    val indentLevel: Int,
    val signConvention: String,
    val aliases: List<String>,
    val displayOrder: Int,
)
