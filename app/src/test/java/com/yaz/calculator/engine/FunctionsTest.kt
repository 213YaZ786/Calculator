package com.yaz.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionsTest {

    private fun calc(text: String, degrees: Boolean = false) = Format.write(Calc().evaluate(text, degrees), 14).text

    @Test
    fun numMenu() {
        assertEquals("3.5", calc("abs(−3.5)"))
        assertEquals("−4", calc("int(−3.5)"))
        assertEquals("−3", calc("iPart(−3.5)"))
        assertEquals("−0.5", calc("fPart(−3.5)"))
        assertEquals("3.14", calc("round(π,2)"))
        assertEquals("2", calc("min(5,2,9)"))
        assertEquals("9", calc("max(5,2,9)"))
        assertEquals("6", calc("gcd(12,18)"))
        assertEquals("36", calc("lcm(12,18)"))
        assertEquals("2", calc("remainder(17,5)"))
    }

    @Test
    fun mathAndProb() {
        assertEquals("−3", calc("∛(−27)"))
        assertEquals("2", calc("root(16,4)"))
        assertEquals("3", calc("logBASE(8,2)"))
        assertEquals("60", calc("nPr(5,3)"))
        assertEquals("10", calc("nCr(5,2)"))
        assertEquals("1", calc("cosh(0)"))
        assertEquals("0", calc("sinh⁻¹(0)"))
    }

    @Test
    fun calculus() {
        assertEquals("6", calc("nDeriv(x²,x,3)"))
        assertEquals("0.333333333333", calc("fnInt(x²,0,1)"))
        assertEquals("5050", calc("Σ(x,x,1,100)"))
        assertEquals("1", calc("fMin(x²−2x,−5,5)").take(1))
    }
}
