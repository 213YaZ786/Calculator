package com.yaz.calculator.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.hipparchus.linear.MatrixUtils
import org.hipparchus.linear.QRDecomposition

/** 1-Var Stats, as the TI's STAT CALC menu gives them. */
data class OneVar(
    val n: Double, val mean: Double, val sum: Double, val sumSquares: Double,
    val sigma: Double, val s: Double, val min: Double, val q1: Double, val median: Double, val q3: Double, val max: Double
)

enum class RegKind { LIN, QUAD, EXP, LN, PWR }

/**
 * A regression: its coefficients, how well it fits ([r] for the linear
 * ones, [r2] always) and its equation in the app's form, ready for Y=.
 */
data class Regression(val kind: RegKind, val coefficients: List<Double>, val r: Double?, val r2: Double, val equation: String, val shown: String)

object Stats {

    /**
     * [values] with their [counts] (1 each when null). Quartiles as the French
     * lycée defines them when [french] (Q1: the smallest value with at least a
     * quarter of the data at or below it), else as the TI does (medians of the
     * halves, the middle value left out).
     */
    fun oneVar(values: List<Double>, counts: List<Double>?, french: Boolean): OneVar? {
        if (values.isEmpty()) return null
        val w = counts ?: List(values.size) { 1.0 }
        if (w.size != values.size || w.any { it < 0 || it != Math.rint(it) }) return null
        val data = values.zip(w).flatMap { (v, c) -> List(c.toInt().coerceAtMost(1_000_000)) { v } }.sorted()
        if (data.isEmpty()) return null
        val n = data.size.toDouble()
        val sum = data.sum()
        val sumSq = data.sumOf { it * it }
        val mean = sum / n
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        val s = if (data.size > 1) sqrt(data.sumOf { (it - mean) * (it - mean) } / (n - 1)) else Double.NaN
        val median = median(data)
        val (q1, q3) = if (french) {
            data[ceil(n / 4).toInt() - 1] to data[ceil(3 * n / 4).toInt() - 1]
        } else {
            val half = data.size / 2
            val lower = data.subList(0, half)
            val upper = data.subList(if (data.size % 2 == 0) half else half + 1, data.size)
            (if (lower.isEmpty()) data.first() else median(lower)) to (if (upper.isEmpty()) data.last() else median(upper))
        }
        return OneVar(n, mean, sum, sumSq, sqrt(variance), s, data.first(), q1, median, q3, data.last())
    }

    private fun median(sorted: List<Double>): Double {
        val m = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[m] else (sorted[m - 1] + sorted[m]) / 2
    }

    /** The TI's LinReg (ax + b), QuadReg, ExpReg (a·bˣ), LnReg (a + b·ln x), PwrReg (a·xᵇ). */
    fun regression(kind: RegKind, xs: List<Double>, ys: List<Double>): Regression? {
        if (xs.size != ys.size || xs.size < if (kind == RegKind.QUAD) 3 else 2) return null
        return when (kind) {
            RegKind.LIN -> linear(xs, ys)?.let { (a, b, r) ->
                Regression(kind, listOf(a, b), r, r * r, "${num(a)}x${signed(b)}", "y = ${show(a)}x ${plus(b)}")
            }
            RegKind.QUAD -> {
                val m = MatrixUtils.createRealMatrix(xs.size, 3)
                xs.forEachIndexed { i, x -> m.setRow(i, doubleArrayOf(x * x, x, 1.0)) }
                val c = runCatching { QRDecomposition(m).solver.solve(MatrixUtils.createRealVector(ys.toDoubleArray())).toArray() }.getOrNull() ?: return null
                val fit = xs.map { c[0] * it * it + c[1] * it + c[2] }
                Regression(kind, c.toList(), null, r2(ys, fit), "${num(c[0])}x²${signed(c[1])}x${signed(c[2])}", "y = ${show(c[0])}x² ${plus(c[1])}x ${plus(c[2])}")
            }
            RegKind.EXP -> {
                if (ys.any { it <= 0 }) return null
                linear(xs, ys.map(::ln))?.let { (slope, icpt, r) ->
                    val a = exp(icpt); val b = exp(slope)
                    Regression(kind, listOf(a, b), r, r * r, "${num(a)}×${num(b)}^x", "y = ${show(a)} × ${show(b)}ˣ")
                }
            }
            RegKind.LN -> {
                if (xs.any { it <= 0 }) return null
                linear(xs.map(::ln), ys)?.let { (b, a, r) ->
                    Regression(kind, listOf(a, b), r, r * r, "${num(a)}${signed(b)}ln(x)", "y = ${show(a)} ${plus(b)} ln x")
                }
            }
            RegKind.PWR -> {
                if (xs.any { it <= 0 } || ys.any { it <= 0 }) return null
                linear(xs.map(::ln), ys.map(::ln))?.let { (b, icpt, r) ->
                    val a = exp(icpt)
                    Regression(kind, listOf(a, b), r, r * r, "${num(a)}x^${num(b)}", "y = ${show(a)} × x^${show(b)}")
                }
            }
        }
    }

    /** Least squares y = a·x + b, and the correlation coefficient r. */
    private fun linear(xs: List<Double>, ys: List<Double>): Triple<Double, Double, Double>? {
        val n = xs.size.toDouble()
        val mx = xs.average(); val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val syy = ys.sumOf { (it - my) * (it - my) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        if (sxx == 0.0 || n < 2) return null
        val a = sxy / sxx
        val r = if (syy == 0.0) 1.0 else sxy / sqrt(sxx * syy)
        return Triple(a, my - a * mx, r)
    }

    private fun r2(ys: List<Double>, fit: List<Double>): Double {
        val my = ys.average()
        val tot = ys.sumOf { (it - my) * (it - my) }
        val res = ys.indices.sumOf { (ys[it] - fit[it]) * (ys[it] - fit[it]) }
        return if (tot == 0.0) 1.0 else 1 - res / tot
    }

    /** A coefficient in the app's form for Y= (10 significant digits, − sign). */
    private fun num(v: Double): String =
        java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString().replace('-', Sym.MINUS)

    private fun signed(v: Double) = if (v < 0) "${Sym.MINUS}${num(abs(v))}" else "+${num(v)}"

    fun show(v: Double): String = java.math.BigDecimal(v).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString().replace('-', '−')
    private fun plus(v: Double) = if (v < 0) "− ${show(abs(v))}" else "+ ${show(v)}"
}
