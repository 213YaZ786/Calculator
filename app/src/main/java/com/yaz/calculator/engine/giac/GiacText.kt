package com.yaz.calculator.engine.giac

import com.yaz.calculator.engine.cas.Add
import com.yaz.calculator.engine.cas.Const
import com.yaz.calculator.engine.cas.E
import com.yaz.calculator.engine.cas.Ex
import com.yaz.calculator.engine.cas.Fn
import com.yaz.calculator.engine.cas.Mul
import com.yaz.calculator.engine.cas.Num
import com.yaz.calculator.engine.cas.PI
import com.yaz.calculator.engine.cas.Pow
import com.yaz.calculator.engine.cas.Q
import com.yaz.calculator.engine.cas.X
import com.yaz.calculator.engine.cas.num
import com.yaz.calculator.engine.cas.simplify

/** An expression of ours written in Giac's language, nothing left to guess. */
fun toGiac(e: Ex): String = when (e) {
    is Num -> if (e.q.isInteger) "(${e.q.n})" else "(${e.q.n}/${e.q.d})"
    X -> "x"
    is Const -> if (e.name == "π") "pi" else "e"
    is Add -> e.terms.joinToString("+", "(", ")") { toGiac(it) }
    is Mul -> e.factors.joinToString("*", "(", ")") { toGiac(it) }
    is Pow -> "(${toGiac(e.base)})^(${toGiac(e.exp)})"
    is Fn -> "${e.name}(${toGiac(e.arg)})"
}

/**
 * Giac's answer read back into our expressions, so it is written as
 * Calculator writes ((x² − 2x + 2)eˣ); null when it holds something we do
 * not have (a constant c_0, a list, a complex number).
 */
fun fromGiac(text: String): Ex? = runCatching { GiacReader(text).read() }.getOrNull()

/** A list [a,b,…] or list[a,b,…] or a,b,…: each item read back, null when one is not. */
fun fromGiacList(text: String): List<Ex>? {
    val t = text.trim().removePrefix("list").trim()
    val inner = if (t.startsWith("[") && t.endsWith("]")) t.substring(1, t.length - 1) else t
    if (inner.isBlank()) return emptyList()
    return splitTop(inner).map { fromGiac(it) ?: return null }
}

/** Splits at the commas outside brackets. */
fun splitTop(text: String): List<String> {
    val out = ArrayList<String>(); var depth = 0; val cur = StringBuilder()
    for (c in text) {
        when (c) {
            '(', '[' -> depth++
            ')', ']' -> depth--
        }
        if (c == ',' && depth == 0) { out += cur.toString().trim(); cur.clear() } else cur.append(c)
    }
    if (cur.isNotBlank()) out += cur.toString().trim()
    return out
}

/** Giac's text made readable when it is not read back: ·, powers raised, π. */
fun tidyGiac(text: String): String = text
    .replace("*", "·").replace("pi", "π").replace("sqrt(", "√(").replace("atan", "arctan").replace("asin", "arcsin").replace("acos", "arccos")
    .replace(Regex("\\^(\\d+)")) { m -> m.groupValues[1].map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("") }
    .replace("+-", "−").replace("-", "−").replace("+", " + ").replace("−", " − ").replace("  ", " ").trim()

private class GiacReader(val s: String) {
    var i = 0
    fun read(): Ex { val e = sum(); skip(); require(i >= s.length) { "unread" }; return simplify(e) }
    private fun skip() { while (i < s.length && s[i] == ' ') i++ }
    private fun peek(): Char? { skip(); return s.getOrNull(i) }

    private fun sum(): Ex {
        val terms = arrayListOf(product())
        while (true) when (peek()) {
            '+' -> { i++; terms += product() }
            '-' -> { i++; terms += Mul(listOf(num(-1), product())) }
            else -> return if (terms.size == 1) terms[0] else Add(terms)
        }
    }

    private fun product(): Ex {
        val fs = arrayListOf(unary())
        while (true) when (peek()) {
            '*' -> { i++; fs += unary() }
            '/' -> { i++; fs += Pow(unary(), num(-1)) }
            else -> return if (fs.size == 1) fs[0] else Mul(fs)
        }
    }

    private fun unary(): Ex = if (peek() == '-') { i++; Mul(listOf(num(-1), unary())) } else power()

    private fun power(): Ex {
        val b = atom()
        if (peek() == '^') { i++; return Pow(b, unary()) }
        return b
    }

    private fun atom(): Ex {
        val c = peek() ?: error("end")
        return when {
            c == '(' -> { i++; val e = sum(); require(peek() == ')'); i++; e }
            c == '√' -> { i++; Pow(atom(), Num(Q.HALF)) }
            c.isDigit() || c == '.' -> {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                require(i >= s.length || s[i] != 'e') { "float" }
                Num(Q.parse(s.substring(start, i)))
            }
            c.isLetter() -> {
                val start = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                val name = s.substring(start, i)
                if (peek() == '(') {
                    i++
                    val arg = sum()
                    require(peek() == ')'); i++
                    function(name, arg)
                } else when (name) {
                    "x" -> X
                    "pi" -> PI
                    "e" -> E
                    else -> error("name $name")
                }
            }
            else -> error("char $c")
        }
    }

    private fun function(name: String, a: Ex): Ex = when (name) {
        "sin", "cos", "tan", "exp", "abs", "sinh", "cosh", "tanh", "asin", "acos", "atan" -> Fn(name, a)
        "ln", "log" -> Fn("ln", a)
        "sqrt" -> Pow(a, Num(Q.HALF))
        else -> error("function $name")
    }
}
