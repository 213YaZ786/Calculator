package com.yaz.calculator.engine.cas

import java.math.BigInteger

// Derivatives, expansion and polynomials, by the textbook rules.

/** f′, simplified. */
fun derivative(e: Ex): Ex = simplify(d(e))

private fun d(e: Ex): Ex = when (e) {
    is Num, is Const -> ZERO
    X -> ONE
    is Add -> Add(e.terms.map(::d))
    is Mul -> Add(e.factors.indices.map { i -> Mul(e.factors.mapIndexed { j, f -> if (i == j) d(f) else f }) })
    is Pow -> when {
        !e.exp.hasX -> Mul(listOf(e.exp, Pow(e.base, Add(listOf(e.exp, num(-1)))), d(e.base)))
        // u^v = e^(v·ln u).
        else -> Mul(listOf(e, Add(listOf(Mul(listOf(d(e.exp), Fn("ln", e.base))), Mul(listOf(e.exp, d(e.base), Pow(e.base, num(-1))))))))
    }
    is Fn -> {
        val u = e.arg; val du = d(u)
        val inner: Ex = when (e.name) {
            "sin" -> Fn("cos", u)
            "cos" -> Mul(listOf(num(-1), Fn("sin", u)))
            "tan" -> Pow(Fn("cos", u), num(-2))
            "asin" -> Pow(Add(listOf(ONE, Mul(listOf(num(-1), Pow(u, num(2)))))), Num(-Q.HALF))
            "acos" -> Mul(listOf(num(-1), Pow(Add(listOf(ONE, Mul(listOf(num(-1), Pow(u, num(2)))))), Num(-Q.HALF))))
            "atan" -> Pow(Add(listOf(ONE, Pow(u, num(2)))), num(-1))
            "ln" -> Pow(if (u is Fn && u.name == "abs") u.arg else u, num(-1))
            "exp" -> e
            "abs" -> Mul(listOf(u, Pow(e, num(-1))))
            "sinh" -> Fn("cosh", u)
            "cosh" -> Fn("sinh", u)
            "tanh" -> Add(listOf(ONE, Mul(listOf(num(-1), Pow(e, num(2))))))
            else -> error("derivative")
        }
        // ln|u|: its derivative is u′/u, the abs's own derivative left out.
        if (e.name == "ln" && u is Fn && u.name == "abs") Mul(listOf(d(u.arg), inner)) else Mul(listOf(inner, du))
    }
}

/** Products of sums distributed, whole powers of sums up to 12 developed. */
fun expand(e: Ex): Ex = when (e) {
    is Add -> simplify(Add(e.terms.map(::expand)))
    is Mul -> e.factors.map(::expand).fold(ONE) { acc, f -> distribute(acc, f) }
    is Pow -> {
        val b = expand(e.base)
        val k = (e.exp as? Num)?.q
        if (b is Add && k != null && k.isInteger && k.n in BigInteger.TWO..BigInteger.valueOf(12)) (1 until k.n.toInt()).fold(b as Ex) { acc, _ -> distribute(acc, b) }
        else simplify(Pow(b, e.exp))
    }
    is Fn -> simplify(Fn(e.name, expand(e.arg)))
    else -> e
}

private fun termsOf(e: Ex) = if (e is Add) e.terms else listOf(e)

private fun distribute(a: Ex, b: Ex): Ex = simplify(Add(termsOf(a).flatMap { s -> termsOf(b).map { t -> Mul(listOf(s, t)) } }))

/** The polynomial's coefficients by degree, when [e] is one with rational coefficients. */
fun poly(e: Ex): Map<Int, Q>? {
    val out = HashMap<Int, Q>()
    for (t in termsOf(expand(e))) {
        val (c, rest) = split(t)
        val k = when {
            rest == ONE -> 0
            rest == X -> 1
            rest is Pow && rest.base == X && rest.exp is Num && rest.exp.q.isInteger && rest.exp.q.sign > 0 -> rest.exp.q.n.toInt()
            else -> return null
        }
        out[k] = (out[k] ?: Q.ZERO) + c
    }
    return out.filterValues { !it.isZero }
}

fun degreeOf(p: Map<Int, Q>) = p.keys.maxOrNull() ?: -1

