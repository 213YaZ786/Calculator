package com.yaz.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CalcTest {

    private val calc = Calc()

    private fun value(text: String, degrees: Boolean = true): String = Format.write(calc.evaluate(text, degrees), 16).let { it.text + if (it.more) "…" else "" }

    @Test
    fun exactArithmetic() {
        assertEquals("1", value("1÷3×3"))
        assertEquals("0.3", value("0.1+0.2"))
        assertEquals("14", value("2+3×4"))
        assertEquals("−4", value("−2^2"))
        assertEquals("512", value("2^3^2"))
        assertEquals("120", value("5!"))
        assertEquals("0.001", value("1E−3"))
    }

    @Test
    fun percentAsPeopleMeanIt() {
        assertEquals("110", value("100+10%"))
        assertEquals("180", value("200−10%"))
        assertEquals("0.5", value("50%"))
        assertEquals("15", value("150×10%"))
    }

    @Test
    fun implicitMultiplicationAndOpenParentheses() {
        assertTrue(value("2π").startsWith("6.28318530717"))
        assertEquals("5", value("(2+3"))
        assertEquals("20", value("4(2+3)"))
        assertEquals("2", value("2+"))
    }

    @Test
    fun anglesAreExact() {
        assertEquals("0.5", value("sin(30)"))
        assertEquals("1", value("tan(45)"))
        assertEquals("30", value("sin⁻¹(0.5)"))
        assertEquals("0", value("sin(π)", degrees = false))
    }

    @Test
    fun logarithms() {
        assertEquals("3", value("log(1000)"))
        assertEquals("1", value("ln(e)"))
        assertEquals("1000", value("10^3"))
    }

    @Test
    fun errorsAreNamed() {
        expect(CalcError.SYNTAX) { calc.evaluate("2)", true) }
        expect(CalcError.SYNTAX) { calc.evaluate("", true) }
        try {
            calc.evaluate("1÷0", true)
            fail()
        } catch (_: ArithmeticException) {
        }
        expect(CalcError.TOO_LARGE) { calc.evaluate("10000!", true) }
    }

    @Test
    fun interesting() {
        assertFalse(calc.interesting("12"))
        assertFalse(calc.interesting("−12"))
        assertFalse(calc.interesting("12+"))
        assertTrue(calc.interesting("12+3"))
        assertTrue(calc.interesting("π"))
    }

    @Test
    fun storedResultsStayExact() {
        val root2 = calc.evaluate("√2", true)
        val withRef = Calc(ref = { if (it == '') root2 else null })
        assertEquals("2", Format.write(withRef.evaluate("×", true), 16).text)
    }

    @Test
    fun exactForms() {
        assertEquals("2√2", calc.evaluate("√8", true).toNiceString())
        assertEquals("1/3", calc.evaluate("1÷3", true).toNiceString())
    }

    private fun expect(error: CalcError, block: () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: CalcException) {
            assertEquals(error, e.error)
        }
    }
}
