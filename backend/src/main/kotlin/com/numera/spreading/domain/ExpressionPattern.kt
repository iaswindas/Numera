package com.numera.spreading.domain

import com.numera.customer.domain.Customer
import com.numera.model.domain.ModelTemplate
import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "expression_patterns")
class ExpressionPattern : TenantAwareEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    lateinit var customer: Customer

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id")
    lateinit var template: ModelTemplate

    @Column(nullable = false)
    var itemCode: String = ""

    @Column(nullable = false)
    var patternType: String = "EXPRESSION"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var patternJson: String = "{}"

    @Column(nullable = false)
    var usageCount: Int = 0

    @Column
    var lastUsedAt: Instant? = null
}