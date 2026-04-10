package com.numera.spreading

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SpreadServiceTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `create spread item list by customer and submit transitions status`() {
        val user = createUser()
        val customer = createCustomer()
        val document = createDocument(customer, user)
        val template = createTemplate()

        val createResponse = mockMvc.perform(
            post("/api/customers/${customer.id}/spread-items")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
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
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn()

        val spreadId = objectMapper.readTree(createResponse.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/customers/${customer.id}/spread-items").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(spreadId))

        mockMvc.perform(
            post("/api/spread-items/$spreadId/submit")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"comments":"submit for review","overrideValidationWarnings":false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.version").value(1))
    }
}
