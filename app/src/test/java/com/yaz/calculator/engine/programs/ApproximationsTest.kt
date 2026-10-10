package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The analysis algorithms on cases known by heart. */
class ApproximationsTest {

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    private fun text(id: String, fields: Map<String, String>): String {
        val p = programs.first { it.id == id }
        val values = p.fields.associate { f ->
            f.symbol to if (f.kind == FieldKind.NUMBER) fields[f.symbol]?.let { com.yaz.calculator.engine.Calc().evaluate(it, false).crValue().toDouble() } else null
        }
        return p.run(values, fields, degrees = false).lines.joinToString("\n") { it.text }
    }

    @Test
    fun secantFindsRootTwo() {
        val t = text("secant", mapOf("f" to "x²−2", "a" to "1", "b" to "2"))
        assertTrue(t, t.contains("x ≈ 1.414213562"))
    }

    @Test
    fun eulerAndBriggs() {
        // y′ = 2x from (0, 0): y = x², Euler below it.
        val t = text("euler-primitive", mapOf("f" to "2x", "x₀" to "0", "y₀" to "0", "h" to "0.1", "x" to "1"))
        assertTrue(t, t.contains("y(1) ≈ 0.9"))
        val b = text("briggs", mapOf("x" to "2", "p" to "6"))
        assertTrue(b, b.contains("ln 2 = 0.6931471806"))
        assertTrue(b, b.contains("0.69314"))
    }

    @Test
    fun heronAndConstants() {
        val h = text("heron", mapOf("a" to "2", "x₀" to "1", "n" to "5"))
        assertTrue(h, h.contains("√2 ≈ 1.4142135623730950488"))
        val c = text("constants", mapOf("n" to "20"))
        assertTrue(c, c.contains("e ≈ 2.718281828"))
    }

    @Test
    fun unitCircleExactValues() {
        val t = text("unit-circle", mapOf("θ" to "π÷3"))
        assertTrue(t, t.contains("cos θ = 1/2"))
        assertTrue(t, t.contains("sin θ = √3/2"))
    }

    @Test
    fun cooling() {
        val t = text("newton-cooling", mapOf("T₀" to "90", "Tₐ" to "20", "k" to "0.1", "t" to "10"))
        assertTrue(t, t.contains("T(10) = 45.751"))
    }
}
