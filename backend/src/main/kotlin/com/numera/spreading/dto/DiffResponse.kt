package com.numera.spreading.dto

import java.math.BigDecimal

data class DiffChangeResponse(
    val itemCode: String,
    val label: String,
    val oldValue: BigDecimal?,
    val newValue: BigDecimal?,
    val changeType: String,
    val modifiedBy: String,
)

data class DiffResponse(
    val fromVersion: Int,
    val toVersion: Int,
    val changes: List<DiffChangeResponse>,
    val totalAdded: Int,
    val totalModified: Int,
    val totalRemoved: Int,
)