package com.numera.shared.events

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Published when a spread sheet is submitted for review. Lives in shared.events so that the
 *  spreading module (publisher) and covenant module (consumer) can use it without the covenant
 *  module needing to access spreading's private infrastructure or events packages.
 *
 *  The snapshot of spread values is embedded so consumers do not need to query the spreading
 *  module's repositories. */
data class SpreadSubmittedEvent(
    val spreadItemId: UUID,
    val tenantId: UUID,
    /** ID of the customer this spread belongs to — allows covenant listener to resolve covenants
     *  without querying spreading's SpreadItemRepository. */
    val customerId: UUID,
    val statementDate: LocalDate,
    /** Line-item-code → mapped value snapshot at submission time. Embedded so that the covenant
     *  module can evaluate financial formulas without querying spreading's SpreadValueRepository. */
    val spreadValues: Map<String, BigDecimal?>,
)
