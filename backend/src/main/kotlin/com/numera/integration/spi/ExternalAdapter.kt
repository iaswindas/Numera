package com.numera.integration.spi

import com.numera.integration.domain.ExternalSystem
import com.numera.integration.domain.ExternalSystemType

/**
 * Service Provider Interface that every external-system adapter must implement.
 * Spring discovers adapters automatically via [List] injection.
 */
interface ExternalAdapter {

    fun systemType(): ExternalSystemType

    fun pushSpread(system: ExternalSystem, spreadData: CanonicalSpreadPayload): AdapterResponse

    fun pullMetadata(system: ExternalSystem, externalRef: String): CanonicalSpreadPayload?

    fun validateConnection(system: ExternalSystem): Boolean

    fun mapToCanonical(externalData: Map<String, Any>): CanonicalSpreadPayload

    fun mapFromCanonical(canonicalData: CanonicalSpreadPayload): Map<String, Any>
}
