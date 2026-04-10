package com.numera.covenant.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "covenant_contacts")
class CovenantContact : BaseEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "covenant_customer_id")
    lateinit var covenantCustomer: CovenantCustomer

    /** INTERNAL (system-registered user) or EXTERNAL (email-only person) */
    @Column(nullable = false)
    var contactType: String = "EXTERNAL"

    /** Populated only for INTERNAL contacts */
    @Column
    var userId: UUID? = null

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var email: String = ""
}
