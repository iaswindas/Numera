package com.numera.covenant.application

import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.shared.config.FeatureFlagService
import com.numera.shared.events.CovenantBreachedEvent
import com.numera.shared.infrastructure.DomainEventPublisher
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Component
class CovenantAutoActionService(
    private val monitoringRepository: CovenantMonitoringRepository,
    private val eventPublisher: DomainEventPublisher,
    private val featureFlags: FeatureFlagService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun resolvedTenantId(): UUID =
        TenantContext.get()?.let { UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    /**
     * Runs every 5 minutes. Checks for overdue items and newly breached items,
     * then triggers appropriate state transitions and events.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    fun processAutoActions() {
        val tenantId = TenantAwareEntity.DEFAULT_TENANT
        if (!featureFlags.isEnabled("covenantAutoActions", tenantId.toString())) {
            return
        }

        log.info("Running covenant auto-action sweep")
        processOverdueItems(tenantId)
        processBreachedItems(tenantId)
    }

    private fun processOverdueItems(tenantId: UUID) {
        val today = LocalDate.now()

        val dueItems = monitoringRepository.findByTenantIdAndStatusIn(
            tenantId,
            listOf(CovenantStatus.DUE, CovenantStatus.SUBMITTED),
        ).filter { it.dueDate.isBefore(today) }

        if (dueItems.isEmpty()) return

        log.info("Transitioning {} items to OVERDUE", dueItems.size)

        for (item in dueItems) {
            val previous = item.status
            item.status = CovenantStatus.OVERDUE
            monitoringRepository.save(item)
            log.info("Item {} ({}) transitioned {} → OVERDUE", item.id, item.covenant.name, previous)
        }
    }

    private fun processBreachedItems(tenantId: UUID) {
        val newlyBreached = monitoringRepository.findByTenantIdAndStatus(tenantId, CovenantStatus.BREACHED)
            .filter { it.status == CovenantStatus.BREACHED }

        for (item in newlyBreached) {
            // Publish breach event so downstream workflow / notification handlers can act
            eventPublisher.publish(
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

            // Transition to TRIGGER_ACTION to indicate workflow has been kicked off
            item.status = CovenantStatus.TRIGGER_ACTION
            monitoringRepository.save(item)
            log.info("Breached item {} ({}) escalated to TRIGGER_ACTION", item.id, item.covenant.name)
        }
    }
}
