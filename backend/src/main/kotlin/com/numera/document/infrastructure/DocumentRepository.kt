package com.numera.document.infrastructure

import com.numera.document.domain.Document
import com.numera.document.domain.DocumentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByCustomerId(customerId: UUID): List<Document>
    fun findByStatus(status: DocumentStatus): List<Document>
}