package com.yaz.calculator.engine

/** A line's value in a sheet, what it counts (a unit, a currency) and whether it is a total of the lines above it. */
data class LineValue(val value: UnifiedReal, val total: Boolean, val measure: Measure? = null)

/**
 * A sheet: lines of words and numbers, each worked out on its own, as in a
 * notebook. "rent = 800" names a value for the lines below; "Food: 320.50"
 * keeps its label; words around a number are left out; "total" adds up the
 * lines since the last empty line or # heading; "ans" is the line above.
 * Units and currencies go along (UnitCalc): "12 km + 300 m in mi", "rent × 12".
 */
class SheetEval(private val style: NumberStyle, rates: Map<String, String>? = null) {

    private val units = UnitCalc(rates)

    /** Set by [evaluate] when a line converts currencies and no rates are kept yet. */
    var needsRates = false
        private set

    /** One value per line of [text], null where a line has none. Runs on the caller's thread. */
    fun evaluate(text: String, degrees: Boolean): List<LineValue?> {
        val variables = HashMap<String, UnifiedReal>()
        val calc = Calc(variable = { name ->
            variables[name.lowercase()] ?: when (name.lowercase()) {
                "pi" -> UnifiedReal.PI
                else -> null
            }
        })
        val measured = HashMap<String, Quantity>()
        val out = ArrayList<LineValue?>()
        var group = ArrayList<Quantity>()
        var previous: Quantity? = null
        needsRates = false
        for (raw in text.split('\n')) {
            if (Thread.interrupted()) throw com.hp.creals.CR.AbortedException()
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("#")) {
                group = ArrayList()
                out += null
                continue
            }
            var body = line.removeSuffix("=").trim()
            var name: String? = null
            ASSIGNMENT.matchEntire(body)?.let { m ->
                name = m.groupValues[1]
                body = m.groupValues[2].trim()
            } ?: LABEL.matchEntire(body)?.let { m ->
                val label = m.groupValues[1].trim()
                body = if (label.lowercase() in TOTAL && m.groupValues[2].isBlank()) label else m.groupValues[2].trim()
            }
            val word = body.lowercase()
            val value: Quantity? = when {
                word in TOTAL -> settle(units.sum(group))
                word in PREVIOUS -> previous
                else -> {
                    variables.remove("ans")
                    measured.remove("ans")
                    previous?.let { p -> if (p.measure != null) measured["ans"] = p else variables["ans"] = p.value }
                    if (previous == null) variables["ans"] = UnifiedReal.ZERO
                    worth(calc, body, degrees) { measured[it.lowercase()] }
                }
            }
            val total = word in TOTAL
            if (value != null) {
                name?.let {
                    val key = it.lowercase()
                    if (value.measure != null) { measured[key] = value; variables.remove(key) } else { variables[key] = value.value; measured.remove(key) }
                }
                if (!total) group += value
                previous = value
            }
            out += value?.let { LineValue(it.value, total, it.measure) }
        }
        return out
    }

    private fun settle(result: UnitCalc.Result): Quantity? = when (result) {
        is UnitCalc.Result.Value -> result.quantity
        UnitCalc.Result.NeedsRates -> { needsRates = true; null }
        else -> null
    }

    /** The value of [body]: with its units and currencies, else as written, else without the words around its numbers. */
    private fun worth(calc: Calc, body: String, degrees: Boolean, named: (String) -> Quantity?): Quantity? {
        val read = prepare(body)
        val withUnits = try {
            units.evaluate(read, { expression, holders -> evaluateLoosely(calc, expression, degrees, holders) }, named)
        } catch (_: CalcException) {
            UnitCalc.Result.Invalid
        } catch (_: ArithmeticException) {
            UnitCalc.Result.Invalid
        }
        if (withUnits !is UnitCalc.Result.Plain) return settle(withUnits)
        return try {
            Quantity(evaluateLoosely(calc, CURRENCY.replace(read, ""), degrees) { null }, null)
        } catch (_: CalcException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** [math] as written, then, if it does not read, without the words around its numbers ("Bread 2.40"). */
    private fun evaluateLoosely(calc: Calc, math: String, degrees: Boolean, holders: (String) -> UnifiedReal?): UnifiedReal {
        val withHolders = Calc(variable = { name -> holders(name) ?: calc.variableOf(name) })
        try {
            return withHolders.evaluate(math, degrees)
        } catch (e: CalcException) {
            if (e.error != CalcError.SYNTAX) throw e
        }
        val bare = WORD.replace(math) { m ->
            val w = m.value
            if (FUNCTION_WORDS.contains(w.lowercase()) || w.startsWith(UnitCalc.HOLDER) || calc.variableOf(w) != null) w else " "
        }.trim()
        if (bare.none { it.isDigit() } && !bare.contains(UnitCalc.HOLDER) || SIDE_BY_SIDE.containsMatchIn(bare)) throw CalcException(CalcError.SYNTAX)
        return withHolders.evaluate(bare, degrees)
    }

    /** What a person types, in the app's form: 3 x 4, 2*3, 7/2, sqrt, asin; currency signs kept for UnitCalc. */
    private fun prepare(text: String): String {
        var t = style.read(text)
        t = TIMES_X.replace(t, Sym.TIMES.toString())
        for ((from, to) in NAMES) t = t.replace(Regex("\\b$from\\s*\\(", RegexOption.IGNORE_CASE), to)
        return t
    }

    /** What a person types, in the app's form, currency signs left out. */
    internal fun normalise(text: String): String = CURRENCY.replace(prepare(text), "")

    companion object {
        private val ASSIGNMENT = Regex("^([\\p{L}_][\\p{L}\\p{N}_]*)\\s*=\\s*(.+)$")
        private val LABEL = Regex("^([^:=]*\\p{L}[^:=]*):\\s*(.*)$")
        private val WORD = Regex("[\\p{L}_][\\p{L}\\p{N}_]*")
        private val SIDE_BY_SIDE = Regex("[\\d.)]\\s+[\\d.(]")
        private val CURRENCY = Regex("\\p{Sc}")
        private val TIMES_X = Regex("(?<=[\\d)])\\s*[xX]\\s*(?=[\\d(.])")
        private val NAMES = listOf(
            "arcsin" to "sin⁻¹(", "arccos" to "cos⁻¹(", "arctan" to "tan⁻¹(",
            "asin" to "sin⁻¹(", "acos" to "cos⁻¹(", "atan" to "tan⁻¹(",
            "sqrt" to "√(", "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
            "ln" to "ln(", "log" to "log(", "exp" to "exp("
        )
        private val FUNCTION_WORDS = setOf("sin", "cos", "tan", "ln", "log", "exp", "e", "pi", "ans")

        /** "Total" as the major languages write it. */
        val TOTAL = setOf(
            "total", "sum", "subtotal", "somme", "sous-total", "suma", "summe", "gesamt",
            "totale", "somma", "soma", "totaal", "som", "toplam", "итого", "сумма", "المجموع", "合计", "合計"
        )
        private val PREVIOUS = setOf("ans", "prev")
    }
}
