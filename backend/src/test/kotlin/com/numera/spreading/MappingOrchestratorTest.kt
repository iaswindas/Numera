package com.numera.spreading

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.document.domain.DetectedZone
import com.numera.document.infrastructure.DetectedZoneRepository
import com.numera.document.infrastructure.MlServiceClient
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MappingOrchestratorTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var detectedZoneRepository: DetectedZoneRepository

    @MockBean private lateinit var mlServiceClient: MlServiceClient

    @Test
    fun `spread processing correction audit submit history diff and rollback`() {
        val user = createUser()
        val customer = createCustomer()
        val document = createDocument(customer, user)
        detectedZoneRepository.save(DetectedZone().also {
            it.document = document
            it.tableId = "table-1"
            it.zoneType = "INCOME_STATEMENT"
            it.zoneLabel = "Income Statement"
            it.confidence = 0.95
            it.pageNumber = 1
            it.metadataJson = """{"classificationMethod":"VLM","detectedPeriods":["2025"],"detectedCurrency":"AED","detectedUnit":"thousands","rowCount":12}"""
        })

        val template = createTemplate()
        addLineItem(template, "IS001", "Revenue", required = true, sortOrder = 10)
        addLineItem(template, "IS002", "COGS", required = true, sortOrder = 20)
        addLineItem(template, "IS003", "Gross Profit", itemType = com.numera.model.domain.ModelItemType.FORMULA, formula = "{IS001} - {IS002}", total = true, sortOrder = 30)
        addValidation(template, "Balance Sheet Check", "{IS003} - 60", "ERROR")

        given(mlServiceClient.suggestMappings(any())).willReturn(
            MlServiceClient.MappingSuggestResponse(
                document.id.toString(),
                listOf(
                    mapOf("item_code" to "IS001", "value" to 100, "confidence" to 0.92, "confidence_level" to "HIGH", "page" to 1, "source_text" to "Revenue", "expression_type" to "SUM"),
                    mapOf("item_code" to "IS002", "value" to 40, "confidence" to 0.88, "confidence_level" to "MEDIUM", "page" to 1, "source_text" to "COGS", "expression_type" to "DIRECT")
                ),
                mapOf("mapped" to 2),
                50,
            )
        )
        given(mlServiceClient.buildExpressions(any())).willReturn(
            MlServiceClient.ExpressionBuildResponse(
                document.id.toString(),
                template.id.toString(),
                "ALL",
                listOf(
                    mapOf("item_code" to "IS001", "value" to 100, "scale_factor" to 1, "confidence" to 0.92, "confidence_level" to "HIGH", "page" to 1, "source_text" to "Revenue", "expression_type" to "SUM", "autofilled" to false),
                    mapOf("item_code" to "IS002", "value" to 40, "scale_factor" to 1, "confidence" to 0.88, "confidence_level" to "MEDIUM", "page" to 1, "source_text" to "COGS", "expression_type" to "DIRECT", "autofilled" to false)
                ),
                2,
                3,
                66.7f,
                1.0f,
                0,
                emptyList(),
            )
        )

        val createResponse = mockMvc.perform(
            post("/api/customers/${customer.id}/spread-items")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerFor(user))
                .content(
                    """
                    {
                      "documentId": "${document.id}",
                      "templateId": "${template.id}",
                      "statementDate": "${today()}",
                      "frequency": "ANNUAL",
                      "auditMethod": "AUDITED",
                      "sourceCurrency": "AED",
                      "consolidation": "CONSOLIDATED"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val spreadId = objectMapper.readTree(createResponse.response.contentAsString)["id"].asText()

        val processResponse = mockMvc.perform(post("/api/spread-items/$spreadId/process").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.spreadItemId").value(spreadId))
            .andExpect(jsonPath("$.summary.mapped").value(3))
            .andExpect(jsonPath("$.values.length()").value(3))
            .andReturn()

        val values = objectMapper.readTree(processResponse.response.contentAsString)["values"]
        val revenueValueId = values.first { it["itemCode"].asText() == "IS001" }["id"].asText()

        mockMvc.perform(
            put("/api/spread-items/$spreadId/values/$revenueValueId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerFor(user))
                .content("""{"mappedValue":120,"overrideComment":"Corrected","expressionType":"MANUAL"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mappedValue").value(120))

        mockMvc.perform(
            get("/api/audit/events")
                .param("entityType", "spread_item")
                .param("entityId", spreadId)
                .header("Authorization", bearerFor(user))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events[0].eventType").exists())

        mockMvc.perform(
            post("/api/spread-items/$spreadId/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerFor(user))
                .content("""{"comments":"Q4 spread complete","overrideValidationWarnings":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.version").value(2))

        mockMvc.perform(get("/api/spread-items/$spreadId/history").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.versions[0].versionNumber").value(2))

        mockMvc.perform(get("/api/spread-items/$spreadId/diff/1/2").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.toVersion").value(2))

        mockMvc.perform(
            post("/api/spread-items/$spreadId/rollback/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerFor(user))
                .content("""{"comments":"Rolling back"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.restoredFromVersion").value(1))

        mockMvc.perform(
            post("/api/spread-items/$spreadId/values/bulk-accept")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerFor(user))
                .content("""{"confidenceThreshold":"MEDIUM"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(2))
            .andExpect(jsonPath("$.total").value(2))
    }

    private fun com.fasterxml.jackson.databind.JsonNode.first(predicate: (com.fasterxml.jackson.databind.JsonNode) -> Boolean): com.fasterxml.jackson.databind.JsonNode =
        this.toList().first(predicate)
}
