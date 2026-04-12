package com.numera.admin.application

import com.numera.admin.domain.ExclusionRule
import com.numera.admin.infrastructure.ExclusionRuleRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ExclusionService(
    private val repository: ExclusionRuleRepository,
) {
    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun list(): List<Map<String, Any?>> =
        repository.findByTenantId(resolvedTenantId()).map { it.toMap() }

    fun listActive(): List<Map<String, Any?>> =
        repository.findByTenantIdAndIsActiveTrue(resolvedTenantId()).map { it.toMap() }

    fun listByCategory(category: String): List<Map<String, Any?>> =
        repository.findByTenantIdAndCategory(resolvedTenantId(), category).map { it.toMap() }

    fun getCategories(): List<String> = ExclusionRule.VALID_CATEGORIES

    @Transactional
    fun create(category: String, pattern: String, patternType: String, description: String?): Map<String, Any?> {
        if (category !in ExclusionRule.VALID_CATEGORIES) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Invalid category: $category. Valid: ${ExclusionRule.VALID_CATEGORIES}")
        }
        if (patternType !in ExclusionRule.VALID_PATTERN_TYPES) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Invalid pattern type: $patternType. Valid: ${ExclusionRule.VALID_PATTERN_TYPES}")
        }
        val rule = repository.save(ExclusionRule().also {
            it.tenantId = resolvedTenantId()
            it.category = category
            it.pattern = pattern
            it.patternType = patternType
            it.description = description
        })
        return rule.toMap()
    }

    @Transactional
    fun update(id: UUID, category: String?, pattern: String?, patternType: String?, description: String?, isActive: Boolean?): Map<String, Any?> {
        val rule = repository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Exclusion rule not found") }
        category?.let { rule.category = it }
        pattern?.let { rule.pattern = it }
        patternType?.let { rule.patternType = it }
        description?.let { rule.description = it }
        isActive?.let { rule.isActive = it }
        return repository.save(rule).toMap()
    }

    @Transactional
    fun delete(id: UUID) {
        repository.deleteById(id)
    }

    /**
     * Apply exclusion rules to clean text. Returns cleaned text.
     */
    fun applyRules(text: String): String {
        val rules = repository.findByTenantIdAndIsActiveTrue(resolvedTenantId())
        var cleaned = text
        for (rule in rules) {
            cleaned = when (rule.patternType) {
                "EXACT" -> cleaned.replace(rule.pattern, "")
                "CONTAINS" -> cleaned.replace(rule.pattern, "", ignoreCase = true)
                "PREFIX" -> if (cleaned.startsWith(rule.pattern, ignoreCase = true)) cleaned.removePrefix(rule.pattern) else cleaned
                "SUFFIX" -> if (cleaned.endsWith(rule.pattern, ignoreCase = true)) cleaned.removeSuffix(rule.pattern) else cleaned
                "REGEX" -> cleaned.replace(Regex(rule.pattern, RegexOption.IGNORE_CASE), "")
                else -> cleaned
            }
        }
        return cleaned.trim()
    }

    private fun ExclusionRule.toMap() = mapOf(
        "id" to id.toString(),
        "category" to category,
        "pattern" to pattern,
        "patternType" to patternType,
        "description" to description,
        "isActive" to isActive,
    )
}
