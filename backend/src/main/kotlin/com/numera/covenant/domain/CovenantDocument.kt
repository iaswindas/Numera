package com.numera.covenant.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "covenant_documents")
class CovenantDocument : BaseEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "monitoring_item_id")
    lateinit var monitoringItem: CovenantMonitoringItem

    @Column(nullable = false)
    var fileName: String = ""

    /** Object storage key (MinIO / S3) */
    @Column(nullable = false)
    var storageKey: String = ""

    @Column
    var fileSize: Long? = null

    @Column
    var contentType: String? = null

    @Column
    var uploadedBy: UUID? = null

    @Column(nullable = false)
    var uploadedAt: Instant = Instant.now()
}
