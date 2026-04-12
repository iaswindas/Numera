package com.numera.admin.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "exclusion_rules")
class ExclusionRule : TenantAwareEntity() {
    @Column(nullable = false)
    var category: String = ""

    @Column(nullable = false)
    var pattern: String = ""

    @Column(name = "pattern_type", nullable = false)
    var patternType: String = "EXACT"

    @Column
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    companion object {
        val VALID_CATEGORIES = listOf(
            "HEADER_FOOTER", "PAGE_NUMBER", "WATERMARK", "DISCLAIMER",
            "SIGNATURE", "DATE_STAMP", "LOGO_TEXT", "BOILERPLATE",
            "ANNOTATION", "CURRENCY_SYMBOL", "UNIT_LABEL", "METADATA"
        )
        val VALID_PATTERN_TYPES = listOf("EXACT", "CONTAINS", "REGEX", "PREFIX", "SUFFIX")
    }
}
