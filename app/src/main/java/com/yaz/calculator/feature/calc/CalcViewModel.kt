package com.yaz.calculator.feature.calc

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaz.calculator.data.history.Entry
import com.yaz.calculator.data.history.Ledger
import com.yaz.calculator.data.history.Stored
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.engine.Calc
import com.yaz.calculator.engine.CalcError
import com.yaz.calculator.engine.Base
import com.yaz.calculator.engine.ProgExpr
import com.yaz.calculator.engine.CalcException
import com.yaz.calculator.engine.Format
import com.yaz.calculator.engine.Outcome
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.engine.UnifiedReal
import com.yaz.calculator.engine.Written
import com.yaz.calculator.engine.compute
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The calculator: the expression being written, its result as it is typed,
 * the result once asked for, the memory and the history. Every result is
 * exact (see engine); a result that does not end is kept as a stored value,
 * so taking it back loses nothing.
 */
class CalcViewModel(private val ledger: Ledger, private val settings: SettingsStore) : ViewModel() {

    /** The expression, in the app's own form (see Sym). */
    val text = TextFieldState()

    /** How each stored result reads on screen, in the app's form, "…" when it goes on. */
    val shown = mutableStateMapOf<Char, String>()

    /** The result under the expression while it is typed. */
    var live by mutableStateOf<String?>(null)
        private set

    /** Why the last "=" gave nothing. */
    var error by mutableStateOf<CalcError?>(null)
        private set

    /** The expression a result on screen came from, shown small above it. */
    var previous by mutableStateOf<String?>(null)
        private set

    /** The exact form of the result on screen when it says more than its digits: 1/3, 2√2, π/2. */
    var exactForm by mutableStateOf<String?>(null)
        private set

    /** True right after "=": the next digit starts a new calculation, an operator goes on from the result. */
    var evaluated by mutableStateOf(false)
        private set

    /** Counts "=", for its animation. */
    var results by mutableIntStateOf(0)
        private set

    /** The inverse functions on the scientific keys: sin⁻¹, eˣ, 10ˣ, x². */
    var inverse by mutableStateOf(false)

    /** How many characters a result has on the screen; set by the screen as it measures itself. */
    var room by mutableIntStateOf(14)
        private set

    /** Further digits the user unfolded on the result. */
    private var extra = 0

    val degrees: Boolean get() = settings.current.degrees

    private val values = ConcurrentHashMap<Char, UnifiedReal>()
    private val calc = Calc(ref = ::valueOf)
    private var liveJob: Job? = null
    private var restoring = true

    init {
        viewModelScope.launch {
            ledger.loaded.first { it }
            val kept = ledger.data.value.expression
            if (kept.isNotEmpty()) text.setTextAndPlaceCursorAtEnd(kept)
            restoring = false
            refreshShown()
            onChanged()
            snapshotFlow { text.text.toString() }.drop(1).collect {
                ledger.update { d -> d.copy(expression = it) }
                onChanged()
            }
        }
    }

    // ---------- keys ----------

    private val typing = Typing(text)

    fun digit(d: Char): Feel { startOver(); return typing.digit(d) }

    fun point(): Feel { startOver(); return typing.point() }

    fun operator(op: Char): Feel { goOn(); return typing.operator(op) }

    fun suffix(c: Char): Feel { goOn(); return typing.suffix(c) }

    fun parenthesis(): Feel {
        // A result has no parenthesis left open: one after it starts a new calculation.
        startOver()
        return typing.parenthesis()
    }

    fun piece(piece: String): Feel { startOver(); return typing.piece(piece) }

    fun backspace(): Feel {
        error = null
        if (evaluated) { evaluated = false; previous = null; exactForm = null }
        return typing.backspace()
    }

    /**
     * Where the colour that clears the display starts (a key's centre in the
     * window), and how many times it was asked: AOSP Calculator's onClear,
     * a circular reveal from the key that clears, the expression cleared under it.
     */
    var clearFrom by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
        private set
    var reveals by mutableIntStateOf(0)
        private set

    /** The reveals already played: a screen built again (back from Settings) does not clear again. */
    var revealsPlayed = 0

    /** AC, or a long press on ⌫: the reveal runs, then [clear] under it. */
    /** Whether the running clear sparkles (a long press on ⌫, Android 12's ripple) or reveals (AC). */
    var sparkles = false
        private set

    fun clearWithReveal(from: androidx.compose.ui.geometry.Offset, sparkle: Boolean = false): Feel {
        if (text.text.isEmpty() && previous == null && error == null) return Feel.REJECT
        clearFrom = from
        sparkles = sparkle
        reveals++
        return Feel.DONE
    }

    // ---------- advanced mode ----------

