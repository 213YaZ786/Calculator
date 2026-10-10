package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgramsTest {

    @Before
    fun french() = Locale.setDefault(Locale.FRANCE)

    private fun run(id: String, vararg values: Pair<String, Double?>) = programs.first { it.id == id }.run(mapOf(*values))
    private fun text(id: String, vararg values: Pair<String, Double?>) = run(id, *values).lines.joinToString("\n") { it.text }

    @Test
    fun gravitationSolvedForAnyQuantity() {
        // Earth and Moon.
        val force = run("gravitation", "F" to null, "m₁" to 5.972e24, "m₂" to 7.348e22, "d" to 3.844e8).found["F"]!!
        assertEquals(1.982e20, force, 0.002e20)
        val d = run("gravitation", "F" to force, "m₁" to 5.972e24, "m₂" to 7.348e22, "d" to null).found["d"]!!
        assertEquals(3.844e8, d, 1.0)
        val m = run("gravitation", "F" to force, "m₁" to null, "m₂" to 7.348e22, "d" to 3.844e8).found["m₁"]!!
        assertEquals(5.972e24, m, 1e19)
    }

    @Test
    fun negativeQuantities() {
        val a = run("newton2", "F" to -20.0, "m" to 4.0, "a" to null).found["a"]!!
        assertEquals(-5.0, a, 1e-9)
        val image = run("lens", "OA'" to null, "OA" to -0.3, "f'" to 0.1).found["OA'"]!!
        assertEquals(0.15, image, 1e-9)
    }

    @Test
    fun quadratic() {
        val t = text("quadratic", "a" to 2.0, "b" to 3.0, "c" to -1.0)
        assertTrue(t, t.contains("Δ = b² − 4ac = 17"))
        assertTrue(t, t.contains("(−3 ± √17) / 4"))
        val c = text("quadratic", "a" to 1.0, "b" to 2.0, "c" to 5.0)
        assertTrue(c, c.contains("z₁ = −1 − 2i"))
    }

    @Test
    fun arithmetic() {
        assertTrue(text("gcd", "a" to 252.0, "b" to 198.0).contains("PGCD = 18"))
        assertTrue(text("bezout", "a" to 240.0, "b" to 46.0).contains("240 × (−9) + 46 × (47) = 2".replace("−", "-")))
        assertTrue(text("factor", "n" to 360.0).contains("360 = 2³ × 3² × 5"))
        assertTrue(text("congruence", "a" to 3.0, "b" to 4.0, "n" to 7.0).contains("x ≡ 6 [7]"))
        assertTrue(text("modpow", "a" to 3.0, "k" to 100.0, "n" to 7.0).contains("3^100 ≡ 4 [7]"))
    }

    @Test
    fun matricesAndChains() {
        val t = text("matrix2", "a" to 2.0, "b" to 1.0, "c" to 1.0, "d" to 1.0, "n" to 2.0)
        assertTrue(t, t.contains("det A = 1"))
        assertTrue(t, t.contains("A⁻¹ = (1  −1)   (−1  2)"))
        assertTrue(t, t.contains("Aⁿ = (5  3)   (3  2)"))
    }

    @Test
    fun missingValues() {
        assertTrue(text("ohm", "U" to null, "R" to null, "I" to 2.0).startsWith("Remplissez"))
    }
}
