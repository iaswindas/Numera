package com.numera.spreading.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "spread_versions")
class SpreadVersion : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "spread_item_id")
    lateinit var spreadItem: SpreadItem

    @Column(nullable = false)
    var versionNumber: Int = 1

    @Column(nullable = false)
    var action: String = "CREATED"

    @Column(columnDefinition = "text")
    var comments: String? = null

    @Column(nullable = false, columnDefinition = "jsonb")
    var snapshotJson: String = "[]"

    @Column(nullable = false)
    var cellsChanged: Int = 0

    @Column(nullable = false)
    var createdBy: String = "System"
}