package com.numera.document.domain

import com.numera.customer.domain.Customer
import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "documents")
class Document : TenantAwareEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    lateinit var customer: Customer

    @Column(nullable = false)
    var fileName: String = ""

    @Column(nullable = false)
    var originalFilename: String = ""

    @Column(nullable = false)
    var storagePath: String = ""

    @Column(nullable = false)
    var fileSize: Long = 0

    @Column(nullable = false)
    var contentType: String = "application/octet-stream"

    @Column(nullable = false)
    var language: String = "en"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DocumentStatus = DocumentStatus.UPLOADED

    @Column(nullable = false)
    var uploadedBy: String = "system@numera.local"

    @Column(nullable = false)
    var uploadedByName: String = "System"

    @Column
    var pdfType: String? = null

    @Column
    var backendUsed: String? = null

    @Column
    var totalPages: Int? = null

    @Column
    var processingTimeMs: Int? = null

    @Column
    var errorMessage: String? = null
}