fun polyEx(p: Map<Int, Q>): Ex = simplify(Add(p.map { (k, c) -> Mul(listOf(Num(c), Pow(X, num(k.toLong())))) }.ifEmpty { listOf(ZERO) }))

/** p(r). */
private fun evalPoly(p: Map<Int, Q>, r: Q): Q = p.entries.fold(Q.ZERO) { s, (k, c) -> s + c * r.pow(k) }

/** p divided by x − r (r a root). */
private fun deflate(p: Map<Int, Q>, r: Q): Map<Int, Q> {
    val n = degreeOf(p)
    val out = HashMap<Int, Q>()
    var carry = Q.ZERO
    for (k in n downTo 1) {
        carry = (p[k] ?: Q.ZERO) + carry * r
        out[k - 1] = carry
    }
    return out.filterValues { !it.isZero }
}

private fun divisors(n: BigInteger): List<BigInteger> {
    val a = n.abs()
    if (a.signum() == 0) return listOf(BigInteger.ONE)
    if (a.bitLength() > 40) return listOf(BigInteger.ONE)
    val v = a.toLong()
    val out = ArrayList<BigInteger>()
    var k = 1L
    while (k * k <= v) { if (v % k == 0L) { out += BigInteger.valueOf(k); if (k * k != v) out += BigInteger.valueOf(v / k) }; k++ }
    return out
}

/** The rational roots of p with their multiplicity, and what is left once they are divided out. */
fun rationalRoots(p: Map<Int, Q>): Pair<List<Pair<Q, Int>>, Map<Int, Q>> {
    var rest = p
    val roots = ArrayList<Pair<Q, Int>>()
    // Zero first.
    var zero = 0
    while (degreeOf(rest) > 0 && (rest[0] ?: Q.ZERO).isZero) { rest = rest.mapKeys { it.key - 1 }; zero++ }
    if (zero > 0) roots += Q.ZERO to zero
    while (degreeOf(rest) > 0) {
        // Whole coefficients: candidates ±p/q, p | a₀, q | aₙ.
        val lcm = rest.values.fold(BigInteger.ONE) { l, c -> l / l.gcd(c.d) * c.d }
        val ints = rest.mapValues { (it.value * Q.of(lcm, BigInteger.ONE)).n }
        val a0 = ints[0] ?: BigInteger.ZERO; val an = ints[degreeOf(rest)]!!
        val found = divisors(a0).flatMap { pp -> divisors(an).flatMap { qq -> listOf(Q.of(pp, qq), Q.of(-pp, qq)) } }.distinct().firstOrNull { evalPoly(rest, it).isZero } ?: break
        var m = 0
        while (degreeOf(rest) > 0 && evalPoly(rest, found).isZero) { rest = deflate(rest, found); m++ }
        roots += found to m
    }
    return roots to rest
}

/**
 * A result put in order, or left as it is: a factor every term shares taken
 * out, (x² − 2x + 2)eˣ, (sin(x) − cos(x))eˣ/2; else the terms sharing the
 * same non-polynomial part gathered under it.
 */
fun tidy(e: Ex): Ex {
    val ex = expand(e)
    if (ex !is Add) return e
    fun factorsOf(t: Ex) = (if (t is Mul) t.factors else listOf(t)).filter { it !is Num }
    fun isPoly(f: Ex) = f == X || (f is Pow && f.base == X && f.exp is Num && (f.exp as Num).q.isInteger && (f.exp as Num).q.sign > 0)
    // A factor every term holds, no x: eˣ, cos(x).
    val shared = factorsOf(ex.terms[0]).filter { f -> !isPoly(f) && ex.terms.all { t -> f in factorsOf(t) } }
    if (shared.isNotEmpty()) {
        val common = simplify(Mul(shared))
        val inside = simplify(Add(ex.terms.map { simplify(Mul(listOf(it, Pow(common, num(-1))))) }))
        // The rational coefficient outside when all share it: (sin(x) − cos(x))eˣ/2.
        val c = (inside as? Add)?.terms?.map { split(it).first.abs() }?.distinct()?.singleOrNull()
        return if (c != null && !c.isOne) simplify(Mul(listOf(Num(c), simplify(Mul(listOf(inside, Num(Q.ONE / c)))), common)))
        else simplify(Mul(listOf(inside, common)))
    }
    val groups = LinkedHashMap<String, Pair<Ex, MutableList<Ex>>>()
    for (t in ex.terms) {
        val factors = if (t is Mul) t.factors else listOf(t)
        val polyPart = factors.filter { it is Num || isPoly(it) }
        val rest = factors - polyPart.toSet()
        val restEx = simplify(Mul(rest.ifEmpty { listOf(ONE) }))
        groups.getOrPut(restEx.toString()) { restEx to ArrayList() }.second += simplify(Mul(polyPart.ifEmpty { listOf(ONE) }))
    }
    // Only worth it when some part gathers several terms.
    if (groups.values.none { it.second.size > 1 && it.first != ONE }) return e
    return simplify(Add(groups.values.map { (rest, ps) -> simplify(Mul(listOf(simplify(Add(ps)), rest))) }))
}

