package com.numera.covenant.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "email_templates")
class EmailTemplate : TenantAwareEntity() {

    @Column(nullable = false)
    var name: String = ""

    /** FINANCIAL, NON_FINANCIAL, or null for both */
    @Column
    var covenantType: String? = null

    /** DUE_REMINDER, OVERDUE_NOTICE, BREACH_ALERT, WAIVER, NOT_WAIVER */
    @Column
    var templateCategory: String? = null

    @Column
    var subject: String? = null

    @Column(columnDefinition = "TEXT", nullable = false)
    var bodyHtml: String = ""

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    var createdBy: UUID? = null
}
