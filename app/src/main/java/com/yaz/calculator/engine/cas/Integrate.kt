package com.yaz.calculator.engine.cas

import java.math.BigInteger

/** A primitive found, the methods it took in order, and whether its derivative gives the function back. */
class Primitive(val result: Ex?, val steps: List<String>)

/**
 * Primitives as a student finds them, in this order: the table (with a
 * linear argument), linearity, the substitution u′·f(u), rational functions
 * by partial fractions, trigonometric linearisation, integration by parts.
 * Every result is differentiated back and compared before it is given.
 */
fun integrate(f: Ex, french: Boolean): Primitive {
    val w = Integrator(french)
    val r = runCatching { w.int(simplify(f), 0) }.getOrNull()?.let { tidy(it) }
    val ok = r != null && derivative(r).sameAs(f)
    return Primitive(if (ok) r else null, w.steps)
}

private class Integrator(val french: Boolean) {
    val steps = ArrayList<String>()
    private fun say(fr: String, en: String) = if (french) fr else en
    private fun note(text: String) { if (steps.size < 12 && text !in steps) steps += text }

    fun int(f: Ex, depth: Int): Ex? {
        require(depth < 9)
        if (!f.hasX) return f * X
        if (f == X) return Num(Q.HALF) * X.pow(2)
        if (f is Add) {
            note(say("Linéarité : terme à terme", "Linearity: term by term"))
            return f.terms.map { int(it, depth + 1) ?: return rational(f, depth) ?: null }.fold(ZERO) { s, t -> s + t }
        }
        if (f is Mul) {
            val constant = f.factors.filter { !it.hasX }
            if (constant.isNotEmpty()) {
                val c = simplify(Mul(constant)); val rest = simplify(Mul(f.factors.filter { it.hasX }))
                return int(rest, depth + 1)?.let { c * it }
            }
        }
        table(f)?.let { return it }
        // x(x² + 1)⁵ by u = x² + 1 before any expansion.
        substitution(f, depth)?.let { return it }
        poly(f)?.let { p -> if (degreeOf(p) >= 0) return p.entries.fold(ZERO) { s, (k, c) -> s + Num(c / Q.of(k + 1L)) * X.pow(k + 1) } }
        rational(f, depth)?.let { return it }
        trig(f, depth)?.let { return it }
        parts(f, depth)?.let { return it }
        val e = expand(f)
        if (e != f) return int(e, depth + 1)
        return null
    }

    /** a and b when [e] is a·x + b with a ≠ 0. */
    private fun linear(e: Ex): Pair<Q, Q>? {
        val p = poly(e) ?: return null
        if (degreeOf(p) != 1) return null
        return p[1]!! to (p[0] ?: Q.ZERO)
    }

