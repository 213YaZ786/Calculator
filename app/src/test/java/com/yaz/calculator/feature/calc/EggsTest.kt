package com.yaz.calculator.feature.calc

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EggsTest {

    @Test
    fun words() {
        assertEquals("hELLO", Eggs.word("07734"))
        assertEquals("ⵣ", Eggs.word("213786"))
        assertNull(Eggs.word("7734"))
    }

    @Test
    fun piDay() {
        assertTrue(Eggs.piDay(LocalDate.of(2027, 3, 14)))
        assertFalse(Eggs.piDay(LocalDate.of(2027, 3, 15)))
    }

    @Test
    fun scienceEggs() {
        assertEquals(ScienceEgg.GOLDEN, ScienceEgg.of((1 + Math.sqrt(5.0)) / 2))
        assertEquals(ScienceEgg.LIGHT, ScienceEgg.of(299_792_458.0))
        assertEquals(ScienceEgg.AVOGADRO, ScienceEgg.of(6.02214076e23))
        assertNull(ScienceEgg.of(1.618))
    }

    @Test
    fun sheetEggs() {
        val out = com.yaz.calculator.engine.SheetEval(com.yaz.calculator.engine.NumberStyle.of(java.util.Locale.US, false)).evaluate("e^(iπ) + 1\nz² + c", true)
        assertEquals("Euler", out[0]!!.egg)
        assertEquals("Mandelbrot", out[1]!!.egg)
    }
}
