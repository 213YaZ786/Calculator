package com.yaz.calculator.feature.calc

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import com.yaz.calculator.engine.Sym

/** What a key press did, for its feel under the finger. */
enum class Feel { TICK, DONE, REJECT }

/**
 * Typing an expression with keys, where the cursor is, as Google's and
 * Apple's calculators do: a second operator replaces the first (except a
 * minus for a negative number), one point per number, one key for both
 * parentheses, a function deleted whole. Used by the calculator and the
 * converter alike.
 */
class Typing(val text: TextFieldState) {

    fun digit(d: Char): Feel {
        insert(d.toString())
        return Feel.TICK
    }

    fun point(): Feel {
        val before = before()
        var i = before.length
        while (i > 0 && before[i - 1].isDigit()) i--
        if (i > 0 && (before[i - 1] == Sym.POINT || before[i - 1] == Sym.EXP)) return Feel.REJECT
        insert(if (before.isEmpty() || !before.last().isDigit()) "0." else ".")
        return Feel.TICK
    }

    fun operator(op: Char): Feel {
        if (!text.selection.collapsed) { insert(op.toString()); return Feel.TICK }
        val before = before()
        if (before.isEmpty()) {
            if (op != Sym.MINUS) return Feel.REJECT
            insert(op.toString())
            return Feel.TICK
        }
        val last = before.last()
        when {
            last in Sym.BINARY -> {
                val afterStrong = before.length >= 2 && before[before.length - 2] in STRONG
                when {
                    op == Sym.MINUS && last in STRONG -> insert(op.toString())
                    last == Sym.MINUS && afterStrong -> replaceBefore(2, op.toString())
                    else -> replaceBefore(1, op.toString())
                }
            }
            last == Sym.OPEN || last == Sym.SQRT || Sym.FUNCTIONS.any { before.endsWith(it) } -> {
                if (op != Sym.MINUS) return Feel.REJECT
                insert(op.toString())
            }
            else -> insert(op.toString())
        }
        return Feel.TICK
    }

    /** ! % ² and ): only after a value. */
    fun suffix(c: Char): Feel {
        val before = before()
        if (before.isEmpty() || !endsValue(before.last())) return Feel.REJECT
        if (c == Sym.CLOSE && opened(before) <= 0) return Feel.REJECT
        insert(c.toString())
        return Feel.TICK
    }

    /** Whether the parenthesis key closes one here (a value ends inside an open one). */
    fun closes(): Boolean {
        val before = before()
        return opened(before) > 0 && before.isNotEmpty() && endsValue(before.last())
    }

    fun parenthesis(): Feel {
        insert(if (closes()) Sym.CLOSE.toString() else Sym.OPEN.toString())
        return Feel.TICK
    }

    fun piece(piece: String): Feel {
        insert(piece)
        return Feel.TICK
    }

    fun backspace(): Feel {
        val selection = text.selection
        if (!selection.collapsed) {
            text.edit { replace(selection.min, selection.max, "") }
            return Feel.TICK
        }
        val before = before()
        if (before.isEmpty()) return Feel.REJECT
        val cut = Sym.FUNCTIONS.firstOrNull { before.endsWith(it) }?.length ?: 1
        text.edit { replace(selection.min - cut, selection.min, "") }
        return Feel.TICK
    }

    fun clear() = text.setTextAndPlaceCursorAtEnd("")

    fun insert(piece: String) {
        if (text.text.length + piece.length > MAX_LENGTH) return
        val at = text.selection
        text.edit {
            replace(at.min, at.max, piece)
            selection = TextRange(at.min + piece.length)
        }
    }

    private fun replaceBefore(count: Int, piece: String) {
        val at = text.selection.min
        text.edit {
            replace(at - count, at, piece)
            selection = TextRange(at - count + piece.length)
        }
    }

    private fun before(): String = text.text.substring(0, text.selection.min)

    private fun opened(before: String) =
        before.count { it == Sym.OPEN } - before.count { it == Sym.CLOSE }

    companion object {
        private val STRONG = setOf(Sym.TIMES, Sym.DIVIDE, Sym.POWER)
        const val MAX_LENGTH = 1_000

        fun endsValue(c: Char) = c.isDigit() || c == Sym.CLOSE || c == Sym.PI || c == Sym.E || c == 'x' ||
            c == Sym.FACT || c == Sym.PERCENT || c == Sym.SQUARE || Sym.isRef(c)
    }
}
