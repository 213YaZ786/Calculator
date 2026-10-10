package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaz.calculator.data.advanced.AdvancedStore
import com.yaz.calculator.data.advanced.Window
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.engine.Curve
import com.yaz.calculator.engine.programs.Outcome
import com.yaz.calculator.engine.programs.Program
import com.yaz.calculator.feature.calc.Feel
import com.yaz.calculator.feature.calc.Typing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.differentiation.DSFactory
import org.hipparchus.analysis.differentiation.FiniteDifferencesDifferentiator
import org.hipparchus.analysis.integration.IterativeLegendreGaussIntegrator
import org.hipparchus.analysis.solvers.BrentSolver
import org.hipparchus.optim.MaxEval
import org.hipparchus.optim.nonlinear.scalar.GoalType
import org.hipparchus.optim.univariate.BrentOptimizer
import org.hipparchus.optim.univariate.SearchInterval
import org.hipparchus.optim.univariate.UnivariateObjectiveFunction

/** The advanced mode's pages, as NumWorks' grapher and the TI's keys lay them out. */
enum class Page { CALC, FUNCTIONS, GRAPH, TABLE, STATS, BASES, PROGRAMS }

/** What the TI's CALC menu works out on the graph. */
enum class CalcKind { ZEROS, MINIMUM, MAXIMUM, INTERSECTION, DERIVATIVE, INTEGRAL }

/** A point the CALC menu found, on curve [curve]; [value] is what it answers (y, or the slope for dy/dx). */
data class Mark(val x: Double, val y: Double, val curve: Int, val kind: CalcKind, val value: Double = y)

/** The area under curve [curve] between [from] and [to], and its value. */
data class Shade(val curve: Int, val from: Double, val to: Double, val value: Double)

/**
 * The advanced mode: Y1 to Y6, the graph's window and table, the CALC
 * menu's results. Kept sealed between sessions (AdvancedStore).
 */
class AdvancedViewModel(private val store: AdvancedStore, private val settings: SettingsStore) : ViewModel() {

    var page by mutableStateOf(Page.FUNCTIONS)

    /** The function the keys type into, and the CALC menu works on. */
    var selected by mutableIntStateOf(0)

    val fields = List(com.yaz.calculator.data.advanced.AdvancedData.FUNCTIONS) { TextFieldState() }
    val typings = fields.map(::Typing)

    val data get() = store.data
    val degrees: Boolean get() = settings.current.degrees
    val degreesFlow = settings.settings.map { it.degrees }

    var marks by mutableStateOf<List<Mark>>(emptyList())
        private set
    var shade by mutableStateOf<Shade?>(null)
        private set

    /** The x touched on the graph: every curve is read there (TRACE). */
    var traced by mutableStateOf<Double?>(null)

    /** ∫ waits for its bounds: the first touch is the lower one, the second the upper. */
    var picking by mutableStateOf<Double?>(null)
        private set
    var pickingIntegral by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** L1 and L2, row by row; the last row is always empty. Declared before init, which may fill it at once. */
    var rows by mutableStateOf<List<Pair<TextFieldState, TextFieldState>>>(listOf(TextFieldState() to TextFieldState()))
        private set

    init {
        viewModelScope.launch {
            store.loaded.first { it }
            store.data.value.functions.forEachIndexed { i, f -> fields[i].setTextAndPlaceCursorAtEnd(f.expression) }
            val d = store.data.value
            rows = (0 until maxOf(d.l1.size, d.l2.size)).map { i ->
                TextFieldState(d.l1.getOrElse(i) { "" }) to TextFieldState(d.l2.getOrElse(i) { "" })
            } + (TextFieldState() to TextFieldState())
            fields.forEachIndexed { i, field ->
                launch {
                    snapshotFlow { field.text.toString() }.collect { text ->
                        store.update { d -> d.copy(functions = d.functions.toMutableList().also { it[i] = it[i].copy(expression = text) }) }
                        clearResults()
                    }
                }
            }
        }
    }

    /** The drawn curves, by index; null where a line is empty, off or does not read. */
    fun curves(): List<Curve?> = data.value.functions.mapIndexed { i, f ->
        if (!f.on) null else Curve.of(fields[i].text.toString(), degrees)
    }

