package com.yaz.calculator.engine.tibasic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.truncate

/**
 * A TI-Basic expression in doubles, with the TI's order: or xor, and,
 * the comparisons (= ≠ < > ≤ ≥, 1 or 0), + −, × ÷ and implicit
 * multiplication, the sign, ^, then ² ! ⁻¹ after a value; functions as
 * the catalogue writes them (sin( √( abs( int( iPart( …), and not(.
 */
internal class Expr(private val s: String, private val lookup: (String) -> Double?, private val degrees: Boolean) {
    private var i = 0
    private var depth = 0

    fun parse(): Double {
        if (s.isBlank()) throw Interpreter.Failure("An expression is missing")
        val v = or()
        skip()
        if (i < s.length && s[i] != ')') throw Interpreter.Failure("Syntax: ${s.substring(i)}")
        return v
    }

    private fun skip() { while (i < s.length && s[i] == ' ') i++ }

    private fun word(w: String): Boolean {
        skip()
        if (!s.startsWith(w, i)) return false
        val after = i + w.length
        if (after < s.length && s[after].isLetterOrDigit()) return false
        i = after
        return true
    }

    private fun sym(c: Char): Boolean {
        skip()
        if (i < s.length && s[i] == c) { i++; return true }
        return false
    }

    private fun or(): Double {
        var v = and()
        while (true) {
            v = when {
                word("or") -> if (v != 0.0 || and() != 0.0) 1.0 else 0.0
                word("xor") -> if ((v != 0.0) != (and() != 0.0)) 1.0 else 0.0
                else -> return v
            }
        }
    }

    private fun and(): Double {
        var v = compare()
        while (word("and")) { val r = compare(); v = if (v != 0.0 && r != 0.0) 1.0 else 0.0 }
        return v
    }

    private fun compare(): Double {
        var v = sum()
        while (true) {
            skip()
            val c = s.getOrNull(i) ?: return v
            if (c !in "=≠<>≤≥") return v
            i++
            val r = sum()
            v = if (when (c) { '=' -> v == r; '≠' -> v != r; '<' -> v < r; '>' -> v > r; '≤' -> v <= r; else -> v >= r }) 1.0 else 0.0
        }
    }

    private fun sum(): Double {
        var v = product()
        while (true) {
            v = when {
                sym('+') -> v + product()
                sym('−') || sym('-') -> v - product()
                else -> return v
            }
        }
    }

    private fun product(): Double {
        var v = signed()
        while (true) {
            skip()
            val c = s.getOrNull(i) ?: return v
            v = when {
                c == '*' || c == '×' -> { i++; v * signed() }
                c == '/' || c == '÷' -> { i++; v / signed() }
                // 2A, 3(4), Aπ, 2sin(…): implicit multiplication; not the words or, and, xor.
                (c.isLetterOrDigit() || c == '(' || c == 'π' || c == 'θ' || c == '√' || c == '.') && !startsWord() -> v * power()
                else -> return v
            }
        }
    }

    private fun startsWord() = listOf("or", "and", "xor").any { w -> s.startsWith(w, i) && (i + w.length >= s.length || !s[i + w.length].isLetterOrDigit()) }

    private fun signed(): Double {
        if (sym('−') || sym('-') || sym('⁻')) return -signed()
        return power()
    }

    private fun power(): Double {
        val base = postfix(atom())
        if (sym('^')) return base.pow(signed())
        return base
    }

    private fun postfix(v0: Double): Double {
        var v = v0
        while (true) {
            skip()
            v = when {
                s.startsWith("⁻¹", i) -> { i += 2; 1 / v }
                sym('²') -> v * v
                sym('³') -> v * v * v
                sym('!') -> factorial(v)
                else -> return v
            }
        }
    }

    private fun atom(): Double {
        // Nesting bounded: a hostile program cannot overflow the stack.
        if (++depth > MAX_DEPTH) throw Interpreter.Failure("Too deeply nested")
        try { return atomInner() } finally { depth-- }
    }

