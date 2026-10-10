package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The probability programs on cases worked by hand. */
class ProbabilityTest {

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    private fun out(id: String, numbers: Map<String, Double>): Outcome {
        val p = programs.first { it.id == id }
        return p.run(p.fields.associate { it.symbol to numbers[it.symbol] }, emptyMap(), degrees = false)
    }

    private fun text(id: String, numbers: Map<String, Double>) = out(id, numbers).lines.joinToString("\n") { it.text }

    @Test
    fun trees() {
        val t = text("bernoulli-tree", mapOf("n" to 3.0, "p" to 0.5, "k" to 2.0))
        assertTrue(t, t.contains("P(X = 2) = 0.375"))
        assertTrue(t, t.contains("3 paths"))
        val c = text("conditional-tree", mapOf("P(A)" to 0.3, "P_A(B)" to 0.8, "P_Ā(B)" to 0.1))
        assertTrue(c, c.contains("= 0.31"))
        assertTrue(c, c.contains("P_B(A) = P(A∩B)/P(B) = 0.7741935484"))
    }

    @Test
    fun screening() {
        // 1 % ill, 99 % sensitive, 95 % specific: a positive is ill one time in six.
        val t = text("diagnostic", mapOf("prév" to 0.01, "sens" to 0.99, "spéc" to 0.95))
        assertTrue(t, t.contains("16.67 %"))
    }

    @Test
    fun pascalAndDice() {
        val t = text("pascal", mapOf("n" to 4.0))
        assertTrue(t, t.contains("1 4 6 4 1"))
        val d = text("dice-sum", mapOf("n" to 2.0))
        assertTrue(d, d.contains("E(S) = 3,5 × 2 = 7"))
        assertTrue(d, d.contains("most likely sum 7"))
    }

    @Test
    fun overbookingFindsTheSmallestK() {
        // B(10, 0.9): P(X > 9) = 0.9^10 ≈ 0.349 > 0.05, so 10 seats.
        val t = text("overbooking", mapOf("n" to 10.0, "p" to 0.9, "α" to 0.05))
        assertTrue(t, t.contains("10 seats needed"))
    }

    @Test
    fun concentration() {
        val t = text("concentration-law", mapOf("n" to 100.0, "p" to 0.5, "δ" to 0.1))
        assertTrue(t, t.contains("Chebyshev's bound: 0.25"))
        assertTrue(t, t.contains("Exact value (binomial): 0.0568"))
    }

    @Test
    fun simulationsHaveFigures() {
        assertNotNull(out("galton", mapOf("n" to 8.0, "N" to 500.0)).figure)
        assertNotNull(out("random-walk", mapOf("n" to 100.0, "N" to 100.0)).figure)
        val s = text("samples", mapOf("p" to 0.5, "n" to 100.0, "N" to 400.0))
        val share = Regex("([0-9.]+) % of the frequencies").find(s)!!.groupValues[1].toDouble()
        assertTrue(s, share in 88.0..100.0)
    }
}
