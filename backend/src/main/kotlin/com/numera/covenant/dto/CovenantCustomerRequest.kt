package com.numera.covenant.dto

import java.time.LocalDate

data class CovenantCustomerRequest(
    val customerId: java.util.UUID,
    val rimId: String? = null,
    val clEntityId: String? = null,
    val financialYearEnd: LocalDate? = null,
    val contacts: List<CovenantContactRequest> = emptyList(),
)

data class CovenantContactRequest(
    val contactType: String,   // INTERNAL | EXTERNAL
    val userId: java.util.UUID? = null,
    val name: String,
    val email: String,
)