    // ---------- STAT ----------

    var cellRow by mutableIntStateOf(0)
    var cellColumn by mutableIntStateOf(0)
    var twoVar by mutableStateOf(false)
    var regKind by mutableStateOf(com.yaz.calculator.engine.RegKind.LIN)

    fun cell(): Typing = Typing(rows[cellRow.coerceIn(0, rows.lastIndex)].let { if (cellColumn == 0) it.first else it.second })

    /** After a key on STAT: the empty last row kept, the lists saved. */
    fun statsChanged() {
        val last = rows.last()
        if (last.first.text.isNotEmpty() || last.second.text.isNotEmpty()) rows = rows + (TextFieldState() to TextFieldState())
        store.update { d -> d.copy(l1 = rows.map { it.first.text.toString() }.dropLastWhile { it.isEmpty() }, l2 = rows.map { it.second.text.toString() }.dropLastWhile { it.isEmpty() }) }
    }

    /** ↵ on STAT: down a row, as the TI's list editor. */
    fun nextCell(): Feel {
        cellRow = (cellRow + 1).coerceAtMost(rows.lastIndex)
        return Feel.TICK
    }

    fun clearLists(): Feel {
        rows = listOf(TextFieldState() to TextFieldState())
        cellRow = 0
        statsChanged()
        return Feel.DONE
    }

    private fun value(t: TextFieldState): Double? =
        t.text.toString().takeIf { it.isNotBlank() }?.let { runCatching { com.yaz.calculator.engine.Calc().evaluate(it, degrees).crValue().toDouble() }.getOrNull() }

    /** The lists as numbers: rows where L1 (and L2 for two variables) read. */
    fun data(): Pair<List<Double>, List<Double?>> {
        val xs = ArrayList<Double>(); val ys = ArrayList<Double?>()
        for ((a, b) in rows) {
            val x = value(a) ?: continue
            xs += x; ys += value(b)
        }
        return xs to ys
    }

    /** The regression stored as a function: the first empty Y, else Y1. */
    fun storeRegression(equation: String): Feel {
        val i = fields.indexOfFirst { it.text.isEmpty() }.let { if (it < 0) 0 else it }
        fields[i].setTextAndPlaceCursorAtEnd(equation)
        store.update { d -> d.copy(functions = d.functions.toMutableList().also { it[i] = it[i].copy(expression = equation, on = true) }) }
        selected = i
        page = Page.GRAPH
        return Feel.DONE
    }

    // ---------- the user's TI-Basic programs ----------

    /** The program being written: its index, −1 for a new one; null when none. */
    var editing by mutableStateOf<Int?>(null)
        private set
    val editorName = TextFieldState()
    val editorSource = TextFieldState()

    fun edit(index: Int) {
        val p = data.value.userPrograms.getOrNull(index)
        editorName.setTextAndPlaceCursorAtEnd(p?.name ?: "")
        editorSource.setTextAndPlaceCursorAtEnd(p?.source ?: "")
        editing = if (p == null) -1 else index
        stop()
        running = null
    }

    fun saveEdit(): Feel {
        val i = editing ?: return Feel.REJECT
        val name = editorName.text.toString().trim().take(16).ifEmpty { return Feel.REJECT }
        val program = com.yaz.calculator.data.advanced.UserProgram(name, editorSource.text.toString().take(MAX_SOURCE))
        store.update { d -> d.copy(userPrograms = if (i < 0) d.userPrograms + program else d.userPrograms.toMutableList().also { it[i] = program }) }
        editing = null
        return Feel.DONE
    }

    fun cancelEdit() { editing = null }

    fun deleteProgram(index: Int): Feel {
        store.update { d -> d.copy(userPrograms = d.userPrograms.filterIndexed { i, _ -> i != index }) }
        running = null
        return Feel.DONE
    }