/**
 * A sum of rational terms over one denominator, cancelled: the quotient
 * rule's result, −2/(x − 1)². Null when [e] is not a rational function.
 */
fun together(e: Ex): Ex? {
    val terms = if (e is Add) e.terms else return null
    // Each term as (numerator, denominator) polynomials.
    val parts = terms.map { t ->
        val fs = if (t is Mul) t.factors else listOf(t)
        var top: Ex = ONE; var bottom: Ex = ONE
        for (f in fs) {
            if (f is Pow && f.exp is Num && (f.exp as Num).q.sign < 0 && (f.exp as Num).q.isInteger) bottom = Mul(listOf(bottom, Pow(f.base, Num(-(f.exp as Num).q))))
            else top = Mul(listOf(top, f))
        }
        val p = poly(top) ?: return null
        val q = poly(bottom) ?: return null
        p to q
    }
    if (parts.all { degreeOf(it.second) <= 0 }) return null
    var num: Map<Int, Q> = emptyMap(); var den: Map<Int, Q> = mapOf(0 to Q.ONE)
    for ((p, q) in parts) {
        // a/b + p/q = (aq + pb)/(bq), the common factor taken out at the end.
        num = addP(mulP(num, q), mulP(p, den)); den = mulP(den, q)
    }
    val g = gcdP(num, den)
    if (degreeOf(g) > 0) { num = divP(num, g); den = divP(den, g) }
    // The denominator written back as powers of its factors when they are rational: (x − 1)².
    val (roots, rest) = rationalRoots(den)
    val lead = rest[degreeOf(rest)] ?: Q.ONE
    var denEx: Ex = polyEx(rest.mapValues { it.value / lead })
    for ((r, m) in roots) denEx = simplify(Mul(listOf(denEx, Pow(simplify(Add(listOf(X, Num(-r)))), num(m.toLong())))))
    return simplify(Mul(listOf(polyEx(num.mapValues { it.value / lead }), Pow(denEx, num(-1)))))
}

private fun addP(a: Map<Int, Q>, b: Map<Int, Q>) = (a.keys + b.keys).associateWith { (a[it] ?: Q.ZERO) + (b[it] ?: Q.ZERO) }.filterValues { !it.isZero }
private fun mulP(a: Map<Int, Q>, b: Map<Int, Q>): Map<Int, Q> {
    val out = HashMap<Int, Q>()
    for ((i, x) in a) for ((j, y) in b) out[i + j] = (out[i + j] ?: Q.ZERO) + x * y
    return out.filterValues { !it.isZero }
}
private fun divmodP(p: Map<Int, Q>, d: Map<Int, Q>): Pair<Map<Int, Q>, Map<Int, Q>> {
    var r = p; val s = HashMap<Int, Q>()
    val dd = degreeOf(d); val lead = d[dd]!!
    while (r.isNotEmpty() && degreeOf(r) >= dd) {
        val k = degreeOf(r) - dd; val c = r[degreeOf(r)]!! / lead
        s[k] = (s[k] ?: Q.ZERO) + c
        r = addP(r, mulP(d, mapOf(k to -c)))
    }
    return s to r
}
private fun divP(p: Map<Int, Q>, d: Map<Int, Q>) = divmodP(p, d).first
private fun gcdP(a: Map<Int, Q>, b: Map<Int, Q>): Map<Int, Q> {
    var x = a; var y = b
    while (y.isNotEmpty()) { val r = divmodP(x, y).second; x = y; y = r }
    return x
}