    /** The advanced mode in place of the calculator's keys (the gold key). */
    var advanced by mutableStateOf(false)
        private set

    /** Openings of the advanced mode, and those whose gold sweep has played. */
    var advancedOpenings by mutableIntStateOf(0)
        private set
    var advancedPlayed = 0

    fun toggleAdvanced(): Feel {
        advanced = !advanced
        if (advanced) advancedOpenings++
        return Feel.DONE
    }

    // ---------- programmer (Windows Calculator's Programmer mode) ----------

    /** The programmer's keys and display in place of the calculator's. */
    var programmer by mutableStateOf(false)
        private set

    /** The base numbers are typed in; the others show the same value. */
    var base by mutableStateOf(Base.DEC)

    var prog by mutableStateOf(ProgExpr())
        private set

    var progError by mutableStateOf<CalcError?>(null)
        private set

    /** Right after "=": the next digit starts again. */
    private var progDone = false

    fun toggleProgrammer(): Feel {
        programmer = !programmer
        return Feel.TICK
    }

    fun progDigit(d: Int): Feel {
        val from = if (progDone) ProgExpr() else prog
        val next = from.digit(d, base) ?: return Feel.REJECT
        progDone = false
        progError = null
        prog = next
        return Feel.TICK
    }

    fun progOperator(op: String): Feel {
        val next = prog.operator(op) ?: return Feel.REJECT
        progDone = false
        progError = null
        prog = next
        return Feel.TICK
    }

    fun progParenthesis(): Feel {
        if (progDone) prog = ProgExpr()
        progDone = false
        prog = prog.parenthesis()
        return Feel.TICK
    }

    fun progBackspace(): Feel {
        progDone = false
        progError = null
        prog = prog.backspace(base) ?: return Feel.REJECT
        return Feel.TICK
    }

    fun progClear(): Feel {
        if (prog.tokens.isEmpty() && progError == null) return Feel.REJECT
        prog = ProgExpr()
        progError = null
        progDone = false
        return Feel.DONE
    }

    fun progEquals(): Feel {
        if (prog.tokens.size < 2) return Feel.REJECT
        return try {
            val v = prog.value() ?: return Feel.REJECT
            prog = ProgExpr(listOf(ProgExpr.Tok.Num(v)))
            progDone = true
            Feel.DONE
        } catch (e: CalcException) {
            progError = e.error
            Feel.REJECT
        }
    }

    /** The result shown in words under it, until the next calculation. */
    var inWords by mutableStateOf(false)

    /** What a clear took away, offered back for a few seconds (PCalc, Kalkyl): Undo, then Redo. */
    var offer by mutableStateOf<Offer?>(null)
        private set

    private var cleared: Screen? = null

    /** The expression as the offer left it: any other means the user typed on. */
    var offerText = ""
        private set

    fun clear(): Feel {
        snapshot().takeIf { it.text.isNotEmpty() || it.previous != null || it.error != null }?.let {
            cleared = it
            offer = Offer.UNDO
        }
        wipe()
        offerText = ""
        return Feel.DONE
    }

    /** Puts back what the last clear took away, or clears it again. */
    fun undoOrRedo(): Feel {
        val screen = cleared ?: return Feel.REJECT
        when (offer) {
            Offer.UNDO -> { restore(screen); offer = Offer.REDO }
            Offer.REDO -> { wipe(); offer = Offer.UNDO }
            null -> return Feel.REJECT
        }
        offerText = text.text.toString()
        return Feel.TICK
    }

    /** The offer goes when its time is up or the user types on. */
    fun dropOffer() {
        offer = null
    }

    private fun snapshot() = Screen(text.text.toString(), text.selection, evaluated, previous, exactForm, error, live, extra)

    private fun restore(s: Screen) {
        text.edit { replace(0, length, s.text); selection = s.selection }
        evaluated = s.evaluated
        previous = s.previous
        exactForm = s.exactForm
        error = s.error
        live = s.live
        extra = s.extra
    }

    private fun wipe() {
        text.setTextAndPlaceCursorAtEnd("")
        evaluated = false
        previous = null
        error = null
        exactForm = null
        live = null
        extra = 0
    }

    fun toggleDegrees() {
        settings.update { it.copy(degrees = !it.degrees) }
        onChanged()
    }

    fun equals(): Feel {
        val expression = text.text.toString()
        if (expression.isBlank() || evaluated) return Feel.REJECT
        val deg = degrees
        viewModelScope.launch {
            val outcome = compute(EQUALS_MS) {
                val value = calc.evaluate(expression, deg)
                Outcome.Value(value, Format.write(value, room))
            }
            when (outcome) {
                is Outcome.Failed -> {
                    error = outcome.error
                    live = null
                }
                is Outcome.Value -> settle(expression, deg, outcome.value, outcome.written)
            }
        }
        return Feel.DONE
    }