    /** The table of primitives, f(ax + b) giving F(ax + b)/a. */
    private fun table(f: Ex): Ex? {
        fun over(a: Q, F: Ex) = Num(Q.ONE / a) * F
        when (f) {
            is Pow -> {
                val ab = linear(f.base)
                if (ab != null && !f.exp.hasX) {
                    val (a, _) = ab
                    if (f.exp == num(-1)) { note(say("∫ 1/u = ln|u|", "∫ 1/u = ln|u|")); return over(a, fn("ln", fn("abs", f.base))) }
                    val k = f.exp + ONE
                    note(say("∫ uⁿ = uⁿ⁺¹/(n + 1)", "∫ uⁿ = uⁿ⁺¹/(n + 1)"))
                    return over(a, f.base.pow(k) / k)
                }
                // c^(ax + b) = e^((ax + b) ln c).
                if (!f.base.hasX && linear(f.exp) != null) {
                    val (a, _) = linear(f.exp)!!
                    return over(a, f / fn("ln", f.base))
                }
                // 1/cos²(u) = tan′(u), 1/sin²(u) = −cot′(u).
                val inner = (f.base as? Fn)
                if (inner != null && f.exp == num(-2)) {
                    val lab = linear(inner.arg) ?: return null
                    if (inner.name == "cos") return over(lab.first, fn("tan", inner.arg))
                    if (inner.name == "sin") return over(lab.first, -(fn("cos", inner.arg) / fn("sin", inner.arg)))
                }
                // 1/√(1 − u²) = arcsin′(u), 1/(1 + x²) handled with the rational functions.
                if (f.exp == Num(-Q.HALF)) {
                    val p = poly(f.base)
                    if (p != null && degreeOf(p) == 2 && p[1] == null && (p[2] ?: Q.ZERO).sign < 0 && (p[0] ?: Q.ZERO).sign > 0) {
                        // 1/√(c − a x²) = (1/√a)·arcsin(x·√(a/c)).
                        val a = -(p[2]!!); val c = p[0]!!
                        note(say("∫ 1/√(1 − u²) = arcsin(u)", "∫ 1/√(1 − u²) = arcsin(u)"))
                        return Num(a).pow(Num(-Q.HALF)) * fn("asin", X * Num(a / c).pow(Num(Q.HALF)))
                    }
                }
            }
            is Fn -> {
                val (a, _) = linear(f.arg) ?: return null
                val u = f.arg
                val F: Ex = when (f.name) {
                    "exp" -> f.also { note(say("∫ eᵘ = eᵘ", "∫ eᵘ = eᵘ")) }
                    "sin" -> -fn("cos", u)
                    "cos" -> fn("sin", u)
                    "tan" -> -fn("ln", fn("abs", fn("cos", u)))
                    "sinh" -> fn("cosh", u)
                    "cosh" -> fn("sinh", u)
                    "ln" -> (u * fn("ln", u) - u).also { note(say("∫ ln u = u ln u − u (par parties)", "∫ ln u = u ln u − u (by parts)")) }
                    "atan" -> u * fn("atan", u) - Num(Q.HALF) * fn("ln", ONE + u.pow(2))
                    "asin" -> u * fn("asin", u) + (ONE - u.pow(2)).pow(Num(Q.HALF))
                    "acos" -> u * fn("acos", u) - (ONE - u.pow(2)).pow(Num(Q.HALF))
                    else -> return null
                }
                return over(a, F)
            }
            else -> {}
        }
        return null
    }

    /** Every inner piece: the arguments of functions and the bases of powers that hold x. */
    private fun inners(e: Ex, into: MutableList<Ex>) {
        when (e) {
            is Add -> e.terms.forEach { inners(it, into) }
            is Mul -> e.factors.forEach { inners(it, into) }
            is Pow -> { if (e.base.hasX && e.base != X) into += e.base; if (e.exp.hasX) into += e.exp; inners(e.base, into); inners(e.exp, into) }
            is Fn -> { if (e.arg.hasX && e.arg != X) into += e.arg; into += e; inners(e.arg, into) }
            else -> {}
        }
    }

    private val T = Const("u")

    /** [e] with every [target] written as the new variable. */
    private fun replace(e: Ex, target: Ex): Ex = if (e == target) T else when (e) {
        is Add -> Add(e.terms.map { replace(it, target) })
        is Mul -> Mul(e.factors.map { replace(it, target) })
        is Pow -> Pow(replace(e.base, target), replace(e.exp, target))
        is Fn -> Fn(e.name, replace(e.arg, target))
        else -> e
    }

    private fun rename(e: Ex, from: Ex, to: Ex): Ex = if (e == from) to else when (e) {
        is Add -> Add(e.terms.map { rename(it, from, to) })
        is Mul -> Mul(e.factors.map { rename(it, from, to) })
        is Pow -> Pow(rename(e.base, from, to), rename(e.exp, from, to))
        is Fn -> Fn(e.name, rename(e.arg, from, to))
        else -> e
    }

    /** ∫ g(u(x))·u′(x) dx = G(u(x)): the integrand over u′ written in u alone. */
    private fun substitution(f: Ex, depth: Int): Ex? {
        val candidates = ArrayList<Ex>()
        inners(f, candidates)
        for (u in candidates.distinct().filter { linear(it) == null }) {
            val du = derivative(u)
            if (du == ZERO) continue
            val ratio = simplify(f / du)
            val inU = simplify(replace(ratio, u))
            if (inU.hasX) continue
            // g(t) in x, integrated, u put back.
            val g = simplify(rename(inU, T, X))
            if (g == simplify(f)) continue
            val G = runCatching { int(g, depth + 1) }.getOrNull() ?: continue
            note(say("Changement de variable u = ${show(u)}, du = ${show(du)} dx", "Substitution u = ${show(u)}, du = ${show(du)} dx"))
            return G.sub(u)
        }
        return null
    }

