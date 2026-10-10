package com.yaz.calculator.engine.cas

import java.math.BigInteger

/**
 * One canonical form: sums flattened with like terms gathered, products
 * flattened with equal bases gathered into powers, numbers folded exactly,
 * the known values of the functions written out (sin(π/6) = 1/2, ln(1) = 0,
 * exp(ln u) = u). Equal expressions come out structurally equal.
 */
fun simplify(e: Ex): Ex = when (e) {
    is Num, X, is Const -> e
    is Fn -> simplifyFn(e.name, simplify(e.arg))
    is Pow -> simplifyPow(simplify(e.base), simplify(e.exp))
    is Mul -> simplifyMul(e.factors.map(::simplify))
    is Add -> simplifyAdd(e.terms.map(::simplify))
}

/** The number in front and the rest: 3x² gives (3, x²). */
fun split(e: Ex): Pair<Q, Ex> = when (e) {
    is Num -> e.q to ONE
    is Mul -> (e.factors.firstOrNull() as? Num)?.let { c -> c.q to (if (e.factors.size == 2) e.factors[1] else Mul(e.factors.drop(1))) } ?: (Q.ONE to e)
    else -> Q.ONE to e
}

private fun withCoefficient(c: Q, rest: Ex): Ex = when {
    c.isZero -> ZERO
    rest == ONE -> Num(c)
    c.isOne -> rest
    rest is Mul -> Mul(listOf(Num(c)) + rest.factors)
    else -> Mul(listOf(Num(c), rest))
}

/** x's degree as far as it reads simply, to put the terms of a sum in order. */
private fun degree(e: Ex): Double = when (e) {
    X -> 1.0
    is Pow -> if (e.base == X && e.exp is Num) e.exp.q.toDouble() else if (e.hasX) 0.5 else 0.0
    is Mul -> e.factors.sumOf { degree(it) }
    is Fn -> if (e.hasX) 0.25 else 0.0
    else -> 0.0
}

private fun rank(e: Ex): Int = when (e) {
    is Num -> 0
    is Const -> 1
    X -> 2
    is Pow -> if (e.base == X) 2 else if (e.base is Num) 1 else 4
    is Fn -> 5
    is Add -> 3
    is Mul -> 6
}

private fun key(e: Ex): String = e.toString()

private fun simplifyAdd(items: List<Ex>): Ex {
    val flat = items.flatMap { if (it is Add) it.terms else listOf(it) }
    var constant = Q.ZERO
    val gathered = LinkedHashMap<String, Pair<Ex, Q>>()
    for (t in flat) {
        if (t is Num) { constant += t.q; continue }
        val (c, rest) = split(t)
        val k = key(rest)
        val prev = gathered[k]
        gathered[k] = rest to ((prev?.second ?: Q.ZERO) + c)
    }
    val terms = gathered.values.mapNotNull { (rest, c) -> if (c.isZero) null else withCoefficient(c, rest) }
        .sortedWith(compareByDescending<Ex> { degree(it) }.thenBy { key(split(it).second) })
        .toMutableList()
    if (!constant.isZero) terms += Num(constant)
    return when (terms.size) { 0 -> ZERO; 1 -> terms[0]; else -> Add(terms) }
}

