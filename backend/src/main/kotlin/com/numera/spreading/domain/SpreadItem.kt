package com.numera.spreading.domain

import com.numera.customer.domain.Customer
import com.numera.document.domain.Document
import com.numera.model.domain.ModelTemplate
import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "spread_items")
class SpreadItem : TenantAwareEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    lateinit var customer: Customer

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id")
    lateinit var document: Document

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id")
    lateinit var template: ModelTemplate

    @Column(nullable = false)
    var statementDate: LocalDate = LocalDate.now()

    @Column(nullable = false)
    var frequency: String = "ANNUAL"

    @Column
    var auditMethod: String? = null

    @Column
    var sourceCurrency: String? = null

    @Column
    var consolidation: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SpreadStatus = SpreadStatus.DRAFT

    @Column(nullable = false)
    var currentVersion: Int = 0
}