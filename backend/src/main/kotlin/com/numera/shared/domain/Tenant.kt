package com.numera.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "tenants")
class Tenant : BaseEntity() {
    @Column(nullable = false, unique = true)
    var code: String = ""

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var active: Boolean = true
}
