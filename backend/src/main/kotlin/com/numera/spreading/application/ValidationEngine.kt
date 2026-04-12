package com.numera.spreading.application

import com.numera.model.application.FormulaEngine
import com.numera.model.application.TemplateService
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.spreading.domain.SpreadStatus
import com.numera.spreading.dto.BalanceCheckResultDto
import com.numera.spreading.dto.ValidationReportDto
import com.numera.spreading.dto.ValidationResultDto
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class ValidationEngine(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val templateService: TemplateService,
    private val formulaEngine: FormulaEngine,
) {
    fun validateSpread(spreadId: UUID): ValidationReportDto {
        val spreadItem = spreadItemRepository.findById(spreadId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Spread item not found") }

        if (spreadItem.status != SpreadStatus.SUBMITTED && spreadItem.status != SpreadStatus.APPROVED) {
            throw ApiException(
                ErrorCode.VALIDATION_FAILED,
                "Can only validate SUBMITTED or APPROVED spreads"
            )
        }

        val values = spreadValueRepository.findBySpreadItemId(spreadId)
        val template = templateService.findById(spreadItem.template.id!!)

        // Evaluate all model validations
        val valueMap = values.associateBy { it.itemCode }
        val numericMap = values.associate { it.itemCode to it.mappedValue }

        val validationResults = template.validations.map { validation ->
            val difference = formulaEngine.evaluate(validation.expression, numericMap) ?: BigDecimal.ZERO
            ValidationResultDto(
                ruleId = validation.id,
                ruleName = validation.name,
                expression = validation.expression,
                result = if (difference.compareTo(BigDecimal.ZERO) == 0) "PASS" else "FAIL",
                difference = difference,
                severity = validation.severity,
            )
        }

        // Check balance: Total Assets = Total Liabilities + Total Equity
        val balanceCheck = performBalanceCheck(valueMap)

        val isValid = validationResults.all { it.result == "PASS" } && balanceCheck.isBalanced

        return ValidationReportDto(
            results = validationResults,
            balanceCheck = balanceCheck,
            isValid = isValid,
        )
    }

    private fun performBalanceCheck(valueMap: Map<String, com.numera.spreading.domain.SpreadValue>): BalanceCheckResultDto {
        // Parse item codes to find Assets, Liabilities, Equity totals
        // Assuming codes like ASSET_TOTAL, LIABILITY_TOTAL, EQUITY_TOTAL
        val totalAssets = valueMap["ASSET_TOTAL"]?.mappedValue
        val totalLiabilities = valueMap["LIABILITY_TOTAL"]?.mappedValue
        val totalEquity = valueMap["EQUITY_TOTAL"]?.mappedValue

        val difference = if (totalAssets != null && totalLiabilities != null && totalEquity != null) {
            val rightSide = totalLiabilities.add(totalEquity)
            totalAssets.subtract(rightSide)
        } else null

        val isBalanced = difference == null || difference.compareTo(BigDecimal.ZERO) == 0

        return BalanceCheckResultDto(
            status = if (isBalanced) "PASS" else "FAIL",
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            totalEquity = totalEquity,
            difference = difference,
            isBalanced = isBalanced,
        )
    }
}
