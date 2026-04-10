package com.numera.document

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.document.infrastructure.MinioStorageClient
import com.numera.document.infrastructure.MlServiceClient
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.reactive.function.client.WebClientResponseException

class DocumentProcessingTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockBean private lateinit var minioStorageClient: MinioStorageClient
    @MockBean private lateinit var mlServiceClient: MlServiceClient

    @Test
    fun `upload response contract and async processing persistence`() {
        val user = createUser()
        val customer = createCustomer()

        given(minioStorageClient.upload(any())).willReturn("numera-docs/acme.pdf")
        given(mlServiceClient.extractText(anyString(), anyString(), anyString())).willAnswer {
            Thread.sleep(100)
            MlServiceClient.OcrResponse("doc-1", 4, 4, emptyList(), "text", 10, "native", "NATIVE")
        }
        given(mlServiceClient.detectTables(anyString(), anyString())).willReturn(
            MlServiceClient.TableDetectResponse(
                "doc-1",
                4,
                1,
                listOf(mapOf("table_id" to "table-1", "page" to 2, "row_count" to 28)),
                20,
                "native",
                "NATIVE",
            )
        )
        given(mlServiceClient.classifyZones(anyString(), anyList())).willReturn(
            MlServiceClient.ZoneClassifyResponse(
                "doc-1",
                listOf(
                    MlServiceClient.ClassifiedZone(
                        "table-1",
                        "INCOME_STATEMENT",
                        "Consolidated Statement of Profit or Loss",
                        0.96f,
                        "VLM",
                        listOf("2025", "2024"),
                        "AED",
                        "thousands",
                    )
                ),
                30,
            )
        )

        val uploadResult = mockMvc.perform(
            multipart("/api/documents/upload")
                .file(MockMultipartFile("file", "statement.pdf", "application/pdf", "pdf".toByteArray()))
                .part(MockPart("customerId", customer.id.toString().toByteArray()))
                .part(MockPart("language", "en".toByteArray()))
                .header("Authorization", bearerFor(user))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("UPLOADED"))
            .andReturn()

        val documentId = objectMapper.readTree(uploadResult.response.contentAsString)["documentId"].asText()

        repeat(20) {
            val response = mockMvc.perform(get("/api/documents/$documentId").header("Authorization", bearerFor(user)))
                .andExpect(status().isOk)
                .andReturn()
            val body = objectMapper.readTree(response.response.contentAsString)
            if (body["processingStatus"].asText() == "READY") {
                return@repeat
            }
            Thread.sleep(150)
        }

        mockMvc.perform(get("/api/documents/$documentId").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("READY"))
            .andExpect(jsonPath("$.language").value("en"))
            .andExpect(jsonPath("$.zonesDetected").value(1))

        val zonesResult = mockMvc.perform(get("/api/documents/$documentId/zones").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.zones[0].zoneType").value("INCOME_STATEMENT"))
            .andExpect(jsonPath("$.zones[0].classificationMethod").value("VLM"))
            .andExpect(jsonPath("$.zones[0].rowCount").value(28))
            .andReturn()

        assertTrue(zonesResult.response.contentAsString.contains("Consolidated Statement of Profit or Loss"))
    }

    @Test
    fun `template items by zone endpoint returns spec shape`() {
        val user = createUser()
        val template = createTemplate()
        addLineItem(template, "IS001", "Revenue", required = true, sortOrder = 10, zone = "INCOME_STATEMENT")
        addLineItem(template, "BS001", "Cash", required = true, sortOrder = 20, zone = "BALANCE_SHEET")

        mockMvc.perform(
            get("/api/model-templates/${template.id}/items")
                .param("zone", "INCOME_STATEMENT")
                .header("Authorization", bearerFor(user))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.templateId").value(template.id.toString()))
            .andExpect(jsonPath("$.zone").value("INCOME_STATEMENT"))
            .andExpect(jsonPath("$.items[0].itemCode").value("IS001"))
            .andExpect(jsonPath("$.items[0].displayOrder").value(10))
    }

    @Test
    fun `processing error transitions document status to error`() {
        val user = createUser()
        val customer = createCustomer()

        given(minioStorageClient.upload(any())).willReturn("numera-docs/failing.pdf")
        given(mlServiceClient.extractText(anyString(), anyString(), anyString())).willThrow(
            WebClientResponseException.create(
                500,
                "ML unavailable",
                org.springframework.http.HttpHeaders.EMPTY,
                ByteArray(0),
                null,
            )
        )

        val uploadResult = mockMvc.perform(
            multipart("/api/documents/upload")
                .file(MockMultipartFile("file", "failing.pdf", "application/pdf", "pdf".toByteArray()))
                .part(MockPart("customerId", customer.id.toString().toByteArray()))
                .part(MockPart("language", "en".toByteArray()))
                .header("Authorization", bearerFor(user))
        )
            .andExpect(status().isAccepted)
            .andReturn()

        val documentId = objectMapper.readTree(uploadResult.response.contentAsString)["documentId"].asText()

        repeat(20) {
            val statusResult = mockMvc.perform(
                get("/api/documents/$documentId/status").header("Authorization", bearerFor(user))
            )
                .andExpect(status().isOk)
                .andReturn()
            val body = objectMapper.readTree(statusResult.response.contentAsString)
            if (body["status"].asText() == "ERROR") {
                return@repeat
            }
            Thread.sleep(100)
        }

        mockMvc.perform(get("/api/documents/$documentId/status").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").isNotEmpty)
    }
}
