package com.numera.covenant

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.covenant.infrastructure.CovenantMonitoringRepository
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CovenantServiceTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var covenantMonitoringRepository: CovenantMonitoringRepository

    @Test
    fun `create covenant customer and lifecycle of covenant definition`() {
        val user = createUser()
        val customer = createCustomer()

        val covenantCustomerResponse = mockMvc.perform(
            post("/api/covenants/customers")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "${customer.id}",
                      "rimId": "RIM-101",
                      "clEntityId": "CL-201",
                      "financialYearEnd": "2025-12-31",
                      "contacts": [
                        {
                          "contactType": "INTERNAL",
                          "name": "Risk Officer",
                          "email": "risk@numera.ai"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.customerId").value(customer.id.toString()))
            .andReturn()

        val covenantCustomerId = objectMapper.readTree(covenantCustomerResponse.response.contentAsString)["id"].asText()

        val covenantResponse = mockMvc.perform(
            post("/api/covenants/definitions")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "covenantCustomerId": "$covenantCustomerId",
                      "covenantType": "FINANCIAL",
                      "name": "Debt Service Coverage Ratio",
                      "description": "DSCR must be above threshold",
                      "frequency": "QUARTERLY",
                      "formula": "EBITDA / DEBT_SERVICE",
                      "operator": "GTE",
                      "thresholdValue": 1.20
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Debt Service Coverage Ratio"))
            .andReturn()

        val covenantId = objectMapper.readTree(covenantResponse.response.contentAsString)["id"].asText()

        mockMvc.perform(
            get("/api/covenants/definitions")
                .header("Authorization", bearerFor(user))
                .param("covenantCustomerId", covenantCustomerId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(covenantId))

        mockMvc.perform(delete("/api/covenants/definitions/$covenantId").header("Authorization", bearerFor(user)))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/covenants/definitions")
                .header("Authorization", bearerFor(user))
                .param("covenantCustomerId", covenantCustomerId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `monitoring lifecycle with waiver and email template rendering`() {
        val user = createUser()
        val customer = createCustomer(name = "Breach Test Customer", code = "BR001")

        val covenantCustomerResponse = mockMvc.perform(
            post("/api/covenants/customers")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "${customer.id}",
                      "financialYearEnd": "2025-12-31",
                      "contacts": []
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val covenantCustomerId = objectMapper.readTree(covenantCustomerResponse.response.contentAsString)["id"].asText()

        val covenantResponse = mockMvc.perform(
            post("/api/covenants/definitions")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "covenantCustomerId": "$covenantCustomerId",
                      "covenantType": "FINANCIAL",
                      "name": "Leverage Ratio",
                      "frequency": "QUARTERLY",
                      "formula": "NET_DEBT / EBITDA",
                      "operator": "LTE",
                      "thresholdValue": 3.50
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val covenantId = objectMapper.readTree(covenantResponse.response.contentAsString)["id"].asText()

        val generated = mockMvc.perform(
            post("/api/covenants/monitoring/generate")
                .header("Authorization", bearerFor(user))
                .param("covenantId", covenantId)
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].status").value("DUE"))
            .andReturn()

        val monitoringItemId = objectMapper.readTree(generated.response.contentAsString)[0]["id"].asText()

        mockMvc.perform(
            put("/api/covenants/monitoring/$monitoringItemId/manual-value")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":4.2,"justification":"Analyst override"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.manualValue").value(4.2))

        val monitoring = covenantMonitoringRepository.findById(java.util.UUID.fromString(monitoringItemId)).orElseThrow()
        monitoring.status = com.numera.covenant.domain.CovenantStatus.BREACHED
        covenantMonitoringRepository.save(monitoring)

        mockMvc.perform(
            post("/api/covenants/monitoring/$monitoringItemId/trigger-action")
                .header("Authorization", bearerFor(user))
                .param("actorId", user.id.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("TRIGGER_ACTION"))

        val templateResponse = mockMvc.perform(
            post("/api/covenants/templates")
                .header("Authorization", bearerFor(user))
                .param("actorId", user.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Waiver Template",
                      "covenantType": "FINANCIAL",
                      "templateCategory": "WAIVER",
                      "subject": "Waiver Notice",
                      "bodyHtml": "<p>Hello {{CUSTOMER_NAME}}</p><p>{{COVENANT_NAME}}</p><p>{{COMMENTS}}</p>"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        val templateId = objectMapper.readTree(templateResponse.response.contentAsString)["id"].asText()

        val signatureResponse = mockMvc.perform(
            post("/api/covenants/templates/signatures")
                .header("Authorization", bearerFor(user))
                .param("actorId", user.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Risk Manager","htmlContent":"<p>Regards,<br/>Risk Team</p>"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()
        val signatureId = objectMapper.readTree(signatureResponse.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/covenants/waivers")
                .header("Authorization", bearerFor(user))
                .param("actorId", user.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "monitoringItemId": "$monitoringItemId",
                      "waiverType": "INSTANCE",
                      "letterType": "WAIVE",
                      "signatureId": "$signatureId",
                      "emailTemplateId": "$templateId",
                      "deliveryMethod": "EMAIL",
                      "comments": "Approved exceptionally"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.monitoringItemId").value(monitoringItemId))
            .andExpect(jsonPath("$.deliveryMethod").value("EMAIL"))
            .andExpect(jsonPath("$.letterHtml").value(org.hamcrest.Matchers.containsString("Approved exceptionally")))
            .andExpect(jsonPath("$.letterHtml").value(org.hamcrest.Matchers.containsString("Risk Team")))
    }
}