    /** The program running: its index; its console; what it waits for. */
    var running by mutableStateOf<Int?>(null)
        private set
    val console = androidx.compose.runtime.mutableStateListOf<String>()
    var asking by mutableStateOf<String?>(null)
        private set
    var choosing by mutableStateOf<Pair<String, List<String>>?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    val answer = TextFieldState()
    val answerTyping = Typing(answer)
    private var reply: kotlinx.coroutines.CompletableDeferred<Double>? = null
    private var picked: kotlinx.coroutines.CompletableDeferred<Int>? = null
    private var resumed: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var runJob: Job? = null
    @Volatile private var stopping = false

    fun runUser(index: Int) {
        val p = data.value.userPrograms.getOrNull(index) ?: return
        stop()
        running = index
        console.clear()
        finished = false
        stopping = false
        val io = object : com.yaz.calculator.engine.tibasic.Interpreter.Io {
            override fun show(line: String) { if (console.size < MAX_LINES) console += line }
            override fun clear() = console.clear()
            override suspend fun ask(prompt: String): Double {
                val d = kotlinx.coroutines.CompletableDeferred<Double>()
                reply = d
                answer.setTextAndPlaceCursorAtEnd("")
                asking = prompt
                return d.await().also { console += "$prompt ${com.yaz.calculator.engine.tibasic.Interpreter.write(it)}"; asking = null }
            }
            override suspend fun choose(title: String, options: List<String>): Int {
                val d = kotlinx.coroutines.CompletableDeferred<Int>()
                picked = d
                choosing = title to options
                return d.await().also { choosing = null }
            }
            override suspend fun pause(line: String?) {
                line?.let { console += it }
                val d = kotlinx.coroutines.CompletableDeferred<Unit>()
                resumed = d
                paused = true
                d.await()
                paused = false
            }
            override fun stopped() = stopping
        }
        runJob = viewModelScope.launch {
            try {
                // Off the main thread; the interpreter stops itself after a million statements.
                kotlinx.coroutines.withContext(Dispatchers.Default) {
                    com.yaz.calculator.engine.tibasic.Interpreter(p.source, io, degrees).run()
                }
            } catch (e: com.yaz.calculator.engine.tibasic.Interpreter.Failure) {
                console += "ERR: ${e.message}"
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                console += "ERR"
            } catch (_: StackOverflowError) {
                console += "ERR: Too deeply nested"
            }
            asking = null; choosing = null; paused = false
            finished = true
        }
    }

    /** "=" while a program runs: the answer typed, or on after a Pause. */
    fun submit(): Feel {
        if (paused) { resumed?.complete(Unit); return Feel.TICK }
        val d = reply ?: return Feel.REJECT
        if (asking == null) return Feel.REJECT
        val v = runCatching { com.yaz.calculator.engine.Calc().evaluate(answer.text.toString(), degrees).crValue().toDouble() }.getOrNull() ?: return Feel.REJECT
        d.complete(v)
        return Feel.DONE
    }

    fun pick(i: Int) { picked?.complete(i) }

    fun stop() {
        stopping = true
        runJob?.cancel()
        runJob = null
    }

    fun closeRun() {
        stop()
        running = null
    }

    // ---------- PRGM ----------

    /** The program open on the PRGM page, null for the list. */
    var program by mutableStateOf<Program?>(null)
        private set
    var programFields by mutableStateOf<List<TextFieldState>>(emptyList())
        private set
    var programTypings by mutableStateOf<List<Typing>>(emptyList())
        private set
    var programField by mutableIntStateOf(0)
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    fun open(p: Program?) {
        program = p
        programFields = p?.fields?.map { TextFieldState() }.orEmpty()
        programTypings = programFields.map(::Typing)
        programField = 0
        outcome = null
    }

    /** "=" on a program: each field read as an expression (6.67×10^−11 works), the empty ones left to find. */
    /** A program's figure on show: the keys fold to their gold row until a field is touched. */
    var showingScene by mutableStateOf(false)

