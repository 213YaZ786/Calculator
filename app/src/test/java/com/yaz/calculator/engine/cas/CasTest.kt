package com.yaz.calculator.engine.cas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The symbolic engine on the primitives of the textbooks: each found, checked by its derivative. */
class CasTest {

    private fun prim(text: String): Ex {
        val f = parse(text)
        val p = integrate(f, french = false)
        assertNotNull("no primitive for $text; steps ${p.steps}", p.result)
        assertTrue("${show(p.result!!)} is not a primitive of $text", derivative(p.result).sameAs(f))
        return p.result
    }

    @Test
    fun printsAsWritten() {
        assertEquals("3x² − 2x + 1", show(parse("3x²−2x+1")))
        assertEquals("x³", show(parse("x×x²")))
        assertEquals("1/2", show(parse("0.5")))
        assertEquals("2√3", show(parse("√12")))
        assertEquals("1/2", show(parse("sin(π÷6)")))
    }

    @Test
    fun derivatives() {
        assertEquals("2x", show(derivative(parse("x²+1"))))
        assertTrue(derivative(parse("x×e^x")).sameAs(parse("e^x+x×e^x")))
        assertTrue(derivative(parse("ln(x²+1)")).sameAs(parse("2x÷(x²+1)")))
        assertTrue(derivative(parse("sin(3x)")).sameAs(parse("3cos(3x)")))
    }

    @Test
    fun table() {
        prim("x³"); prim("1÷x"); prim("e^(2x+1)"); prim("cos(3x)"); prim("√x"); prim("1÷(2x+1)"); prim("ln(x)"); prim("2^x")
        prim("1÷(cos(x))²"); prim("tan(x)")
    }

    @Test
    fun substitution() {
        prim("x×e^(x²)"); prim("2x÷(x²+1)"); prim("(sin(x))³×cos(x)"); prim("ln(x)÷x"); prim("x×(x²+1)^5"); prim("cos(x)×e^(sin(x))")
    }

    @Test
    fun rationalFunctions() {
        prim("1÷(x²+1)"); prim("1÷(x²−1)"); prim("(x+3)÷(x²−3x+2)"); prim("1÷(x²+2x+5)"); prim("x³÷(x²+1)"); prim("1÷(x(x+1)²)")
    }

    @Test
    fun byParts() {
        prim("x×e^x"); prim("x²×e^x"); prim("x×sin(x)"); prim("x×ln(x)"); prim("tan⁻¹(x)"); prim("x×tan⁻¹(x)"); prim("e^x×sin(x)")
    }

    @Test
    fun trigonometry() {
        prim("(sin(x))²"); prim("(cos(x))²"); prim("sin(x)×cos(x)"); prim("(sin(x))³"); prim("sin(2x)×cos(3x)")
    }

    @Test
    fun readsAsTheTextbook() {
        assertEquals("(x² − 2x + 2)eˣ", show(prim("x²×e^x")))
        assertEquals("(x² + 1)⁶/12", show(prim("x×(x²+1)^5")))
        assertEquals("(sin(x) − cos(x))eˣ/2", show(prim("e^x×sin(x)")))
        assertEquals("x·ln(x) − x", show(prim("ln(x)")))
        assertEquals("2ˣ/ln(2)", show(prim("2^x")))
        assertEquals("arctan((x + 1)/2)/2", show(prim("1÷(x²+2x+5)")))
        assertEquals("−2/(x − 1)²", show(together(derivative(parse("(x+1)÷(x−1)")))!!))
    }

    @Test
    fun exactIntegral() {
        val F = prim("x²")
        assertEquals("1/3", show(definite(F, ZERO, ONE)))
        val G = prim("1÷x")
        assertEquals("ln(2)", show(definite(G, ONE, num(2))))
    }
}
