package com.numera.covenant.application

import com.numera.covenant.domain.CovenantMonitoringItem
import com.numera.covenant.domain.CovenantStatus
import com.numera.covenant.domain.CovenantThresholdOperator
import com.numera.covenant.domain.CovenantType
import com.numera.covenant.events.CovenantBreachedEvent
import com.numera.covenant.events.CovenantStatusChangedEvent
import com.numera.covenant.infrastructure.CovenantCustomerRepository
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.covenant.infrastructure.CovenantRepository
import com.numera.model.application.FormulaEngine
import com.numera.spreading.events.SpreadSubmittedEvent
import com.numera.spreading.infrastructure.SpreadItemRepository
import com.numera.spreading.infrastructure.SpreadValueRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class SpreadCovenantEventListener(
    private val spreadItemRepository: SpreadItemRepository,
    private val spreadValueRepository: SpreadValueRepository,
    private val covenantCustomerRepository: CovenantCustomerRepository,
    private val covenantRepository: CovenantRepository,
    private val monitoringRepository: CovenantMonitoringRepository,
    private val formulaEngine: FormulaEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    @Transactional
    fun onSpreadSubmitted(event: SpreadSubmittedEvent) {
        log.info("Spread submitted: spreadItemId={}, tenantId={}", event.spreadItemId, event.tenantId)

        val spreadItem = spreadItemRepository.findById(event.spreadItemId).orElse(null) ?: run {
            log.warn("SpreadItem {} not found, skipping covenant recalculation", event.spreadItemId)
            return
        }

        val covenantCustomer = covenantCustomerRepository
            .findByTenantIdAndCustomerId(event.tenantId, spreadItem.customer.id!!) ?: run {
            log.debug("No covenant customer linked to customer {} in tenant {}", spreadItem.customer.id, event.tenantId)
            return
        }

        val financialCovenants = covenantRepository
            .findByCovenantCustomerIdAndIsActiveTrue(covenantCustomer.id!!)
            .filter { it.covenantType == CovenantType.FINANCIAL && !it.formula.isNullOrBlank() }

        if (financialCovenants.isEmpty()) {
            log.debug("No active financial covenants for customer {}", covenantCustomer.id)
            return
        }

        // Build values map: itemCode → mappedValue
        val spreadValues = spreadValueRepository.findBySpreadItemId(event.spreadItemId)
        val valuesMap: Map<String, BigDecimal?> = spreadValues.associate { it.itemCode to it.mappedValue }

        log.info("Processing {} covenants with {} spread values", financialCovenants.size, valuesMap.size)

        for (covenant in financialCovenants) {
            try {
                val calculatedValue = formulaEngine.evaluate(covenant.formula!!, valuesMap)

                // Find DUE/SUBMITTED monitoring items for the period matching the spread's statement date
                val monitoringItems = monitoringRepository.findByCovenantId(covenant.id!!)
                    .filter { it.status.name in listOf("DUE", "SUBMITTED", "OVERDUE") }
                    .filter { !spreadItem.statementDate.isBefore(it.periodStart) && !spreadItem.statementDate.isAfter(it.periodEnd) }

                for (item in monitoringItems) {
                    item.calculatedValue = calculatedValue
                    monitoringRepository.save(item)

                    if (calculatedValue != null) {
                        val met = evaluateThreshold(calculatedValue, covenant.operator, covenant.thresholdValue, covenant.thresholdMin, covenant.thresholdMax)
                        val newStatus = if (met) CovenantStatus.MET else CovenantStatus.BREACHED
                        if (item.status != newStatus) {
                            log.info("Covenant {} item {} transitioning {} → {}", covenant.name, item.id, item.status, newStatus)
                            transitionStatus(item, newStatus, event.tenantId)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to evaluate covenant {}: {}", covenant.name, e.message)
            }
        }
    }

    private fun transitionStatus(item: CovenantMonitoringItem, newStatus: CovenantStatus, tenantId: java.util.UUID) {
        val previous = item.status
        item.status = newStatus
        monitoringRepository.save(item)

        eventPublisher.publishEvent(
            CovenantStatusChangedEvent(
                tenantId = tenantId,
                monitoringItemId = item.id!!,
                covenantId = item.covenant.id!!,
                previousStatus = previous.name,
                newStatus = newStatus.name,
                actorId = null,
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
    }

    private fun evaluateThreshold(
        value: BigDecimal,
        operator: CovenantThresholdOperator?,
        threshold: BigDecimal?,
        min: BigDecimal?,
        max: BigDecimal?,
    ): Boolean = when (operator) {
        CovenantThresholdOperator.GTE -> threshold != null && value >= threshold
        CovenantThresholdOperator.LTE -> threshold != null && value <= threshold
        CovenantThresholdOperator.EQ -> threshold != null && value.compareTo(threshold) == 0
        CovenantThresholdOperator.BETWEEN -> min != null && max != null && value >= min && value <= max
        null -> true // No operator = no threshold check = considered met
    }
}
