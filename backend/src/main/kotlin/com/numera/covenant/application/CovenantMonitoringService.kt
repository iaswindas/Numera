package com.numera.covenant.application

import com.numera.covenant.domain.CovenantDocument
import com.numera.covenant.domain.CovenantFrequency
import com.numera.covenant.domain.CovenantMonitoringItem
import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.domain.CovenantType
import com.numera.covenant.dto.CheckerDecisionRequest
import com.numera.covenant.dto.CovenantDocumentResponse
import com.numera.covenant.dto.CovenantMonitoringItemResponse
import com.numera.covenant.dto.ManualValueRequest
import com.numera.covenant.events.CovenantBreachedEvent
import com.numera.covenant.events.CovenantStatusChangedEvent
import com.numera.covenant.infrastructure.CovenantDocumentRepository
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.CovenantRepository
import com.numera.document.infrastructure.MinioStorageClient
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class CovenantMonitoringService(
    private val covenantRepository: CovenantRepository,
    private val monitoringRepository: CovenantMonitoringRepository,
    private val documentRepository: CovenantDocumentRepository,
    private val storageClient: MinioStorageClient,
    private val auditService: AuditService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    // ── Queries ───────────────────────────────────────────────────────────

    fun listByTenant(): List<CovenantMonitoringItemResponse> =
        monitoringRepository.findByTenantId(tenantId).map { it.toResponse() }

    fun listByCovenant(covenantId: UUID): List<CovenantMonitoringItemResponse> =
        monitoringRepository.findByCovenantId(covenantId).map { it.toResponse() }

    fun listPending(): List<CovenantMonitoringItemResponse> =
        monitoringRepository.findByTenantIdAndStatusIn(
            tenantId,
            listOf(CovenantStatus.DUE, CovenantStatus.OVERDUE, CovenantStatus.REJECTED),
        ).map { it.toResponse() }

    fun listBreached(): List<CovenantMonitoringItemResponse> =
        monitoringRepository.findByTenantIdAndStatus(tenantId, CovenantStatus.BREACHED).map { it.toResponse() }

    fun get(id: UUID): CovenantMonitoringItemResponse =
        monitoringRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: $id") }
            .toResponse()

    // ── Generation ────────────────────────────────────────────────────────

    /**
     * Generate monitoring items for a covenant from [fromDate] to [toDate].
     * Skips periods that already have existing items.
     */
    @Transactional
    fun generateMonitoringItems(covenantId: UUID, fromDate: LocalDate, toDate: LocalDate): List<CovenantMonitoringItemResponse> {
        val covenant = covenantRepository.findById(covenantId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Covenant not found: $covenantId") }

        val periods = buildPeriods(covenant.frequency, covenant.covenantCustomer.financialYearEnd, fromDate, toDate)
        val existing = monitoringRepository.findByCovenantId(covenantId)
            .map { it.periodEnd }
            .toSet()

        val newItems = periods.filterNot { (_, end, _) -> end in existing }.map { (start, end, due) ->
            CovenantMonitoringItem().also {
                it.tenantId = tenantId
                it.covenant = covenant
                it.periodStart = start
                it.periodEnd = end
                it.dueDate = due
                it.status = CovenantStatus.DUE
            }
        }

        return monitoringRepository.saveAll(newItems).map { it.toResponse() }
    }

    // ── Manual value override ─────────────────────────────────────────────

    @Transactional
    fun setManualValue(id: UUID, request: ManualValueRequest): CovenantMonitoringItemResponse {
        val item = monitoringRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: $id") }

        item.manualValue = request.value
        item.manualValueJustification = request.justification
        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "MANUAL_VALUE_SET",
            action = AuditAction.OVERRIDE,
            entityType = "covenant_monitoring_item",
            entityId = id.toString(),
        )
        return monitoringRepository.save(item).toResponse()
    }

    // ── Non-financial document workflow ───────────────────────────────────

    @Transactional
    fun uploadDocument(itemId: UUID, file: MultipartFile, actorId: UUID): CovenantDocumentResponse {
        val item = monitoringRepository.findById(itemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: $itemId") }

        if (item.covenant.covenantType != CovenantType.NON_FINANCIAL) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Documents can only be uploaded for non-financial covenants")
        }

        val storageKey = storageClient.upload(file)

        val doc = CovenantDocument().also {
            it.monitoringItem = item
            it.fileName = file.originalFilename ?: file.name
            it.storageKey = storageKey
            it.fileSize = file.size
            it.contentType = file.contentType
            it.uploadedBy = actorId
            it.uploadedAt = Instant.now()
        }
        item.documents.add(doc)

        // Auto-transition to SUBMITTED when documents are uploaded
        if (item.status == CovenantStatus.DUE || item.status == CovenantStatus.REJECTED) {
            transitionStatus(item, CovenantStatus.SUBMITTED, actorId)
        }

        monitoringRepository.save(item)
        return doc.toDocResponse()
    }

    @Transactional
    fun checkerDecision(itemId: UUID, request: CheckerDecisionRequest, actorId: UUID): CovenantMonitoringItemResponse {
        val item = monitoringRepository.findById(itemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: $itemId") }

        if (item.status != CovenantStatus.SUBMITTED) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Item must be in SUBMITTED state for checker decision")
        }

        val newStatus = when (request.decision.uppercase()) {
            "APPROVE" -> CovenantStatus.APPROVED
            "REJECT" -> CovenantStatus.REJECTED
            else -> throw ApiException(ErrorCode.VALIDATION_ERROR, "Unknown decision: ${request.decision}")
        }

        item.approvedBy = actorId
        item.approvedAt = Instant.now()
        item.checkerComments = request.comments
        transitionStatus(item, newStatus, actorId)

        return monitoringRepository.save(item).toResponse()
    }

    // ── Financial covenant status ──────────────────────────────────────────

    @Transactional
    fun triggerAction(itemId: UUID, actorId: UUID): CovenantMonitoringItemResponse {
        val item = monitoringRepository.findById(itemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Monitoring item not found: $itemId") }

        if (item.status != CovenantStatus.BREACHED) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Can only trigger action on BREACHED items")
        }

        transitionStatus(item, CovenantStatus.TRIGGER_ACTION, actorId)
        return monitoringRepository.save(item).toResponse()
    }

    // ── Overdue sweep (called by scheduler) ───────────────────────────────

    @Transactional
    fun markOverdue(): Int {
        val items = monitoringRepository.findOverdue(tenantId, LocalDate.now())
        items.forEach { transitionStatus(it, CovenantStatus.OVERDUE, null) }
        monitoringRepository.saveAll(items)
        return items.size
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun transitionStatus(item: CovenantMonitoringItem, newStatus: CovenantStatus, actorId: UUID?) {
        val previous = item.status
        item.status = newStatus
        if (previous == newStatus) return

        eventPublisher.publishEvent(
            CovenantStatusChangedEvent(
                tenantId = tenantId,
                monitoringItemId = item.id!!,
                covenantId = item.covenant.id!!,
                previousStatus = previous.name,
                newStatus = newStatus.name,
                actorId = actorId,
            )
        )

        if (newStatus == CovenantStatus.BREACHED) {
            eventPublisher.publishEvent(
                CovenantBreachedEvent(
                    tenantId = tenantId,
                    covenantId = item.covenant.id!!,
                    monitoringItemId = item.id!!,
                    covenantName = item.covenant.name,
                    customerName = item.covenant.covenantCustomer.customer.name,
                    periodEnd = item.periodEnd,
                    calculatedValue = item.calculatedValue,
                    thresholdValue = item.covenant.thresholdValue,
                )
            )
        }

        auditService.record(
            tenantId = tenantId.toString(),
            eventType = "MONITORING_STATUS_CHANGED",
            action = AuditAction.UPDATE,
            entityType = "covenant_monitoring_item",
            entityId = item.id.toString(),
            diffJson = """{"from":"$previous","to":"$newStatus"}""",
        )
    }

    /**
     * Build non-overlapping (start, end, due) triples based on frequency and financial year end.
     * Due date is set to 30 days after period end as a sensible default.
     */
    private fun buildPeriods(
        frequency: CovenantFrequency,
        financialYearEnd: LocalDate?,
        from: LocalDate,
        to: LocalDate,
    ): List<Triple<LocalDate, LocalDate, LocalDate>> {
        val result = mutableListOf<Triple<LocalDate, LocalDate, LocalDate>>()
        val monthsPerPeriod = when (frequency) {
            CovenantFrequency.MONTHLY -> 1L
            CovenantFrequency.QUARTERLY -> 3L
            CovenantFrequency.SEMI_ANNUALLY -> 6L
            CovenantFrequency.ANNUALLY, CovenantFrequency.FY_TO_DATE -> 12L
        }
        var periodEnd = from.plusMonths(monthsPerPeriod).minusDays(1)
        var periodStart = from
        while (periodEnd <= to) {
            val due = periodEnd.plusDays(30)
            result.add(Triple(periodStart, periodEnd, due))
            periodStart = periodEnd.plusDays(1)
            periodEnd = periodStart.plusMonths(monthsPerPeriod).minusDays(1)
        }
        return result
    }

    // ── Response mappers ──────────────────────────────────────────────────

    private fun CovenantMonitoringItem.toResponse() = CovenantMonitoringItemResponse(
        id = id!!,
        covenantId = covenant.id!!,
        covenantName = covenant.name,
        covenantType = covenant.covenantType.name,
        periodStart = periodStart,
        periodEnd = periodEnd,
        dueDate = dueDate,
        status = status.name,
        calculatedValue = calculatedValue,
        manualValue = manualValue,
        manualValueJustification = manualValueJustification,
        submittedBy = submittedBy,
        submittedAt = submittedAt,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        checkerComments = checkerComments,
        breachProbability = breachProbability,
        documentCount = documents.size,
        updatedAt = updatedAt,
    )

    private fun CovenantDocument.toDocResponse() = CovenantDocumentResponse(
        id = id!!,
        fileName = fileName,
        fileSize = fileSize,
        contentType = contentType,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
    )
}
