package com.numera.shared.exception

import java.util.Optional

fun <T> Optional<T>.orThrow(errorCode: ErrorCode, message: String): T =
    orElseThrow { ApiException(errorCode, message) }

fun <T> Optional<T>.orThrow(errorCode: ErrorCode, lazyMessage: () -> String): T =
    orElseThrow { ApiException(errorCode, lazyMessage()) }