    fun runProgram(): Feel {
        val p = program ?: return Feel.REJECT
        val values = HashMap<String, Double?>()
        val texts = HashMap<String, String>()
        p.fields.forEachIndexed { i, f ->
            val text = programFields[i].text.toString()
            // A function of x or a list is read by the program itself.
            if (f.kind != com.yaz.calculator.engine.programs.FieldKind.NUMBER) {
                texts[f.symbol] = text
                values[f.symbol] = null
                return@forEachIndexed
            }
            values[f.symbol] = if (text.isBlank()) null else {
                runCatching { com.yaz.calculator.engine.Calc().evaluate(text, degrees).crValue().toDouble() }.getOrNull()
                    ?: run { outcome = Outcome(listOf(com.yaz.calculator.engine.programs.Line(UNREADABLE.get().format(f.symbol)))); return Feel.REJECT }
            }
        }
        outcome = p.run(values, texts, degrees)
        showingScene = outcome?.scene != null
        return if (outcome!!.lines.any { it.result }) Feel.DONE else Feel.REJECT
    }

    fun toggle(i: Int): Feel {
        store.update { d -> d.copy(functions = d.functions.toMutableList().also { it[i] = it[i].copy(on = !it[i].on) }) }
        clearResults()
        return Feel.TICK
    }

    fun select(i: Int) {
        selected = i
    }

    /** "=" on the functions page: on to the next line, as ENTER on the TI. */
    fun next(): Feel {
        selected = (selected + 1) % fields.size
        return Feel.TICK
    }

    fun setWindow(w: Window) = store.update { it.copy(window = w) }

    /** The angle unit, the calculator's own (its DEG key): the TI's MODE. */
    fun toggleDegrees(): Feel {
        settings.update { it.copy(degrees = !it.degrees) }
        clearResults()
        return Feel.TICK
    }

    /** The graph's height over its width, for ZSquare. */
    var plotAspect = 1.0

    /** ZSquare: the same unit on both axes, around the window's middle. */
    fun squareWindow() {
        val w = data.value.window
        val half = (w.xMax - w.xMin) * plotAspect / 2
        val mid = (w.yMin + w.yMax) / 2
        setWindow(w.copy(yMin = mid - half, yMax = mid + half))
    }

    /** ZoomFit: y from the lowest to the highest value the curves take across x. */
    fun fitWindow() {
        val w = data.value.window
        val ys = curves().filterNotNull().flatMap { c -> (0..SAMPLES).map { c(w.xMin + (w.xMax - w.xMin) * it / SAMPLES) } }.filter { it.isFinite() }
        if (ys.isEmpty()) return
        var lo = ys.min()
        var hi = ys.max()
        if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.05
        setWindow(w.copy(yMin = lo - pad, yMax = hi + pad))
    }

    /** Zoom in (k < 1) or out around the window's middle. */
    fun zoom(k: Double) {
        val w = data.value.window
        val cx = (w.xMin + w.xMax) / 2
        val cy = (w.yMin + w.yMax) / 2
        val hw = (w.xMax - w.xMin) / 2 * k
        val hh = (w.yMax - w.yMin) / 2 * k
        setWindow(Window(cx - hw, cx + hw, cy - hh, cy + hh))
    }

    fun setTable(start: Double, step: Double) = store.update { it.copy(tableStart = start, tableStep = step) }

    fun clearResults() {
        marks = emptyList()
        shade = null
        picking = null
        pickingIntegral = false
    }

    /** A touch on the graph: a bound while ∫ waits for one, else the x read on every curve. */
    fun touch(touched: Double): Feel {
        // Snapped to a round value, a hundredth of the window, as the TI's cursor moves in steps.
        val w = data.value.window
        val step = niceStep((w.xMax - w.xMin) / 100)
        val x = Math.round(touched / step) * step
        if (pickingIntegral) {
            val from = picking
            if (from == null) {
                picking = x
                return Feel.TICK
            }
            val f = curves().getOrNull(selected) ?: return Feel.REJECT
            val (a, b) = if (from <= x) from to x else x to from
            pickingIntegral = false
            picking = null
            val value = runCatching { IterativeLegendreGaussIntegrator(5, 1e-10, 1e-12).integrate(MAX_EVALUATIONS, fn(f), a, b) }.getOrNull()
                ?: return Feel.REJECT
            shade = Shade(selected, a, b, value)
            return Feel.DONE
        }
        traced = x
        return Feel.TICK
    }

