package com.numera.shared

import com.numera.shared.domain.TenantAwareEntity
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuditServiceTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `audit event creation and hash chain verification`() {
        val user = createUser()

        val createCustomer = mockMvc.perform(
            post("/api/customers")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerCode": "AUD001",
                      "name": "Audit Target LLC",
                      "industry": "Logistics",
                      "country": "AE",
                      "relationshipManager": "RM Audit"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val customerId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createCustomer.response.contentAsString)["id"].asText()

        mockMvc.perform(
            get("/api/audit/events")
                .header("Authorization", bearerFor(user))
                .param("entityType", "customer")
                .param("entityId", customerId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.events[0].eventType").value("CUSTOMER_CREATED"))

        mockMvc.perform(
            get("/api/audit/verify/${TenantAwareEntity.DEFAULT_TENANT}")
                .header("Authorization", bearerFor(user))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("VALID"))
            .andExpect(jsonPath("$.eventsVerified").isNumber)
    }
}
