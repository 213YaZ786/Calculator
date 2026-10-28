package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.Fractions
import java.util.Locale
import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.solvers.BrentSolver

/** A text in French and in English, the phone's language choosing (the others at translation time). */
data class Words(val fr: String, val en: String) {
    fun get(locale: Locale = Locale.getDefault()) = if (locale.language == "fr") fr else en
}

enum class Subject(val title: Words) {
    MATHS(Words("Maths", "Maths")),
    ANALYSIS(Words("Analyse", "Calculus")),
    STATS(Words("Probabilités et statistiques", "Probability and statistics")),
    GEOMETRY(Words("Vecteurs et géométrie", "Vectors and geometry")),
    EXPERT(Words("Maths expertes", "Advanced maths")),
    PHYSICS(Words("Physique", "Physics")),
    CHEMISTRY(Words("Chimie", "Chemistry"))
}

/** What a field holds: a number, a function of x, numbers separated by the "," key, or words typed on the phone's keyboard. */
enum class FieldKind { NUMBER, FUNCTION, LIST, TEXT }

/**
 * A value the program asks for: its symbol, its name, its unit; [whole] for
 * integers; [optional] may stay empty; [slider], a range the value can be
 * dragged in under the figure (GeoGebra's sliders), by [step].
 */
data class Field(
    val symbol: String,
    val name: Words,
    val unit: String = "",
    val whole: Boolean = false,
    val kind: FieldKind = FieldKind.NUMBER,
    val optional: Boolean = false,
    val slider: ClosedFloatingPointRange<Double>? = null,
    val step: Double = 0.0
)

/** A line of what a program gives back: a result to read, or a step of the working. */
data class Line(val text: String, val result: Boolean = false)

/** What a run gives: the lines, the values it found for the fields left empty, a scene in space or a figure of the plane to look at. */
data class Outcome(val lines: List<Line>, val found: Map<String, Double> = emptyMap(), val scene: Scene? = null, val figure: Figure? = null)

/** A program of the PRGM menu: fields to fill, then [run] works out the rest. */
interface Program {
    val id: String
    val subject: Subject
    val title: Words
    /** The relation or the method, shown above the fields. */
    val formula: String
    val fields: List<Field>
    /** Programs of one family (the laws of probability) switch into each other, keeping the values of fields they share. */
    val family: String? get() = null
    /** The family's short name for this one, on its chip. */
    val short: Words? get() = null

    /** [values] by symbol, null where empty; an Outcome, or a message saying what is missing. */
    fun run(values: Map<String, Double?>): Outcome

    /** With the text of the function and list fields as typed, and the angle unit. */
    fun run(values: Map<String, Double?>, texts: Map<String, String>, degrees: Boolean): Outcome = run(values)
}

/**
 * A formula program, as ticalc.org's "Physics Solver": every field but one
 * filled, the empty one is found, whichever it is. [residual] is the
 * formula written as left − right, zero when the values agree; [positive]
 * when every quantity is above zero (masses, distances), which guides the
 * search.
 */
class Solver(
    override val id: String,
    override val subject: Subject,
    override val title: Words,
    override val formula: String,
    override val fields: List<Field>,
    private val positive: Boolean = true,
    private val residual: (Map<String, Double>) -> Double
) : Program {

    override fun run(values: Map<String, Double?>): Outcome {
        Fractions.forget()
        val empty = fields.filter { values[it.symbol] == null }
        if (empty.size != 1) return Outcome(listOf(Line(MISSING_ONE.get())))
        val unknown = empty.single()
        val known = values.filterValues { it != null }.mapValues { it.value!! }
        val f = UnivariateFunction { x -> residual(known + (unknown.symbol to x)) }
        val root = Solve.root(f, positive) ?: return Outcome(listOf(Line(NO_SOLUTION.get())))
        return Outcome(
            listOf(Line(Fractions.annotate("${unknown.symbol} = ${Solve.show(root)} ${unknown.unit}".trim()), result = true)),
            mapOf(unknown.symbol to root)
        )
    }

    companion object {
        val MISSING_ONE = Words("Remplissez toutes les valeurs sauf une : elle sera calculée.", "Fill every value but one: it will be worked out.")
        val NO_SOLUTION = Words("Pas de solution avec ces valeurs.", "No solution with these values.")
    }
}

/** Finding where a function is zero, wherever it may be. */
object Solve {

    /**
     * A root of [f]: a sign change searched across the powers of ten
     * (10⁻³⁰ to 10³⁰, both signs unless [positive]), then Brent's method.
     */
    fun root(f: UnivariateFunction, positive: Boolean): Double? {
        val points = ArrayList<Double>()
        for (k in -300..300) points += Math.pow(10.0, k / 10.0)
        val candidates = if (positive) points else points.map { -it }.reversed() + listOf(0.0) + points
        var prev = candidates.first()
        var fp = safe(f, prev)
        for (x in candidates.drop(1)) {
            val fx = safe(f, x)
            if (fx == 0.0) return x
            if (fp.isFinite() && fx.isFinite() && fp * fx < 0) {
                val r = runCatching { BrentSolver(1e-14, 1e-15).solve(10_000, f, prev, x) }.getOrNull()
                // A sign change across a pole is no root.
                if (r != null && Math.abs(safe(f, r)) <= 1e-6 * (Math.abs(fp) + Math.abs(fx))) return r
            }
            prev = x
            fp = fx
        }
        return null
    }

    private fun safe(f: UnivariateFunction, x: Double) = runCatching { f.value(x) }.getOrDefault(Double.NaN)

    /** A number as a student writes it: 10 significant digits, a power of ten when far from 1. */
    fun show(v: Double): String {
        // No dash for an undefined value: the words errorText (Display.kt) shows; the way of building a line from parts is AOSP Dialer's CallLogEntryText.java (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
        if (v.isNaN()) return "Not a number"
        if (v.isInfinite()) return "Too large"
        val a = Math.abs(v)
        val text = if (a != 0.0 && (a >= 1e9 || a < 1e-4)) {
            String.format(Locale.ROOT, "%.6e", v).let { s ->
                val (m, e) = s.split('e')
                m.trimEnd('0').trimEnd('.') + " × 10" + e.toInt().toString().map { c -> if (c == '-') '⁻' else "⁰¹²³⁴⁵⁶⁷⁸⁹"[c - '0'] }.joinToString("")
            }
        } else {
            java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
        }
        return text.replace('-', '−').also { Fractions.note(it, v) }
    }
}
