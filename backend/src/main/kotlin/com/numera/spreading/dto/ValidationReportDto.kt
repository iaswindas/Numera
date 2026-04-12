package com.numera.spreading.dto

import java.math.BigDecimal

data class ValidationReportDto(
    val results: List<ValidationResultDto>,
    val balanceCheck: BalanceCheckResultDto,
    val isValid: Boolean,
)

data class ValidationResultDto(
    val ruleId: String,
    val ruleName: String,
    val expression: String,
    val result: String, // PASS, FAIL, WARNING
    val difference: BigDecimal,
    val severity: String,
)

data class BalanceCheckResultDto(
    val status: String, // PASS or FAIL
    val totalAssets: BigDecimal?,
    val totalLiabilities: BigDecimal?,
    val totalEquity: BigDecimal?,
    val difference: BigDecimal?,
    val isBalanced: Boolean,
)