private fun simplifyMul(items: List<Ex>): Ex {
    val flat = items.flatMap { if (it is Mul) it.factors else listOf(it) }
    var coefficient = Q.ONE
    // Equal bases gathered: x·x² = x³, (x + 1)/(x + 1) = 1; exponentials into one.
    val bases = LinkedHashMap<String, Pair<Ex, MutableList<Ex>>>()
    val exps = ArrayList<Ex>()
    for (f in flat) {
        when {
            f is Num -> { if (f.q.isZero) return ZERO; coefficient *= f.q }
            f is Fn && f.name == "exp" -> exps += f.arg
            else -> {
                val (b, k) = if (f is Pow) f.base to f.exp else f to ONE
                bases.getOrPut(key(b)) { b to ArrayList() }.second += k
            }
        }
    }
    val factors = ArrayList<Ex>()
    for ((b, ks) in bases.values) {
        val k = simplify(Add(ks))
        val p = simplifyPow(b, k)
        when {
            p is Num -> coefficient *= p.q
            p is Mul -> p.factors.forEach { if (it is Num) coefficient *= it.q else factors += it }
            p != ONE -> factors += p
        }
    }
    if (exps.isNotEmpty()) {
        val e = simplifyFn("exp", simplify(Add(exps)))
        if (e is Num) coefficient *= e.q else if (e != ONE) factors += e
    }
    if (coefficient.isZero) return ZERO
    // Bases left alone might gather now (√2·√2), once.
    val again = factors.groupBy { key(if (it is Pow) it.base else it) }
    if (again.values.any { it.size > 1 }) return simplifyMul(listOf(Num(coefficient)) + factors)
    factors.sortWith(compareBy<Ex> { rank(it) }.thenBy { key(it) })
    return when {
        factors.isEmpty() -> Num(coefficient)
        coefficient.isOne && factors.size == 1 -> factors[0]
        else -> Mul((if (coefficient.isOne) emptyList() else listOf(Num(coefficient))) + factors)
    }
}

/** n written as m^k·r with r as small as can be quickly found (r has no k-th power factor below 10⁴). */
private fun extractRoot(n: BigInteger, k: Int): Pair<BigInteger, BigInteger> {
    var outside = BigInteger.ONE
    var inside = n
    var p = 2L
    while (p < 10_000 && BigInteger.valueOf(p).pow(k) <= inside) {
        val pk = BigInteger.valueOf(p).pow(k)
        while (inside.mod(pk).signum() == 0) { inside /= pk; outside *= BigInteger.valueOf(p) }
        p++
    }
    return outside to inside
}

private fun simplifyPow(b: Ex, k: Ex): Ex {
    if (k is Num && k.q.isZero) return ONE
    if (k == ONE) return b
    if (b == ONE) return ONE
    if (b is Num && b.q.isZero) return if (k is Num && k.q.sign > 0) ZERO else Pow(b, k)
    if (b == E) return simplifyFn("exp", k)
    if (b is Num && k is Num) {
        val q = k.q
        if (q.isInteger && q.n.abs() <= BigInteger.valueOf(2000)) return Num(b.q.pow(q.n.toInt()))
        if (!q.isInteger && b.q.sign > 0 && q.d <= BigInteger.valueOf(12)) {
            // (a/b)^(p/q): perfect q-th powers taken out, 12^(1/2) = 2·3^(1/2), 2^(3/2) = 2·2^(1/2).
            val (on, inN) = extractRoot(b.q.n, q.d.toInt())
            val (od, inD) = extractRoot(b.q.d, q.d.toInt())
            val outside = Q.of(on, od)
            val inner = Q.of(inN, inD)
            if (outside.isOne) {
                val whole = q.n.divide(q.d)
                if (q.n.signum() > 0 && whole.signum() > 0) return simplifyMul(listOf(Num(b.q.pow(whole.toInt())), Pow(b, Num(q - Q.of(whole, BigInteger.ONE)))))
                return Pow(b, k)
            }
            return simplifyMul(listOf(Num(outside.pow(q.n.toInt())), if (inner.isOne) ONE else simplifyPow(Num(inner), Num(q))))
        }
        return Pow(b, k)
    }
    // (u^a)^k = u^(ak) when no sign is lost: k whole, or a with an odd numerator ((x²)^(1/2) is |x|, kept).
    if (b is Pow && k is Num && (k.q.isInteger || (b.exp is Num && b.exp.q.n.testBit(0)))) {
        return simplifyPow(b.base, simplify(Mul(listOf(b.exp, k))))
    }
    if (b is Mul && k is Num && k.q.isInteger) return simplifyMul(b.factors.map { Pow(it, k) })
    if (b is Mul && k is Num) {
        // (4x)^(1/2) = 2·x^(1/2): the number taken out when positive.
        val (c, rest) = split(b)
        if (c.sign > 0 && !c.isOne) return simplifyMul(listOf(simplifyPow(Num(c), k), Pow(rest, k)))
    }
    if (b is Fn && b.name == "exp") return simplifyFn("exp", simplify(Mul(listOf(b.arg, k))))
    return Pow(b, k)
}

