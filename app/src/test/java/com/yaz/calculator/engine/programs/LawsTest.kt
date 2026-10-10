package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The law calculator on values from the tables. */
class LawsTest {

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    private fun out(id: String, numbers: Map<String, Double>): Outcome {
        val p = programs.first { it.id == id }
        return p.run(p.fields.associate { it.symbol to numbers[it.symbol] }, emptyMap(), degrees = false)
    }

    private fun prob(id: String, numbers: Map<String, Double>): Double {
        val t = out(id, numbers).lines.first().text
        return t.substringAfterLast("= ").toDouble()
    }

    @Test
    fun everyLawGivesTheTablesValue() {
        assertEquals(0.623046875, prob("law-binomial", mapOf("n" to 10.0, "p" to 0.5, "b" to 5.0)), 1e-9)
        assertEquals(0.95, prob("law-normal", mapOf("μ" to 0.0, "σ" to 1.0, "a" to -1.959964, "b" to 1.959964)), 1e-6)
        assertEquals(0.1353352832, prob("law-poisson", mapOf("λ" to 2.0, "a" to 0.0, "b" to 0.0)), 1e-9)
        assertEquals(0.75, prob("law-geometric", mapOf("p" to 0.5, "b" to 2.0)), 1e-12)
        assertEquals(0.6321205588, prob("law-exponential", mapOf("λ" to 1.0, "b" to 1.0)), 1e-9)
        assertEquals(0.3, prob("law-uniform", mapOf("α" to 0.0, "β" to 10.0, "a" to 2.0, "b" to 5.0)), 1e-12)
    }

    @Test
    fun theLawsAreOneFamilyWithFigures() {
        val family = programs.filter { it.family == "law" }
        assertEquals(6, family.size)
        assertTrue(family.all { p -> p.fields.any { it.symbol == "a" } && p.fields.any { it.symbol == "b" } })
        assertNotNull(out("law-binomial", mapOf("n" to 20.0, "p" to 0.3)).figure)
    }
}
