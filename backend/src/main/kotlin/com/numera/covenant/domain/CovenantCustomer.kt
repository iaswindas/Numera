package com.numera.covenant.domain

import com.numera.customer.domain.Customer
import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "covenant_customers")
class CovenantCustomer : TenantAwareEntity() {

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    lateinit var customer: Customer

    @Column
    var rimId: String? = null

    @Column
    var clEntityId: String? = null

    @Column
    var financialYearEnd: LocalDate? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @OneToMany(mappedBy = "covenantCustomer", cascade = [CascadeType.ALL], orphanRemoval = true)
    var contacts: MutableList<CovenantContact> = mutableListOf()
}
