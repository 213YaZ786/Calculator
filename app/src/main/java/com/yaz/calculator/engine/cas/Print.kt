package com.yaz.calculator.engine.cas

import java.math.BigInteger

/** An expression as a student writes it: 3x² − 2x + 1, (x + 1)/(x² + 1), √(x), eˣ, ln|x|. */
fun show(e: Ex): String = Printer.sum(e).replace("+ −", "− ")

private object Printer {
    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    private fun superscript(n: BigInteger): String = (if (n.signum() < 0) "⁻" else "") + n.abs().toString().map { SUP[it - '0'] }.joinToString("")

    fun number(q: Q): String = (if (q.sign < 0) "−" else "") + q.abs().toString()

    fun sum(e: Ex): String {
        if (e !is Add) return product(e)
        val out = StringBuilder()
        // Not a polynomial: a positive term first, sin(x) − cos(x) rather than −cos(x) + sin(x).
        val ordered = if (poly(e) == null && split(e.terms[0]).first.sign < 0) e.terms.firstOrNull { split(it).first.sign > 0 }?.let { p -> listOf(p) + (e.terms - p) } ?: e.terms else e.terms
        ordered.forEachIndexed { i, t ->
            val (c, rest) = split(t)
            if (i == 0) out.append(product(t))
            else if (c.sign < 0) out.append(" − ").append(product(withPositive(-c, rest)))
            else out.append(" + ").append(product(t))
        }
        return out.toString()
    }

    private fun withPositive(c: Q, rest: Ex): Ex = if (rest == ONE) Num(c) else if (c.isOne) rest else simplify(Mul(listOf(Num(c), rest)))

    /** Wrapped in parentheses when it would read wrongly inside a product. */
    private fun factor(e: Ex): String = when (e) {
        is Add -> "(${sum(e)})"
        is Num -> if (e.q.sign < 0 || !e.q.isInteger) "(${number(e.q)})" else number(e.q)
        else -> product(e)
    }

    fun product(e: Ex): String = when (e) {
        is Num -> number(e.q)
        X -> "x"
        is Const -> e.name
        is Add -> "(${sum(e)})"
        is Fn -> function(e)
        is Pow -> power(e)
        is Mul -> mul(e)
    }

    private fun negativePower(f: Ex) = f is Pow && f.exp is Num && f.exp.q.sign < 0

    /** Pieces put side by side, a × only between two numbers. */
    private fun join(pieces: List<String>) = pieces.fold("") { acc, p ->
        val last = acc.lastOrNull(); val first = p.firstOrNull()
        when {
            last == null || first == null -> acc + p
            last.isDigit() && first.isDigit() -> "$acc×$p"
            // x·ln(x), 2x·cos(x): a dot before a function after a letter or a parenthesis.
            (last.isLetter() || last == ')' || last == '|' || last in "²³⁴⁵⁶⁷⁸⁹") && first.isLetter() && p.contains('(') -> "$acc·$p"
            else -> acc + p
        }
    }

    private fun mul(e: Mul): String {
        var (c, _) = split(e)
        // A sum's fractions put under the product's bar: (x² + 1)·arctan(x)/2.
        val factors = e.factors.filter { it !is Num }.map { f ->
            if (f !is Add) return@map f
            val d = f.terms.fold(BigInteger.ONE) { l, t -> val q = split(t).first; l / l.gcd(q.d) * q.d }
            if (d == BigInteger.ONE) return@map f
            val dq = Q.of(d, BigInteger.ONE)
            c = c / dq
            Add(f.terms.map { simplify(Mul(listOf(Num(dq), it))) })
        }
        // Negative powers go under the bar: 3x/(x² + 1).
        val top = factors.filter { !negativePower(it) }
        val bottom = factors.filter(::negativePower).map { simplify(Pow((it as Pow).base, Num(-(it.exp as Num).q))) }
        val n = c.n.abs(); val d = c.d
        val upperPieces = ArrayList<String>()
        if (n != BigInteger.ONE || top.isEmpty()) upperPieces += n.toString()
        top.forEach { upperPieces += if (top.size == 1 && upperPieces.isEmpty()) product(it) else factor(it) }
        val upper = join(upperPieces)
        val sign = if (c.sign < 0) "−" else ""
        val lowerPieces = (if (d != BigInteger.ONE) listOf(d.toString()) else emptyList()) + bottom.map { factor(it) }
        if (lowerPieces.isEmpty()) return sign + upper
        var lower = join(lowerPieces)
        if (lowerPieces.size > 1 && !(lower.startsWith("(") && lower.endsWith(")") && lowerPieces.size == 1)) lower = "($lower)"
        return "$sign$upper/$lower"
    }

    private fun power(e: Pow): String {
        val k = (e.exp as? Num)?.q
        val base = e.base
        val atom = base == X || base is Const || (base is Num && base.q.sign > 0 && base.q.isInteger)
        return when {
            k == Q.HALF -> "√" + if (atom) product(base) else "(${sum(base)})"
            // x^(3/2) = x√x, (x + 1)^(5/2) = (x + 1)²√(x + 1).
            k != null && k.d == java.math.BigInteger.TWO && k.sign > 0 -> {
                val whole = k.n.divide(java.math.BigInteger.TWO)
                val b = if (atom) product(base) else "(${sum(base)})"
                (if (whole == java.math.BigInteger.ONE) b else b + superscript(whole)) + "√" + (if (atom) product(base) else "(${sum(base)})")
            }
            k == Q.of(1, 3) -> "∛" + if (atom) product(base) else "(${sum(base)})"
            k != null && k.sign < 0 -> "1/" + factor(simplify(Pow(base, Num(-k)))).let { if (it.contains(' ') && !it.startsWith("(")) "($it)" else it }
            k != null && k.isInteger && base is Fn && base.name in listOf("sin", "cos", "tan", "ln", "sinh", "cosh", "tanh") ->
                fnName(base.name) + superscript(k.n) + "(" + sum(base.arg) + ")"
            k != null && k.isInteger -> (if (atom) product(base) else "(${sum(base)})") + superscript(k.n)
            e.exp == X && atom -> product(base) + "ˣ"
            else -> (if (atom) product(base) else "(${sum(base)})") + "^(" + sum(e.exp) + ")"
        }
    }

    private fun fnName(n: String) = when (n) { "asin" -> "arcsin"; "acos" -> "arccos"; "atan" -> "arctan"; else -> n }

    private fun function(e: Fn): String = when (e.name) {
        "exp" -> when {
            e.arg == X -> "eˣ"
            else -> "e^(" + inner(e.arg) + ")"
        }
        "abs" -> "|" + inner(e.arg) + "|"
        "ln" -> if (e.arg is Fn && e.arg.name == "abs") "ln|" + inner(e.arg.arg) + "|" else "ln(" + inner(e.arg) + ")"
        else -> fnName(e.name) + "(" + inner(e.arg) + ")"
    }

    /** A function's argument: x/2 + 1/2 read as (x + 1)/2. */
    private fun inner(e: Ex): String {
        val p = if (e is Add) poly(e) else null
        if (p == null || p.size < 2) return sum(e)
        val d = p.values.fold(BigInteger.ONE) { l, c -> l / l.gcd(c.d) * c.d }
        if (d == BigInteger.ONE) return sum(e)
        val dq = Q.of(d, BigInteger.ONE)
        return "(" + sum(polyEx(p.mapValues { it.value * dq })) + ")/" + d
    }
}
