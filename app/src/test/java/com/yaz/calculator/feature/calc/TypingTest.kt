package com.yaz.calculator.feature.calc

import androidx.compose.foundation.text.input.TextFieldState
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingTest {

    private fun typed(vararg keys: (Typing) -> Unit): String {
        val t = Typing(TextFieldState())
        keys.forEach { it(t) }
        return t.text.text.toString()
    }

    @Test
    fun aSecondOperatorReplacesTheFirst() {
        assertEquals("5×", typed({ it.digit('5') }, { it.operator('+') }, { it.operator('×') }))
        assertEquals("5×−", typed({ it.digit('5') }, { it.operator('×') }, { it.operator('−') }))
        assertEquals("5+", typed({ it.digit('5') }, { it.operator('×') }, { it.operator('−') }, { it.operator('+') }))
    }

    @Test
    fun onePointPerNumber() {
        assertEquals("0.5", typed({ it.point() }, { it.digit('5') }, { it.point() }))
        assertEquals("1.5+0.", typed({ it.digit('1') }, { it.point() }, { it.digit('5') }, { it.operator('+') }, { it.point() }))
    }

    @Test
    fun oneKeyForBothParentheses() {
        assertEquals("(2)", typed({ it.parenthesis() }, { it.digit('2') }, { it.parenthesis() }))
        assertEquals("2×(", typed({ it.digit('2') }, { it.operator('×') }, { it.parenthesis() }))
    }

    @Test
    fun aFunctionGoesWhole() {
        assertEquals("2", typed({ it.digit('2') }, { it.piece("sin(") }, { it.backspace() }))
    }

    @Test
    fun noOperatorToStart() {
        assertEquals("−", typed({ it.operator('×') }, { it.operator('−') }))
    }
}