/** π times a fraction, if [a] is one: (π/6) gives 1/6. */
private fun piMultiple(a: Ex): Q? = when {
    a is Num && a.q.isZero -> Q.ZERO
    a == PI -> Q.ONE
    a is Mul && a.factors.size == 2 && a.factors[0] is Num && a.factors[1] == PI -> (a.factors[0] as Num).q
    else -> null
}

private val SQRT2_2: Ex get() = Mul(listOf(Num(Q.HALF), Pow(num(2), Num(Q.HALF))))
private val SQRT3_2: Ex get() = Mul(listOf(Num(Q.HALF), Pow(num(3), Num(Q.HALF))))

/** sin(qπ) where q has a denominator of 1, 2, 3, 4 or 6. */
private fun sinPi(q: Q): Ex? {
    val twelfths = q * Q.of(12)
    if (!twelfths.isInteger) return null
    val k = twelfths.n.mod(BigInteger.valueOf(24)).toInt()
    return when (k) {
        0, 12 -> ZERO; 6 -> ONE; 18 -> num(-1)
        2, 10 -> Num(Q.HALF); 14, 22 -> Num(-Q.HALF)
        3, 9 -> SQRT2_2; 15, 21 -> simplify(Mul(listOf(num(-1), SQRT2_2)))
        4, 8 -> SQRT3_2; 16, 20 -> simplify(Mul(listOf(num(-1), SQRT3_2)))
        else -> null
    }
}

private fun negative(a: Ex): Boolean = split(a).first.sign < 0 || (a is Add && split(a.terms[0]).first.sign < 0)

private fun simplifyFn(name: String, a: Ex): Ex {
    when (name) {
        "ln" -> {
            if (a == ONE) return ZERO
            if (a == E) return ONE
            if (a is Fn && a.name == "exp") return a.arg
            if (a is Pow && a.base == E) return a.exp
        }
        "exp" -> {
            if (a is Num && a.q.isZero) return ONE
            if (a == ONE) return E
            if (a is Fn && a.name == "ln") return a.arg
            // e^(k·ln u) = u^k.
            if (a is Mul && a.factors.size == 2 && a.factors[0] is Num && (a.factors[1] as? Fn)?.name == "ln") return simplifyPow((a.factors[1] as Fn).arg, a.factors[0])
        }
        "sin" -> {
            piMultiple(a)?.let { q -> sinPi(q)?.let { return it } }
            if (negative(a)) return simplify(Mul(listOf(num(-1), Fn("sin", simplify(Mul(listOf(num(-1), a)))))))
        }
        "cos" -> {
            piMultiple(a)?.let { q -> sinPi(q + Q.HALF)?.let { return it } }
            if (negative(a)) return Fn("cos", simplify(Mul(listOf(num(-1), a))))
        }
        "tan" -> {
            piMultiple(a)?.let { q ->
                val s = sinPi(q); val c = sinPi(q + Q.HALF)
                if (s != null && c != null && c != ZERO) return simplify(Mul(listOf(s, Pow(c, num(-1)))))
            }
        }
        "asin" -> { if (a == ZERO) return ZERO; if (a == ONE) return simplify(Mul(listOf(Num(Q.HALF), PI))); if (a == Num(Q.HALF)) return simplify(Mul(listOf(Num(Q.of(1, 6)), PI))) }
        "acos" -> { if (a == ONE) return ZERO; if (a == ZERO) return simplify(Mul(listOf(Num(Q.HALF), PI))); if (a == Num(Q.HALF)) return simplify(Mul(listOf(Num(Q.of(1, 3)), PI))) }
        "atan" -> { if (a == ZERO) return ZERO; if (a == ONE) return simplify(Mul(listOf(Num(Q.of(1, 4)), PI))) }
        "abs" -> {
            if (a is Num) return Num(a.q.abs())
            if (a is Fn && a.name == "exp") return a
        }
        "sinh", "tanh" -> if (a == ZERO) return ZERO
        "cosh" -> if (a == ZERO) return ONE
    }
    return Fn(name, a)
}
