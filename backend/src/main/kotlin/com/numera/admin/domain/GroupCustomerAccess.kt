package com.numera.admin.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "group_customer_access")
class GroupCustomerAccess : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    lateinit var group: UserGroup

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID = UUID.randomUUID()
}
