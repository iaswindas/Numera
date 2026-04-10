package com.numera.shared.exception

open class ApiException(
    val errorCode: ErrorCode,
    override val message: String,
    val validations: List<Map<String, Any?>>? = null,
    val unmappedRequired: List<String>? = null,
) : RuntimeException(message)
