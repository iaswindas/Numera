package com.numera.model

import com.numera.model.application.FormulaEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FormulaEngineTest {
    private val engine = FormulaEngine()

    private val Int.bd get() = BigDecimal(this)

    @Test
    fun `simple addition`() {
        assertEquals(0, engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to 200.bd))!!.compareTo(300.bd))
    }

    @Test
    fun `subtraction with negatives`() {
        assertEquals(0, engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to (-150).bd))!!.compareTo((-50).bd))
    }

    @Test
    fun `null propagation`() {
        assertNull(engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to null)))
    }

    @Test
    fun `division by zero returns null`() {
        assertNull(engine.evaluate("{A} / {B}", mapOf("A" to 100.bd, "B" to BigDecimal.ZERO)))
    }

    @Test
    fun `ABS function`() {
        assertEquals(0, engine.evaluate("ABS({A})", mapOf("A" to (-100).bd))!!.compareTo(100.bd))
    }

    @Test
    fun `percentage calculation`() {
        assertEquals(0, engine.evaluate("{A} / {B} * 100", mapOf("A" to 250.bd, "B" to 1000.bd))!!.compareTo(25.bd))
    }

    @Test
    fun `sum range`() {
        val values = linkedMapOf("BS001" to 100.bd, "BS002" to 200.bd, "BS003" to 50.bd)
        assertEquals(0, engine.evaluate("SUM({BS001}:{BS003})", values)!!.compareTo(350.bd))
    }

    @Test
    fun `if condition`() {
        val values = mapOf("BS001" to 10.bd, "BS050" to 2.bd)
        assertEquals(0, engine.evaluate("IF({BS001} > 0, {BS001} / {BS050}, 0)", values)!!.compareTo(5.bd))
    }

    @Test
    fun `balance sheet validation`() {
        val values = mapOf("ASSETS" to 1000.bd, "LIABILITIES" to 700.bd, "EQUITY" to 300.bd)
        assertEquals(0, engine.evaluate("{ASSETS} - {LIABILITIES} - {EQUITY}", values)!!.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `invalid formula fails gracefully with parse error`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate("IF({BS001} > 0, {BS001}", mapOf("BS001" to 10.bd))
        }
    }
}
