package com.numera.model.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import org.hibernate.annotations.ColumnTransformer
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "model_line_items")
class ModelLineItem : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id")
    lateinit var template: ModelTemplate

    @Column(nullable = false)
    var itemCode: String = ""

    @Column(nullable = false)
    var label: String = ""

    @Column(nullable = false)
    var zone: String = "INCOME_STATEMENT"

    @Column
    var category: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var itemType: ModelItemType = ModelItemType.INPUT

    @Column
    var formula: String? = null

    @Column(nullable = false)
    var required: Boolean = false

    @Column(nullable = false)
    var total: Boolean = false

    @Column(nullable = false)
    var indentLevel: Int = 0

    @Column(nullable = false)
    var signConvention: String = "NATURAL"

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    var aliasesJson: String? = null

    @Column(nullable = false)
    var sortOrder: Int = 0
}
