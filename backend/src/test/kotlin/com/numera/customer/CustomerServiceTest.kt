package com.numera.customer

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.customer.domain.Customer
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class CustomerServiceTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `customer CRUD and search by name`() {
        val user = createUser()

        val createResult = mockMvc.perform(
            post("/api/customers")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerCode": "ALPHA01",
                      "name": "Alpha Holdings",
                      "industry": "Banking",
                      "country": "AE",
                      "relationshipManager": "RM Alpha"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Alpha Holdings"))
            .andReturn()

        val customerId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/customers/$customerId").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerCode").value("ALPHA01"))

        mockMvc.perform(
            put("/api/customers/$customerId")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerCode": "ALPHA01",
                      "name": "Alpha Holdings PJSC",
                      "industry": "Banking",
                      "country": "AE",
                      "relationshipManager": "RM Beta"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Alpha Holdings PJSC"))

        mockMvc.perform(
            get("/api/customers")
                .header("Authorization", bearerFor(user))
                .param("query", "Alpha")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Alpha Holdings PJSC"))
    }

    @Test
    fun `tenant isolation hides customers from other tenants`() {
        val user = createUser()

        customerRepository.save(Customer().also {
            it.tenantId = UUID.fromString("00000000-0000-0000-0000-000000000099")
            it.customerCode = "EXT01"
            it.name = "External Tenant Customer"
            it.industry = "Energy"
            it.country = "GB"
            it.relationshipManager = "RM External"
        })

        val response = mockMvc.perform(get("/api/customers").header("Authorization", bearerFor(user)))
            .andExpect(status().isOk)
            .andReturn()

        val payload = response.response.contentAsString
        assertFalse(payload.contains("External Tenant Customer"))
    }
}
