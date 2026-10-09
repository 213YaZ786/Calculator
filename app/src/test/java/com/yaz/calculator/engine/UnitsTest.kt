package com.yaz.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    private fun convert(category: Category, amount: String, from: String, to: String): String {
        val a = Units.find(category, from)!!
        val b = Units.find(category, to)!!
        val w = Format.write(b.fromBase(a.toBase(Calc().evaluate(amount, false))), 16)
        return w.text + if (w.more) "…" else ""
    }

    @Test
    fun exactConversions() {
        assertEquals("2.54", convert(Category.LENGTH, "1", "in", "cm"))
        assertEquals("1.609344", convert(Category.LENGTH, "1", "mi", "km"))
        assertEquals("212", convert(Category.TEMPERATURE, "100", "c", "f"))
        assertEquals("−40", convert(Category.TEMPERATURE, "−40", "f", "c"))
        assertEquals("36", convert(Category.SPEED, "10", "mps", "kmh"))
        assertEquals("1024", convert(Category.DATA, "1", "MiB", "KiB"))
        assertEquals("180", convert(Category.ANGLE, "π", "rad", "deg"))
    }

    @Test
    fun fuelEconomyIsInverse() {
        assertEquals("10", convert(Category.FUEL, "10", "kml", "l100"))
        assertEquals("23.5214583", convert(Category.FUEL, "10", "l100", "mpg").take(10))
    }

    @Test
    fun everyUnitGoesBackAndForth() {
        for ((category, units) in Units.all) {
            for (u in units) {
                val back = u.fromBase(u.toBase(Calc().evaluate("3", false)))
                // A radian goes through π: back to 3 within a hundred binary places.
                if (u.id == "rad") assertTrue(back.approxEquals(UnifiedReal(3), -100))
                else assertEquals("$category ${u.id}", "3", Format.write(back, 16).text)
            }
        }
    }
}
