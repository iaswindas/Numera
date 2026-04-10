package com.numera.document.infrastructure

import com.numera.document.domain.DetectedZone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DetectedZoneRepository : JpaRepository<DetectedZone, UUID> {
    fun findByDocumentId(documentId: UUID): List<DetectedZone>
}