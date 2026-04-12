package com.numera.integration.application

import com.numera.shared.config.FeatureFlagService
import com.numera.shared.security.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SyncRetryScheduler(
    private val syncService: IntegrationSyncService,
    private val featureFlags: FeatureFlagService,
) {
    private val log = LoggerFactory.getLogger(SyncRetryScheduler::class.java)

    @Scheduled(fixedDelay = 60_000)
    fun retryFailedSyncs() {
        if (!featureFlags.isEnabled("externalIntegrations", TenantContext.get())) {
            return
        }
        val retried = syncService.retryFailedSyncs()
        if (retried > 0) {
            log.info("Scheduled retry processed {} sync records", retried)
        }
    }
}
