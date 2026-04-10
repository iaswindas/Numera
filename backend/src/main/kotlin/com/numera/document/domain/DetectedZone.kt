package com.numera.document.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "detected_zones")
class DetectedZone : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id")
    lateinit var document: Document

    @Column(nullable = false)
    var tableId: String = ""

    @Column(nullable = false)
    var zoneType: String = ""

    @Column
    var zoneLabel: String? = null

    @Column
    var confidence: Double? = null

    @Column
    var pageNumber: Int? = null

    @Column(columnDefinition = "jsonb")
    var metadataJson: String? = null
}