    private suspend fun settle(expression: String, deg: Boolean, value: UnifiedReal, written: Written) {
        val result = if (written.exact) {
            written.text
        } else {
            val c = ledger.store(Stored(expression, deg))
            values[c] = value
            shown[c] = written.text + "…"
            c.toString()
        }
        if (settings.current.keepHistory) {
            ledger.update { d ->
                val entry = Entry(expression, result, written.text + if (written.more) "…" else "", deg, System.currentTimeMillis())
                d.copy(entries = (d.entries + entry).takeLast(Ledger.MAX_ENTRIES))
            }
        }
        // A numerical result (nDeriv, fnInt, fMin, fMax) has no exact form to show.
        val numerical = NUMERICAL.any { expression.contains(it) }
        exactForm = if (numerical) null else compute(LIVE_MS) {
            Outcome.Value(value, Written(nice(value, written), exact = true, more = false))
        }.let { (it as? Outcome.Value)?.written?.text?.takeIf(String::isNotEmpty) }
        extra = 0
        previous = expression
        live = null
        error = null
        evaluated = true
        text.setTextAndPlaceCursorAtEnd(result)
        results++
        ScienceEgg.of(runCatching { value.crValue().toDouble() }.getOrDefault(Double.NaN))?.let { scienceEgg = it; scienceEggs++ }
    }

    /** The scientific egg last given, and how many: the screen plays each once. */
    var scienceEgg by mutableStateOf<ScienceEgg?>(null)
        private set
    var scienceEggs by mutableIntStateOf(0)
        private set
    var scienceEggsPlayed = 0

    /** The exact form, when it is not what the digits already say. */
    private fun nice(value: UnifiedReal, written: Written): String {
        if (!value.exactlyDisplayable()) return ""
        // (1/2)√2 reads √2/2, (3/4)π reads 3π/4.
        val form = value.toNiceString().replace('-', Sym.MINUS).let { raw ->
            FRACTION_TIMES.matchEntire(raw)?.let { m ->
                val (top, bottom, named) = m.destructured
                if (top == "1") "$named/$bottom" else if (top == "${Sym.MINUS}1") "${Sym.MINUS}$named/$bottom" else "$top$named/$bottom"
            } ?: raw
        }
        if (written.exact && form == written.text) return ""
        if (form.length > 40 || form.contains("ln(")) return ""
        return form
    }

    /** Whether the result on screen has further digits to unfold. */
    val unfoldable: Boolean get() {
        val t = text.text
        return evaluated && t.length == 1 && Sym.isRef(t[0]) && shown[t[0]]?.endsWith("…") == true && extra < MAX_EXTRA
    }

    /** Further digits of the result on screen, three lines more each time. */
    fun unfold(): Boolean {
        val t = text.text
        if (!evaluated || t.length != 1 || !Sym.isRef(t[0])) return false
        val c = t[0]
        if (extra >= MAX_EXTRA) return false
        extra += room * 3
        val wanted = extra
        viewModelScope.launch {
            val outcome = compute(EQUALS_MS) {
                val v = valueOf(c) ?: throw IllegalStateException()
                Outcome.Value(v, Format.write(v, room, wanted))
            }
            (outcome as? Outcome.Value)?.let { shown[c] = it.written.text + if (it.written.more) "…" else "" }
        }
        return true
    }

    /** The screen measured how many characters a line holds. */
    fun measured(chars: Int) {
        if (chars == room || chars < 4) return
        room = chars
        refreshShown()
        onChanged()
    }

    // ---------- memory ----------

    val memory: String? get() = ledger.data.value.memory

    fun memoryStore(): Feel = withValue { source -> ledger.update { it.copy(memory = source) } }

    fun memoryAdd(): Feel = withValue { source ->
        ledger.update { d -> d.copy(memory = d.memory?.let { "($it)+($source)" } ?: source) }
    }

    fun memorySubtract(): Feel = withValue { source ->
        ledger.update { d -> d.copy(memory = d.memory?.let { "($it)${Sym.MINUS}($source)" } ?: "${Sym.MINUS}($source)") }
    }

    fun memoryRecall(): Feel {
        val kept = memory ?: return Feel.REJECT
        val c = ledger.store(Stored(kept, degrees))
        startOver()
        typing.insert(c.toString())
        showLater(c)
        return Feel.TICK
    }

    fun memoryClear(): Feel {
        ledger.update { it.copy(memory = null) }
        return Feel.DONE
    }

