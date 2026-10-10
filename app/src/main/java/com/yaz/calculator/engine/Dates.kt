package com.yaz.calculator.engine

import java.text.DateFormatSymbols
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A sheet line's value when it is about dates or the clock. */
sealed interface Moment {
    data class Day(val date: LocalDate) : Moment
    data class Clock(val time: LocalTime) : Moment
    /** A count of [unit] between two days: "days until 15/03". */
    data class Count(val amount: Long, val unit: ChronoUnit) : Moment
    /** Time between two times of day. */
    data class Span(val duration: Duration) : Moment
}

/**
 * Dates and durations in a sheet line, as Soulver and Numi read them:
 * "today + 90 days", "15 March + 3 weeks", "days until 25/12",
 * "10/01/2027 − 5/02/2027", "14:30 + 2h45", "17:30 − 9:00", in the major
 * languages. A numeric date alone (15/3) stays a division: it is a date
 * only beside a duration, a day's name (today), a month's name, a time or
 * "until".
 */
class DateCalc(
    private val locale: Locale = Locale.getDefault(),
    private val today: LocalDate = LocalDate.now(),
    private val now: LocalTime = LocalTime.now().withSecond(0).withNano(0)
) {

    fun evaluate(raw: String): Moment? {
        val text = raw.trim().lowercase(locale).replace('−', '-').replace('–', '-')
        if (text.isEmpty()) return null
        COUNT.matchEntire(text)?.let { m -> count(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it } }
        val tokens = tokens(text) ?: return null
        val strong = tokens.any { it is Tok.At && it.strong || it is Tok.Time }
        val hasSpan = tokens.any { it is Tok.Span }
        if (!strong && !hasSpan) return null
        return combine(tokens)
    }

    private sealed interface Tok {
        /** A day; [strong] when it cannot be a division (a name, a month, an ISO date, a year). */
        class At(val date: LocalDate, val strong: Boolean) : Tok
        class Time(val time: LocalTime) : Tok
        class Span(val amount: Long, val unit: ChronoUnit) : Tok
        class Sign(val minus: Boolean) : Tok
    }

    private fun combine(tokens: List<Tok>): Moment? {
        // value (sign term)*: a day or a time, moved by spans; or two days or two times apart.
        var i = 0
        val first = tokens.getOrNull(i++) ?: return null
        var day: LocalDate? = (first as? Tok.At)?.date
        var time: LocalTime? = (first as? Tok.Time)?.time
        if (day == null && time == null) return null
        while (i < tokens.size) {
            val sign = tokens[i++] as? Tok.Sign ?: return null
            // Spans written one after another add up: 3 months 5 days.
            val start = i
            while (i < tokens.size && tokens[i] is Tok.Span) i++
            if (i > start) {
                for (k in start until i) {
                    val s = tokens[k] as Tok.Span
                    val n = if (sign.minus) -s.amount else s.amount
                    if (day != null) {
                        if (!s.unit.isDateBased) return null
                        day = day.plus(n, s.unit)
                    } else {
                        if (s.unit.isDateBased) return null
                        time = time!!.plus(n, s.unit)
                    }
                }
                continue
            }
            val other = tokens.getOrNull(i++) ?: return null
            if (!sign.minus || i != tokens.size) return null
            return when {
                day != null && other is Tok.At -> Moment.Count(Math.abs(ChronoUnit.DAYS.between(other.date, day)), ChronoUnit.DAYS)
                time != null && other is Tok.Time -> {
                    var d = Duration.between(other.time, time)
                    if (d.isNegative) d = d.plusDays(1)
                    Moment.Span(d)
                }
                else -> null
            }
        }
        return day?.let { Moment.Day(it) } ?: time?.let { Moment.Clock(it) }
    }

    /** "days until 25/12", "weeks since 1 Jan": whole units between today and that day. */
    private fun count(unitWord: String, direction: String, rest: String): Moment? {
        val unit = SPAN_WORDS[unitWord] ?: return null
        if (!unit.isDateBased) return null
        val day = (tokens(rest)?.singleOrNull() as? Tok.At)?.date ?: return null
        val forward = direction in UNTIL
        // A day without its year, already past this year, is next year's.
        val target = if (forward && day.isBefore(today) && !Regex("\\d{4}|\\d+[/.]\\d+[/.]\\d+").containsMatchIn(rest)) day.plusYears(1) else day
        val n = if (forward) unit.between(today, target) else unit.between(target, today)
        return Moment.Count(n, unit)
    }

    private fun tokens(text: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var p = 0
        while (p < text.length) {
            if (text[p] == ' ' || text[p] == ',') { p++; continue }
            if (text[p] == '+' || text[p] == '-') {
                out += Tok.Sign(text[p] == '-')
                p++
                continue
            }
            val rest = text.substring(p)
            // 14h30 is a time where a line starts, 2h45 a duration after a sign.
            val hit = readDay(rest) ?: readTime(rest, hours = out.isEmpty()) ?: readSpan(rest) ?: return null
            out += hit.first
            p += hit.second
        }
        return out
    }

    private fun readDay(s: String): Pair<Tok, Int>? {
        WORD.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            RELATIVE[m.value]?.let { return Tok.At(today.plusDays(it), true) to m.value.length }
        }
        ISO.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            val (y, mo, d) = m.destructured
            return date(y.toInt(), mo.toInt(), d.toInt())?.let { Tok.At(it, true) to m.value.length }
        }
        NUMERIC.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            if (m.value.contains('.') && y == null) return null
            // In the locale's order, or the other one when a month would pass 12 (25/12 in the US).
            val (day, month) = if (monthFirst && a <= 12 || b > 12) b to a else a to b
            return date(y ?: today.year, month, day)?.let { Tok.At(it, y != null) to m.value.length }
        }
        DAY_MONTH.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            val month = months[m.groupValues[2].trimEnd('.')] ?: return@let
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: today.year
            return date(y, month, m.groupValues[1].toInt())?.let { Tok.At(it, true) to m.value.length }
        }
        MONTH_DAY.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            val month = months[m.groupValues[1].trimEnd('.')] ?: return@let
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: today.year
            return date(y, month, m.groupValues[2].toInt())?.let { Tok.At(it, true) to m.value.length }
        }
        return null
    }

    private fun readTime(s: String, hours: Boolean): Pair<Tok, Int>? {
        WORD.find(s)?.takeIf { it.range.first == 0 && it.value in NOW }?.let { return Tok.Time(now) to it.value.length }
        val m = TIME.find(s)?.takeIf { it.range.first == 0 && (hours || it.value.contains(':')) } ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        when (m.groupValues[3].replace(".", "")) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h > 23 || min > 59) return null
        return Tok.Time(LocalTime.of(h, min)) to m.value.length
    }

    private fun readSpan(s: String): Pair<Tok, Int>? {
        // 2h45: hours and minutes run together.
        COMPACT.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
            val minutes = m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()
            return Tok.Span(minutes, ChronoUnit.MINUTES) to m.value.length
        }
        val m = SPAN.find(s)?.takeIf { it.range.first == 0 } ?: return null
        val unit = SPAN_WORDS[m.groupValues[2]] ?: return null
        return Tok.Span(m.groupValues[1].toLong(), unit) to m.value.length
    }

    private fun date(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** Whether the locale writes the month before the day (3/15 in the US). */
    private val monthFirst: Boolean by lazy {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        pattern.indexOf('M') in 0 until pattern.indexOf('d')
    }

    /** Month names and their short forms, in the phone's language and the major ones. */
    private val months: Map<String, Int> by lazy {
        val map = HashMap<String, Int>()
        for (l in listOf(locale) + LANGUAGES) {
            val symbols = DateFormatSymbols.getInstance(l)
            for ((i, name) in symbols.months.withIndex()) if (name.isNotEmpty()) map.putIfAbsent(name.lowercase(l), i + 1)
            for ((i, name) in symbols.shortMonths.withIndex()) if (name.isNotEmpty()) map.putIfAbsent(name.lowercase(l).trimEnd('.'), i + 1)
        }
        map
    }

    private val monthPattern: String by lazy { months.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } }
    private val DAY_MONTH by lazy { Regex("(\\d{1,2})(?:er|º|\\.)?\\s+(?:de\\s+)?($monthPattern)\\.?(?:\\s+(?:de\\s+)?(\\d{4}))?(?![\\p{L}])") }
    private val MONTH_DAY by lazy { Regex("($monthPattern)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?![\\p{L}\\d])") }

    companion object {
        private val LANGUAGES = listOf("en", "fr", "es", "de", "it", "pt", "nl").map { Locale.forLanguageTag(it) }
        private val WORD = Regex("[\\p{L}']+")
        private val ISO = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
        private val NUMERIC = Regex("(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{4}|\\d{2}))?(?![\\d/.])")
        private val TIME = Regex("(\\d{1,2})[:h](\\d{2})(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.))?(?![\\d\\p{L}])")
        private val COMPACT = Regex("(\\d+)\\s*h\\s*(\\d{1,2})(?![\\d\\p{L}])")
        private val SPAN = Regex("(\\d+)\\s*([\\p{L}]+)")

        private val RELATIVE = buildMap {
            for (w in "today aujourd'hui hoy heute oggi hoje vandaag".split(' ')) put(w, 0L)
            for (w in "tomorrow demain mañana morgen domani amanhã".split(' ')) put(w, 1L)
            for (w in "yesterday hier ayer gestern ieri ontem gisteren".split(' ')) put(w, -1L)
        }
        private val NOW = "now maintenant ahora jetzt adesso agora".split(' ').toSet()
        private val UNTIL = "until till to jusqu'au jusqu'à avant hasta bis fino até tot".split(' ').toSet()
        private val SINCE = "since depuis desde seit da sinds".split(' ').toSet()
        private val COUNT = Regex("([\\p{L}]+)\\s+(${(UNTIL + SINCE).joinToString("|") { Regex.escape(it) }})\\s+(?:the\\s+|le\\s+|el\\s+|il\\s+|o\\s+|a\\s+)?(.+)")

        /** Durations by name in the major languages. */
        private val SPAN_WORDS: Map<String, ChronoUnit> = buildMap {
            fun all(unit: ChronoUnit, words: String) = words.split(' ').forEach { put(it, unit) }
            all(ChronoUnit.DAYS, "d day days jour jours día días dia dias tag tage tagen giorno giorni dag dagen")
            all(ChronoUnit.WEEKS, "w wk week weeks semaine semaines semana semanas woche wochen settimana settimane weken")
            all(ChronoUnit.MONTHS, "mo month months mois mes meses monat monate mese mesi mês maand maanden")
            all(ChronoUnit.YEARS, "y yr year years an ans année années año años jahr jahre anno anni ano anos jaar jaren")
            all(ChronoUnit.HOURS, "h hr hrs hour hours heure heures hora horas stunde stunden ora ore uur uren")
            all(ChronoUnit.MINUTES, "m min mins minute minutes minuto minutos minuten minuti minuut")
        }
    }
}
