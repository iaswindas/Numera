package com.numera.model.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "model_templates")
class ModelTemplate : TenantAwareEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var version: Int = 1

    @Column(nullable = false)
    var currency: String = "USD"

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "customer_id")
    var customerId: UUID? = null

    @ManyToOne
    @JoinColumn(name = "parent_template_id")
    var parentTemplate: ModelTemplate? = null

    @Column(nullable = false)
    var isGlobal: Boolean = true
}