    /** f written as P/Q with whole polynomials, when it is one. */
    private fun asFraction(f: Ex): Pair<Map<Int, Q>, Map<Int, Q>>? {
        val factors = if (f is Mul) f.factors else listOf(f)
        var top: Ex = ONE; var bottom: Ex = ONE
        for (g in factors) {
            if (g is Pow && g.exp is Num && (g.exp as Num).q.sign < 0 && (g.exp as Num).q.isInteger) bottom = Mul(listOf(bottom, Pow(g.base, Num(-(g.exp as Num).q))))
            else top = Mul(listOf(top, g))
        }
        if (f is Add) top = f
        val p = poly(top) ?: return null
        val q = poly(bottom) ?: return null
        if (degreeOf(q) < 1) return null
        return p to q
    }

    private fun scale(p: Map<Int, Q>, c: Q) = p.mapValues { it.value * c }.filterValues { !it.isZero }
    private fun add(a: Map<Int, Q>, b: Map<Int, Q>) = (a.keys + b.keys).associateWith { (a[it] ?: Q.ZERO) + (b[it] ?: Q.ZERO) }.filterValues { !it.isZero }
    private fun times(a: Map<Int, Q>, b: Map<Int, Q>): Map<Int, Q> {
        val out = HashMap<Int, Q>()
        for ((i, x) in a) for ((j, y) in b) out[i + j] = (out[i + j] ?: Q.ZERO) + x * y
        return out.filterValues { !it.isZero }
    }

    /** P = S·D + R. */
    private fun divide(p: Map<Int, Q>, dv: Map<Int, Q>): Pair<Map<Int, Q>, Map<Int, Q>> {
        var r = p; val s = HashMap<Int, Q>()
        val dd = degreeOf(dv); val lead = dv[dd]!!
        while (degreeOf(r) >= dd && r.isNotEmpty()) {
            val k = degreeOf(r) - dd; val c = r[degreeOf(r)]!! / lead
            s[k] = (s[k] ?: Q.ZERO) + c
            r = add(r, scale(times(dv, mapOf(k to Q.ONE)), -c))
        }
        return s to r
    }

    /** Exact Gauss elimination on rationals; null when singular. */
    private fun solve(m: Array<Array<Q>>, b: Array<Q>): Array<Q>? {
        val n = b.size
        val a = Array(n) { i -> m[i].copyOf() + b[i] }
        for (c in 0 until n) {
            val p = (c until n).firstOrNull { !a[it][c].isZero } ?: return null
            val tmp = a[p]; a[p] = a[c]; a[c] = tmp
            for (r in 0 until n) if (r != c && !a[r][c].isZero) {
                val k = a[r][c] / a[c][c]
                for (j in c..n) a[r][j] = a[r][j] - k * a[c][j]
            }
        }
        return Array(n) { a[it][n] / a[it][it] }
    }

