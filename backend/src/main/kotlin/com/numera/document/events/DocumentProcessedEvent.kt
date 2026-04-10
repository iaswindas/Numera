package com.numera.document.events

import java.util.UUID

data class DocumentProcessedEvent(
    val documentId: UUID,
    val tenantId: UUID,
)