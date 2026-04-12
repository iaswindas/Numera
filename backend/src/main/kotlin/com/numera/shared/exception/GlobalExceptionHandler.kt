package com.numera.shared.exception

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {
        val status = when (ex.errorCode) {
            ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN
            ErrorCode.CONFLICT -> HttpStatus.CONFLICT
            ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status)
            .body(ErrorResponse(ex.errorCode, ex.message, ex.validations, ex.unmappedRequired))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.NOT_FOUND, ex.message ?: "Entity not found"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR, message))
    }

    @ExceptionHandler(Exception::class)
    fun handleFallback(ex: Exception): ResponseEntity<ErrorResponse> {
        // V-13: Log full exception server-side, return generic message to client
        log.error("Unhandled exception: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again later."))
    }
}