    /** The CALC menu, over the window's width: every answer at once, not one per guess. */
    fun calc(kind: CalcKind): Feel {
        val all = curves()
        val f = all.getOrNull(selected) ?: return Feel.REJECT
        clearResults()
        if (kind == CalcKind.INTEGRAL) {
            pickingIntegral = true
            return Feel.TICK
        }
        val w = data.value.window
        job?.cancel()
        job = viewModelScope.launch {
            marks = withContext(Dispatchers.Default) {
                when (kind) {
                    CalcKind.ZEROS -> zeros(fn(f), w).map { Mark(it, 0.0, selected, kind) }
                    CalcKind.MINIMUM, CalcKind.MAXIMUM -> extrema(fn(f), w, kind == CalcKind.MAXIMUM).map { Mark(it, f(it), selected, kind) }
                    CalcKind.INTERSECTION -> all.withIndex().filter { it.index != selected && it.value != null }.flatMap { (j, g) ->
                        zeros(UnivariateFunction { x -> f(x) - g!!(x) }, w).map { Mark(it, f(it), j, kind) }
                    }
                    CalcKind.DERIVATIVE -> {
                        val at = traced ?: ((w.xMin + w.xMax) / 2)
                        val d = runCatching {
                            FiniteDifferencesDifferentiator(5, 1e-4 * maxOf(1.0, Math.abs(at))).differentiate(fn(f))
                                .value(DSFactory(1, 1).variable(0, at)).getPartialDerivative(1)
                        }.getOrNull()
                        if (d == null || !d.isFinite()) emptyList() else listOf(Mark(at, f(at), selected, kind, d))
                    }
                    CalcKind.INTEGRAL -> emptyList()
                }
            }
        }
        return Feel.DONE
    }

    private fun fn(c: Curve) = UnivariateFunction { x -> c(x) }

    /** Where f changes sign between samples, then Brent's method. */
    private fun zeros(f: UnivariateFunction, w: Window): List<Double> {
        val out = ArrayList<Double>()
        val n = SAMPLES
        var x0 = w.xMin
        var y0 = f.value(x0)
        for (i in 1..n) {
            val x1 = w.xMin + (w.xMax - w.xMin) * i / n
            val y1 = f.value(x1)
            if (y0.isFinite() && y1.isFinite()) {
                when {
                    y0 == 0.0 -> out += x0
                    y0 * y1 < 0 -> runCatching { BrentSolver(1e-12).solve(MAX_EVALUATIONS, f, x0, x1) }.getOrNull()
                        // A sign change across a pole is no zero.
                        ?.takeIf { Math.abs(f.value(it)) < 1e-6 * maxOf(1.0, Math.abs(y0) + Math.abs(y1)) }?.let { out += it }
                }
            }
            x0 = x1
            y0 = y1
        }
        return out.distinctBy { Math.round(it * 1e9) }
    }

    /** Local minima (or maxima) inside the window: a turn between samples, then Brent's optimiser. */
    private fun extrema(f: UnivariateFunction, w: Window, max: Boolean): List<Double> {
        val n = SAMPLES
        val xs = DoubleArray(n + 1) { w.xMin + (w.xMax - w.xMin) * it / n }
        val ys = DoubleArray(n + 1) { f.value(xs[it]) }
        val out = ArrayList<Double>()
        for (i in 1 until n) {
            if (!ys[i - 1].isFinite() || !ys[i].isFinite() || !ys[i + 1].isFinite()) continue
            val turn = if (max) ys[i] >= ys[i - 1] && ys[i] > ys[i + 1] else ys[i] <= ys[i - 1] && ys[i] < ys[i + 1]
            if (!turn) continue
            runCatching {
                BrentOptimizer(1e-10, 1e-14).optimize(
                    MaxEval(MAX_EVALUATIONS), UnivariateObjectiveFunction(f),
                    if (max) GoalType.MAXIMIZE else GoalType.MINIMIZE, SearchInterval(xs[i - 1], xs[i + 1])
                ).point
            }.getOrNull()?.let { out += it }
        }
        return out
    }

    private companion object {
        val UNREADABLE = com.yaz.calculator.engine.programs.Words("%s ne se lit pas.", "%s does not read.")
        const val SAMPLES = 600
        const val MAX_SOURCE = 20_000
        const val MAX_LINES = 500
        const val MAX_EVALUATIONS = 100_000
    }
}