    /** Rational functions: division, then simple elements over the rational roots and one quadratic left. */
    private fun rational(f: Ex, depth: Int): Ex? {
        val (p, q) = asFraction(f) ?: return null
        val (whole, r) = divide(p, q)
        val (roots, rest) = rationalRoots(q)
        val restDeg = degreeOf(rest)
        if (restDeg > 2) return null
        // The simple elements: A/(x − r)ʲ for each root and power, (Bx + C)/rest.
        val lead = rest[restDeg]!!
        val pieces = ArrayList<Pair<Map<Int, Q>, Ex>>()  // (its numerator times Q/(its denominator), its shape)
        val unknowns = roots.sumOf { it.second } + if (restDeg == 2) 2 else 0
        if (unknowns != degreeOf(q)) return null
        val linearOf = { root: Q -> mapOf(1 to Q.ONE, 0 to -root).filterValues { !it.isZero } }
        val qMonic = scale(q, Q.ONE / lead)
        // Each unknown's polynomial: Q/denominator of its element (times x for B).
        for ((root, m) in roots) for (j in 1..m) {
            var cof = qMonic
            repeat(j) { cof = divide(cof, linearOf(root)).first }
            pieces += cof to (X - Num(root)).pow(-j)
        }
        if (restDeg == 2) {
            val monicRest = scale(rest, Q.ONE / lead)
            val cof = divide(qMonic, monicRest).first
            pieces += times(cof, mapOf(1 to Q.ONE)) to X * polyEx(monicRest).pow(-1)
            pieces += cof to polyEx(monicRest).pow(-1)
        }
        val target = scale(r, Q.ONE / lead)
        val n = pieces.size
        val m = Array(n) { row -> Array(n) { col -> pieces[col].first[row] ?: Q.ZERO } }
        val coeffs = solve(m, Array(n) { target[it] ?: Q.ZERO }) ?: return null
        var sum: Ex = ZERO
        val shown = ArrayList<String>()
        for (i in 0 until n) if (!coeffs[i].isZero) { sum += Num(coeffs[i]) * pieces[i].second; }
        note(say("Fraction rationnelle : division puis éléments simples", "Rational function: division, then partial fractions"))
        if (sum != ZERO) note("= " + show(simplify(polyEx(whole) + sum)))
        var result: Ex = ZERO
        for ((k, c) in whole) result += Num(c / Q.of(k + 1L)) * X.pow(k + 1)
        var i = 0
        for ((root, mult) in roots) for (j in 1..mult) {
            val a = coeffs[i++]
            if (a.isZero) continue
            val base = X - Num(root)
            result += if (j == 1) Num(a) * fn("ln", fn("abs", base)) else Num(a / Q.of(1L - j)) * base.pow(1 - j)
        }
        if (restDeg == 2) {
            val bb = coeffs[i]; val cc = coeffs[i + 1]
            val mr = scale(rest, Q.ONE / lead)
            val b1 = mr[1] ?: Q.ZERO; val c0 = mr[0] ?: Q.ZERO
            // (Bx + C)/(x² + b x + c) = (B/2)(2x + b)/(…) + (C − Bb/2)/(…).
            if (!bb.isZero) result += Num(bb * Q.HALF) * fn("ln", fn("abs", polyEx(mr)))
            val k = cc - bb * b1 * Q.HALF
            if (!k.isZero) {
                val delta = c0 - b1 * b1 * Q.of(1, 4)
                if (delta.sign > 0) {
                    // 1/((x + b/2)² + δ) = (1/√δ)·arctan((x + b/2)/√δ).
                    val s = Num(delta).pow(Num(Q.HALF))
                    result += Num(k) / s * fn("atan", (X + Num(b1 * Q.HALF)) / s)
                    note(say("∫ 1/(u² + a²) = (1/a)·arctan(u/a)", "∫ 1/(u² + a²) = (1/a)·arctan(u/a)"))
                } else {
                    val s = Num(-delta).pow(Num(Q.HALF))
                    val shift = X + Num(b1 * Q.HALF)
                    result += Num(k) / (num(2) * s) * fn("ln", fn("abs", (shift - s) / (shift + s)))
                }
            }
        }
        return result
    }

