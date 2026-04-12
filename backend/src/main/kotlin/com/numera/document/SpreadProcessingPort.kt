package com.numera.document

import com.numera.document.infrastructure.DetectedZoneRepository
import com.numera.document.infrastructure.MlServiceClient
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for document-zone querying and ML-based spread mapping, exposed by the document module.
 *
 * Lives in the document ROOT package so the spreading module can call zone-lookup and ML-mapping
 * operations without crossing into document's private infrastructure package.
 *
 * The request/response types are defined as nested data classes here so they belong to the
 * document module's public API surface.
 */
@Service
class SpreadProcessingPort(
    private val zoneRepository: DetectedZoneRepository,
    private val mlClient: MlServiceClient,
) {
    /**
     * Returns the detected zones for a document as source-row maps, ready for ML mapping.
     * Shape: [{"table_id": "...", "zone_type": "...", "zone_label": "..."}, ...]
     */
    fun getDocumentZoneRows(documentId: UUID): List<Map<String, Any>> =
        zoneRepository.findByDocumentId(documentId).map {
            mapOf(
                "table_id" to it.tableId,
                "zone_type" to it.zoneType,
                "zone_label" to (it.zoneLabel ?: ""),
            )
        }

    /**
     * Calls the ML service to suggest semantic mappings between source rows and target line items.
     * Returns the mapping suggestions, or null if the ML call fails.
     */
    fun suggestMappings(
        documentId: String,
        sourceRows: List<Map<String, Any>>,
        targetItems: List<Map<String, Any>>,
        tenantId: String,
    ): List<Map<String, Any>>? =
        runCatching {
            mlClient.suggestMappings(
                MlServiceClient.MappingSuggestRequest(
                    document_id = documentId,
                    source_rows = sourceRows,
                    target_items = targetItems,
                    tenant_id = tenantId,
                )
            ).mappings
        }.getOrNull()

    /**
     * Calls the ML service to build value-extraction expressions for each template line item.
     * Returns [ExpressionBuildResult] on success, null if the ML call fails.
     */
    fun buildExpressions(
        documentId: String,
        tenantId: String,
        customerId: String,
        templateId: String,
        sourceRows: List<Map<String, Any>>,
        semanticMatches: List<Map<String, Any>>,
    ): ExpressionBuildResult? =
        runCatching {
            val raw = mlClient.buildExpressions(
                MlServiceClient.ExpressionBuildRequest(
                    document_id = documentId,
                    tenant_id = tenantId,
                    customer_id = customerId,
                    template_id = templateId,
                    zone_type = "ALL",
                    extracted_rows = sourceRows,
                    semantic_matches = semanticMatches,
                    use_autofill = true,
                )
            )
            ExpressionBuildResult(expressions = raw.expressions, unitScale = raw.unit_scale)
        }.getOrNull()

    /** Result of an expression-build ML call. */
    data class ExpressionBuildResult(
        val expressions: List<Map<String, Any>>,
        val unitScale: Float,
    )
}
