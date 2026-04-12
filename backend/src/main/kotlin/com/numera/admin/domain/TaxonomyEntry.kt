package com.numera.admin.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "taxonomy_entries")
class TaxonomyEntry : TenantAwareEntity() {
    @Column(name = "item_code", nullable = false)
    var itemCode: String = ""

    @Column(nullable = false)
    var label: String = ""

    @Column
    var category: String? = null

    @Column(name = "parent_code")
    var parentCode: String? = null

    @Column(columnDefinition = "TEXT[]")
    var synonyms: Array<String> = emptyArray()

    @Column(nullable = false)
    var language: String = "en"

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
