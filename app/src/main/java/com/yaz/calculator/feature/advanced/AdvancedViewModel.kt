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
enum class Page { CALC, FUNCTIONS, GRAPH, TABLE, BASES }

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

    init {
        viewModelScope.launch {
            store.loaded.first { it }
            store.data.value.functions.forEachIndexed { i, f -> fields[i].setTextAndPlaceCursorAtEnd(f.expression) }
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
        const val SAMPLES = 600
        const val MAX_EVALUATIONS = 100_000
    }
}
