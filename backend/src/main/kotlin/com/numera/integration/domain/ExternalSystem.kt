package com.numera.integration.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

enum class ExternalSystemType {
    CREDITLENS,
    NCINO,
    FINASTRA,
    GENERIC_REST,
}

@Entity
@Table(name = "external_systems")
class ExternalSystem : TenantAwareEntity() {

    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ExternalSystemType = ExternalSystemType.GENERIC_REST

    @Column(nullable = false)
    var baseUrl: String = ""

    /** Encrypted API key — null when not configured. */
    @Column
    var apiKey: String? = null

    @Column(nullable = false)
    var active: Boolean = true

    /** System-specific configuration stored as JSON. */
    @Column(columnDefinition = "TEXT")
    var configJson: String? = null
}
