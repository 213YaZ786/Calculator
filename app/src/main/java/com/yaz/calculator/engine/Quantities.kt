package com.yaz.calculator.engine

import java.util.Currency
import java.util.Locale

/** What a value is counted in: a unit of a category, or a currency. */
sealed interface Measure

data class Physical(val category: Category, val unit: MeasureUnit) : Measure

data class Money(val code: String) : Measure

/** A value and what it counts; a plain number has no [measure]. */
class Quantity(val value: UnifiedReal, val measure: Measure?)

/**
 * Units and currencies inside a line, as Soulver and Numi read them:
 * "12 km + 300 m in mi", "45 € in DZD", "$30 + 5 CAD". "in", "to", "as"
 * (and "en", "em") convert; a plain number among measured ones takes the
 * nearest one's unit; mixed units of a kind give the largest unit, mixed
 * currencies the last one. Temperatures and fuel use convert one value at
 * a time. A product of two measured values (km × km) has no result.
 * [rates] are units of each currency for one euro (Rates), null when none
 * are kept yet.
 */
class UnitCalc(private val rates: Map<String, String>?, private val locale: Locale = Locale.getDefault()) {

    sealed interface Result {
        /** No unit nor currency in the line: the plain calculation applies. */
        data object Plain : Result
        class Value(val quantity: Quantity) : Result
        /** Units that do not go together (km + kg, km × km). */
        data object Invalid : Result
        /** Currencies to convert, and no rates kept yet. */
        data object NeedsRates : Result
    }

    /** The value of [text], already in the app's form (NumberStyle.read); [named] gives a sheet's measured names. */
    fun evaluate(text: String, calc: (String, (String) -> UnifiedReal?) -> UnifiedReal, named: (String) -> Quantity? = { null }): Result {
        val (left, target) = splitTarget(text)
        val pieces = scan(left, named)
        if (pieces.none { it.measure != null }) return if (target != null) Result.Invalid else Result.Plain
        val measured = pieces.mapNotNull { it.measure }
        val money = measured.any { it is Money }
        if (money && measured.any { it !is Money }) return Result.Invalid
        if (!money && measured.map { (it as Physical).category }.distinct().size > 1) return Result.Invalid
        if (target != null && (target is Money) != money) return Result.Invalid
        if (target is Physical && target.category != (measured.first() as Physical).category) return Result.Invalid

        // Temperatures and fuel use are not proportional: one value, converted.
        val category = (measured.first() as? Physical)?.category
        if (category == Category.TEMPERATURE || category == Category.FUEL) {
            val only = pieces.singleOrNull()?.takeIf { it.measure != null } ?: return Result.Invalid
            val rest = left.removeRange(only.start, only.end).trim()
            if (rest.isNotEmpty() && rest != Sym.MINUS.toString()) return Result.Invalid
            val from = (only.measure as Physical).unit
            val amount = if (rest.isEmpty()) only.amount else only.amount.negate()
            val to = (target as? Physical)?.unit ?: from
            return Result.Value(Quantity(to.fromBase(from.toBase(amount)), Physical(category, to)))
        }

        // Plain numbers added to measured ones take the nearest one's measure (Soulver's assimilation).
        val assigned = pieces.map { p ->
            if (p.measure != null || !additive(left, p)) p
            else p.copy(measure = pieces.filter { it.measure != null }.minBy { distance(it, p) }.measure)
        }
        val unit: Measure = target ?: if (money) assigned.last { it.measure != null }.measure!! else
            assigned.mapNotNull { it.measure as? Physical }.maxBy { it.unit.toBase(UnifiedReal.ONE).crValue().toDouble() }

        val codes = assigned.mapNotNull { (it.measure as? Money)?.code } + listOfNotNull((unit as? Money)?.code)
        val sameMoney = money && codes.distinct().size == 1
        if (money && !sameMoney && codes.any { rate(it) == null }) return Result.NeedsRates

        fun base(p: Piece): UnifiedReal = when (val m = p.measure) {
            null -> p.amount
            is Physical -> m.unit.toBase(p.amount)
            is Money -> if (sameMoney) p.amount else p.amount.divide(rate(m.code)!!)
        }
        val expression = StringBuilder(left)
        for ((i, p) in assigned.withIndex().reversed()) expression.replace(p.start, p.end, " $HOLDER$i ")
        val values = assigned.map(::base)
        fun run(scale: UnifiedReal): UnifiedReal = calc(expression.toString()) { name ->
            name.removePrefix(HOLDER).takeIf { name.startsWith(HOLDER) }?.toIntOrNull()?.let { i ->
                val v = values.getOrNull(i) ?: return@let null
                if (assigned[i].measure != null) v.multiply(scale) else v
            }
        }
        val once = run(UnifiedReal.ONE)
        // Proportional to the measured values, or the line mixes them in a way no unit describes.
        val twice = run(UnifiedReal.TWO).crValue().toDouble()
        val expected = once.crValue().toDouble() * 2
        if (Math.abs(twice - expected) > 1e-9 * maxOf(1.0, Math.abs(expected))) return Result.Invalid

        val value = when (unit) {
            is Physical -> unit.unit.fromBase(once)
            is Money -> if (sameMoney) once else once.multiply(rate(unit.code)!!)
        }
        return Result.Value(Quantity(value, unit))
    }

