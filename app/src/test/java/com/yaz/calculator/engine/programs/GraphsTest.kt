package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Maths expertes: graphs, chains, ciphers, on cases worked by hand. */
class GraphsTest {

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
    fun pathsInATriangle() {
        // The triangle A B C: 2 closed paths of length 2 from A to A.
        val t = text("graph-paths", mapOf("A" to "0,1,1,1,0,1,1,1,0", "k" to "2", "i" to "1", "j" to "1"))
        assertTrue(t, t.contains("2 paths of length 2 from A to A"))
        assertTrue(t, t.contains("Eulerian circuit"))
        assertNotNull(out("graph-paths", mapOf("A" to "0,1,1,0", "k" to "1")).figure)
    }

    @Test
    fun markov() {
        val t = text("markov", mapOf("P" to "0.8,0.2,0.3,0.7", "π₀" to "1,0", "n" to "1"))
        assertTrue(t, t.contains("π1 = (0.8 ; 0.2)"))
        assertTrue(t, t.contains("π = (0.6 ; 0.4)"))
    }

    @Test
    fun recurrenceAndPageRank() {
        val t = text("affine-recurrence", mapOf("A" to "0.5,0,0,0.5", "C" to "1,2", "U₀" to "0,0", "n" to "40"))
        assertTrue(t, t.contains("Fixed point U = (2 ; 4)"))
        val r = text("pagerank", mapOf("L" to "0,1,1,0,0,1,1,0,0", "c" to "0.15"))
        assertTrue(r, r.contains("Ranking: C"))
    }

    @Test
    fun arithmetic() {
        assertTrue(text("sieve", mapOf("N" to "100")).contains("25 primes up to 100"))
        assertTrue(text("pythagorean", mapOf("c" to "30")).contains("(3, 4, 5)"))
        assertTrue(text("pell", mapOf("d" to "2", "k" to "2")).contains("x = 3, y = 2"))
        assertTrue(text("pell", mapOf("d" to "61", "k" to "1")).contains("x = 1766319049, y = 226153980"))
    }

    @Test
    fun ciphers() {
        assertTrue(text("affine-cipher", mapOf("M" to "MATHS", "a" to "3", "b" to "5")).contains("Encrypted: PFKAH"))
        assertTrue(text("vigenere", mapOf("M" to "ATTACK", "K" to "LEMON")).contains("Encrypted: LXFOPV"))
        val h = text("hill-cipher", mapOf("M" to "HELP", "A" to "3,3,2,5"))
        assertTrue(h, h.contains("Encrypted: HIAT"))
    }
}
