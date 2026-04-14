package com.numera.auth

import com.numera.auth.dto.LoginRequest
import com.numera.shared.security.JwtTokenProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `login with short password returns 4xx`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "test@test.com", "password" to "short"))
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun `login with long password returns 4xx`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "test@test.com", "password" to "a".repeat(129)))
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun `accessing protected endpoint without token returns 401`() {
        mockMvc.get("/api/customers").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `rate limiting returns 429 after too many attempts`() {
        // This test verifies the rate limiting filter
        // Note: in test env, the filter still applies
        // We send 6 rapid requests, the 6th should be rate limited
        repeat(5) {
            mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("email" to "rate-test@test.com", "password" to "wrongpass1"))
            }
        }
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "rate-test@test.com", "password" to "wrongpass1"))
        }.andExpect {
            status { isTooManyRequests() }
        }
    }

    @Test
    fun `SQL injection in customer query returns safe error`() {
        val token = tokenProvider.generateAccessToken("test@numera.ai", "00000000-0000-0000-0000-000000000001", listOf("ROLE_ANALYST"))
        mockMvc.get("/api/customers") {
            header("Authorization", "Bearer $token")
            param("query", "'; DROP TABLE customers; --")
        }.andExpect {
            status { isOk() }
        }
    }
}
