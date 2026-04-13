package com.numera.shared.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LogSanitizerTest {

    @Test
    fun `sanitize replaces password fields`() {
        val input = """{"email":"test@test.com","password":"secret123","name":"John"}"""
        val result = LogSanitizer.sanitize(input)!!
        assertFalse(result.contains("secret123"))
        assertTrue(result.contains("[REDACTED]"))
        assertTrue(result.contains("John"))
    }

    @Test
    fun `sanitize replaces token fields`() {
        val input = """{"refreshToken":"abc123def456","userId":"user1"}"""
        val result = LogSanitizer.sanitize(input)!!
        assertFalse(result.contains("abc123def456"))
        assertTrue(result.contains("[REDACTED]"))
        assertTrue(result.contains("user1"))
    }

    @Test
    fun `sanitize handles null input`() {
        assertNull(LogSanitizer.sanitize(null))
    }

    @Test
    fun `sanitize preserves non-sensitive content`() {
        val input = """{"name":"John","email":"test@test.com"}"""
        val result = LogSanitizer.sanitize(input)!!
        assertEquals(input, result)
    }
}
