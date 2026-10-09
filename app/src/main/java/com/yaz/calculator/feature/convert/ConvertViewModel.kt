package com.yaz.calculator.feature.convert

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaz.calculator.data.rates.RateTable
import com.yaz.calculator.data.rates.Rates
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.engine.Calc
import com.yaz.calculator.engine.Category
import com.yaz.calculator.engine.Format
import com.yaz.calculator.engine.Outcome
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.engine.UnifiedReal
import com.yaz.calculator.engine.Written
import com.yaz.calculator.engine.Units
import com.yaz.calculator.engine.compute
import com.yaz.calculator.feature.calc.Feel
import com.yaz.calculator.feature.calc.Typing
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A unit as the converter lists it: an id, its name and its symbol (a currency's code). */
data class Choice(val id: String, val name: String, val symbol: String)

/**
 * The converter: a category, a unit to convert from and one to convert to,
 * an amount typed with the calculator's keys (an expression works: 12+3 km),
 * the other side worked out exactly; currencies from the latest rates.
 */
class ConvertViewModel(private val settings: SettingsStore, val rates: Rates) : ViewModel() {

    val input = TextFieldState("1")
    val typing = Typing(input)

    var category by mutableStateOf(runCatching { Category.valueOf(settings.current.convertCategory) }.getOrDefault(Category.LENGTH))
        private set
    var from by mutableStateOf("")
        private set
    var to by mutableStateOf("")
        private set
    var output by mutableStateOf<String?>(null)
        private set
    var room by mutableIntStateOf(16)
        private set

    private val calc = Calc()
    private var job: Job? = null
    private val locale: Locale get() = Locale.getDefault()

    init {
        pickUnits()
        viewModelScope.launch {
            combine(snapshotFlow { input.text.toString() }, rates.table) { text, table -> text to table }
                .collect { (text, table) -> convert(text, table) }
        }
    }

    fun choose(next: Category) {
        if (next == category) return
        category = next
        settings.update { it.copy(convertCategory = next.name) }
        pickUnits()
        if (next == Category.CURRENCY) rates.refreshIfOld()
        convert(input.text.toString(), rates.table.value)
    }

    fun chooseFrom(id: String) {
        if (id == to) to = from
        from = id
        remember()
    }

    fun chooseTo(id: String) {
        if (id == from) from = to
        to = id
        remember()
    }

    fun swap(): Feel {
        val f = from
        from = to
        to = f
        remember()
        return Feel.DONE
    }

    /** The amount typed is replaced by its value: 12+3 becomes 15. */
    fun equals(): Feel {
        val text = input.text.toString()
        viewModelScope.launch {
            val outcome = compute(LIVE_MS) {
                val v = calc.evaluate(text, false)
                Outcome.Value(v, Format.write(v, room))
            }
            (outcome as? Outcome.Value)?.takeIf { it.written.exact }?.let { input.setTextAndPlaceCursorAtEnd(it.written.text) }
        }
        return Feel.DONE
    }

    fun measured(chars: Int) {
        if (chars == room || chars < 4) return
        room = chars
        convert(input.text.toString(), rates.table.value)
    }

    /** The units of the current category, as listed: currencies by their names in the user's language. */
    fun choices(): List<Choice> = when (category) {
        Category.CURRENCY -> {
            val table = rates.table.value?.rates ?: emptyMap()
            table.keys.mapNotNull { code -> runCatching { Currency.getInstance(code) }.getOrNull() }
                .map { Choice(it.currencyCode, it.getDisplayName(locale).replaceFirstChar { c -> c.titlecase(locale) }, it.currencyCode) }
                .sortedWith(compareBy({ it.id !in favourites() }, { favourites().indexOf(it.id) }, { it.name }))
        }
        else -> Units.all[category].orEmpty().map { Choice(it.id, it.name, it.symbol) }
    }

    fun choice(id: String): Choice? = if (category == Category.CURRENCY) {
        runCatching { Currency.getInstance(id) }.getOrNull()?.let { Choice(id, it.getDisplayName(locale), id) }
    } else {
        Units.find(category, id)?.let { Choice(it.id, it.name, it.symbol) }
    }

    /** The currencies most people reach for: their own first. */
    private fun favourites(): List<String> = listOfNotNull(localCurrency()) + listOf("USD", "EUR", "GBP", "JPY", "CNY", "CHF", "CAD")

    private fun localCurrency(): String? = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()

    private fun pickUnits() {
        val kept = settings.current.convertUnits[category.name]
        if (kept != null && kept.size == 2) {
            from = kept[0]
            to = kept[1]
            return
        }
        if (category == Category.CURRENCY) {
            val own = localCurrency() ?: "EUR"
            from = own
            to = if (own == "USD") "EUR" else "USD"
        } else {
            val (f, t) = Units.defaults(category, locale)
            from = f
            to = t
        }
    }

    private fun remember() {
        settings.update { it.copy(convertUnits = it.convertUnits + (category.name to listOf(from, to))) }
        convert(input.text.toString(), rates.table.value)
    }

    private fun convert(text: String, table: RateTable?) {
        job?.cancel()
        val cat = category
        val f = from
        val t = to
        val width = room
        if (text.isBlank()) { output = null; return }
        job = viewModelScope.launch {
            val outcome = compute(LIVE_MS) {
                val amount = calc.evaluate(text, false)
                val result = if (cat == Category.CURRENCY) {
                    val rf = table?.rates?.get(f)?.let(Units::exact) ?: throw IllegalStateException()
                    val rt = table.rates[t]?.let(Units::exact) ?: throw IllegalStateException()
                    amount.divide(rf).multiply(rt)
                } else {
                    val a = Units.find(cat, f) ?: throw IllegalStateException()
                    val b = Units.find(cat, t) ?: throw IllegalStateException()
                    b.fromBase(a.toBase(amount))
                }
                val money = if (cat == Category.CURRENCY) money(result, t, width) else null
                Outcome.Value(result, money ?: Format.write(result, width))
            }
            output = (outcome as? Outcome.Value)?.written?.let { it.text + if (it.more) "…" else "" }
        }
    }

    private companion object {
        const val LIVE_MS = 2_000L

        /** An amount of money as it is written: rounded to the cents its currency uses. */
        fun money(value: UnifiedReal, code: String, room: Int): Written? {
            val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).let { if (it < 0) 4 else it }
            val half = UnifiedReal(5).divide(UnifiedReal.TEN.pow(UnifiedReal((digits + 1).toLong())))
            val rounded = if (value.signum(-200) < 0) value.subtract(half) else value.add(half)
            val text = rounded.toStringTruncated(digits).let { if (digits == 0) it.trimEnd('.') else it }.replace('-', Sym.MINUS)
            return if (text.length <= room) Written(text, exact = true, more = false) else null
        }
    }
}
