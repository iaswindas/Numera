package com.numera.admin.application

import com.numera.admin.domain.ManagedZone
import com.numera.admin.infrastructure.ManagedZoneRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.security.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ZoneManagementService(
    private val zoneRepository: ManagedZoneRepository,
) {
    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun findAll(): List<Map<String, Any?>> =
        zoneRepository.findByTenantIdOrderBySortOrderAsc(resolvedTenantId()).map { toMap(it) }

    fun findActive(): List<Map<String, Any?>> =
        zoneRepository.findByTenantIdAndIsActiveTrue(resolvedTenantId()).map { toMap(it) }

    @Transactional
    fun create(body: Map<String, Any?>): Map<String, Any?> {
        val zone = ManagedZone().also {
            it.tenantId = resolvedTenantId()
            it.name = body["name"] as? String ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "name required")
            it.code = body["code"] as? String ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "code required")
            it.color = body["color"] as? String ?: "#6366f1"
            it.description = body["description"] as? String
            it.sortOrder = (body["sortOrder"] as? Number)?.toInt() ?: 0
            it.isActive = body["isActive"] as? Boolean ?: true
        }
        return toMap(zoneRepository.save(zone))
    }

    @Transactional
    fun update(id: UUID, body: Map<String, Any?>): Map<String, Any?> {
        val zone = zoneRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Zone not found") }
        body["name"]?.let { zone.name = it as String }
        body["code"]?.let { zone.code = it as String }
        body["color"]?.let { zone.color = it as String }
        body["description"]?.let { zone.description = it as String }
        body["sortOrder"]?.let { zone.sortOrder = (it as Number).toInt() }
        body["isActive"]?.let { zone.isActive = it as Boolean }
        return toMap(zoneRepository.save(zone))
    }

    @Transactional
    fun toggleActive(id: UUID, active: Boolean): Map<String, Any?> {
        val zone = zoneRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Zone not found") }
        zone.isActive = active
        return toMap(zoneRepository.save(zone))
    }

    @Transactional
    fun delete(id: UUID) {
        zoneRepository.deleteById(id)
    }

    private fun toMap(z: ManagedZone): Map<String, Any?> = mapOf(
        "id" to z.id.toString(),
        "name" to z.name,
        "code" to z.code,
        "color" to z.color,
        "description" to z.description,
        "sortOrder" to z.sortOrder,
        "isActive" to z.isActive,
        "createdAt" to z.createdAt.toString(),
        "updatedAt" to z.updatedAt.toString(),
    )
}
