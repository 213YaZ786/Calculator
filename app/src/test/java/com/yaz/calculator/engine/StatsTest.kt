package com.yaz.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StatsTest {

    @Test
    fun oneVar() {
        val d = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val s = Stats.oneVar(d, null, french = false)!!
        assertEquals(5.0, s.mean, 1e-12)
        assertEquals(2.0, s.sigma, 1e-12)
        assertEquals(4.5, s.median, 1e-12)
        assertEquals(4.0, s.q1, 1e-12)
        assertEquals(6.0, s.q3, 1e-12)
        // The French definition: Q3 is a value of the series.
        val f = Stats.oneVar(d, null, french = true)!!
        assertEquals(4.0, f.q1, 1e-12)
        assertEquals(5.0, f.q3, 1e-12)
        // Counts in L2.
        val c = Stats.oneVar(listOf(1.0, 2.0), listOf(3.0, 1.0), french = false)!!
        assertEquals(4.0, c.n, 0.0)
        assertEquals(1.25, c.mean, 1e-12)
    }

    @Test
    fun regressions() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0)
        val lin = Stats.regression(RegKind.LIN, xs, listOf(3.0, 5.0, 7.0, 9.0))!!
        assertEquals(2.0, lin.coefficients[0], 1e-12)
        assertEquals(1.0, lin.coefficients[1], 1e-12)
        assertEquals(1.0, lin.r!!, 1e-12)
        val quad = Stats.regression(RegKind.QUAD, xs, listOf(1.0, 4.0, 9.0, 16.0))!!
        assertEquals(1.0, quad.coefficients[0], 1e-9)
        val e = Stats.regression(RegKind.EXP, xs, listOf(2.0, 4.0, 8.0, 16.0))!!
        assertEquals(1.0, e.coefficients[0], 1e-9)
        assertEquals(2.0, e.coefficients[1], 1e-9)
        // The equation goes to Y= and draws.
        val curve = Curve.of(e.equation, false)
        assertNotNull(curve)
        assertEquals(32.0, curve!!(5.0), 1e-6)
        assertEquals(11.0, Curve.of(lin.equation, false)!!(5.0), 1e-9)
    }
}
