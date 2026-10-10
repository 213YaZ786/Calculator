package com.yaz.calculator.engine.cas

import java.math.BigInteger

/**
 * Calculator's own symbolic engine, written from the rules of the
 * textbooks (nothing taken from another engine): exact rationals, π and e,
 * expressions in x, simplified to one canonical form so that equal things
 * look equal. Enough for derivatives, primitives and integration by parts
 * as lycée and first-year university ask them.
 */

/** An exact rational number, always in lowest terms with a positive denominator. */
class Q private constructor(val n: BigInteger, val d: BigInteger) : Comparable<Q> {
    operator fun plus(o: Q) = of(n * o.d + o.n * d, d * o.d)
    operator fun minus(o: Q) = of(n * o.d - o.n * d, d * o.d)
    operator fun times(o: Q) = of(n * o.n, d * o.d)
    operator fun div(o: Q): Q { require(o.n.signum() != 0); return of(n * o.d, d * o.n) }
    operator fun unaryMinus() = of(-n, d)
    val isZero get() = n.signum() == 0
    val isOne get() = n == BigInteger.ONE && d == BigInteger.ONE
    val isInteger get() = d == BigInteger.ONE
    val sign get() = n.signum()
    fun toDouble() = n.toDouble() / d.toDouble()
    fun abs() = of(n.abs(), d)

    /** This to an integer power. */
    fun pow(k: Int): Q = if (k >= 0) of(n.pow(k), d.pow(k)) else { require(!isZero); of(d.pow(-k), n.pow(-k)) }

    override fun compareTo(other: Q) = (n * other.d).compareTo(other.n * d)
    override fun equals(other: Any?) = other is Q && n == other.n && d == other.d
    override fun hashCode() = n.hashCode() * 31 + d.hashCode()
    override fun toString() = if (d == BigInteger.ONE) n.toString() else "$n/$d"

    companion object {
        val ZERO = of(0); val ONE = of(1); val HALF = of(1, 2)
        fun of(v: Long, w: Long = 1) = of(BigInteger.valueOf(v), BigInteger.valueOf(w))
        fun of(n: BigInteger, d: BigInteger): Q {
            require(d.signum() != 0)
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            val s = if (d.signum() < 0) -1 else 1
            return Q(n / g * BigInteger.valueOf(s.toLong()), d.abs() / g)
        }

        /** A decimal as typed (2.5) made exact. */
        fun parse(text: String): Q {
            val dot = text.indexOf('.')
            if (dot < 0) return of(BigInteger(text), BigInteger.ONE)
            val digits = text.removeRange(dot, dot + 1)
            return of(BigInteger(digits.ifEmpty { "0" }), BigInteger.TEN.pow(text.length - dot - 1))
        }
    }
}

sealed interface Ex

data class Num(val q: Q) : Ex
data object X : Ex
/** π or e, kept exact. */
data class Const(val name: String) : Ex
data class Add(val terms: List<Ex>) : Ex
data class Mul(val factors: List<Ex>) : Ex
data class Pow(val base: Ex, val exp: Ex) : Ex
/** sin, cos, tan, asin, acos, atan, ln, exp, abs, sinh, cosh, tanh, of one argument. */
data class Fn(val name: String, val arg: Ex) : Ex

val ZERO: Ex = Num(Q.ZERO)
val ONE: Ex = Num(Q.ONE)
val PI: Ex = Const("π")
val E: Ex = Const("e")
fun num(v: Long, w: Long = 1): Ex = Num(Q.of(v, w))

operator fun Ex.plus(o: Ex): Ex = simplify(Add(listOf(this, o)))
operator fun Ex.minus(o: Ex): Ex = simplify(Add(listOf(this, Mul(listOf(num(-1), o)))))
operator fun Ex.times(o: Ex): Ex = simplify(Mul(listOf(this, o)))
operator fun Ex.div(o: Ex): Ex = simplify(Mul(listOf(this, Pow(o, num(-1)))))
operator fun Ex.unaryMinus(): Ex = simplify(Mul(listOf(num(-1), this)))
fun Ex.pow(k: Ex): Ex = simplify(Pow(this, k))
fun Ex.pow(k: Int): Ex = simplify(Pow(this, num(k.toLong())))
fun fn(name: String, a: Ex): Ex = simplify(Fn(name, a))

/** Does this hold x? */
val Ex.hasX: Boolean get() = when (this) {
    X -> true
    is Num, is Const -> false
    is Add -> terms.any { it.hasX }
    is Mul -> factors.any { it.hasX }
    is Pow -> base.hasX || exp.hasX
    is Fn -> arg.hasX
}

/** The value at [x] in doubles; NaN where there is none. */
fun Ex.at(x: Double): Double = when (this) {
    is Num -> q.toDouble()
    X -> x
    is Const -> if (name == "π") Math.PI else Math.E
    is Add -> terms.sumOf { it.at(x) }
    is Mul -> factors.fold(1.0) { p, f -> p * f.at(x) }
    is Pow -> {
        val b = base.at(x); val e = exp.at(x)
        val r = Math.pow(b, e)
        // A real odd root of a negative number: ∛(−8) = −2.
        if (r.isNaN() && b < 0 && exp is Num && exp.q.d.testBit(0)) Math.pow(-b, e).let { m -> if (exp.q.n.testBit(0)) -m else m } else r
    }
    is Fn -> arg.at(x).let { a ->
        when (name) {
            "sin" -> Math.sin(a); "cos" -> Math.cos(a); "tan" -> Math.tan(a)
            "asin" -> Math.asin(a); "acos" -> Math.acos(a); "atan" -> Math.atan(a)
            "ln" -> Math.log(a); "exp" -> Math.exp(a); "abs" -> Math.abs(a)
            "sinh" -> Math.sinh(a); "cosh" -> Math.cosh(a); "tanh" -> Math.tanh(a)
            else -> Double.NaN
        }
    }
}

/** This with x replaced by [by]. */
fun Ex.sub(by: Ex): Ex = when (this) {
    X -> by
    is Num, is Const -> this
    is Add -> simplify(Add(terms.map { it.sub(by) }))
    is Mul -> simplify(Mul(factors.map { it.sub(by) }))
    is Pow -> simplify(Pow(base.sub(by), exp.sub(by)))
    is Fn -> simplify(Fn(name, arg.sub(by)))
}

/** Numerically the same function as [o]: at seven points where both are defined. */
fun Ex.sameAs(o: Ex): Boolean {
    var checked = 0
    for (x in listOf(0.37, 0.71, 1.13, 1.61, 2.29, 2.83, -0.53, -1.27, 3.41, 0.13)) {
        val a = at(x); val b = o.at(x)
        if (!a.isFinite() || !b.isFinite()) continue
        if (kotlin.math.abs(a - b) > 1e-7 * (1 + kotlin.math.abs(a) + kotlin.math.abs(b))) return false
        checked++
    }
    return checked >= 4
}
