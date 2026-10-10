package com.yaz.calculator.engine

import java.math.BigInteger
import kotlin.random.Random

/**
 * The TI-82's MATH, NUM, PROB and hyperbolic functions, worked out exactly
 * where the value allows: abs, round, iPart, fPart, int, min, max, gcd, lcm,
 * remainder, ∛, root, logBASE, nPr, nCr, rand, randInt, sinh … tanh⁻¹.
 * Null for a function this object does not know (the trigonometric ones).
 */
internal object Functions {

    fun apply(name: String, a: List<UnifiedReal>, @Suppress("UNUSED_PARAMETER") degrees: Boolean): UnifiedReal? = when (name) {
        "abs(" -> one(a).let { if (it.signum(PRECISION) < 0) it.negate() else it }
        "int(" -> UnifiedReal(floor(one(a)))
        "iPart(" -> one(a).let { x -> UnifiedReal(if (x.signum(PRECISION) < 0) floor(x.negate()).negate() else floor(x)) }
        "fPart(" -> one(a).let { x -> x.subtract(UnifiedReal(if (x.signum(PRECISION) < 0) floor(x.negate()).negate() else floor(x))) }
        "round(" -> round(a[0], if (a.size > 1) whole(a[1]).toInt() else 0)
        "min(" -> a.ifEmpty { syntax() }.reduce { x, y -> if (x.compareTo(y) <= 0) x else y }
        "max(" -> a.ifEmpty { syntax() }.reduce { x, y -> if (x.compareTo(y) >= 0) x else y }
        "gcd(" -> a.ifEmpty { syntax() }.map(::whole).reduce(BigInteger::gcd).let(::UnifiedReal)
        "lcm(" -> a.ifEmpty { syntax() }.map(::whole).reduce { x, y -> if (x.signum() == 0 || y.signum() == 0) BigInteger.ZERO else x.multiply(y).abs().divide(x.gcd(y)) }.let(::UnifiedReal)
        "remainder(" -> two(a).let { (x, y) -> whole(y).let { d -> if (d.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO) else UnifiedReal(whole(x).mod(d.abs())) } }
        "∛(" -> one(a).let { x -> if (x.signum(PRECISION) < 0) nth(x.negate(), 3).negate() else nth(x, 3) }
        // root(x, n): the n-th root, odd roots of negatives included.
        "root(" -> two(a).let { (x, n) ->
            val k = whole(n)
            if (k.signum() == 0) throw CalcException(CalcError.DOMAIN)
            if (k.signum() < 0 || k > BigInteger.valueOf(1000)) throw CalcException(CalcError.DOMAIN)
            if (x.signum(PRECISION) < 0) {
                if (!k.testBit(0)) throw CalcException(CalcError.DOMAIN)
                nth(x.negate(), k.toInt()).negate()
            } else nth(x, k.toInt())
        }
        "logBASE(" -> two(a).let { (x, b) -> x.ln().divide(b.ln()) }
        "nPr(" -> two(a).let { (n, r) -> permutations(whole(n), whole(r), choose = false) }
        "nCr(" -> two(a).let { (n, r) -> permutations(whole(n), whole(r), choose = true) }
        "rand(" -> UnifiedReal.valueOf(Random.nextDouble())
        "randInt(" -> two(a).let { (x, y) ->
            val lo = whole(x).toLong(); val hi = whole(y).toLong()
            if (lo > hi) throw CalcException(CalcError.DOMAIN)
            UnifiedReal(Random.nextLong(lo, hi + 1))
        }
        "sinh(" -> one(a).let { x -> x.exp().subtract(x.negate().exp()).divide(UnifiedReal.TWO) }
        "cosh(" -> one(a).let { x -> x.exp().add(x.negate().exp()).divide(UnifiedReal.TWO) }
        "tanh(" -> one(a).let { x -> x.multiply(UnifiedReal.TWO).exp().let { e -> e.subtract(UnifiedReal.ONE).divide(e.add(UnifiedReal.ONE)) } }
        "sinh⁻¹(" -> one(a).let { x -> x.add(x.multiply(x).add(UnifiedReal.ONE).sqrt()).ln() }
        "cosh⁻¹(" -> one(a).let { x -> x.add(x.multiply(x).subtract(UnifiedReal.ONE).sqrt()).ln() }
        "tanh⁻¹(" -> one(a).let { x -> UnifiedReal.ONE.add(x).divide(UnifiedReal.ONE.subtract(x)).ln().divide(UnifiedReal.TWO) }
        else -> null
    }

    private fun syntax(): Nothing = throw CalcException(CalcError.SYNTAX)
    private fun one(a: List<UnifiedReal>) = a.singleOrNull() ?: syntax()
    private fun two(a: List<UnifiedReal>) = if (a.size == 2) a[0] to a[1] else syntax()

    /** A whole number, or DOMAIN. */
    private fun whole(x: UnifiedReal): BigInteger = x.bigIntegerValue() ?: throw CalcException(CalcError.DOMAIN)

    /** The greatest whole number not above [x]: an approximation, then checked exactly. */
    private fun floor(x: UnifiedReal): BigInteger {
        x.bigIntegerValue()?.let { return it }
        var c = x.crValue().get_appr(-64).shiftRight(64)
        if (UnifiedReal(c).compareTo(x) > 0) c = c.subtract(BigInteger.ONE)
        if (UnifiedReal(c.add(BigInteger.ONE)).compareTo(x) <= 0) c = c.add(BigInteger.ONE)
        return c
    }

    /** The [n]-th root of x ≥ 0, exact when x is a whole n-th power (∛27 is 3). */
    private fun nth(x: UnifiedReal, n: Int): UnifiedReal {
        x.bigIntegerValue()?.let { v ->
            val guess = Math.round(Math.pow(v.toDouble(), 1.0 / n)).toBigInteger()
            for (r in listOf(guess.subtract(BigInteger.ONE), guess, guess.add(BigInteger.ONE))) if (r.signum() >= 0 && r.pow(n) == v) return UnifiedReal(r)
        }
        return x.pow(UnifiedReal.ONE.divide(UnifiedReal(n.toLong())))
    }

    private fun round(x: UnifiedReal, digits: Int): UnifiedReal {
        if (digits !in 0..100) throw CalcException(CalcError.DOMAIN)
        val scale = UnifiedReal.TEN.pow(UnifiedReal(digits.toLong()))
        val scaled = x.multiply(scale)
        val half = if (scaled.signum(PRECISION) < 0) UnifiedReal.HALF.negate() else UnifiedReal.HALF
        val r = scaled.add(half).let { y -> if (y.signum(PRECISION) < 0) floor(y.negate()).negate() else floor(y) }
        return UnifiedReal(r).divide(scale)
    }

    private fun permutations(n: BigInteger, r: BigInteger, choose: Boolean): UnifiedReal {
        if (n.signum() < 0 || r.signum() < 0 || n > MAX_N) throw CalcException(CalcError.DOMAIN)
        if (r > n) return UnifiedReal.ZERO
        val k = if (choose) r.min(n.subtract(r)).toInt() else r.toInt()
        var out = BigInteger.ONE
        for (i in 0 until k) out = out.multiply(n.subtract(BigInteger.valueOf(i.toLong())))
        if (choose) for (i in 2..k) out = out.divide(BigInteger.valueOf(i.toLong()))
        return UnifiedReal(out)
    }

    private val MAX_N = BigInteger.valueOf(100_000)
    /** Bits of precision when a sign or an order must be decided. */
    private const val PRECISION = -200
}
