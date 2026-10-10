package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.Calc
import com.yaz.calculator.engine.Curve
import com.yaz.calculator.engine.Sym

/**
 * What a program was given: numbers by symbol, functions of x and lists of
 * numbers read from their text with Calc's meaning, in the angle unit.
 */
class Inputs(private val values: Map<String, Double?>, private val texts: Map<String, String>, val degrees: Boolean) {
    operator fun get(symbol: String): Double = values[symbol] ?: throw IllegalArgumentException(symbol)
    fun has(symbol: String): Boolean = values[symbol] != null || !texts[symbol].isNullOrBlank()
    fun int(symbol: String): Int = Math.round(get(symbol)).toInt()
    fun long(symbol: String): Long = Math.round(get(symbol))

    /** The function typed in [symbol], as a function of x. */
    fun f(symbol: String): (Double) -> Double {
        val text = texts[symbol].orEmpty()
        val curve = Curve.of(text, degrees)
        // Outside its domain (ln at 0) a value is NaN, never an exception.
        if (curve != null) return { x -> runCatching { curve(x) }.getOrDefault(Double.NaN) }
        // A constant, g(x) = 0: as typed, without x.
        val c = runCatching { Calc().evaluate(text, degrees).crValue().toDouble() }.getOrNull() ?: throw IllegalArgumentException(symbol)
        return { c }
    }

    /** The numbers typed in [symbol], separated by the "," key. */
    fun list(symbol: String): List<Double> = texts[symbol].orEmpty().split(Sym.ARG).map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
        Calc().evaluate(part, degrees).crValue().toDouble()
    }

    /** The field as an exact expression (fractions, √, π, ln…) for the symbolic engine. */
    fun exact(symbol: String): com.yaz.calculator.engine.cas.Ex = com.yaz.calculator.engine.cas.parse(texts[symbol].orEmpty())

    /** Three numbers: a point or a vector of space. */
    fun vec(symbol: String): Vec3 = list(symbol).let { require(it.size == 3); Vec3(it[0], it[1], it[2]) }
}

/**
 * A program that works out its answer from every field filled, numbers,
 * functions and lists alike; [work] gives its lines and, may be, a scene.
 */
class Rich(
    override val id: String,
    override val subject: Subject,
    override val title: Words,
    override val formula: String,
    override val fields: List<Field>,
    private val work: (Inputs) -> Outcome
) : Program {
    override fun run(values: Map<String, Double?>): Outcome = run(values, emptyMap(), true)

    override fun run(values: Map<String, Double?>, texts: Map<String, String>, degrees: Boolean): Outcome {
        for (fd in fields) {
            if (fd.optional) continue
            val filled = if (fd.kind == FieldKind.NUMBER) values[fd.symbol] != null else !texts[fd.symbol].isNullOrBlank()
            if (!filled) return Outcome(listOf(Line(ALL.get())))
        }
        for (fd in fields) {
            val v = values[fd.symbol] ?: continue
            if (fd.kind == FieldKind.NUMBER && fd.whole && v != Math.rint(v)) return Outcome(listOf(Line(WHOLE.get().format(fd.symbol))))
        }
        val out = runCatching { work(Inputs(values, texts, degrees)) }.getOrElse { Outcome(listOf(Line(IMPOSSIBLE.get()))) }
        // A function with sin, cos or tan: the angle unit it was read in, said once.
        val trig = fields.any { it.kind == FieldKind.FUNCTION && texts[it.symbol].orEmpty().let { t -> "sin" in t || "cos" in t || "tan" in t } }
        return if (trig && out.lines.none { "DEG" in it.text || "RAD" in it.text }) out.copy(lines = out.lines + Line((if (degrees) DEGREES else RADIANS).get())) else out
    }

    companion object {
        val ALL = Words("Remplissez toutes les valeurs.", "Fill every value.")
        val WHOLE = Words("%s doit être un entier.", "%s must be a whole number.")
        val IMPOSSIBLE = Words("Pas de résultat avec ces valeurs.", "No result with these values.")
        val DEGREES = Words("x en degrés (DEG)", "x in degrees (DEG)")
        val RADIANS = Words("x en radians (RAD)", "x in radians (RAD)")
    }
}

internal fun title(fr: String, en: String) = Words(fr, en)
internal fun say(fr: String, en: String) = Words(fr, en).get()
internal fun num(symbol: String, fr: String, en: String, unit: String = "", whole: Boolean = false, optional: Boolean = false) =
    Field(symbol, Words(fr, en), unit, whole, FieldKind.NUMBER, optional)
internal fun fn(symbol: String, fr: String, en: String) = Field(symbol, Words(fr, en), kind = FieldKind.FUNCTION)
internal fun list(symbol: String, fr: String, en: String, optional: Boolean = false) = Field(symbol, Words(fr, en), kind = FieldKind.LIST, optional = optional)
internal fun show(v: Double) = Solve.show(v)
internal fun res(text: String) = Line(text, result = true)
internal fun stp(text: String) = Line(text)
internal fun lines(vararg l: Line) = Outcome(l.toList())
