package com.yaz.calculator.engine

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    private fun of(text: String) = Calc().evaluate(text, false)

    @Test
    fun cutNeverRounded() {
        val w = Format.write(of("2÷3"), 10)
        assertEquals("0.66666666", w.text)
        assertTrue(w.more)
        assertFalse(w.exact)
    }

    @Test
    fun exactValuesLoseTheirZeros() {
        val w = Format.write(of("1÷8"), 12)
        assertEquals("0.125", w.text)
        assertTrue(w.exact)
    }

    @Test
    fun largeAndSmallTakeAPowerOfTen() {
        assertEquals("1E20", Format.write(of("10^20"), 12).text)
        assertEquals("1.23456789E17", Format.write(of("123456789000000000"), 14).text)
        assertEquals("1.2345E−9", Format.write(of("0.0000000012345"), 14).text)
        assertEquals("−1.5E30", Format.write(of("−15E29"), 14).text)
    }

    @Test
    fun negativeAndZero() {
        assertEquals("−0.25", Format.write(of("−1÷4"), 12).text)
        assertEquals("0", Format.write(of("1−1"), 12).text)
    }

    @Test
    fun moreDigitsWhenUnfolded() {
        val w = Format.write(of("1÷7"), 10, extra = 20)
        assertEquals(10 + 20 - 2, w.text.substringAfter('.').length)
    }

    @Test
    fun languagesWriteNumbersTheirWay() {
        val fr = NumberStyle.of(Locale.FRANCE, true)
        assertEquals("1 234 567,89", fr.show("1234567.89"))
        assertEquals("1234.5", fr.read("1 234,5"))
        val en = NumberStyle.of(Locale.US, true)
        assertEquals("1,234,567.89", en.show("1234567.89"))
        assertEquals("1234.5", en.read("1,234.5"))
        assertEquals("1.5E−12", en.show("1.5E−12"))
        assertEquals("1234", NumberStyle.of(Locale.US, false).show("1234"))
    }
}
