package com.numera.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.numera.auth.infrastructure.RefreshTokenRepository
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

class AuthServiceTest : IntegrationTestBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `login success and me endpoint`() {
        val user = createUser()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${user.email}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value(user.email))
            .andReturn()

        val accessToken = objectMapper.readTree(loginResult.response.contentAsString)["accessToken"].asText()

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(user.email))
            .andExpect(jsonPath("$.fullName").value(user.fullName))
            .andExpect(jsonPath("$.roles[0]").value("ANALYST"))
            .andExpect(jsonPath("$.tenantName").value("demo"))

        val refreshed = userRepository.findById(user.id!!).orElseThrow()
        assertEquals(user.email, refreshed.email)
        assertEquals(true, refreshed.lastLoginAt != null)
    }

    @Test
    fun `login failure returns unauthorized`() {
        createUser()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"analyst@numera.ai","password":"wrong-password"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
    }

    @Test
    fun `stored password hash uses bcrypt and validates raw password`() {
        val user = createUser(password = "Password123!")
        val stored = userRepository.findById(user.id!!).orElseThrow()
        assertEquals(true, stored.passwordHash.startsWith("$2"))
        assertEquals(true, passwordEncoder.matches("Password123!", stored.passwordHash))
    }

    @Test
    fun `refresh token returns a new token pair`() {
        val user = createUser()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${user.email}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val loginJson = objectMapper.readTree(loginResult.response.contentAsString)
        val oldRefresh = loginJson["refreshToken"].asText()
        val oldAccess = loginJson["accessToken"].asText()

        val refreshResult = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$oldRefresh"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value(user.email))
            .andReturn()

        val refreshJson = objectMapper.readTree(refreshResult.response.contentAsString)
        assertNotEquals(oldAccess, refreshJson["accessToken"].asText())
        assertNotEquals(oldRefresh, refreshJson["refreshToken"].asText())
    }

    @Test
    fun `expired refresh token returns unauthorized`() {
        val user = createUser()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${user.email}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val refreshToken = objectMapper.readTree(loginResult.response.contentAsString)["refreshToken"].asText()
        val tokenRecord = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken).orElseThrow()
        tokenRecord.expiresAt = java.time.Instant.now().minusSeconds(60)
        refreshTokenRepository.save(tokenRecord)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
    }

    @Test
    fun `admin can configure password policy and weak passwords are rejected`() {
        val admin = createUser(roleName = "ROLE_ADMIN", email = "admin@numera.ai", fullName = "Admin User")

        mockMvc.perform(
            put("/api/admin/password-policy")
                .header("Authorization", bearerFor(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "minLength": 16,
                      "requireUppercase": true,
                      "requireLowercase": true,
                      "requireDigit": true,
                      "requireSpecialCharacter": true,
                      "expiryDays": 30,
                      "historySize": 3,
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.minLength").value(16))

        mockMvc.perform(
            post("/api/admin/users")
                .header("Authorization", bearerFor(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "new.user@numera.ai",
                      "fullName": "New User",
                      "password": "Password123!",
                      "roles": ["ROLE_ANALYST"],
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }

    @Test
    fun `password expiry is flagged during login`() {
        val user = createUser()
        user.passwordChangedAt = Instant.now().minus(120, ChronoUnit.DAYS)
        userRepository.save(user)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${user.email}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.passwordExpired").value(true))
    }

    @Test
    fun `password reuse is blocked after change`() {
        val user = createUser()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${user.email}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val accessToken = objectMapper.readTree(loginResult.response.contentAsString)["accessToken"].asText()

        mockMvc.perform(
            post("/api/auth/password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"Password123!","newPassword":"Password456!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.changed").value(true))

        mockMvc.perform(
            post("/api/auth/password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"Password456!","newPassword":"Password123!"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }
}
