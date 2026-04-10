package com.numera.spreading.application

import com.numera.spreading.infrastructure.ExpressionPatternRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AutofillService(
    private val expressionPatternRepository: ExpressionPatternRepository,
) {
    fun bestMatch(
        tenantId: UUID,
        customerId: UUID,
        templateId: UUID,
        itemCode: String,
    ): String? = expressionPatternRepository
        .findByTenantIdAndCustomerIdAndTemplateIdAndItemCode(tenantId, customerId, templateId, itemCode)
        ?.patternJson
}