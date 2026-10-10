package com.yaz.calculator.engine.cas

import com.yaz.calculator.engine.Sym

/**
 * Reads what the keys type, with Calc's meaning (implicit multiplication,
 * √, ², ^, sin⁻¹, ln, log, π, e, x), into an exact expression; decimals
 * become fractions. Angles are in radians: the symbolic engine works there.
 */
fun parse(text: String): Ex = Reader(text).read()

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

private class Reader(val s: String) {
    var i = 0

    fun read(): Ex {
        val n = sum()
        skip()
        require(i >= s.length) { "unread" }
        return simplify(n)
    }

    private fun skip() { while (i < s.length && s[i] == ' ') i++ }
    private fun peek(): Char? { skip(); return s.getOrNull(i) }

    private fun sum(): Ex {
        val terms = arrayListOf(product())
        while (true) {
            when (peek()) {
                Sym.PLUS, '+' -> { i++; terms += product() }
                Sym.MINUS, '-' -> { i++; terms += Mul(listOf(num(-1), product())) }
                else -> return if (terms.size == 1) terms[0] else Add(terms)
            }
        }
    }

    private fun product(): Ex {
        val factors = arrayListOf(signed())
        while (true) {
            val c = peek()
            when {
                c == Sym.TIMES || c == '*' -> { i++; factors += signed() }
                c == Sym.DIVIDE || c == '/' -> { i++; factors += Pow(signed(), num(-1)) }
                c != null && (c.isLetterOrDigit() || c == '(' || c == Sym.SQRT || c == Sym.PI || c == Sym.POINT) -> factors += power()
                else -> return if (factors.size == 1) factors[0] else Mul(factors)
            }
        }
    }

    private fun signed(): Ex {
        if (peek() == Sym.MINUS || peek() == '-') { i++; return Mul(listOf(num(-1), signed())) }
        return power()
    }

    private fun power(): Ex {
        val base = suffix(atom())
        if (peek() == Sym.POWER) { i++; return Pow(base, signed()) }
        return base
    }

    private fun suffix(n: Ex): Ex {
        var v = n
        while (true) {
            val c = peek() ?: return v
            when (c) {
                Sym.SQUARE -> { i++; v = Pow(v, num(2)) }
                in SUPERSCRIPTS -> {
                    // x³ pasted or written: superscript digits as a power.
                    var k = 0L
                    while (i < s.length && s[i] in SUPERSCRIPTS) { k = k * 10 + SUPERSCRIPTS.indexOf(s[i]); i++ }
                    v = Pow(v, num(k))
                }
                Sym.PERCENT -> { i++; v = Mul(listOf(v, num(1, 100))) }
                else -> return v
            }
        }
    }

    private fun atom(): Ex {
        val c = peek() ?: error("end")
        return when {
            c.isDigit() || c == Sym.POINT -> {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == Sym.POINT)) i++
                Num(Q.parse(s.substring(start, i)))
            }
            c == '(' -> { i++; val inner = sum(); if (peek() == ')') i++; inner }
            c == Sym.SQRT -> { i++; Pow(suffix(atom()), Num(Q.HALF)) }
            c == Sym.PI -> { i++; PI }
            c.isLetter() -> {
                Sym.functionAt(s, i)?.let { f ->
                    i += f.length
                    val arg = if (peek() != ')' && peek() != null) sum() else error("argument")
                    if (peek() == Sym.ARG) error("one argument")
                    if (peek() == ')') i++
                    function(f.dropLast(1), arg)
                } ?: when (s[i]) {
                    'x' -> { i++; X }
                    'e' -> { i++; E }
                    else -> error("name")
                }
            }
            else -> error("unexpected")
        }
    }

    private fun function(name: String, a: Ex): Ex = when (name) {
        "sin", "cos", "tan", "ln", "exp", "abs", "sinh", "cosh", "tanh" -> Fn(name, a)
        "sin⁻¹" -> Fn("asin", a)
        "cos⁻¹" -> Fn("acos", a)
        "tan⁻¹" -> Fn("atan", a)
        "log" -> Mul(listOf(Fn("ln", a), Pow(Fn("ln", num(10)), num(-1))))
        "∛" -> Pow(a, num(1, 3))
        else -> error("function")
    }
}
