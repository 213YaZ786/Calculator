package com.yaz.calculator.engine.programs

import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The vector and geometry programs on cases known by heart; lists typed with the "," key. */
class VectorsTest {

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
    fun chaslesInThePlaneAndInSpace() {
        val flat = out("chasles-vectors", mapOf("A" to "0,0", "B" to "2,1", "C" to "3,4"))
        assertTrue(flat.lines.joinToString { it.text }, flat.lines.any { "AC = (3 ; 4)" in it.text })
        assertNotNull(flat.figure)
        val space = out("chasles-vectors", mapOf("A" to "0,0,0", "B" to "1,0,0", "C" to "1,1,0", "D" to "1,1,1"))
        assertTrue(space.lines.first().text, space.lines.first().text == "AB + BC + CD = AD")
        assertNotNull(space.scene)
    }

    @Test
    fun collinearAndAligned() {
        assertTrue(text("collinear", mapOf("u" to "2,3", "v" to "4,6")).contains("Collinear: v = 2·u"))
        assertTrue(text("alignment", mapOf("A" to "0,0", "B" to "1,1", "C" to "3,3")).contains("are in a line"))
        assertTrue(text("alignment", mapOf("A" to "0,0", "B" to "1,1", "C" to "3,4")).contains("not in a line"))
    }

    @Test
    fun lines() {
        val t = text("line-two-points", mapOf("A" to "1,2", "B" to "3,6"))
        assertTrue(t, t.contains("y = 2x"))
        val i = text("two-lines-plane", mapOf("d" to "1,−1,0", "d′" to "1,1,−2"))
        assertTrue(i, i.contains("I(1 ; 1)"))
        assertTrue(i, i.contains("perpendicular"))
        val p = text("two-lines-plane", mapOf("d" to "1,1,0", "d′" to "2,2,4"))
        assertTrue(p, p.contains("parallel"))
    }

    @Test
    fun bases() {
        val t = text("decompose", mapOf("w" to "3,5", "u" to "1,1", "v" to "1,2"))
        assertTrue(t, t.contains("w = 1u + 2v"))
        val s = text("decompose", mapOf("w" to "1,2,3", "u" to "1,0,0", "v" to "0,1,0", "t" to "0,0,1"))
        assertTrue(s, s.contains("w = 1u + 2v + 3t"))
        val c = text("coplanar", mapOf("u" to "1,0,0", "v" to "0,1,0", "w" to "2,3,0"))
        assertTrue(c, c.contains("Coplanar: w = 2u + 3v"))
    }

    @Test
    fun projectionAndCircles() {
        val t = text("normal-projection", mapOf("A" to "0,0", "n" to "0,1", "M" to "3,4"))
        assertTrue(t, t.contains("H(3 ; 0)"))
        assertTrue(t, t.contains("MH = 4"))
        val c = text("circle-equation", mapOf("e" to "−2,4,−4"))
        assertTrue(c, c.contains("Centre Ω(1 ; −2), radius 3"))
        val d = text("right-angle-circle", mapOf("A" to "−1,0", "B" to "1,0"))
        assertTrue(d, d.contains("I(0 ; 0)"))
    }

    @Test
    fun triangleCentres() {
        // Right angle at A: H = A, O the middle of the hypotenuse.
        val t = text("triangle-centres", mapOf("A" to "0,0", "B" to "4,0", "C" to "0,3"))
        assertTrue(t, t.contains("Orthocentre H(0 ; 0)"))
        assertTrue(t, t.contains("O(2 ; 1.5), R = 5/2 = 2.5"))
        assertTrue(t, t.contains("Area 6"))
    }

    @Test
    fun parabolaAndCube() {
        val p = text("parabola", mapOf("a" to "1", "b" to "−2", "c" to "−3"))
        assertTrue(p, p.contains("Vertex S(1 ; −4)"))
        assertTrue(p, p.contains("x = −1 ; 3"))
        // Through three corners' neighbours: the plane x + y + z = 1.5 cuts a regular hexagon.
        val c = text("cube-section", mapOf("a" to "1", "P" to "1,0.5,0", "Q" to "0.5,1,0", "R" to "0,1,0.5"))
        assertTrue(c, c.contains("a hexagon"))
        val tri = text("cube-section", mapOf("a" to "1", "P" to "1,0,0", "Q" to "0,1,0", "R" to "0,0,1"))
        assertTrue(tri, tri.contains("a triangle"))
    }

    @Test
    fun angles() {
        val t = text("angles-space", mapOf("u" to "1,0,0", "v" to "1,1,0"))
        assertTrue(t, t.contains("45°"))
    }
}
