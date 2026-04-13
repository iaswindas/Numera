package com.numera.shared.config

import com.numera.shared.infrastructure.TenantFeatureFlagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class FeatureFlagService(
    private val properties: NumeraProperties,
    private val tenantFlagRepo: TenantFeatureFlagRepository,
) {
    private val log = LoggerFactory.getLogger(FeatureFlagService::class.java)

    private val globalFlags: Map<String, Boolean> by lazy { buildGlobalFlags() }

    /** Cache of tenant overrides: tenantId -> (flagName -> enabled). */
    private val tenantCache = ConcurrentHashMap<UUID, Map<String, Boolean>>()

    /** Cache of tenant flag values: tenantId -> (flagName -> value). */
    private val tenantValueCache = ConcurrentHashMap<UUID, Map<String, String?>>()

    fun isEnabled(flag: String, tenantId: String? = null): Boolean {
        if (tenantId != null) {
            val tid = UUID.fromString(tenantId)
            val overrides = tenantCache.computeIfAbsent(tid) { loadTenantOverrides(it) }
            overrides[flag]?.let { return it }
        }
        return globalFlags.getOrDefault(flag, false)
    }

    fun getFlags(tenantId: String? = null): Map<String, Boolean> {
        val base = globalFlags.toMutableMap()
        if (tenantId != null) {
            val tid = UUID.fromString(tenantId)
            val overrides = tenantCache.computeIfAbsent(tid) { loadTenantOverrides(it) }
            base.putAll(overrides)
        }
        return base
    }

    /**
     * Read a string flag value for a tenant, falling back to a default.
     */
    fun getString(tenantId: UUID, flag: String, default: String? = null): String? {
        val values = tenantValueCache.computeIfAbsent(tenantId) { loadTenantValues(it) }
        return values[flag] ?: default
    }

    /**
     * Read a numeric (Long) flag value for a tenant, falling back to a default.
     */
    fun getLong(tenantId: UUID, flag: String, default: Long): Long {
        val raw = getString(tenantId, flag) ?: return default
        return try {
            raw.toLong()
        } catch (e: NumberFormatException) {
            log.warn("Non-numeric flag value for {}:{} = '{}', returning default {}", tenantId, flag, raw, default)
            default
        }
    }

    fun evictTenantCache(tenantId: UUID) {
        tenantCache.remove(tenantId)
        tenantValueCache.remove(tenantId)
        log.debug("Evicted feature-flag cache for tenant {}", tenantId)
    }

    private fun loadTenantOverrides(tenantId: UUID): Map<String, Boolean> =
        tenantFlagRepo.findByTenantId(tenantId).associate { it.flagName to it.enabled }

    private fun loadTenantValues(tenantId: UUID): Map<String, String?> =
        tenantFlagRepo.findByTenantId(tenantId)
            .filter { it.flagValue != null }
            .associate { it.flagName to it.flagValue }

    private fun buildGlobalFlags(): Map<String, Boolean> {
        val f = properties.features
        return mapOf(
            "workflowEngine" to f.workflowEngine,
            "eventBroker" to f.eventBroker,
            "zkRfaAudit" to f.zkRfaAudit,
            "rsBsnPredictor" to f.rsBsnPredictor,
            "ngMilpSolver" to f.ngMilpSolver,
            "hsparKnowledgeGraph" to f.hsparKnowledgeGraph,
            "owpggrAnomaly" to f.owpggrAnomaly,
            "fsoFederatedLearning" to f.fsoFederatedLearning,
            "stghFingerprinting" to f.stghFingerprinting,
            "externalIntegrations" to f.externalIntegrations,
        )
    }
}
