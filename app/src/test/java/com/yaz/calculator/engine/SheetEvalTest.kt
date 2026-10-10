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

    private fun measured(text: String, rates: Map<String, String>? = null) =
        SheetEval(NumberStyle.of(Locale.US, false), rates).evaluate(text, true).map { v ->
            v?.let {
                val unit = when (val m = it.measure) { is Physical -> m.unit.symbol; is Money -> m.code; null -> "" }
                Format.write(it.value, 12).text + " " + unit
            }
        }

    @Test
    fun unitsAsSoulverReadsThem() {
        assertEquals(listOf("2 km"), measured("1km + 1000m"))
        assertEquals(listOf("320 km"), measured("300 + 20 km"))
        assertEquals(listOf("7.6428656645 mi"), measured("12 km + 300 m in mi"))
        assertEquals(listOf("12.7 cm"), measured("5 in in cm"))
        assertEquals(listOf("37.777777777 °C"), measured("100 °F in °C"))
        assertEquals(listOf("3 mi"), measured("3 miles"))
        assertEquals(listOf("1.5 h"), measured("90 minutes to hours"))
    }

    @Test
    fun unitsThatDoNotGoTogetherHaveNoValue() {
        assertEquals(listOf(null), measured("3 kg × 3 L"))
        assertEquals(listOf(null), measured("2 km × 3 km"))
        assertEquals(listOf(null), measured("2 km + 3 kg"))
    }

    @Test
    fun currencies() {
        assertEquals(listOf("50 USD"), measured("$20 + 30"))
        assertEquals(listOf("6750 DZD"), measured("45 € in DZD", mapOf("DZD" to "150")))
        assertEquals(listOf(null), measured("$200 + €200"))
        assertEquals(listOf("381.81818181 EUR"), measured("$200 + €200", mapOf("USD" to "1.1")))
    }

    @Test
    fun measuredNamesAndTotals() {
        assertEquals(listOf("800 EUR", "9600 EUR"), measured("rent = 800 €\nrent × 12"))
        assertEquals(listOf("320 EUR", "800 EUR", "1120 EUR"), measured("Food: 320 €\nRent: 800 €\nTotal"))
        assertEquals(listOf("12 "), measured("Lunch 12 in Paris"))
    }
}
