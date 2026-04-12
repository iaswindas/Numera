package com.numera.integration.api

import com.numera.integration.application.ConflictResolution
import com.numera.integration.application.IntegrationSyncService
import com.numera.integration.domain.ExternalSystem
import com.numera.integration.domain.ExternalSystemRepository
import com.numera.integration.domain.ExternalSystemType
import com.numera.integration.domain.SyncRecord
import com.numera.integration.spi.CanonicalSpreadPayload
import com.numera.integration.spi.ExternalAdapter
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import com.numera.shared.security.TenantContext
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────

data class CreateExternalSystemRequest(
    @field:NotBlank val name: String,
    val type: ExternalSystemType,
    @field:NotBlank val baseUrl: String,
    val apiKey: String? = null,
    val configJson: String? = null,
)

data class PullRequest(
    @field:NotBlank val externalRef: String,
)

data class ConflictResolutionRequest(
    val resolution: ConflictResolution,
)

data class ExternalSystemResponse(
    val id: UUID?,
    val name: String,
    val type: ExternalSystemType,
    val baseUrl: String,
    val active: Boolean,
    val hasApiKey: Boolean,
)

data class ConnectionTestResponse(
    val systemId: UUID?,
    val systemName: String,
    val connected: Boolean,
)

// ── Controller ───────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/integrations")
class IntegrationController(
    private val externalSystemRepo: ExternalSystemRepository,
    private val syncService: IntegrationSyncService,
    private val adapters: List<ExternalAdapter>,
) {
    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    // ── External systems ─────────────────────────────────────────────

    @GetMapping("/systems")
    fun listSystems(): List<ExternalSystemResponse> =
        externalSystemRepo.findByTenantId(resolvedTenantId()).map { it.toResponse() }

    @PostMapping("/systems")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSystem(@Valid @RequestBody request: CreateExternalSystemRequest): ExternalSystemResponse {
        val system = ExternalSystem().apply {
            tenantId = resolvedTenantId()
            name = request.name
            type = request.type
            baseUrl = request.baseUrl
            apiKey = request.apiKey
            configJson = request.configJson
        }
        return externalSystemRepo.save(system).toResponse()
    }

    // ── Sync operations ──────────────────────────────────────────────

    @PostMapping("/sync/push/{systemId}/{spreadItemId}")
    fun pushSpread(
        @PathVariable systemId: UUID,
        @PathVariable spreadItemId: UUID,
    ): SyncRecord = syncService.pushSpread(resolvedTenantId(), systemId, spreadItemId)

    @PostMapping("/sync/pull/{systemId}")
    fun pullMetadata(
        @PathVariable systemId: UUID,
        @Valid @RequestBody request: PullRequest,
    ): CanonicalSpreadPayload? = syncService.pullMetadata(resolvedTenantId(), systemId, request.externalRef)

    @GetMapping("/sync/records")
    fun listSyncRecords(): List<SyncRecord> =
        syncService.listSyncRecords(resolvedTenantId())

    @PostMapping("/sync/retry/{syncRecordId}")
    fun retrySingle(@PathVariable syncRecordId: UUID): SyncRecord =
        syncService.retrySingle(syncRecordId)

    @PostMapping("/sync/resolve/{syncRecordId}")
    fun resolveConflict(
        @PathVariable syncRecordId: UUID,
        @Valid @RequestBody request: ConflictResolutionRequest,
    ): SyncRecord = syncService.resolveConflict(syncRecordId, request.resolution)

    // ── Connection test ──────────────────────────────────────────────

    @PostMapping("/test/{systemId}")
    fun testConnection(@PathVariable systemId: UUID): ConnectionTestResponse {
        val system = externalSystemRepo.findById(systemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "External system not found: $systemId") }

        val adapter = adapters.firstOrNull { it.systemType() == system.type }
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "No adapter for type ${system.type}")

        val connected = adapter.validateConnection(system)
        return ConnectionTestResponse(systemId = system.id, systemName = system.name, connected = connected)
    }

    private fun ExternalSystem.toResponse() = ExternalSystemResponse(
        id = id,
        name = name,
        type = type,
        baseUrl = baseUrl,
        active = active,
        hasApiKey = !apiKey.isNullOrBlank(),
    )
}
