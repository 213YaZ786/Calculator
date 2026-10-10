package com.yaz.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammerTest {

    private fun typed(base: Base, vararg keys: String): ProgExpr {
        var e = ProgExpr()
        for (k in keys) {
            e = when {
                k == "()" -> e.parenthesis()
                k.length == 1 && k[0].isLetterOrDigit() -> e.digit(Character.digit(k[0], 16), base)!!
                else -> e.operator(k)!!
            }
        }
        return e
    }

    @Test
    fun windowsExamples() {
        val e = typed(Base.HEX, "F", "F", ProgExpr.AND, "3", "C")
        assertEquals("FF AND 3C", e.written(Base.HEX))
        assertEquals(0x3CL, e.value())
        assertEquals("0011 1100", ProgExpr.show(0x3C, Base.BIN))
        assertEquals("74", ProgExpr.show(0x3C, Base.OCT))
        assertEquals("255 AND 60", e.written(Base.DEC))
    }

    @Test
    fun precedenceAsInC() {
        assertEquals(1L or (2L and 3L), typed(Base.DEC, "1", ProgExpr.OR, "2", ProgExpr.AND, "3").value())
        assertEquals(1L shl 4, typed(Base.DEC, "1", ProgExpr.SHL, "2", "+", "2").value())
        assertEquals(-1L, typed(Base.DEC, ProgExpr.NOT, "0").value())
        assertEquals("FFFFFFFFFFFFFFFF", ProgExpr.show(-1, Base.HEX))
        assertEquals("−1", ProgExpr.show(-1, Base.DEC))
        assertEquals(7L, typed(Base.DEC, "2", "2", "%", "5", "+", "5").value())
    }

    @Test
    fun digitsOutsideTheBaseAndOverflow() {
        assertNull(ProgExpr().digit(2, Base.BIN))
        assertNull(ProgExpr().digit(8, Base.OCT))
        var e = ProgExpr()
        repeat(16) { e = e.digit(15, Base.HEX)!! }
        assertNull(e.digit(0, Base.HEX))
    }

    @Test
    fun parenthesesCloseAtTheEnd() {
        assertEquals(20L, typed(Base.DEC, "()", "2", "+", "3", "()", "×", "4").value())
        assertEquals(5L, typed(Base.DEC, "()", "2", "+", "3").value())
    }
}
