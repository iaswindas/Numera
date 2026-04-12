package com.numera.shared

import com.numera.shared.config.FeatureFlagService
import com.numera.shared.config.NumeraProperties
import com.numera.shared.infrastructure.TenantFeatureFlag
import com.numera.shared.infrastructure.TenantFeatureFlagRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FeatureFlagServiceTest {

    private lateinit var repo: TenantFeatureFlagRepository
    private lateinit var service: FeatureFlagService

    private val tenantId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repo = mockk()
        every { repo.findByTenantId(any()) } returns emptyList()

        val props = NumeraProperties(
            features = NumeraProperties.Features(
                workflowEngine = false,
                ngMilpSolver = true,
                stghFingerprinting = true,
            ),
        )
        service = FeatureFlagService(props, repo)
    }

    @Test
    fun `returns global flag value when no tenant override`() {
        assertTrue(service.isEnabled("ngMilpSolver"))
        assertFalse(service.isEnabled("workflowEngine"))
    }

    @Test
    fun `returns false for unknown flag`() {
        assertFalse(service.isEnabled("nonExistentFlag"))
    }

    @Test
    fun `tenant override takes precedence over global flag`() {
        every { repo.findByTenantId(tenantId) } returns listOf(
            TenantFeatureFlag(tenantId = tenantId, flagName = "workflowEngine", enabled = true),
        )

        assertTrue(service.isEnabled("workflowEngine", tenantId.toString()))
        // Without tenant, still global value
        assertFalse(service.isEnabled("workflowEngine"))
    }

    @Test
    fun `getFlags returns global flags without tenant`() {
        val flags = service.getFlags()
        assertEquals(10, flags.size)
        assertTrue(flags["ngMilpSolver"]!!)
        assertFalse(flags["workflowEngine"]!!)
    }

    @Test
    fun `getFlags merges tenant overrides`() {
        every { repo.findByTenantId(tenantId) } returns listOf(
            TenantFeatureFlag(tenantId = tenantId, flagName = "eventBroker", enabled = true),
        )

        val flags = service.getFlags(tenantId.toString())
        assertTrue(flags["eventBroker"]!!)
        // Non-overridden flag stays global
        assertTrue(flags["ngMilpSolver"]!!)
    }

    @Test
    fun `evictTenantCache forces reload on next access`() {
        // First call caches empty overrides
        assertFalse(service.isEnabled("workflowEngine", tenantId.toString()))

        // Simulate DB change
        every { repo.findByTenantId(tenantId) } returns listOf(
            TenantFeatureFlag(tenantId = tenantId, flagName = "workflowEngine", enabled = true),
        )

        // Still cached => false
        assertFalse(service.isEnabled("workflowEngine", tenantId.toString()))

        // Evict and re-check
        service.evictTenantCache(tenantId)
        assertTrue(service.isEnabled("workflowEngine", tenantId.toString()))
    }
}
