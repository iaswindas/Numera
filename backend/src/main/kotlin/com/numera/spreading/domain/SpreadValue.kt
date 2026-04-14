package com.numera.spreading.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import org.hibernate.annotations.ColumnTransformer
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

@Entity
@Table(name = "spread_values")
class SpreadValue : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "spread_item_id")
    lateinit var spreadItem: SpreadItem

    @Column(nullable = false)
    var lineItemId: java.util.UUID = java.util.UUID.randomUUID()

    @Column(nullable = false)
    var itemCode: String = ""

    @Column(nullable = false)
    var label: String = ""

    @Column
    var mappedValue: BigDecimal? = null

    @Column
    var rawValue: BigDecimal? = null

    @Column
    var expressionType: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    var expressionDetailJson: String? = null

    @Column
    var scaleFactor: BigDecimal? = null

    @Column
    var confidenceScore: BigDecimal? = null

    @Column
    var confidenceLevel: String? = null

    @Column
    var sourcePage: Int? = null

    @Column(columnDefinition = "text")
    var sourceText: String? = null

    @Column(nullable = false)
    var manualOverride: Boolean = false

    @Column(nullable = false)
    var autofilled: Boolean = false

    @Column(nullable = false)
    var formulaCell: Boolean = false

    @Column(nullable = false)
    var accepted: Boolean = false

    @Column(columnDefinition = "text")
    var overrideComment: String? = null

    @Column
    var sourceDocumentName: String? = null

    @Column(columnDefinition = "text")
    var sourceBbox: String? = null

    @Column(columnDefinition = "text")
    var notes: String? = null
}