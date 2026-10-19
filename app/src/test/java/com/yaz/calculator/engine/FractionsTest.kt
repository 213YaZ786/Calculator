package com.yaz.calculator.engine

import com.yaz.calculator.engine.programs.Solve
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fractions beside decimals. Test protocol: Hipparchus' own FractionTest and BigFractionTest
 * (https://github.com/Hipparchus-Math/hipparchus/blob/4.0.3/hipparchus-core/src/test/java/org/hipparchus/fraction/FractionTest.java:
 * testConstructorDouble, testDoubleConstructor, testGoldenRatio, testMath331, testNegative): a double given
 * back as its numerator and denominator, the golden ratio and other irrationals refused, small
 * and huge values, negatives, zero.
 */
class FractionsTest {

    @After
    fun on() {
        Fractions.enabled = true
    }

    private fun text(v: Double) = Fractions.textOf(v)

    @Test
    fun smallFractions() {
        assertEquals("998/999", text(998.0 / 999.0))
        assertEquals("1/3", text(1.0 / 3.0))
        assertEquals("2/7", text(2.0 / 7.0))
        assertEquals("1/10", text(0.1))
        assertEquals("3/4", text(0.75))
    }

    @Test
    fun floatingSumsStillFractions() {
        assertEquals("3/10", text(0.1 + 0.2))
        assertEquals("1/3", text(0.1 + 0.2 - 0.1 - 0.2 + 1.0 / 3.0))
    }

    @Test
    fun negativeFractions() {
        assertEquals("−998/999", text(-998.0 / 999.0))
        assertEquals("−1/3", text(-1.0 / 3.0))
        assertEquals("−5/2", text(-2.5))
        assertEquals("−1/10", text(-0.1))
    }

    @Test
    fun noFractionForWholeZeroOrIrrational() {
        assertNull(text(0.0))
        assertNull(text(5.0))
        assertNull(text(-7.0))
        assertNull(text(Math.PI))
        assertNull(text(Math.E))
        assertNull(text(Math.sqrt(2.0)))
        assertNull(text(Math.sqrt(3.0) / 2))
        assertNull(text((1 + Math.sqrt(5.0)) / 2))
        assertNull(text(Double.NaN))
        assertNull(text(Double.POSITIVE_INFINITY))
    }

    @Test
    fun tooLargeADenominatorStaysDecimal() {
        assertNull(text(1e-12))
        assertNull(text(12345.0 / 99991.0))
        assertNull(text(0.99658))
        assertNull(text(1.0 / 10001.0))
        assertEquals("1/10000", text(1.0 / 10000.0))
    }

    @Test
    fun largeValues() {
        assertNull(text(1e300))
        assertNull(text(123456789012345678.0))
        assertNull(text(1e15))
        assertEquals("1000001/2", text(500000.5))
        assertNull(text(Double.MAX_VALUE))
        assertNull(text(Double.MIN_VALUE))
    }

    @Test
    fun besideSaysEqualOrApproximately() {
        assertEquals("3/4 = 0.75", Fractions.beside(0.75, "0.75"))
        assertEquals("1/3 ≈ 0.3333333333", Fractions.beside(1.0 / 3.0, "0.3333333333"))
        assertEquals("−1/8 = −0.125", Fractions.beside(-0.125, "−0.125"))
        assertEquals("998/999 ≈ 0.998998999", Fractions.beside(998.0 / 999.0, "0.998998999"))
        assertNull(Fractions.beside(Math.PI, "3.141592654"))
    }

    @Test
    fun switchedOff() {
        Fractions.enabled = false
        assertNull(text(0.75))
        assertNull(Fractions.beside(0.75, "0.75"))
        assertNull(Fractions.exact(Calc().evaluate("1÷3", false)))
        assertEquals("x = 0.75", Fractions.annotate("x = 0.75"))
    }

    @Test
    fun exactValuesOfTheEngine() {
        val calc = Calc()
        assertEquals("998/999", Fractions.exact(calc.evaluate("998÷999", false)))
        assertEquals("1/3", Fractions.exact(calc.evaluate("1÷3", false)))
        assertEquals("−2/7", Fractions.exact(calc.evaluate("−2÷7", false)))
        assertEquals("1/10", Fractions.exact(calc.evaluate("0.1", false)))
        assertNull(Fractions.exact(calc.evaluate("12", false)))
        assertNull(Fractions.exact(calc.evaluate("0", false)))
        assertNull(Fractions.exact(calc.evaluate("π", false)))
        assertNull(Fractions.exact(calc.evaluate("√2", false)))
        assertNull(Fractions.exact(calc.evaluate("12345÷99991", false)))
        assertEquals("12345/99991", Fractions.exact(calc.evaluate("12345÷99991", false), 100_000))
        assertNull(Fractions.exact(calc.evaluate("10^30", false)))
    }

    @Test
    fun resultLinesCarryTheirFraction() {
        Fractions.forget()
        assertEquals("x = 998/999 ≈ 0.998998999 m", Fractions.annotate("x = ${Solve.show(998.0 / 999.0)} m"))
        Fractions.forget()
        assertEquals("x = 3/4 = 0.75", Fractions.annotate("x = ${Solve.show(0.75)}"))
        Fractions.forget()
        assertEquals("x = −1/3 ≈ −0.3333333333 m/s²", Fractions.annotate("x = ${Solve.show(-1.0 / 3.0)} m/s²"))
    }

    @Test
    fun linesThatAreNotASingleValueStay() {
        Fractions.forget()
        val a = Solve.show(0.5)
        val b = Solve.show(0.25)
        assertEquals("y = $a x + $b", Fractions.annotate("y = $a x + $b"))
        assertEquals("[$a ; $b]", Fractions.annotate("[$a ; $b]"))
        assertEquals("cos θ = $a   sin θ = $b", Fractions.annotate("cos θ = $a   sin θ = $b"))
        Fractions.forget()
        val pi = Solve.show(Math.PI)
        assertEquals("π = $pi", Fractions.annotate("π = $pi"))
        // A number the program never showed is left as written.
        assertEquals("x = 0.75", Fractions.annotate("x = 0.75"))
    }
}
