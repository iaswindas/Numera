package com.numera.spreading.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.shared.audit.AuditAction
import com.numera.shared.audit.AuditService
import com.numera.spreading.domain.SpreadStatus
import com.numera.spreading.domain.SpreadValue
import com.numera.spreading.domain.SpreadVersion
import com.numera.spreading.dto.DiffChangeResponse
import com.numera.spreading.dto.DiffResponse
import com.numera.spreading.dto.VersionEntryResponse
import com.numera.spreading.dto.VersionHistoryResponse
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import com.numera.spreading.infrastructure.SpreadVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class SpreadVersionService(
    private val versionRepository: SpreadVersionRepository,
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    data class SnapshotValue(
        val itemCode: String,
        val label: String,
        val mappedValue: BigDecimal?,
    )

    @Transactional
    fun createSnapshot(spreadItemId: UUID, action: String, comments: String?, createdBy: String, cellsChanged: Int): SpreadVersion {
        val spreadItem = spreadItemRepository.findById(spreadItemId).orElseThrow()
        val values = spreadValueRepository.findBySpreadItemId(spreadItemId).sortedBy { it.itemCode }.map {
            SnapshotValue(it.itemCode, it.label, it.mappedValue)
        }
        val nextVersion = spreadItem.currentVersion + 1

        val saved = versionRepository.save(SpreadVersion().also {
            it.spreadItem = spreadItem
            it.versionNumber = nextVersion
            it.action = action
            it.comments = comments
            it.snapshotJson = objectMapper.writeValueAsString(values)
            it.cellsChanged = cellsChanged
            it.createdBy = createdBy
        })

        spreadItem.currentVersion = nextVersion
        spreadItemRepository.save(spreadItem)

        return saved
    }

    fun history(spreadItemId: UUID): VersionHistoryResponse {
        val versions = versionRepository.findBySpreadItemIdOrderByVersionNumberDesc(spreadItemId)
        return VersionHistoryResponse(
            spreadItemId = spreadItemId.toString(),
            versions = versions.map {
                VersionEntryResponse(
                    versionNumber = it.versionNumber,
                    action = it.action,
                    comments = it.comments,
                    cellsChanged = it.cellsChanged,
                    createdBy = it.createdBy,
                    createdAt = it.createdAt.toString(),
                )
            }
        )
    }

    fun diff(spreadItemId: UUID, fromVersion: Int, toVersion: Int): DiffResponse {
        val left = versionRepository.findBySpreadItemIdAndVersionNumber(spreadItemId, fromVersion) ?: error("Version not found")
        val right = versionRepository.findBySpreadItemIdAndVersionNumber(spreadItemId, toVersion) ?: error("Version not found")

        val typeRef = object : TypeReference<List<SnapshotValue>>() {}
        val oldVals = objectMapper.readValue(left.snapshotJson, typeRef).associateBy { it.itemCode }
        val newVals = objectMapper.readValue(right.snapshotJson, typeRef).associateBy { it.itemCode }

        val keys = (oldVals.keys + newVals.keys).toSortedSet()
        val changes = mutableListOf<DiffChangeResponse>()

        for (key in keys) {
            val old = oldVals[key]
            val new = newVals[key]
            when {
                old == null && new != null -> changes += DiffChangeResponse(key, new.label, null, new.mappedValue, "ADDED", right.createdBy)
                old != null && new == null -> changes += DiffChangeResponse(key, old.label, old.mappedValue, null, "REMOVED", right.createdBy)
                old != null && new != null && old.mappedValue != new.mappedValue -> {
                    changes += DiffChangeResponse(key, new.label, old.mappedValue, new.mappedValue, "MODIFIED", right.createdBy)
                }
            }
        }

        return DiffResponse(
            fromVersion = fromVersion,
            toVersion = toVersion,
            changes = changes,
            totalAdded = changes.count { it.changeType == "ADDED" },
            totalModified = changes.count { it.changeType == "MODIFIED" },
            totalRemoved = changes.count { it.changeType == "REMOVED" },
        )
    }

    @Transactional
    fun rollback(spreadItemId: UUID, version: Int, comments: String): Map<String, Any> {
        val target = versionRepository.findBySpreadItemIdAndVersionNumber(spreadItemId, version) ?: error("Version not found")
        val spreadItem = spreadItemRepository.findById(spreadItemId).orElseThrow()
        val typeRef = object : TypeReference<List<SnapshotValue>>() {}
        val snapshot = objectMapper.readValue(target.snapshotJson, typeRef).associateBy { it.itemCode }

        val values = spreadValueRepository.findBySpreadItemId(spreadItemId)
        values.forEach {
            it.mappedValue = snapshot[it.itemCode]?.mappedValue
        }
        spreadValueRepository.saveAll(values)

        spreadItem.status = SpreadStatus.DRAFT
        spreadItemRepository.save(spreadItem)

        createSnapshot(spreadItemId, "ROLLBACK", comments, "Demo Analyst", values.size)

        auditService.record(
            tenantId = spreadItem.tenantId.toString(),
            eventType = "SPREAD_ROLLED_BACK",
            action = AuditAction.ROLLBACK,
            entityType = "spread_item",
            entityId = spreadItemId.toString(),
        )

        return mapOf(
            "status" to spreadItem.status.name,
            "currentVersion" to spreadItem.currentVersion,
            "restoredFromVersion" to version,
        )
    }
}
