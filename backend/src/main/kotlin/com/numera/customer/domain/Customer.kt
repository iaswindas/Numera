package com.numera.customer.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "customers")
class Customer : TenantAwareEntity() {
    @Column(nullable = false)
    var customerCode: String = ""

    @Column(nullable = false)
    var name: String = ""

    @Column
    var industry: String? = null

    @Column
    var country: String? = null

    @Column
    var relationshipManager: String? = null
}