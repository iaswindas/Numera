package com.numera.spreading.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.UUID

data class SpreadItemRequest(
    @field:NotNull val documentId: UUID,
    @field:NotNull val templateId: UUID,
    @field:NotNull val statementDate: LocalDate,
    val frequency: String,
    val auditMethod: String?,
    val sourceCurrency: String?,
    val consolidation: String?,
)