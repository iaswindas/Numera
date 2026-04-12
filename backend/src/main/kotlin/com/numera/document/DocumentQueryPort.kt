package com.numera.document

import com.numera.document.domain.Document
import com.numera.document.infrastructure.DocumentRepository
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for document look-up exposed by the document module.
 *
 * Lives in the document ROOT package so that the spreading module can look up Document entities and
 * counts without crossing into document's private infrastructure package.
 *
 * The [Document] type is accessible via the document.domain @NamedInterface.
 */
@Service
class DocumentQueryPort(
    private val documentRepository: DocumentRepository,
) {
    /** Loads the Document entity by ID, throwing NOT_FOUND if absent. */
    fun findEntityById(id: UUID): Document =
        documentRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }

    /** Returns the total number of documents in the store. */
    fun count(): Long = documentRepository.count()

    /** Returns all documents — used for cross-module aggregation (e.g. dashboard stats). */
    fun findAll(): List<Document> = documentRepository.findAll()
}
