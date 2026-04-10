package com.numera.model.dto

import com.numera.model.domain.ModelItemType
import jakarta.validation.constraints.NotBlank

data class TemplateUpsertRequest(
    @field:NotBlank val name: String,
    val version: Int = 1,
    val currency: String = "USD",
    val active: Boolean = true,
    val lineItems: List<TemplateLineItemRequest> = emptyList(),
    val validations: List<TemplateValidationRequest> = emptyList(),
)

data class TemplateLineItemRequest(
    @field:NotBlank val itemCode: String,
    @field:NotBlank val label: String,
    val zone: String = "INCOME_STATEMENT",
    val category: String? = null,
    val itemType: ModelItemType = ModelItemType.INPUT,
    val formula: String? = null,
    val required: Boolean = false,
    val isTotal: Boolean = false,
    val indentLevel: Int = 0,
    val signConvention: String = "NATURAL",
    val aliases: List<String> = emptyList(),
    val sortOrder: Int = 0,
)

data class TemplateValidationRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val expression: String,
    val severity: String = "WARNING",
)
