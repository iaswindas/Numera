package com.numera.customer.dto

data class CustomerResponse(
    val id: String,
    val tenantId: String,
    val customerCode: String,
    val name: String,
    val industry: String?,
    val country: String?,
    val relationshipManager: String?,
)