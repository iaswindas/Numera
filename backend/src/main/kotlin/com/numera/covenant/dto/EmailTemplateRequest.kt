package com.numera.covenant.dto

import java.util.UUID

data class EmailTemplateRequest(
    val name: String,
    val covenantType: String? = null,
    val templateCategory: String? = null,
    val subject: String? = null,
    val bodyHtml: String,
)
