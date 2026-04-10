package com.numera.covenant.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CovenantCustomerResponse(
    val id: UUID,
    val customerId: UUID,
    val customerName: String,
    val rimId: String?,
    val clEntityId: String?,
    val financialYearEnd: LocalDate?,
    val isActive: Boolean,
    val contacts: List<CovenantContactResponse>,
    val createdAt: Instant,
)

data class CovenantContactResponse(
    val id: UUID,
    val contactType: String,
    val userId: UUID?,
    val name: String,
    val email: String,
)
