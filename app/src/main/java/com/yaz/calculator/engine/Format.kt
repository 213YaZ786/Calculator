package com.yaz.calculator.engine

import com.hp.creals.Msd
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

/**
 * A value written out: [text] in the app's own form (− sign, . point, E
 * power of ten), [exact] when every digit is there, [more] when further
 * digits exist and can be asked for.
 */
data class Written(val text: String, val exact: Boolean, val more: Boolean)

/**
 * Writes exact values in a given number of characters, as ExactCalculator
 * does: digits are cut, never rounded up, so 2/3 reads 0.666…; a number too
 * long or too small for its room takes a power of ten.
 */
object Format {
    /** An amount of money as it is written: rounded to the cents its currency uses. */
    fun money(value: UnifiedReal, code: String, room: Int): Written? {
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).let { if (it < 0) 4 else it }
        val half = UnifiedReal(5).divide(UnifiedReal.TEN.pow(UnifiedReal((digits + 1).toLong())))
        val rounded = if (value.signum(-200) < 0) value.subtract(half) else value.add(half)
        val text = rounded.toStringTruncated(digits).let { if (digits == 0) it.trimEnd('.') else it }.replace('-', Sym.MINUS)
        return if (text.length <= room) Written(text, exact = true, more = false) else null
    }


    /** How far below one a value is still told from zero: about a thousand decimal places. */
    private const val ZERO_BITS = 3400
    private val LOG10_2 = log10(2.0)

    /**
     * [value] in at most [room] characters. [extra] adds that many further
     * digits, beyond the room, for a result the user unfolds.
     */
    fun write(value: UnifiedReal, room: Int, extra: Int = 0): Written {
        val width = max(room, 6)
        if (value.definitelyZero()) return Written("0", exact = true, more = false)
        val m = Msd.of(value.crValue(), -ZERO_BITS)
        if (m == Int.MIN_VALUE) return Written("0", exact = false, more = true)
        val k = decimalExponent(value, m)
        val sign = if (value.crValue().get_appr(m - 4).signum() < 0) 1 else 0
        val integerDigits = if (k >= 0) k + 1 else 1
        // Plain: the integer part fits, and a small number keeps at least four digits that count.
        val plainFraction = width - sign - integerDigits - 1
        val plain = k < width - sign && (k >= 0 || plainFraction + k + 1 >= 4) && k >= -6
        if (plain) {
            val needed = value.digitsRequired()
            if (needed <= plainFraction + extra) {
                return Written(trim(value.toStringTruncated(needed)), exact = true, more = false)
            }
            val fraction = max(plainFraction, 0) + extra
            return Written(minus(value.toStringTruncated(fraction).let { if (fraction == 0) it.trimEnd('.') else it }), exact = false, more = true)
        }
        // A power of ten: d.ddd…E±k.
        val mantissa = value.divide(UnifiedReal.TEN.pow(UnifiedReal(k.toLong())))
        val suffix = "E" + if (k < 0) "${Sym.MINUS}${-k}" else "$k"
        val digits = max(width - sign - suffix.length - 2, 1) + extra
        val needed = mantissa.digitsRequired()
        return if (needed <= digits) {
            Written(trim(mantissa.toStringTruncated(needed)) + suffix, exact = true, more = false)
        } else {
            Written(minus(mantissa.toStringTruncated(digits)) + suffix, exact = false, more = true)
        }
    }

    /** The power of ten of [value]'s first digit, from its first bit [m], checked on the digits themselves. */
    private fun decimalExponent(value: UnifiedReal, m: Int): Int {
        var k = floor((m - 1) * LOG10_2).toInt()
        repeat(4) {
            val scaled = value.divide(UnifiedReal.TEN.pow(UnifiedReal(k.toLong())))
            val whole = scaled.toStringTruncated(2).trimStart('-').substringBefore('.')
            when {
                whole.length >= 2 -> k++
                whole == "0" -> k--
                else -> return k
            }
        }
        return k
    }

    /** An exact value without the zeros it does not need. */
    private fun trim(text: String): String {
        var t = text
        if (t.contains('.')) t = t.trimEnd('0').trimEnd('.')
        return minus(t)
    }

    private fun minus(text: String) = text.replace('-', Sym.MINUS)
}

/**
 * How the user's language writes numbers: its decimal mark and its digit
 * grouping. Only the screen uses it; the app keeps one form.
 */
class NumberStyle(
    val point: Char,
    /** The grouping shown, null when the user turned it off. */
    val group: Char?,
    /** The language's grouping mark, read in what is pasted even when not shown. */
    private val localGroup: Char
) {

    /** Where a grouping mark goes in [text]: before each of these indices, from the left. */
    fun groupsIn(text: CharSequence): List<Int> {
        if (group == null) return emptyList()
        val at = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isDigit()) { i++; continue }
            var j = i
            while (j < text.length && text[j].isDigit()) j++
            val before = if (i > 0) text[i - 1] else ' '
            val exponent = before == Sym.EXP || before == Sym.MINUS && i > 1 && text[i - 2] == Sym.EXP
            if (before != Sym.POINT && !exponent && j - i > 3) {
                var k = j - 3
                while (k > i) { at += k; k -= 3 }
            }
            i = j
        }
        at.sort()
        return at
    }

    /** [text] as the user reads it. */
    fun show(text: CharSequence): String {
        val groups = groupsIn(text)
        val out = StringBuilder(text.length + groups.size)
        var g = 0
        for ((i, c) in text.withIndex()) {
            while (g < groups.size && groups[g] == i) { out.append(group); g++ }
            out.append(
                when (c) {
                    Sym.POINT -> point
                    Sym.ARG -> if (point == ',') ';' else ','
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /** What someone typed or pasted in their own way, in the app's form: 1 234,5 is 1234.5 here. */
    fun read(text: CharSequence): String {
        val out = StringBuilder(text.length)
        for ((i, c) in text.withIndex()) {
            val betweenDigits = i > 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit()
            when {
                c == point -> out.append(Sym.POINT)
                betweenDigits && (c == localGroup || c == ' ' || c == '\u00A0' || c == '\u202F' || c == '\'' || c == '’') -> Unit
                c == ';' -> out.append(Sym.ARG)
                c == '.' || c == ',' || c == '٫' -> out.append(Sym.POINT)
                c == '-' || c == '–' -> out.append(Sym.MINUS)
                c == '*' || c == '·' || c == '⋅' -> out.append(Sym.TIMES)
                c == '/' || c == '∕' -> out.append(Sym.DIVIDE)
                c == '\u00A0' || c == '\u202F' -> out.append(' ')
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    companion object {
        fun of(locale: Locale, grouping: Boolean): NumberStyle {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            return NumberStyle(symbols.decimalSeparator, if (grouping) symbols.groupingSeparator else null, symbols.groupingSeparator)
        }
    }
}
