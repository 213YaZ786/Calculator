package com.yaz.calculator.engine

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetEvalTest {

    private fun lines(text: String, locale: Locale = Locale.US) =
        SheetEval(NumberStyle.of(locale, true)).evaluate(text, true).map { v -> v?.let { Format.write(it.value, 16).text } }

    @Test
    fun namesLabelsAndTotals() {
        val out = lines("Rent = 800\nFood: 320.50\nTotal")
        assertEquals(listOf("800", "320.5", "1120.5"), out)
    }

    @Test
    fun namesAreUsedBelow() {
        assertEquals(listOf("800", "200", "1000"), lines("rent = 800\nbills = rent / 4\nrent + bills"))
    }

    @Test
    fun wordsAroundNumbersAreLeftOut() {
        assertEquals(listOf("2.4"), lines("Bread 2.40"))
        assertEquals(listOf(null), lines("3 apples 2"))
        assertEquals(listOf("15"), lines("€12 + 3"))
        assertEquals(listOf("12"), lines("3 x 4"))
    }

    @Test
    fun totalsStopAtEmptyLinesAndHeadings() {
        val out = lines("# Week\n10\n20\nsum\n\n5\ntotal")
        assertEquals(listOf(null, "10", "20", "30", null, "5", "5"), out)
    }

    @Test
    fun theUsersDecimalComma() {
        assertEquals(listOf("1234.5"), lines("1 234,5", Locale.FRANCE))
        assertEquals(listOf("3.5"), lines("Somme: 1,5 + 2", Locale.FRANCE))
    }

    @Test
    fun textAloneHasNoValue() {
        val out = lines("Shopping list\n// a comment")
        assertNull(out[0])
        assertNull(out[1])
        assertTrue(out.size == 2)
    }
}
