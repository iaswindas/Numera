package com.numera

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Flyway Migration Tests")
class FlywayMigrationTest {

    @Autowired
    lateinit var flyway: Flyway

    @Test
    fun `all migrations apply cleanly`() {
        val result = flyway.info()
        val pending = result.pending()
        assertTrue(pending.isEmpty(), "There should be no pending migrations, found: ${pending.map { it.version }}")
    }

    @Test
    fun `all applied migrations succeeded`() {
        val result = flyway.info()
        val failed = result.applied().filter { it.state.isFailed }
        assertTrue(failed.isEmpty(), "No migrations should be in FAILED state, found: ${failed.map { it.version }}")
    }
}