    /** The expression on screen, as a stored value's source for the memory, when it has a value. */
    private fun withValue(use: (String) -> Unit): Feel {
        val expression = text.text.toString()
        if (expression.isBlank()) return Feel.REJECT
        // Kept as an expression worked out in its angle unit, so the memory stays exact.
        val c = ledger.store(Stored(expression, degrees))
        use(c.toString())
        return Feel.DONE
    }

    // ---------- history ----------

    val entries get() = ledger.data

    /** A past result, taken into the expression where the cursor is. */
    fun takeResult(entry: Entry): Feel {
        startOver()
        typing.insert(entry.result)
        entry.result.filter(Sym::isRef).forEach(::showLater)
        return Feel.TICK
    }

    /** A past expression, written again in place of the one on screen. */
    fun takeExpression(entry: Entry): Feel {
        evaluated = false
        previous = null
        error = null
        exactForm = null
        if (entry.degrees != degrees) settings.update { it.copy(degrees = entry.degrees) }
        text.setTextAndPlaceCursorAtEnd(entry.expression)
        entry.expression.filter(Sym::isRef).forEach(::showLater)
        return Feel.TICK
    }

    /** One past calculation removed, by a swipe or from its long press. */
    fun deleteEntry(entry: Entry): Feel {
        ledger.update { d -> d.copy(entries = d.entries - entry) }
        return Feel.DONE
    }

    fun clearHistory() {
        ledger.update { it.copy(entries = emptyList()) }
    }

    /** What the result on screen is, for copying and sharing, in the app's form. */
    fun resultText(): String? {
        val t = text.text.toString()
        if (evaluated) return t.map { c -> if (Sym.isRef(c)) shown[c]?.removeSuffix("…") ?: "" else c.toString() }.joinToString("")
        return live?.removeSuffix("…")
    }

    /** The expression with each stored result written out, for the clipboard. */
    fun spelled(t: CharSequence): String =
        t.map { c -> if (Sym.isRef(c)) shown[c]?.removeSuffix("…") ?: "" else c.toString() }.joinToString("")

    // ---------- inside ----------

    private fun valueOf(c: Char): UnifiedReal? {
        values[c]?.let { return it }
        val stored = ledger.data.value.stored[c.code] ?: return null
        val value = calc.evaluate(stored.source, stored.degrees)
        values[c] = value
        return value
    }

    private fun showLater(c: Char) {
        if (shown[c] != null) return
        shown[c] = "…"
        viewModelScope.launch {
            val outcome = compute(EQUALS_MS) {
                val v = valueOf(c) ?: throw IllegalStateException()
                Outcome.Value(v, Format.write(v, room))
            }
            shown[c] = (outcome as? Outcome.Value)?.written?.let { it.text + if (it.more) "…" else "" } ?: "?"
        }
    }

    /** Every stored result on screen written again for the room there is. */
    private fun refreshShown() {
        val refs = text.text.filter(Sym::isRef).toSet()
        refs.forEach { shown.remove(it) }
        refs.forEach(::showLater)
    }

    private fun onChanged() {
        if (restoring) return
        liveJob?.cancel()
        val expression = text.text.toString()
        if (evaluated || !calc.interesting(expression)) {
            live = null
            return
        }
        val deg = degrees
        liveJob = viewModelScope.launch {
            delay(40)
            val outcome = compute(LIVE_MS) {
                val v = calc.evaluate(expression, deg)
                Outcome.Value(v, Format.write(v, room))
            }
            live = (outcome as? Outcome.Value)?.written?.let { it.text + if (it.more) "…" else "" }
        }
    }

    /** A digit or a value after a result: a new calculation. */
    private fun startOver() {
        error = null
        if (!evaluated) return
        evaluated = false
        previous = null
        exactForm = null
        text.setTextAndPlaceCursorAtEnd("")
    }

    /** An operator after a result: the calculation goes on from it. */
    private fun goOn() {
        error = null
        if (!evaluated) return
        evaluated = false
        previous = null
        exactForm = null
        text.edit { selection = TextRange(length) }
    }

    private companion object {
        val FRACTION_TIMES = Regex("\\((${Sym.MINUS}?\\d+)/(\\d+)\\)(.+)")
        const val LIVE_MS = 1_500L
        const val EQUALS_MS = 10_000L
        const val MAX_EXTRA = 3_000
    }
}

enum class Offer { UNDO, REDO }

/** The calculator's screen as it was, to put back. */
private class Screen(
    val text: String,
    val selection: androidx.compose.ui.text.TextRange,
    val evaluated: Boolean,
    val previous: String?,
    val exactForm: String?,
    val error: CalcError?,
    val live: String?,
    val extra: Int
)

/** The functions worked out numerically, whose results are no exact numbers. */
private val NUMERICAL = listOf("nDeriv(", "fnInt(", "fMin(", "fMax(")
