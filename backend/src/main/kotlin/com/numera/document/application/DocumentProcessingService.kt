package com.numera.document.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.auth.UserLookupFacade
import com.numera.customer.CustomerQueryPort
import com.numera.document.domain.DetectedZone
import com.numera.document.domain.Document
import com.numera.document.domain.DocumentStatus
import com.numera.document.dto.DocumentResponse
import com.numera.document.dto.DocumentStatusResponse
import com.numera.document.dto.ZoneBoundingBox
import com.numera.document.dto.DocumentUploadResponse
import com.numera.document.dto.ZoneResponse
import com.numera.document.dto.ZoneUpdateRequest
import com.numera.document.dto.ZonesResponse
import com.numera.document.events.DocumentProcessedEvent
import com.numera.document.infrastructure.DetectedZoneRepository
import com.numera.document.infrastructure.DocumentRepository
import com.numera.document.infrastructure.MinioStorageClient
import com.numera.document.infrastructure.MlServiceClient
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.infrastructure.DomainEventPublisher
import com.numera.shared.security.CurrentUserProvider
import com.numera.shared.security.TenantContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor

@Service
class DocumentProcessingService(
    private val customerQueryPort: CustomerQueryPort,
    private val userLookupFacade: UserLookupFacade,
    private val documentRepository: DocumentRepository,
    private val zoneRepository: DetectedZoneRepository,
    private val minioStorageClient: MinioStorageClient,
    private val mlServiceClient: MlServiceClient,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val publisher: DomainEventPublisher,
    private val currentUserProvider: CurrentUserProvider,
    private val taskExecutor: Executor,
    private val transactionTemplate: TransactionTemplate,
) {
    @Transactional(readOnly = true)
    fun list(customerId: UUID?, uploadedBy: String?, status: DocumentStatus?, pageable: Pageable? = null): Page<DocumentResponse> {
        val tenantId = TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT
        val effectiveUploadedBy = resolveUploadedByFilter(uploadedBy)

        var spec = Specification.where<Document> { root, _, cb ->
            cb.equal(root.get<UUID>("tenantId"), tenantId)
        }

        if (customerId != null) {
            spec = spec.and { root, _, cb ->
                cb.equal(root.get<Any>("customer").get<UUID>("id"), customerId)
            }
        }

        if (effectiveUploadedBy != null) {
            spec = spec.and { root, _, cb ->
                cb.equal(root.get<String>("uploadedBy"), effectiveUploadedBy)
            }
        }

        if (status != null) {
            spec = spec.and { root, _, cb ->
                cb.equal(root.get<DocumentStatus>("status"), status)
            }
        }

        val docs = documentRepository.findAll(spec)
        val responses = docs.sortedByDescending { it.createdAt }.map { it.toResponse() }
        return if (pageable != null) {
            val start = pageable.offset.toInt().coerceAtMost(responses.size)
            val end = (start + pageable.pageSize).coerceAtMost(responses.size)
            PageImpl(responses.subList(start, end), pageable, responses.size.toLong())
        } else {
            PageImpl(responses)
        }
    }

    @Transactional
    fun upload(customerId: UUID, file: MultipartFile, language: String, password: String? = null): DocumentUploadResponse {
        val customer = customerQueryPort.findEntityById(customerId)
        val storagePath = minioStorageClient.upload(file)
        val currentUser = currentUserProvider.email()
            ?.let { userLookupFacade.findUploadedByInfo(it) }
            ?: userLookupFacade.findUploadedByInfo("admin@numera.ai")
        val uploadedBy = currentUser?.id?.toString() ?: "system"
        val uploadedByName = currentUser?.fullName ?: "System"

        val safeFilename = sanitizeFilename(file.originalFilename ?: "document.pdf")
        validateFileExtension(safeFilename, file.contentType)

        val saved = documentRepository.save(Document().also {
            it.tenantId = customer.tenantId
            it.customer = customer
            it.fileName = safeFilename
            it.originalFilename = safeFilename
            it.storagePath = storagePath
            it.fileSize = file.size
            it.contentType = file.contentType ?: "application/pdf"
            it.language = language
            it.status = DocumentStatus.UPLOADED
            it.uploadedBy = uploadedBy
            it.uploadedByName = uploadedByName
        })

        auditService.record(
            tenantId = saved.tenantId.toString(),
            eventType = "DOCUMENT_UPLOADED",
            action = AuditAction.CREATE,
            entityType = "document",
            entityId = saved.id.toString(),
            parentEntityType = "customer",
            parentEntityId = customer.id.toString(),
        )

        queueProcessing(saved.id!!, password)

        return DocumentUploadResponse(
            documentId = saved.id.toString(),
            filename = saved.fileName,
            status = saved.status,
            message = "Document uploaded. Processing started.",
        )
    }

    fun process(documentId: UUID, password: String? = null): DocumentStatusResponse = transactionTemplate.execute {
        processInternal(documentId, password)
    } ?: throw ApiException(ErrorCode.INTERNAL_ERROR, "Unable to process document")

    private fun queueProcessing(documentId: UUID, password: String?) {
        taskExecutor.execute {
            transactionTemplate.executeWithoutResult {
                processInternal(documentId, password)
            }
        }
    }

    private fun processInternal(documentId: UUID, password: String? = null): DocumentStatusResponse {
        val document = documentRepository.findById(documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }

        return runCatching {
            document.status = DocumentStatus.PROCESSING

            val ocr = mlServiceClient.extractText(
                documentId = documentId.toString(),
                storagePath = document.storagePath,
                language = document.language,
                password = password,
            )
            document.status = DocumentStatus.OCR_COMPLETE
            document.totalPages = ocr.total_pages
            document.backendUsed = ocr.backend
            document.pdfType = ocr.pdf_type

            val tables = mlServiceClient.detectTables(documentId.toString(), document.storagePath, password)
            document.status = DocumentStatus.TABLES_DETECTED

            val zones = mlServiceClient.classifyZones(documentId.toString(), tables.tables)
            document.status = DocumentStatus.ZONES_CLASSIFIED
            val tableMetadata = tables.tables.associateBy { it["table_id"]?.toString() ?: "" }

            zoneRepository.deleteAll(zoneRepository.findByDocumentId(documentId))
            zoneRepository.saveAll(zones.zones.map {
                val table = tableMetadata[it.table_id].orEmpty()
                DetectedZone().also { zone ->
                    zone.document = document
                    zone.tableId = it.table_id
                    zone.zoneType = it.zone_type
                    zone.zoneLabel = it.zone_label
                    zone.confidence = it.confidence.toDouble()
                    zone.pageNumber = (table["page"] as? Number)?.toInt()
                    zone.metadataJson = objectMapper.writeValueAsString(
                        mapOf(
                            "classificationMethod" to it.classification_method,
                            "detectedPeriods" to it.detected_periods,
                            "detectedCurrency" to it.detected_currency,
                            "detectedUnit" to it.detected_unit,
                            "rowCount" to (table["row_count"] as? Number)?.toInt(),
                        )
                    )
                }
            })

            document.status = DocumentStatus.READY
            document.processingTimeMs = ocr.processing_time_ms + tables.processing_time_ms + zones.processing_time_ms
            publisher.publish(DocumentProcessedEvent(documentId, document.tenantId))
            auditService.record(
                tenantId = document.tenantId.toString(),
                eventType = "DOCUMENT_PROCESSED",
                action = AuditAction.PROCESS,
                entityType = "document",
                entityId = document.id.toString(),
            )
            DocumentStatusResponse(document.id.toString(), document.status, null)
        }.getOrElse { ex ->
            document.status = DocumentStatus.ERROR
            document.errorMessage = ex.message
            DocumentStatusResponse(document.id.toString(), document.status, ex.message)
        }
    }

    @Transactional(readOnly = true)
    fun getDocument(documentId: UUID): DocumentResponse {
        val document = documentRepository.findById(documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }
        return document.toResponse()
    }

    @Transactional(readOnly = true)
    fun getStatus(documentId: UUID): DocumentStatusResponse {
        val document = documentRepository.findById(documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }
        return DocumentStatusResponse(document.id.toString(), document.status, document.errorMessage)
    }

    @Transactional(readOnly = true)
    fun zones(documentId: UUID): ZonesResponse =
        ZonesResponse(
            documentId = documentId.toString(),
            zones = zoneRepository.findByDocumentId(documentId).map { it.toResponse() }
        )

    @Transactional
    fun updateZone(documentId: UUID, zoneId: UUID, request: ZoneUpdateRequest): ZoneResponse {
        val zone = zoneRepository.findById(zoneId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Zone not found") }
        if (zone.document.id != documentId) {
            throw ApiException(ErrorCode.NOT_FOUND, "Zone does not belong to document")
        }

        zone.zoneType = request.zoneType
        zone.zoneLabel = request.zoneLabel
        val saved = zoneRepository.save(zone)

        auditService.record(
            tenantId = zone.document.tenantId.toString(),
            eventType = "ZONE_UPDATED",
            action = AuditAction.UPDATE,
            entityType = "detected_zone",
            entityId = saved.id.toString(),
            parentEntityType = "document",
            parentEntityId = documentId.toString(),
        )

        return ZoneResponse(
            id = saved.id.toString(),
            pageNumber = saved.pageNumber,
            zoneType = saved.zoneType,
            zoneLabel = saved.zoneLabel,
            boundingBox = metadataBoundingBox(saved),
            confidenceScore = saved.confidence,
            classificationMethod = metadataValue(saved, "classificationMethod"),
            detectedPeriods = metadataList(saved, "detectedPeriods"),
            detectedCurrency = metadataValue(saved, "detectedCurrency"),
            detectedUnit = metadataValue(saved, "detectedUnit"),
            status = "DETECTED",
            rowCount = metadataValue(saved, "rowCount")?.toIntOrNull(),
        )
    }

    @Transactional
    fun delete(documentId: UUID) {
        val document = documentRepository.findById(documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }
        zoneRepository.deleteAll(zoneRepository.findByDocumentId(documentId))
        documentRepository.delete(document)
    }

    @Transactional(readOnly = true)
    fun getDocumentEntity(documentId: UUID): Document =
        documentRepository.findById(documentId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Document not found") }

    fun downloadFile(storagePath: String): java.io.InputStream =
        minioStorageClient.download(storagePath)

    private fun Document.toResponse() = DocumentResponse(
        id = id.toString(),
        filename = fileName,
        originalFilename = originalFilename,
        fileType = contentType.substringAfterLast("/").uppercase(Locale.getDefault()),
        fileSize = fileSize,
        language = language,
        processingStatus = status,
        pdfType = pdfType,
        backendUsed = backendUsed,
        totalPages = totalPages,
        processingTimeMs = processingTimeMs,
        uploadedBy = uploadedBy,
        uploadedByName = uploadedByName,
        zonesDetected = zoneRepository.findByDocumentId(id!!).size,
        createdAt = createdAt.toString(),
    )

    private fun DetectedZone.toResponse(): ZoneResponse = ZoneResponse(
        id = id.toString(),
        pageNumber = pageNumber,
        zoneType = zoneType,
        zoneLabel = zoneLabel,
        boundingBox = metadataBoundingBox(this),
        confidenceScore = confidence,
        classificationMethod = metadataValue(this, "classificationMethod"),
        detectedPeriods = metadataList(this, "detectedPeriods"),
        detectedCurrency = metadataValue(this, "detectedCurrency"),
        detectedUnit = metadataValue(this, "detectedUnit"),
        status = "DETECTED",
        rowCount = metadataValue(this, "rowCount")?.toIntOrNull(),
    )

    private fun metadataValue(zone: DetectedZone, key: String): String? =
        readMetadata(zone)[key]?.toString()

    private fun metadataList(zone: DetectedZone, key: String): List<String> =
        (readMetadata(zone)[key] as? Collection<*>)?.map { it.toString() } ?: emptyList()

    private fun readMetadata(zone: DetectedZone): Map<String, Any?> =
        if (zone.metadataJson.isNullOrBlank()) emptyMap() else objectMapper.readValue(zone.metadataJson, Map::class.java) as Map<String, Any?>

    private val ALLOWED_EXTENSIONS = mapOf(
        "application/pdf" to listOf(".pdf"),
        "application/vnd.ms-excel" to listOf(".xls"),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to listOf(".xlsx"),
        "image/png" to listOf(".png"),
        "image/jpeg" to listOf(".jpg", ".jpeg"),
        "image/tiff" to listOf(".tif", ".tiff"),
        "application/msword" to listOf(".doc"),
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to listOf(".docx"),
    )

    private fun sanitizeFilename(name: String): String {
        val basename = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = basename.replace(Regex("[^a-zA-Z0-9._()-]"), "_")
            .replace(Regex("\\.{2,}"), ".")
            .replace(Regex("^[.]"), "_")
        val ext = cleaned.substringAfterLast('.', "")
        val stem = cleaned.substringBeforeLast('.', cleaned)
        val safeStem = stem.take(200)
        return if (ext.isNotBlank()) "$safeStem.$ext" else cleaned.ifBlank { "document.pdf" }
    }

    private fun validateFileExtension(filename: String, contentType: String?) {
        if (contentType == null) return
        val allowedExts = ALLOWED_EXTENSIONS[contentType] ?: return
        val ext = ".${filename.substringAfterLast('.', "").lowercase()}"
        if (ext !in allowedExts) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "File extension '$ext' does not match content type '$contentType'. Allowed: $allowedExts")
        }
    }

    private fun resolveUploadedByFilter(uploadedBy: String?): String? {
        val value = uploadedBy?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (value.equals("ME", ignoreCase = true) || value.equals("MINE", ignoreCase = true)) {
            val currentUser = currentUserProvider.email()?.let { userLookupFacade.findUploadedByInfo(it) }
            return currentUser?.id?.toString()
        }
        return value
    }

    private fun metadataBoundingBox(zone: DetectedZone): ZoneBoundingBox? {
        val metadata = readMetadata(zone)

        val mapBox = metadata["boundingBox"] as? Map<*, *>
        if (mapBox != null) {
            val x = mapBox["x"].asDoubleOrNull()
            val y = mapBox["y"].asDoubleOrNull()
            val width = mapBox["width"].asDoubleOrNull()
            val height = mapBox["height"].asDoubleOrNull()
            if (x != null && y != null && width != null && height != null) {
                return ZoneBoundingBox(x, y, width, height)
            }
        }

        val listBox = metadata["bbox"] as? Collection<*>
        if (listBox != null) {
            val parts = listBox.mapNotNull { it.asDoubleOrNull() }
            if (parts.size >= 4) {
                return ZoneBoundingBox(parts[0], parts[1], parts[2], parts[3])
            }
        }

        return null
    }

    private fun Any?.asDoubleOrNull(): Double? = when (this) {
        is Number -> this.toDouble()
        is String -> this.toDoubleOrNull()
        else -> null
    }
}
