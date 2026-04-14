package com.numera.covenant.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CovenantCustomerRequest(
    @field:NotNull val customerId: java.util.UUID,
    @field:Size(max = 50) val rimId: String? = null,
    @field:Size(max = 50) val clEntityId: String? = null,
    val financialYearEnd: LocalDate? = null,
    @field:Valid val contacts: List<CovenantContactRequest> = emptyList(),
)

data class CovenantContactRequest(
    @field:Size(max = 50) val contactType: String,   // INTERNAL | EXTERNAL
    val userId: java.util.UUID? = null,
    @field:Size(max = 200) val name: String,
    @field:Size(max = 200) val email: String,
)
