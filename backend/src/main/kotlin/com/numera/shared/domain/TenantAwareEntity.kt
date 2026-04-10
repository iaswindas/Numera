package com.numera.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.util.UUID

@MappedSuperclass
abstract class TenantAwareEntity : BaseEntity() {
    @Column(nullable = false)
    var tenantId: UUID = DEFAULT_TENANT

    companion object {
        val DEFAULT_TENANT: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}