    /** The sum of [items], a sheet's total: in the largest unit, or the last currency; plain ones take it. */
    fun sum(items: List<Quantity>): Result {
        val measured = items.mapNotNull { it.measure }
        if (measured.isEmpty()) return Result.Value(Quantity(items.fold(UnifiedReal.ZERO) { a, b -> a.add(b.value) }, null))
        val money = measured.any { it is Money }
        if (money && measured.any { it !is Money }) return Result.Invalid
        val categories = measured.mapNotNull { (it as? Physical)?.category }.distinct()
        if (categories.size > 1 || categories.firstOrNull() == Category.TEMPERATURE || categories.firstOrNull() == Category.FUEL) return Result.Invalid
        val unit: Measure = if (money) measured.last() else measured.maxBy { (it as Physical).unit.toBase(UnifiedReal.ONE).crValue().toDouble() }
        val codes = measured.mapNotNull { (it as? Money)?.code }
        val sameMoney = money && codes.distinct().size == 1
        if (money && !sameMoney && codes.any { rate(it) == null }) return Result.NeedsRates
        var total = UnifiedReal.ZERO
        for (q in items) {
            total = total.add(
                when (val m = q.measure ?: unit) {
                    is Physical -> m.unit.toBase(q.value)
                    is Money -> if (sameMoney) q.value else q.value.divide(rate(m.code)!!)
                }
            )
        }
        val value = when (unit) {
            is Physical -> unit.unit.fromBase(total)
            is Money -> if (sameMoney) total else total.multiply(rate(unit.code)!!)
        }
        return Result.Value(Quantity(value, unit))
    }

    private fun rate(code: String): UnifiedReal? =
        if (code == "EUR") UnifiedReal.ONE else rates?.get(code)?.let { runCatching { Units.exact(it) }.getOrNull() }

    /** A value in the line: a number with its measure, or a sheet's measured name. */
    private data class Piece(val start: Int, val end: Int, val amount: UnifiedReal, val measure: Measure?)

    private fun distance(a: Piece, b: Piece) = if (a.end <= b.start) b.start - a.end else a.start - b.end

    /** Whether [p] stands alone between additions: 300 in 300 + 20 km, not 2 in 2 × 3 km. */
    private fun additive(text: String, p: Piece): Boolean {
        val before = text.substring(0, p.start).trimEnd().lastOrNull()
        val after = text.substring(p.end).trimStart().firstOrNull()
        return (before == null || before in ADDITIVE_BEFORE) && (after == null || after in ADDITIVE_AFTER)
    }

    /** The line and the measure it is asked in: the rightmost "in", "to", "as" followed by a unit alone. */
    private fun splitTarget(text: String): Pair<String, Measure?> {
        for (m in TARGET.findAll(text).toList().reversed()) {
            val unit = measureNamed(text.substring(m.range.last + 1).trim()) ?: continue
            val left = text.substring(0, m.range.first)
            if (left.isNotBlank()) return left to unit
        }
        return text to null
    }

