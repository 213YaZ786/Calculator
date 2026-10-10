package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The integral programs on textbook cases; numbers typed are passed as text too, as the screen does. */
class IntegralsTest {

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    private fun out(id: String, fields: Map<String, String>): Outcome {
        val p = programs.first { it.id == id }
        val values = p.fields.associate { f ->
            f.symbol to if (f.kind == FieldKind.NUMBER) fields[f.symbol]?.let { com.yaz.calculator.engine.Calc().evaluate(it, false).crValue().toDouble() } else null
        }
        return p.run(values, fields, degrees = false)
    }

    private fun text(id: String, fields: Map<String, String>) = out(id, fields).lines.joinToString("\n") { it.text }

    @Test
    fun areaIsExactAndSplitAtTheSignChange() {
        val t = text("area-integral", mapOf("f" to "x²", "a" to "0", "b" to "3"))
        assertTrue(t, t.contains("∫ = 9"))
        val s = text("area-integral", mapOf("f" to "sin(x)", "a" to "0", "b" to "2π"))
        assertTrue(s, s.contains("Area between the curve and the axis 4"))
        assertNotNull(out("area-integral", mapOf("f" to "x²", "a" to "0", "b" to "3")).figure)
    }

    @Test
    fun chasles() {
        val t = text("chasles-integral", mapOf("f" to "x", "a" to "0", "c" to "1", "b" to "2"))
        assertTrue(t, t.contains("∫ₐᵇ = 2"))
        assertTrue(t, t.contains("∫ₐᶜ = 1/2"))
    }

    @Test
    fun areaBetweenFindsTheIntersections() {
        // x and x² meet at 0 and 1: area 1/6.
        val t = text("area-between", mapOf("f" to "x", "g" to "x²"))
        
        val e = text("area-between", mapOf("f" to "x", "g" to "x²", "a" to "0", "b" to "1"))
        assertTrue(e, e.contains("Area = 1/6"))
        assertTrue(t, t.contains("Area = 1/6"))
    }

    @Test
    fun meanValue() {
        val t = text("mean-value", mapOf("f" to "x²", "a" to "0", "b" to "3"))
        assertTrue(t, t.contains("μ = 3"))
        assertTrue(t, t.contains("c = 1.732"))
    }

    @Test
    fun accumulation() {
        val t = text("accumulation", mapOf("f" to "2x", "a" to "1", "x" to "3"))
        assertTrue(t, t.contains("F(x) = x² − 1"))
        assertTrue(t, t.contains("F(3) = 8"))
    }

    @Test
    fun monteCarloIsNearTheArea() {
        val t = text("monte-carlo", mapOf("f" to "x²", "a" to "0", "b" to "1", "N" to "100000"))
        val estimate = Regex("A ≈ ([0-9.]+)").find(t)!!.groupValues[1].toDouble()
        assertEquals(1.0 / 3, estimate, 0.01)
    }

    @Test
    fun brouncker() {
        val t = text("brouncker", mapOf("k" to "1"))
        assertTrue(t, t.contains("S1 = 7/12"))
    }

    @Test
    fun integralSequence() {
        // Iₙ = ∫₀¹ xⁿeˣ: I₀ = e − 1, I₁ = 1.
        val t = text("integral-sequence", mapOf("g" to "e^x", "h" to "x", "a" to "0", "b" to "1", "N" to "10"))
        assertTrue(t, t.contains("I₀ = e − 1"))
        assertTrue(t, t.contains("I₁ = 1"))
        assertTrue(t, t.contains("decreasing"))
    }

    @Test
    fun volumeOfACone() {
        // y = x on [0 ; 3] turned: a cone, πr²h/3 = 9π.
        val o = out("revolution", mapOf("f" to "x", "a" to "0", "b" to "3"))
        assertTrue(o.lines.first().text, o.lines.first().text.contains("V = 9π"))
        assertNotNull(o.scene)
    }

    @Test
    fun improper() {
        val t = text("improper", mapOf("f" to "1÷x²", "a" to "1"))
        assertTrue(t, t.contains("Converges: ∫ ≈ 0.99999"))
        val d = text("improper", mapOf("f" to "1÷x", "a" to "1"))
        assertTrue(d, d.contains("diverge"))
        val s = text("improper", mapOf("f" to "1÷√x", "a" to "0", "b" to "1"))
        assertTrue(s, s.contains("Converges: ∫ ≈ 1.99"))
    }

    @Test
    fun arcLength() {
        // A straight line from (0, 0) to (3, 4).
        val t = text("arc-length", mapOf("f" to "4x÷3", "a" to "0", "b" to "3"))
        assertTrue(t, t.contains("L ≈ 5"))
    }
}