    private fun atomInner(): Double {
        skip()
        val c = s.getOrNull(i) ?: throw Interpreter.Failure("A value is missing")
        if (c.isDigit() || c == '.') {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i < s.length && (s[i] == 'E' || s[i] == 'ᴇ')) {
                var j = i + 1
                if (j < s.length && (s[j] == '-' || s[j] == '−' || s[j] == '⁻' || s[j] == '+')) j++
                if (j < s.length && s[j].isDigit()) {
                    while (j < s.length && s[j].isDigit()) j++
                    val text = s.substring(start, j).replace('ᴇ', 'E').replace('−', '-').replace('⁻', '-')
                    i = j
                    return text.toDouble()
                }
            }
            return s.substring(start, i).toDouble()
        }
        if (sym('(')) { val v = or(); sym(')'); return v }
        if (sym('√')) { if (sym('(')) { val v = or(); sym(')'); return sqrt(v) }; return sqrt(postfix(atom())) }
        if (sym('π')) return PI
        for ((name, f) in FUNCTIONS) if (s.startsWith(name, i)) {
            i += name.length
            val a = ArrayList<Double>()
            if (!sym(')')) {
                a += or()
                while (sym(',')) a += or()
                sym(')')
            }
            return f(a)
        }
        if (c.isLetter() || c == 'θ') {
            val start = i
            // Variables are one letter; Ans and pi are words.
            if (s.startsWith("Ans", i)) i += 3 else if (s.startsWith("pi", i)) i += 2 else i++
            val n = s.substring(start, i)
            return lookup(n) ?: throw Interpreter.Failure("Unknown: $n")
        }
        throw Interpreter.Failure("Unexpected: $c")
    }

    private val k get() = if (degrees) PI / 180 else 1.0

    private val FUNCTIONS: List<Pair<String, (List<Double>) -> Double>> = listOf<Pair<String, (List<Double>) -> Double>>(
        "sin⁻¹(" to { a -> asin(one(a)) / k }, "cos⁻¹(" to { a -> acos(one(a)) / k }, "tan⁻¹(" to { a -> atan(one(a)) / k },
        "sin(" to { a -> sin(one(a) * k) }, "cos(" to { a -> cos(one(a) * k) }, "tan(" to { a -> tan(one(a) * k) },
        "ln(" to { a -> ln(one(a)) }, "log(" to { a -> log10(one(a)) }, "e^(" to { a -> exp(one(a)) }, "exp(" to { a -> exp(one(a)) },
        "abs(" to { a -> abs(one(a)) }, "int(" to { a -> floor(one(a)) }, "iPart(" to { a -> truncate(one(a)) },
        "fPart(" to { a -> one(a).let { it - truncate(it) } },
        "round(" to { a -> if (a.size > 1) 10.0.pow(a[1]).let { p -> round(a[0] * p) / p } else round(one(a)) },
        "min(" to { a -> a.minOrNull() ?: Double.NaN }, "max(" to { a -> a.maxOrNull() ?: Double.NaN },
        "remainder(" to { a -> if (a.size == 2) a[0] % a[1] else Double.NaN },
        "gcd(" to { a -> if (a.size == 2) gcd(a[0].toLong(), a[1].toLong()).toDouble() else Double.NaN },
        "lcm(" to { a -> if (a.size == 2) (a[0].toLong() / gcd(a[0].toLong(), a[1].toLong()) * a[1].toLong()).toDouble() else Double.NaN },
        "not(" to { a -> if (one(a) == 0.0) 1.0 else 0.0 },
        "rand(" to { _ -> Math.random() }, "randInt(" to { a -> if (a.size == 2) floor(a[0] + Math.random() * (a[1] - a[0] + 1)) else Double.NaN }
    ).sortedByDescending { it.first.length }

    private fun one(a: List<Double>) = a.singleOrNull() ?: throw Interpreter.Failure("One value expected")

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) Math.abs(a) else gcd(b, a % b)

    private fun factorial(v: Double): Double {
        if (v < 0 || v != Math.rint(v) || v > 170) return Double.NaN
        var r = 1.0
        for (n in 2..v.toInt()) r *= n
        return r
    }

    private companion object {
        const val MAX_DEPTH = 200
    }
}