    private fun scan(text: String, named: (String) -> Quantity?): List<Piece> {
        val out = ArrayList<Piece>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isDigit() || c == Sym.POINT && i + 1 < text.length && text[i + 1].isDigit()) {
                if (i > 0 && (text[i - 1].isLetter() || text[i - 1] == '_')) { i = skipWord(text, i); continue }
                var end = i
                while (end < text.length && (text[end].isDigit() || text[end] == Sym.POINT)) end++
                if (end < text.length && text[end] == Sym.EXP) {
                    var j = end + 1
                    if (j < text.length && (text[j] == Sym.MINUS || text[j] == Sym.PLUS)) j++
                    if (j < text.length && text[j].isDigit()) { while (j < text.length && text[j].isDigit()) j++; end = j }
                }
                val amount = runCatching { Calc.literal(text.substring(i, end)) }.getOrNull()
                if (amount == null) { i = end; continue }
                var start = i
                var measure: Measure? = null
                // A currency sign or code before the number: €45, US$ 30, USD 30.
                prefixMoney(text, i)?.let { (at, code) -> start = at; measure = Money(code) }
                var stop = end
                if (measure == null) unitAfter(text, end)?.let { (at, m) -> stop = at; measure = m }
                out += Piece(start, stop, amount, measure)
                i = stop
            } else if (c.isLetter() || c == '_') {
                val end = skipWord(text, i)
                val word = text.substring(i, end)
                val q = named(word)
                if (q != null && !wordIsUnitOfNumber(text, i)) {
                    var stop = end
                    var measure = q.measure
                    if (measure == null) unitAfter(text, end)?.let { (at, m) -> stop = at; measure = m }
                    out += Piece(i, stop, q.value, measure)
                    i = stop
                } else {
                    i = end
                }
            } else {
                i++
            }
        }
        return out
    }

    private fun skipWord(text: String, from: Int): Int {
        var j = from
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
        return j
    }

    /** A word just after a number is its unit, not a name. */
    private fun wordIsUnitOfNumber(text: String, at: Int): Boolean {
        val before = text.substring(0, at).trimEnd()
        return before.isNotEmpty() && (before.last().isDigit()) && unitAfter(text, before.length) != null
    }

    /** The measure right after a number, and where it ends; "in" as inch only when nothing follows it. */
    private fun unitAfter(text: String, from: Int): Pair<Int, Measure>? {
        var p = from
        while (p < text.length && text[p] == ' ') p++
        if (p >= text.length) return null
        for (len in minOf(MAX_NAME, text.length - p) downTo 1) {
            val end = p + len
            if (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) continue
            if (len > 1 && text[end - 1] == ' ') continue
            val name = text.substring(p, end)
            val m = measureNamed(name) ?: continue
            if (name.equals("in", ignoreCase = true) && text.substring(end).trimStart().firstOrNull()?.isLetter() == true) return null
            return end to m
        }
        return null
    }

    private fun prefixMoney(text: String, at: Int): Pair<Int, String>? {
        val before = text.substring(0, at)
        val trimmed = before.trimEnd()
        for ((sign, code) in moneySigns) {
            if (trimmed.endsWith(sign)) {
                val start = trimmed.length - sign.length
                if (sign.first().isLetter() && start > 0 && trimmed[start - 1].isLetterOrDigit()) continue
                return start to code
            }
        }
        val word = CODE_BEFORE.find(trimmed)?.groupValues?.get(1)
        if (word != null && word in isoCodes) return (trimmed.length - 3) to word
        return null
    }

    /** A unit or currency named alone: a symbol (km, °C, €, USD) as written, a name (miles, litres) in any case. */
    fun measureNamed(raw: String): Measure? {
        val name = raw.trim().replace(Sym.DIVIDE, '/').replace(Sym.MINUS, '-')
        if (name.isEmpty()) return null
        bySymbol[name]?.let { return it }
        moneySigns.firstOrNull { it.first == name }?.let { return Money(it.second) }
        if (name.length == 3 && name in isoCodes) return Money(name)
        byName[name.lowercase(locale)]?.let { return it }
        return null
    }

    private val moneySigns: List<Pair<String, String>> by lazy {
        val local = runCatching { Currency.getInstance(locale) }.getOrNull()
        val dollar = if (local != null && local.getSymbol(locale) == "$") local.currencyCode else "USD"
        val yen = if (locale.country == "CN") "CNY" else "JPY"
        listOf(
            "US$" to "USD", "C$" to "CAD", "A$" to "AUD", "NZ$" to "NZD", "HK$" to "HKD", "R$" to "BRL",
            "$" to dollar, "€" to "EUR", "£" to "GBP", "¥" to yen, "₹" to "INR", "₽" to "RUB", "₩" to "KRW",
            "₺" to "TRY", "₪" to "ILS", "₫" to "VND", "฿" to "THB", "₴" to "UAH", "₦" to "NGN", "₱" to "PHP",
            "zł" to "PLN", "Kč" to "CZK", "DA" to "DZD", "د.ج" to "DZD", "DH" to "MAD", "د.م." to "MAD"
        ).sortedByDescending { it.first.length }
    }

    private val isoCodes: Set<String> by lazy { Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() }

    private val bySymbol: Map<String, Measure> by lazy {
        val map = HashMap<String, Measure>()
        for ((category, units) in Units.all) for (u in units) {
            map.putIfAbsent(u.symbol, Physical(category, u))
        }
        for ((alias, id) in SYMBOL_ALIASES) unit(id)?.let { map[alias] = it }
        // Ambiguous or too short to read safely after a number.
        map.remove("a"); map.remove("′"); map.remove("″")
        map
    }

    private val byName: Map<String, Measure> by lazy {
        val map = HashMap<String, Measure>()
        for ((id, names) in NAMES) {
            val m = (if (id.startsWith("$")) Money(id.drop(1)) else unit(id)) ?: continue
            for (n in names.split(' ')) map[n.replace('+', ' ')] = m
        }
        map
    }

    private fun unit(id: String): Physical? {
        for ((category, units) in Units.all) units.firstOrNull { it.id == id }?.let { return Physical(category, it) }
        return null
    }

    companion object {
        /** Names a holder of a value takes in the expression handed to Calc: a letter no one types. */
        internal const val HOLDER = "ⵣq"
        private const val MAX_NAME = 18
        private val ADDITIVE_BEFORE = setOf('(', Sym.PLUS, Sym.MINUS, '+', '-')
        private val ADDITIVE_AFTER = setOf(')', Sym.PLUS, Sym.MINUS, '+', '-', Sym.PERCENT)
        private val TARGET = Regex("\\s+(?:in|into|to|as|en|em)(?=\\s)|\\s*(?:→|->|${Sym.MINUS}>)\\s*", RegexOption.IGNORE_CASE)
        private val CODE_BEFORE = Regex("(?:^|[^\\p{L}])(\\p{Lu}{3})$")

        /** Other ways the symbols are written: m2 for m², l for L, kmh for km/h. */
        private val SYMBOL_ALIASES = listOf(
            "m2" to "m2", "km2" to "km2", "cm2" to "cm2", "mm2" to "mm2", "ft2" to "ft2", "in2" to "in2", "mi2" to "mi2", "yd2" to "yd2",
            "m3" to "m3", "cm3" to "cm3", "ft3" to "ft3", "in3" to "in3", "cc" to "cm3",
            "l" to "l", "ml" to "ml", "cl" to "cl", "dl" to "dl", "ltr" to "l",
            "kmh" to "kmh", "kph" to "kmh", "km/hr" to "kmh",
            "Ko" to "kB", "Mo" to "MB", "Go" to "GB", "To" to "TB", "KB" to "kB",
            "°" to "deg", "deg" to "deg", "C°" to "c", "F°" to "f",
            "KJ" to "kj", "KW" to "kw", "kwh" to "kwh", "KWh" to "kwh",
            "lbs" to "lb", "sec" to "s", "hr" to "h", "hrs" to "h", "mins" to "min",
            "l/100km" to "l100", "L/100km" to "l100"
        )

        /**
         * Units and currencies by name in the major languages (EN, FR, ES, DE, IT, PT, NL), lower case,
         * space separated; "+" stands for a space inside a name; "$" before a code marks a currency.
         */
        private val NAMES = listOf(
            "m" to "metre metres meter meters mètre mètres metro metros",
            "km" to "kilometre kilometres kilometer kilometers kilomètre kilomètres kilómetro kilómetros chilometro chilometri quilômetro quilômetros quilómetro quilómetros kms",
            "cm" to "centimetre centimetres centimeter centimeters centimètre centimètres centímetro centímetros centimetro centimetri",
            "mm" to "millimetre millimetres millimeter millimeters millimètre millimètres milímetro milímetros millimetro millimetri",
            "mi" to "mile miles milla millas meile meilen miglio miglia milha milhas mijl mijlen",
            "ft" to "foot feet pied pieds pie pies fuß fuss piede piedi pé pés voet voeten",
            "in" to "inch inches pouce pouces pulgada pulgadas zoll pollice pollici polegada polegadas duim duimen",
            "yd" to "yard yards yarda yardas iarda iarde jarda jardas",
            "nmi" to "nautical+mile nautical+miles",
            "ha" to "hectare hectares hectárea hectáreas hektar ettaro ettari",
            "ac" to "acre acres",
            "kg" to "kilogram kilograms kilogramme kilogrammes kilogramo kilogramos kilogramm chilogrammo chilogrammi quilograma quilogramas kilo kilos",
            "g" to "gram grams gramme grammes gramo gramos gramm grammo grammi grama gramas",
            "mg" to "milligram milligrams milligramme milligrammes miligramo miligramos milligramm milligrammo milligrammi miligrama miligramas",
            "t" to "tonne tonnes tonelada toneladas tonnellata tonnellate",
            "lb" to "pound pounds livre livres libra libras pfund libbra libbre pond ponden",
            "oz" to "ounce ounces once onces onza onzas unze unzen oncia onça onças ons",
            "st" to "stone stones",
            "l" to "litre litres liter liters litro litros litri",
            "ml" to "millilitre millilitres milliliter milliliters mililitro mililitros millilitro millilitri",
            "cl" to "centilitre centilitres centiliter centiliters centilitro centilitros centilitri",
            "gal" to "gallon gallons galón galones gallone galão galões",
            "cup" to "cups",
            "tsp" to "teaspoon teaspoons",
            "tbsp" to "tablespoon tablespoons",
            "pt" to "pint pints",
            "qt" to "quart quarts",
            "s" to "second seconds secs seconde secondes segundo segundos sekunde sekunden secondo secondi",
            "min" to "minute minutes minuto minutos minuten minuti minuut",
            "h" to "hour hours heure heures hora horas stunde stunden ora ore uur uren",
            "d" to "day days jour jours día días tag tage giorno giorni dia dias dag dagen",
            "wk" to "week weeks semaine semaines semana semanas woche wochen settimana settimane weken",
            "mo" to "month months mois mes meses monat monate mese mesi mês maand maanden",
            "yr" to "year years an ans année années año años jahr jahre anno anni ano anos jaar jaren",
            "ms" to "millisecond milliseconds",
            "c" to "celsius",
            "f" to "fahrenheit",
            "k" to "kelvin",
            "kn" to "knot knots nœud nœuds noeud noeuds nudo nudos knoten nodo nodi nó nós knoop knopen",
            "B" to "byte bytes octet octets",
            "cal" to "calorie calories caloría calorías kalorie kalorien caloria calorie",
            "w" to "watt watts",
            "deg" to "degree degrees degré degrés grado grados grad grado gradi grau graus graad graden",
            "rad" to "radian radians radiant radiants radián radianes radiant radiante radianti radiano radianos",
            "turn" to "turns",
            "\$EUR" to "euro euros",
            "\$USD" to "dollar dollars dólar dólares dollaro dollari"
        )
    }
}
