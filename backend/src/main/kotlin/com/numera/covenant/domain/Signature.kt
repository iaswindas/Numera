package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "signatures")
class Signature : TenantAwareEntity() {

    @Column(nullable = false)
    var name: String = ""

    @Column
    var title: String? = null

    @Column(columnDefinition = "TEXT", nullable = false)
    var htmlContent: String = ""

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    var createdBy: UUID? = null
}
