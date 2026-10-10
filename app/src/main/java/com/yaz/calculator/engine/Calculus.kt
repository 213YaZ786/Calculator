package com.yaz.calculator.engine

import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.differentiation.DSFactory
import org.hipparchus.analysis.differentiation.FiniteDifferencesDifferentiator
import org.hipparchus.analysis.integration.IterativeLegendreGaussIntegrator
import org.hipparchus.optim.MaxEval
import org.hipparchus.optim.nonlinear.scalar.GoalType
import org.hipparchus.optim.univariate.BrentOptimizer
import org.hipparchus.optim.univariate.SearchInterval
import org.hipparchus.optim.univariate.UnivariateObjectiveFunction

/**
 * The TI-82's calculus functions, as its MATH menu writes them, the
 * variable optional: nDeriv(x², x, 3), fnInt(x², 0, 1), Σ(1/x², x, 1, 100),
 * fMin(x² − 2x, −5, 5), fMax(…). Σ is exact (Calc term by term); the others
 * numerical (Hipparchus), as on the calculator.
 */
internal object Calculus {

    /** Where the parenthesis opened at [from] closes, or the text's end. */
    fun close(text: CharSequence, from: Int): Int {
        var depth = 1
        for (i in from until text.length) {
            when (text[i]) {
                Sym.OPEN -> depth++
                Sym.CLOSE -> if (--depth == 0) return i
            }
        }
        return text.length
    }

    fun value(name: String, inner: String, degrees: Boolean, calc: Calc): UnifiedReal {
        val args = split(inner).toMutableList()
        if (args.size >= 2 && args[1].trim() == "x") args.removeAt(1)
        val body = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: throw CalcException(CalcError.SYNTAX)
        fun number(i: Int): UnifiedReal = args.getOrNull(i)?.let { calc.evaluate(it, degrees) } ?: throw CalcException(CalcError.SYNTAX)
        fun double(i: Int) = number(i).crValue().toDouble()
        val f = function(body, degrees, calc)
        return when (name) {
            "nDeriv(" -> {
                if (args.size != 2) throw CalcException(CalcError.SYNTAX)
                val at = double(1)
                val step = 1e-3 * maxOf(1.0, Math.abs(at))
                val d = FiniteDifferencesDifferentiator(5, step).differentiate(f)
                result(d.value(DSFactory(1, 1).variable(0, at)).getPartialDerivative(1))
            }
            "fnInt(" -> {
                if (args.size != 3) throw CalcException(CalcError.SYNTAX)
                val a = double(1); val b = double(2)
                result(IterativeLegendreGaussIntegrator(5, 1e-12, 1e-14).integrate(MAX_EVALUATIONS, f, a, b))
            }
            "Σ(" -> {
                if (args.size != 3) throw CalcException(CalcError.SYNTAX)
                val from = number(1).bigIntegerValue() ?: throw CalcException(CalcError.DOMAIN)
                val to = number(2).bigIntegerValue() ?: throw CalcException(CalcError.DOMAIN)
                if (to.subtract(from).toLong() > MAX_TERMS) throw CalcException(CalcError.TOO_LARGE)
                var sum = UnifiedReal.ZERO
                var k = from
                while (k <= to) {
                    if (Thread.interrupted()) throw com.hp.creals.CR.AbortedException()
                    val at = UnifiedReal(k)
                    sum = sum.add(Calc(variable = { n -> if (n == "x") at else calc.variableOf(n) }).evaluate(body, degrees))
                    k = k.add(java.math.BigInteger.ONE)
                }
                sum
            }
            "fMin(", "fMax(" -> {
                if (args.size != 3) throw CalcException(CalcError.SYNTAX)
                val a = double(1); val b = double(2)
                if (!(a < b)) throw CalcException(CalcError.DOMAIN)
                val goal = if (name == "fMin(") GoalType.MINIMIZE else GoalType.MAXIMIZE
                val point = BrentOptimizer(1e-10, 1e-14).optimize(MaxEval(MAX_EVALUATIONS), UnivariateObjectiveFunction(f), goal, SearchInterval(a, b)).point
                result(point)
            }
            else -> throw CalcException(CalcError.SYNTAX)
        }
    }

    /** The arguments at the top level, split by the argument mark. */
    private fun split(inner: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((i, c) in inner.withIndex()) {
            when (c) {
                Sym.OPEN -> depth++
                Sym.CLOSE -> depth--
                Sym.ARG -> if (depth == 0) { out += inner.substring(start, i); start = i + 1 }
            }
        }
        out += inner.substring(start)
        return out
    }

    /** [body] as a function of x in doubles; a body without x is a constant. */
    private fun function(body: String, degrees: Boolean, calc: Calc): UnivariateFunction {
        val curve = Curve.of(body, degrees) { n -> calc.variableOf(n)?.let { runCatching { it.crValue().toDouble() }.getOrNull() } }
        if (curve != null) return UnivariateFunction { x -> curve(x) }
        val constant = calc.evaluate(body, degrees).crValue().toDouble()
        return UnivariateFunction { constant }
    }

    private fun result(d: Double): UnifiedReal {
        if (!d.isFinite()) throw CalcException(CalcError.DOMAIN)
        // A numerical result keeps the digits it can vouch for (12), not a double's binary tail.
        val text = java.math.BigDecimal(d).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
        return Calc.literal(text.replace('-', ' ').trim()).let { if (d < 0) it.negate() else it }
    }

    private const val MAX_EVALUATIONS = 100_000
    private const val MAX_TERMS = 100_000L
}