    /** sin², cos² and products of sines and cosines turned into sums (linearisation). */
    private fun trig(f: Ex, depth: Int): Ex? {
        fun lin(e: Ex) = (e as? Fn)?.takeIf { (it.name == "sin" || it.name == "cos") && linear(it.arg) != null }
        if (f is Pow && f.exp is Num && (f.exp as Num).q.isInteger) {
            val g = lin(f.base) ?: return null
            val k = (f.exp as Num).q.n.toInt()
            if (k < 2 || k > 8) return null
            val u = g.arg
            note(say("Linéarisation", "Linearisation"))
            if (k % 2 == 1) {
                // sin²ᵏ⁺¹ = sin·(1 − cos²)ᵏ: then u = cos.
                val other = if (g.name == "sin") fn("cos", u) else fn("sin", u)
                return int(expand(g * (ONE - other.pow(2)).pow(k / 2)), depth + 1)
            }
            val half = if (g.name == "sin") (ONE - fn("cos", num(2) * u)) / num(2) else (ONE + fn("cos", num(2) * u)) / num(2)
            return int(expand(half.pow(k / 2)), depth + 1)
        }
        if (f is Mul) {
            val tr = f.factors.mapNotNull(::lin)
            if (tr.size >= 2 && f.factors.size == tr.size) {
                val a = tr[0]; val b = tr[1]
                val sum = a.arg + b.arg; val diff = a.arg - b.arg
                val prod: Ex = when {
                    a.name == "sin" && b.name == "cos" -> (fn("sin", sum) + fn("sin", diff)) / num(2)
                    a.name == "cos" && b.name == "sin" -> (fn("sin", sum) - fn("sin", diff)) / num(2)
                    a.name == "sin" && b.name == "sin" -> (fn("cos", diff) - fn("cos", sum)) / num(2)
                    else -> (fn("cos", diff) + fn("cos", sum)) / num(2)
                }
                note(say("Linéarisation : produit en somme", "Linearisation: product to sum"))
                val rest = tr.drop(2).fold(ONE) { p, x -> p * x }
                return int(expand(prod * rest), depth + 1)
            }
        }
        return null
    }

    /** Integration by parts: polynomial × (exp, sin, cos, ch, sh) with u the polynomial; ln, arctan, arcsin × polynomial with u the function. */
    private fun parts(f: Ex, depth: Int): Ex? {
        val factors = if (f is Mul) f.factors else return null
        val polyPart = simplify(Mul(factors.filter { poly(it) != null }))
        val others = factors.filter { poly(it) == null }
        if (others.size != 1) {
            // e^(ax)·sin(bx) and e^(ax)·cos(bx): two integrations by parts, solved for the integral.
            if (others.size == 2 && polyPart == ONE) {
                val ex = others.firstOrNull { it is Fn && it.name == "exp" } as Fn? ?: return null
                val tr = others.firstOrNull { it is Fn && (it.name == "sin" || it.name == "cos") } as Fn? ?: return null
                val la = linear(ex.arg) ?: return null; val lb = linear(tr.arg) ?: return null
                val a = Num(la.first); val b = Num(lb.first)
                note(say("Deux intégrations par parties, l'intégrale revient : on la résout", "Two integrations by parts bring the integral back: solved for it"))
                val den = a.pow(2) + b.pow(2)
                return if (tr.name == "sin") ex * (a * fn("sin", tr.arg) - b * fn("cos", tr.arg)) / den
                else ex * (a * fn("cos", tr.arg) + b * fn("sin", tr.arg)) / den
            }
            return null
        }
        val g = others[0]
        val p = poly(polyPart) ?: return null
        val easy = g is Fn && g.name in listOf("exp", "sin", "cos", "sinh", "cosh") && linear(g.arg) != null ||
            g is Pow && !g.base.hasX && linear(g.exp) != null
        val log = g is Fn && g.name in listOf("ln", "atan", "asin", "acos")
        if (easy && degreeOf(p) >= 1) {
            val v = int(g, depth + 1) ?: return null
            note(say("Par parties : u = ${show(polyPart)}, v′ = ${show(g)} ; u′ = ${show(derivative(polyPart))}, v = ${show(v)}", "By parts: u = ${show(polyPart)}, v′ = ${show(g)}; u′ = ${show(derivative(polyPart))}, v = ${show(v)}"))
            val rest = int(simplify(derivative(polyPart) * v), depth + 1) ?: return null
            return polyPart * v - rest
        }
        if (log) {
            val v = int(polyPart, depth + 1) ?: return null
            note(say("Par parties : u = ${show(g)}, v′ = ${show(polyPart)} ; u′ = ${show(derivative(g))}, v = ${show(v)}", "By parts: u = ${show(g)}, v′ = ${show(polyPart)}; u′ = ${show(derivative(g))}, v = ${show(v)}"))
            val rest = int(simplify(derivative(g) * v), depth + 1) ?: return null
            return g * v - rest
        }
        return null
    }
}

/** The definite integral from F: F(b) − F(a), exact when it can be. */
fun definite(F: Ex, a: Ex, b: Ex): Ex = simplify(F.sub(b) - F.sub(a))

