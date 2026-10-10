package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The programs written from the curricula, on cases known by heart. */
class CurriculumTest {

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    private fun out(id: String, numbers: Map<String, Double?> = emptyMap(), texts: Map<String, String> = emptyMap()): Outcome {
        val p = programs.first { it.id == id }
        val values = p.fields.associate { it.symbol to numbers[it.symbol] }
        return p.run(values, texts, degrees = false)
    }

    private fun text(id: String, numbers: Map<String, Double?> = emptyMap(), texts: Map<String, String> = emptyMap()) =
        out(id, numbers, texts).lines.joinToString("\n") { it.text }

    @Test
    fun everyProgramHasAUniqueId() {
        val ids = programs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun threshold() {
        // uₙ₊₁ = 2uₙ from 1 passes 1000 at n = 10.
        val t = text("threshold", mapOf("u₀" to 1.0, "S" to 1000.0), mapOf("f" to "2x"))
        assertTrue(t, t.contains("n = 10"))
    }

    @Test
    fun newtonFindsRootTwo() {
        val t = text("newton", mapOf("x₀" to 1.0), mapOf("f" to "x²−2"))
        assertTrue(t, t.contains("1.414213562"))
    }

    @Test
    fun integralOfSquare() {
        val t = text("integral", mapOf("a" to 0.0, "b" to 1.0, "n" to 10.0), mapOf("f" to "x²"))
        assertTrue(t, t.contains("Simpson : 0.3333333333"))
    }

    @Test
    fun taylorOfExp() {
        val t = text("taylor", mapOf("a" to 0.0, "n" to 4.0), mapOf("f" to "e^x"))
        assertTrue(t, t.contains("(1/24)x⁴"))
    }

    @Test
    fun chineseRemainders() {
        // x ≡ 2 [3], x ≡ 3 [5], x ≡ 2 [7]: x = 23.
        val t = text("crt", texts = mapOf("a" to "2,3,2", "n" to "3,5,7"))
        assertTrue(t, t.contains("x ≡ 23 [105]"))
    }

    @Test
    fun totient() {
        val t = text("euler-phi", mapOf("n" to 36.0))
        assertTrue(t, t.contains("φ(36) = 12"))
        assertTrue(t, t.contains("9 divisors, summing to σ = 91"))
    }

    @Test
    fun rsaRoundTrip() {
        val t = text("rsa", mapOf("p" to 61.0, "q" to 53.0, "e" to 17.0, "m" to 65.0))
        assertTrue(t, t.contains("d = e⁻¹ mod φ(n) = 2753"))
        assertTrue(t, t.contains("Ciphertext c = mᵉ mod n = 2790"))
        assertTrue(t, t.contains("Decrypted cᵈ mod n = 65"))
    }

    @Test
    fun gaussSolves() {
        // x + y + z = 6, 2y + 5z = −4, 2x + 5y − z = 27: (5 ; 3 ; −2).
        val t = text("gauss", texts = mapOf("A" to "1,1,1,0,2,5,2,5,−1", "B" to "6,−4,27"))
        assertTrue(t, t.contains("X = (5 ; 3 ; −2)"))
    }

    @Test
    fun eigenvalues() {
        val t = text("eigen", texts = mapOf("A" to "2,0,0,3"))
        assertTrue(t, t.contains("λ₁ = 2") || t.contains("λ₂ = 2"))
        assertTrue(t, t.contains("= 3"))
    }

    @Test
    fun bayes() {
        val t = text("bayes", mapOf("P(A)" to 0.01, "P(B|A)" to 0.99, "P(B|Ā)" to 0.05))
        assertTrue(t, t.contains("P(A|B) = 0.1666666667"))
    }

    @Test
    fun chiSquaredFit() {
        val t = text("chi2-fit", texts = mapOf("O" to "50,50", "E" to "0.5,0.5"))
        assertTrue(t, t.contains("χ² = 0"))
    }

    @Test
    fun counting() {
        val t = text("counting", mapOf("n" to 5.0, "k" to 2.0))
        assertTrue(t, t.contains("C(n, k) = 10"))
        assertTrue(t, t.contains("A = 20"))
    }

    @Test
    fun planeThroughThreePoints() {
        val o = out("plane-3-points", texts = mapOf("A" to "1,0,0", "B" to "0,1,0", "C" to "0,0,1"))
        val t = o.lines.joinToString("\n") { it.text }
        assertTrue(t, t.contains("1x + 1y + 1z − 1 = 0"))
        assertNotNull(o.scene)
    }

    @Test
    fun distanceToPlane() {
        val t = text("point-plane", texts = mapOf("M" to "0,0,0", "P" to "1,2,2,−9"))
        assertTrue(t, t.contains("Distance 3"))
        assertTrue(t, t.contains("H = (1 ; 2 ; 2)"))
    }

    @Test
    fun skewLines() {
        val t = text("two-lines", texts = mapOf("A" to "0,0,0", "u" to "1,0,0", "B" to "0,1,1", "v" to "0,1,0"))
        assertTrue(t, t.contains("Skew lines, distance 1"))
    }

    @Test
    fun syracuse() {
        val t = text("syracuse", mapOf("N" to 27.0))
        assertTrue(t, t.contains("Flight time 111, highest value 9232"))
    }
}
