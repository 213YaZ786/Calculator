package com.yaz.calculator.engine

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * An expression in x, read once and worked out in doubles, fast enough to
 * draw a curve point by point. The same meaning as Calc (implicit
 * multiplication, √, !, ², %, functions, π, e) without its exactness, which a
 * drawing does not need. [names] are a sheet's values.
 */
class Curve private constructor(private val root: Node) {

    /** y at [x]; NaN where it has none. */
    operator fun invoke(x: Double): Double = runCatching { root.at(x) }.getOrDefault(Double.NaN)

    private fun interface Node { fun at(x: Double): Double }

    companion object {
        /** [text] in the app's form, or null when it does not read or does not use x. */
        fun of(text: String, degrees: Boolean, names: (String) -> Double? = { null }): Curve? {
            if ('x' !in Sym.FUNCTIONS.fold(text) { t, f -> t.replace(f, "") }) return null
            return runCatching { Curve(Reader(text, degrees, names).read()) }.getOrNull()
        }
    }

    private class Reader(val s: String, val degrees: Boolean, val names: (String) -> Double?) {
        var i = 0

        fun read(): Node {
            val n = sum()
            skip()
            if (i < s.length) error("unread")
            // Parentheses left open close at the end, as in Calc.
            return n
        }

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skip(); return s.getOrNull(i) }

        private fun sum(): Node {
            var left = product()
            while (true) {
                when (peek()) {
                    Sym.PLUS, '+' -> { i++; val a = left; val b = product(); left = Node { a.at(it) + b.at(it) } }
                    Sym.MINUS, '-' -> { i++; val a = left; val b = product(); left = Node { a.at(it) - b.at(it) } }
                    else -> return left
                }
            }
        }

        private fun product(): Node {
            var left = signed()
            while (true) {
                val c = peek()
                when {
                    c == Sym.TIMES || c == '*' -> { i++; val a = left; val b = signed(); left = Node { a.at(it) * b.at(it) } }
                    c == Sym.DIVIDE || c == '/' -> { i++; val a = left; val b = signed(); left = Node { a.at(it) / b.at(it) } }
                    // 2x, 3(x+1), 2√x, xsin(x): implicit multiplication.
                    c != null && (c.isLetterOrDigit() || c == '(' || c == Sym.SQRT || c == Sym.PI || c == Sym.POINT) -> {
                        val a = left; val b = power(); left = Node { a.at(it) * b.at(it) }
                    }
                    else -> return left
                }
            }
        }

        private fun signed(): Node {
            if (peek() == Sym.MINUS || peek() == '-') { i++; val a = signed(); return Node { -a.at(it) } }
            return power()
        }

        private fun power(): Node {
            val base = suffix(atom())
            if (peek() == Sym.POWER) {
                i++
                val e = signed()
                return Node { base.at(it).pow(e.at(it)) }
            }
            return base
        }

        private fun suffix(n: Node): Node {
            var v = n
            while (true) {
                when (peek()) {
                    Sym.SQUARE -> { i++; val a = v; v = Node { a.at(it).let { y -> y * y } } }
                    Sym.FACT -> { i++; val a = v; v = Node { factorial(a.at(it)) } }
                    Sym.PERCENT -> { i++; val a = v; v = Node { a.at(it) / 100 } }
                    else -> return v
                }
            }
        }

        private fun atom(): Node {
            val c = peek() ?: error("end")
            when {
                c.isDigit() || c == Sym.POINT -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == Sym.POINT)) i++
                    val v = s.substring(start, i).toDouble()
                    return Node { v }
                }
                c == '(' -> {
                    i++
                    val inner = sum()
                    if (peek() == ')') i++
                    return inner
                }
                c == Sym.SQRT -> { i++; val a = suffix(atom()); return Node { sqrt(a.at(it)) } }
                c == Sym.PI -> { i++; return Node { PI } }
                c.isLetter() -> {
                    Sym.functionAt(s, i)?.let { f ->
                        i += f.length
                        val arg = sum()
                        if (peek() == ')') i++
                        return function(f.dropLast(1), arg)
                    }
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    val name = s.substring(start, i)
                    return single(name) ?: run {
                        // "ax" is a times x when "ax" is no name (Desmos): one letter, the rest read next.
                        i = start + 1
                        single(name.substring(0, 1)) ?: error("unknown")
                    }
                }
                else -> error("unexpected")
            }
        }

        private fun single(name: String): Node? = when {
            name == "x" -> Node { it }
            name == "e" -> Node { E }
            name.lowercase() == "pi" -> Node { PI }
            else -> names(name)?.let { v -> Node { v } }
        }

        private fun function(name: String, a: Node): Node {
            val k = if (degrees) PI / 180 else 1.0
            return when (name) {
                "sin" -> Node { sin(a.at(it) * k) }
                "cos" -> Node { cos(a.at(it) * k) }
                "tan" -> Node { tan(a.at(it) * k) }
                "sin⁻¹" -> Node { asin(a.at(it)) / k }
                "cos⁻¹" -> Node { acos(a.at(it)) / k }
                "tan⁻¹" -> Node { atan(a.at(it)) / k }
                "ln" -> Node { ln(a.at(it)) }
                "log" -> Node { log10(a.at(it)) }
                "exp" -> Node { exp(a.at(it)) }
                else -> error("function")
            }
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v) || v > 170) return Double.NaN
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }
    }
}
