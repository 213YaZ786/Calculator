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
}
