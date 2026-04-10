package com.numera.document.domain

enum class DocumentStatus {
    UPLOADED,
    PROCESSING,
    OCR_COMPLETE,
    TABLES_DETECTED,
    ZONES_CLASSIFIED,
    READY,
    ERROR
}