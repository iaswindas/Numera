package com.numera.customer.dto

data class CustomerSearchRequest(
    val query: String? = null,
    val industry: String? = null,
    val country: String? = null,
)