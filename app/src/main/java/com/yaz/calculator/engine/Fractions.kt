package com.yaz.calculator.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import org.hipparchus.exception.MathIllegalStateException
import org.hipparchus.fraction.BigFraction

/**
 * Exact fractions beside decimals. A double is a small fraction when the
 * continued fraction of its value reaches it within double precision at a
 * small denominator. The layout "exact = approximate" ("≈" when the decimal
 * is cut) is NumWorks' calculation app.
 */
object Fractions {

    /** The largest denominator shown for a double, a converter or a sheet. */
    const val MAX_DENOMINATOR = 10_000L

    private const val MAX_ITERATIONS = 40

    /** Two to the minus fifty: a few units in the last place of a double, relatively. */
    private const val RELATIVE_EPSILON = 8.881784197001252E-16

    /** The user's switch ("Exact fractions", Settings); read by every screen and program. */
    @Volatile
    var enabled: Boolean = true

    // Source: Hipparchus 4.0.3 (Apache 2.0), BigFraction(double value, double epsilon, int maxIterations):
    // https://github.com/Hipparchus-Math/hipparchus/blob/4.0.3/hipparchus-core/src/main/java/org/hipparchus/fraction/BigFraction.java
    // convergents of the value, stopping within epsilon (here a few ulps) or at one ulp; its
    // BigFraction(double, long maxDenominator) bounds the denominator the same way.
    /** [value] as numerator and denominator (den > 1, reduced) when it is a small fraction to double precision; else null. */
    fun of(value: Double, maxDenominator: Long = MAX_DENOMINATOR): Pair<Long, Long>? {
        if (!value.isFinite() || value == 0.0 || abs(value) >= 1e15) return null
        val epsilon = abs(value) * RELATIVE_EPSILON
        val fraction = try {
            BigFraction(value, epsilon, MAX_ITERATIONS)
        } catch (e: MathIllegalStateException) {
            return null
        }
        val den = fraction.denominator
        if (den == BigInteger.ONE || den > BigInteger.valueOf(maxDenominator)) return null
        return fraction.numerator.toLong() to den.toLong()
    }

    // Source: Hipparchus BigFraction.toString (numerator "/" denominator):
    // https://github.com/Hipparchus-Math/hipparchus/blob/4.0.3/hipparchus-core/src/main/java/org/hipparchus/fraction/BigFraction.java
    /** "p/q" with the app's minus sign. */
    fun text(num: Long, den: Long): String = "${if (num < 0) Sym.MINUS.toString() else ""}${abs(num)}/$den"

    // Source: https://github.com/Hipparchus-Math/hipparchus/blob/4.0.3/hipparchus-core/src/main/java/org/hipparchus/fraction/BigFraction.java
    /** [value]'s fraction as text, when it has one and the switch is on. */
    fun textOf(value: Double): String? = if (!enabled) null else of(value)?.let { (p, q) -> text(p, q) }

    // Source: NumWorks Epsilon, EqualSign::Equal and EqualSign::Approximation in
    // https://github.com/numworks/epsilon/blob/master/epsilon/apps/calculation/calculation.cpp (computeEqualSign):
    // "=" when the decimal written is the exact value, "≈" when it is cut.
    /** "p/q = decimal", or "p/q ≈ decimal" when the decimal shown is not the fraction's own value; null without a fraction. */
    fun beside(value: Double, decimal: String): String? {
        if (!enabled) return null
        val (p, q) = of(value) ?: return null
        return "${text(p, q)} ${if (same(p, q, decimal)) "=" else "≈"} $decimal"
    }

    // Source: NumWorks Epsilon, IsCalculationOutputStrictEquality (computeEqualSign, calculation.cpp above), with java.math.BigDecimal.
    /** Whether [decimal] written out is exactly p/q. */
    private fun same(p: Long, q: Long, decimal: String): Boolean {
        val shown = decimal.replace(Sym.MINUS, '-').toBigDecimalOrNull() ?: return false
        return runCatching { BigDecimal(p).divide(BigDecimal(q), MathContext.UNLIMITED).compareTo(shown) == 0 }.getOrDefault(false)
    }

    // Source: LineageOS ExactCalculator, BoundedRational.toNiceString (the reduced n/d of an exact value),
    // https://github.com/LineageOS/android_packages_apps_ExactCalculator ; the denominator bound as Hipparchus' maxDenominator above.
    /**
     * The exact fraction of a value the engine knows exactly (UnifiedReal's
     * rational part), as text, when it is not a whole number and its
     * denominator is at most [maxDenominator]; null otherwise or when off.
     */
    fun exact(value: UnifiedReal, maxDenominator: Long = MAX_DENOMINATOR): String? {
        if (!enabled) return null
        val rational = value.boundedRationalValue() ?: return null
        val nice = rational.toNiceString()
        val slash = nice.indexOf('/')
        if (slash < 0) return null
        val den = nice.substring(slash + 1).toBigIntegerOrNull() ?: return null
        if (den > BigInteger.valueOf(maxDenominator)) return null
        return nice.replace('-', Sym.MINUS)
    }

    private val shown = ThreadLocal.withInitial { LinkedHashMap<String, Double>() }

    // Source: NumWorks Epsilon calculation.cpp (https://github.com/numworks/epsilon/blob/master/epsilon/apps/calculation/calculation.cpp):
    // the approximate output is kept beside the exact one to compare them; here a program's decimal text beside its double.
    /** A program shows [value] as [text]: remembered for [annotate]. */
    fun note(text: String, value: Double) {
        val map = shown.get()
        if (map.size > 400) map.clear()
        map[text] = value
    }

    // Source: as note above (Epsilon's per-calculation outputs, computed again for each calculation).
    /** A run starts: what earlier runs showed is forgotten. */
    fun forget() = shown.get().clear()

    private val RESULT = Regex("^(.*[=≈] )(−?\\d+(?:\\.\\d+)?)((?: [^\\d=≈]*)?)$")
    private val EARLIER = Regex("[=≈] [−(]?\\d")

    // Source: NumWorks Epsilon, DisplayOutput::ExactAndApproximate in
    // https://github.com/numworks/epsilon/blob/master/epsilon/apps/calculation/calculation.h : the line reads "exact = approximate".
    /** A result line "name = number unit" with the number's fraction first: "x = 3/4 = 0.75 m"; any other line stays. */
    fun annotate(line: String): String {
        if (!enabled) return line
        val m = RESULT.matchEntire(line) ?: return line
        val (head, number, tail) = m.destructured
        if (EARLIER.containsMatchIn(head)) return line
        val value = shown.get()[number] ?: return line
        val both = beside(value, number) ?: return line
        return head + both + tail
    }
}
