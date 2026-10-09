package com.yaz.calculator.engine

/** A line's value in a sheet, and whether it is a total of the lines above it. */
data class LineValue(val value: UnifiedReal, val total: Boolean)

/**
 * A sheet: lines of words and numbers, each worked out on its own, as in a
 * notebook. "rent = 800" names a value for the lines below; "Food: 320.50"
 * keeps its label; words around a number are left out; "total" adds up the
 * lines since the last empty line or # heading; "ans" is the line above.
 */
class SheetEval(private val style: NumberStyle) {

    /** One value per line of [text], null where a line has none. Runs on the caller's thread. */
    fun evaluate(text: String, degrees: Boolean): List<LineValue?> {
        val variables = HashMap<String, UnifiedReal>()
        val calc = Calc(variable = { name ->
            variables[name.lowercase()] ?: when (name.lowercase()) {
                "pi" -> UnifiedReal.PI
                else -> null
            }
        })
        val out = ArrayList<LineValue?>()
        var group = ArrayList<UnifiedReal>()
        var previous: UnifiedReal? = null
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
            val value: LineValue? = when {
                word in TOTAL -> LineValue(group.fold(UnifiedReal.ZERO) { a, b -> a.add(b) }, total = true)
                word in PREVIOUS -> previous?.let { LineValue(it, total = false) }
                else -> {
                    variables["ans"] = previous ?: UnifiedReal.ZERO
                    worth(calc, body, degrees)?.let { LineValue(it, total = false) }
                }
            }
            if (value != null) {
                name?.let { variables[it.lowercase()] = value.value }
                if (!value.total) group += value.value
                previous = value.value
            }
            out += value
        }
        return out
    }

    /** The value of [body], first as written, then without the words around its numbers. */
    private fun worth(calc: Calc, body: String, degrees: Boolean): UnifiedReal? {
        val math = normalise(body)
        try {
            return calc.evaluate(math, degrees)
        } catch (e: CalcException) {
            if (e.error != CalcError.SYNTAX) return null
        } catch (_: ArithmeticException) {
            return null
        }
        // "Bread 2.40": the words go, unless two numbers are then left side by side.
        val bare = WORD.replace(math) { m -> if (FUNCTION_WORDS.contains(m.value.lowercase())) m.value else " " }.trim()
        if (bare.none { it.isDigit() } || SIDE_BY_SIDE.containsMatchIn(bare)) return null
        return try {
            calc.evaluate(bare, degrees)
        } catch (_: CalcException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** What a person types, in the app's form: 3 x 4, 2*3, 7/2, € and $ signs, sqrt, asin. */
    internal fun normalise(text: String): String {
        var t = style.read(text)
        t = CURRENCY.replace(t, "")
        t = TIMES_X.replace(t, Sym.TIMES.toString())
        for ((from, to) in NAMES) t = t.replace(Regex("\\b$from\\s*\\(", RegexOption.IGNORE_CASE), to)
        return t
    }

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
