package com.numera

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModuleBoundaryTest {
    @Test
    fun `verify module boundaries are not violated`() {
        val modules = ApplicationModules.of(NumeraApplication::class.java)
        modules.verify()
    }
}
