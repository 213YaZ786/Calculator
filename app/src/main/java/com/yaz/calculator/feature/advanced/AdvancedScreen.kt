package com.yaz.calculator.feature.advanced

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.data.advanced.Window
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.feature.calc.CalcViewModel
import com.yaz.calculator.feature.calc.Feel
import com.yaz.calculator.feature.calc.KeyDef
import com.yaz.calculator.feature.calc.KeyGrid
import com.yaz.calculator.feature.calc.KeyKind
import com.yaz.calculator.feature.calc.ProgrammerDisplay
import com.yaz.calculator.feature.calc.feelIt
import com.yaz.calculator.feature.calc.programmerBand
import com.yaz.calculator.feature.calc.programmerKeys
import com.yaz.calculator.feature.calc.showIn
import com.yaz.calculator.ui.component.QuietButton
import com.yaz.calculator.ui.component.ZoneAlertDialog
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.awaitCancellation

/** The gold of the advanced mode's rim and keys, as the user asked. */
val Gold = Color(0xFFD4AF37)

private val PAGE_ORDER = listOf(Page.CALC, Page.FUNCTIONS, Page.GRAPH, Page.TABLE, Page.STATS, Page.BASES, Page.PROGRAMS)

private enum class Menu { NONE, MATH, CALC, ZOOM, WINDOW, TABLE }

/**
 * The advanced mode: a display with its pages (Calc, f(x), Graph, Table,
 * HEX, as NumWorks' grapher) over the TI-82's keys. [calcDisplay] is the
 * calculator's own display for the Calc page; [onQuit] goes back.
 */
@Composable
fun AdvancedScreen(
    calc: CalcViewModel,
    adv: AdvancedViewModel,
    style: NumberStyle,
    wide: Boolean,
    keysHeight: Dp,
    calcDisplay: @Composable (Modifier) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(Menu.NONE) }
    // On the graph and the table the keys fold to their gold row: the screen takes the room, as the TI's GRAPH.
    // Writing a program takes the phone's keyboard: the keys fold away entirely then.
    val folded = !wide && (adv.page == Page.GRAPH || adv.page == Page.TABLE || adv.page == Page.PROGRAMS && adv.running == null && (adv.program == null || adv.showingScene))
    val hidden = adv.page == Page.PROGRAMS && adv.editing != null
    // A program reading a function of x keeps the sin row, as the f(x) page does.
    val wantsFunctions = adv.page == Page.PROGRAMS && adv.running == null && adv.program?.fields?.any { it.kind == com.yaz.calculator.engine.programs.FieldKind.FUNCTION } == true
    val keys: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            KeyGrid(listOf(goldRow(adv, onQuit) { menu = it }), Modifier.fillMaxWidth().height(keysHeight * 0.13f), gap = 8.dp)
            if (folded) return@Column
            Spacer(Modifier.height(8.dp))
            if (adv.page == Page.BASES) {
                KeyGrid(programmerBand(calc), Modifier.fillMaxWidth().height(keysHeight * 0.13f), gap = 8.dp, firstIndex = 5)
                Spacer(Modifier.height(8.dp))
                KeyGrid(programmerKeys(calc), Modifier.fillMaxWidth().weight(1f), gap = 8.dp, firstIndex = 11)
            } else {
                // A program takes numbers: no sin row, the room goes to its fields.
                if (adv.page != Page.PROGRAMS && adv.page != Page.STATS || wantsFunctions) {
                    KeyGrid(listOf(functionRow(calc, adv)), Modifier.fillMaxWidth().height(keysHeight * 0.13f), gap = 8.dp, firstIndex = 5)
                    Spacer(Modifier.height(8.dp))
                }
                KeyGrid(mainKeys(calc, adv, style), Modifier.fillMaxWidth().weight(1f), gap = 8.dp, firstIndex = 10)
            }
        }
    }
    if (wide) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
            // The keys as wide as round keys need (seven rows of five), the screen the rest.
            // A phone on its side is too low for round keys: flat ones, on 45 % of the width.
            val keysWidth = if (maxHeight < 400.dp) maxWidth * 0.45f else minOf(maxHeight * 5f / 7.2f, maxWidth * 0.5f)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Screen(calc, adv, style, calcDisplay, { menu = it }, Modifier.weight(1f).fillMaxHeight())
                if (!hidden) keys(Modifier.width(keysWidth).fillMaxHeight())
            }
        }
    } else {
        Column(modifier) {
            Screen(calc, adv, style, calcDisplay, { menu = it }, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            val target = when {
                folded -> keysHeight * 0.13f
                adv.page == Page.PROGRAMS || adv.page == Page.STATS -> keysHeight * (if (wantsFunctions) 0.92f else 0.78f)
                else -> keysHeight
            }
            val height by animateDpAsState(target, spring(dampingRatio = 0.8f, stiffness = 300f), label = "keys")
            if (!hidden) keys(Modifier.fillMaxWidth().height(height))
        }
    }

    when (menu) {
        Menu.MATH -> MathMenu(onDismiss = { menu = Menu.NONE }) { piece ->
            menu = Menu.NONE
            feelIt(haptics, key(calc, adv) { it.piece(piece) })
        }
        Menu.CALC -> ListMenu(
            "Calculate",
            listOf(
                "Zeros" to CalcKind.ZEROS, "Minimum" to CalcKind.MINIMUM, "Maximum" to CalcKind.MAXIMUM,
                "Intersection" to CalcKind.INTERSECTION, "dy/dx" to CalcKind.DERIVATIVE, "∫ f(x)dx" to CalcKind.INTEGRAL
            ).map { (label, kind) -> label to { adv.page = Page.GRAPH; feelIt(haptics, adv.calc(kind)) } },
            onDismiss = { menu = Menu.NONE }
        )
        Menu.ZOOM -> ListMenu(
            "Zoom",
            listOf(
                "Standard" to { adv.setWindow(Window()) },
                "Trig" to { adv.setWindow(if (adv.degrees) Window(-352.5, 352.5, -4.0, 4.0) else Window(-6.152, 6.152, -4.0, 4.0)) },
                "Decimal" to { adv.setWindow(Window(-4.7, 4.7, -3.1, 3.1)) },
                "Square" to { adv.squareWindow() },
                "Fit" to { adv.fitWindow() },
                "Zoom in" to { adv.zoom(0.5) },
                "Zoom out" to { adv.zoom(2.0) },
                "Window…" to { menu = Menu.WINDOW }
            ).map { (label, action) -> label to { adv.page = Page.GRAPH; haptics.tick(); action() } },
            onDismiss = { menu = Menu.NONE }
        )
        Menu.WINDOW -> WindowDialog(adv.data.value.window, style, onDismiss = { menu = Menu.NONE }) { w ->
            menu = Menu.NONE
            adv.setWindow(w)
            adv.page = Page.GRAPH
            haptics.done()
        }
        Menu.TABLE -> TableDialog(adv.data.value.tableStart, adv.data.value.tableStep, style, onDismiss = { menu = Menu.NONE }) { start, step ->
            menu = Menu.NONE
            adv.setTable(start, step)
            haptics.done()
        }
        Menu.NONE -> Unit
    }
}

// ---------- the display ----------

@Composable
private fun Screen(
    calc: CalcViewModel,
    adv: AdvancedViewModel,
    style: NumberStyle,
    calcDisplay: @Composable (Modifier) -> Unit,
    openMenu: (Menu) -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    ZoneSurface(
        shape = shape,
        modifier = modifier.border(BorderStroke(1.5.dp, Brush.linearGradient(listOf(Gold.copy(alpha = 0.9f), Gold.copy(alpha = 0.25f), Gold.copy(alpha = 0.8f)))), shape)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PageTabs(adv)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(adv.page, transitionSpec = {
                // The old page goes before the new one slides in from the side of its tab: never two pages over each other.
                val forward = PAGE_ORDER.indexOf(targetState) > PAGE_ORDER.indexOf(initialState)
                ((slideInHorizontally(tween(260, delayMillis = 90)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(220, delayMillis = 90))) togetherWith fadeOut(tween(90))).using(null)
            }, label = "page", modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) { page ->
                when (page) {
                    Page.CALC -> calcDisplay(Modifier.fillMaxSize())
                    Page.FUNCTIONS -> Functions(adv, style)
                    Page.GRAPH -> GraphPage(adv, style)
                    Page.TABLE -> TablePage(adv, style) { openMenu(Menu.TABLE) }
                    Page.BASES -> ProgrammerDisplay(calc, Modifier.fillMaxSize(), chip = false)
                    Page.PROGRAMS -> ProgramsPage(adv, style)
                    Page.STATS -> StatsPage(adv, style)
                }
            }
        }
    }
}

@Composable
private fun PageTabs(adv: AdvancedViewModel) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Page.CALC to "Calc", Page.FUNCTIONS to "f(x)", Page.GRAPH to "Graph", Page.TABLE to "Table", Page.STATS to "Stat", Page.BASES to "HEX").forEach { (page, label) ->
            val on = adv.page == page
            val fill by animateColorAsState(if (on) Gold.copy(alpha = 0.32f) else Color.Transparent, label = "tab")
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(fill)
                    .semantics { role = Role.Tab; selected = on }
                    .clickable { haptics.tick(); adv.page = page }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) colors.onSurface else colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Functions(adv: AdvancedViewModel, style: NumberStyle) {
    val palette = curveColors(MaterialTheme.colorScheme.primary)
    val data by adv.data.collectAsState()
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        adv.fields.forEachIndexed { i, field ->
            val on = data.functions.getOrNull(i)?.on ?: true
            val chosen = adv.selected == i
            val focus = remember { FocusRequester() }
            LaunchedEffect(chosen) { if (chosen) runCatching { focus.requestFocus() } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (chosen) Modifier.border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { haptics.tick(); adv.select(i) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The dot draws or hides the curve, as the TI's "=" sign.
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = if (on) "Hide Y${i + 1}" else "Draw Y${i + 1}") { feelIt(haptics, adv.toggle(i)) },
                    contentAlignment = Alignment.Center
                ) {
                    val color = palette[i % palette.size]
                    Canvas(Modifier.size(14.dp)) { if (on) drawCircle(color) else drawCircle(color, style = Stroke(2.dp.toPx())) }
                }
                Text("Y${i + 1} =", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                val output = remember(style) { OutputTransformation { showIn(this, style) { "" } } }
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    BasicTextField(
                        state = field,
                        outputTransformation = output,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(if (chosen) Gold else Color.Transparent),
                        modifier = Modifier.weight(1f).focusRequester(focus)
                            .onFocusChanged { if (it.isFocused && adv.selected != i) adv.select(i) }
                            .semantics { contentDescription = "Y${i + 1}" }
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphPage(adv: AdvancedViewModel, style: NumberStyle) {
    val palette = curveColors(MaterialTheme.colorScheme.primary)
    val data by adv.data.collectAsState()
    val haptics = rememberHaptics()
    val angle by adv.degreesFlow.collectAsState(adv.degrees)
    val curves = remember(data.functions, angle) { adv.curves() }
    val drawn = curves.withIndex().mapNotNull { (i, c) -> c?.let { it to palette[i % palette.size] } }
    Column(Modifier.fillMaxSize()) {
        // Which curve the CALC menu works on: the TI asks, here a tap chooses.
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            curves.forEachIndexed { i, c ->
                if (c == null) return@forEachIndexed
                val chosen = adv.selected == i
                Row(
                    Modifier.clip(CircleShape).then(if (chosen) Modifier.border(1.dp, Gold, CircleShape) else Modifier)
                        .clickable { haptics.tick(); adv.select(i); adv.clearResults() }.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(palette[i % palette.size]) }
                    Spacer(Modifier.width(6.dp))
                    Text("Y${i + 1}", style = MaterialTheme.typography.labelLarge)
                }
            }
            // No curve yet: the way to write one, in the row of curves.
            if (drawn.isEmpty()) Row(
                Modifier.clip(CircleShape).border(1.dp, Gold, CircleShape)
                    .clickable { haptics.tick(); adv.page = Page.FUNCTIONS }.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(AppIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Write a function", style = MaterialTheme.typography.labelLarge)
            }
            val degrees by adv.degreesFlow.collectAsState(adv.degrees)
            Text(
                if (degrees) "DEG" else "RAD",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .semantics { contentDescription = if (degrees) "Degrees, switch to radians" else "Radians, switch to degrees" }
                    .clickable { feelIt(haptics, adv.toggleDegrees()) }.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        val prompt = when {
            adv.pickingIntegral && adv.picking == null -> "Touch the lower bound"
            adv.pickingIntegral -> "Touch the upper bound"
            else -> null
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp))
                .onSizeChanged { if (it.width > 0) adv.plotAspect = it.height.toDouble() / it.width }
        ) {
            Plotter(
                drawn, data.window, adv::setWindow, style, Modifier.fillMaxSize(),
                marks = adv.marks.map { it to palette[it.curve % palette.size] },
                shade = adv.shade?.let { s -> curves.getOrNull(s.curve)?.let { s to it } },
                shadeColor = palette[(adv.shade?.curve ?: 0) % palette.size],
                traced = adv.traced,
                bound = adv.picking,
                points = if (!adv.twoVar) emptyList() else adv.data().let { (xs, ys) -> xs.indices.mapNotNull { i -> ys[i]?.let { xs[i] to it } } },
                onTouch = { x -> feelIt(haptics, adv.touch(x)) }
            )
        }
        val results = buildList {
            prompt?.let { add(it) }
            adv.marks.forEach { m ->
                add(
                    when (m.kind) {
                        CalcKind.ZEROS -> "Zero  x = ${number(m.x, style, 6)}"
                        CalcKind.MINIMUM -> "Min  x = ${number(m.x, style, 6)}   y = ${number(m.y, style, 6)}"
                        CalcKind.MAXIMUM -> "Max  x = ${number(m.x, style, 6)}   y = ${number(m.y, style, 6)}"
                        CalcKind.INTERSECTION -> "Y${adv.selected + 1} ∩ Y${m.curve + 1}  x = ${number(m.x, style, 6)}   y = ${number(m.y, style, 6)}"
                        CalcKind.DERIVATIVE -> "dy/dx = ${number(m.value, style, 6)} at x = ${number(m.x, style, 6)}"
                        CalcKind.INTEGRAL -> ""
                    }
                )
            }
            adv.shade?.let { add("∫ from ${number(it.from, style, 4)} to ${number(it.to, style, 4)} = ${number(it.value, style, 6)}") }
        }
        if (results.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                results.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun TablePage(adv: AdvancedViewModel, style: NumberStyle, onSettings: () -> Unit) {
    val palette = curveColors(MaterialTheme.colorScheme.primary)
    val data by adv.data.collectAsState()
    val angle by adv.degreesFlow.collectAsState(adv.degrees)
    val curves = remember(data.functions, angle) { adv.curves() }
    val shown = curves.withIndex().filter { it.value != null }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Start ${number(data.tableStart, style, 6)}   Step ${number(data.tableStep, style, 6)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            QuietButton(onClick = { haptics.tick(); onSettings() }) { Text("Set") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("X", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            shown.forEach { (i, _) ->
                Text("Y${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette[i % palette.size], textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Y= is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(TABLE_ROWS) { row ->
                val x = data.tableStart + data.tableStep * row
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(number(x, style, 6), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    shown.forEach { (_, c) ->
                        Text(number(c!!(x), style, 6), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                }
            }
        }
    }
}

private const val TABLE_ROWS = 1000

// ---------- the keys ----------

/** The keys type into the calculator on the Calc page, else into the chosen function. */
private fun type(calc: CalcViewModel, adv: AdvancedViewModel, onCalc: (CalcViewModel) -> Feel = { Feel.REJECT }, onFunction: (com.yaz.calculator.feature.calc.Typing) -> Feel): Feel {
    if (adv.page == Page.CALC) return onCalc(calc)
    // A program's fields, as the TI's Prompt; the list itself takes no typing.
    if (adv.page == Page.PROGRAMS && adv.running != null) return if (adv.asking != null) onFunction(adv.answerTyping) else Feel.REJECT
    if (adv.page == Page.PROGRAMS) return adv.programTypings.getOrNull(adv.programField)?.let(onFunction) ?: Feel.REJECT
    if (adv.page == Page.STATS) return onFunction(adv.cell()).also { adv.statsChanged() }
    if (adv.page == Page.GRAPH || adv.page == Page.TABLE || adv.page == Page.BASES) adv.page = Page.FUNCTIONS
    return onFunction(adv.typings[adv.selected])
}

/** The same piece typed on either target. */
private fun key(calc: CalcViewModel, adv: AdvancedViewModel, both: (Typer) -> Feel): Feel =
    type(calc, adv, { both(Typer.OfCalc(it)) }) { both(Typer.OfFunction(it)) }

/** What a key types into: the calculator or a function. */
private sealed interface Typer {
    fun piece(s: String): Feel
    fun digit(c: Char): Feel
    fun operator(c: Char): Feel
    fun suffix(c: Char): Feel
    fun parenthesis(): Feel
    fun point(): Feel
    fun backspace(): Feel
    fun clear(): Feel

    class OfCalc(val vm: CalcViewModel) : Typer {
        override fun piece(s: String) = vm.piece(s)
        override fun digit(c: Char) = vm.digit(c)
        override fun operator(c: Char) = vm.operator(c)
        override fun suffix(c: Char) = vm.suffix(c)
        override fun parenthesis() = vm.parenthesis()
        override fun point() = vm.point()
        override fun backspace() = vm.backspace()
        override fun clear() = vm.clear()
    }

    class OfFunction(val t: com.yaz.calculator.feature.calc.Typing) : Typer {
        override fun piece(s: String) = t.piece(s)
        override fun digit(c: Char) = t.digit(c)
        override fun operator(c: Char) = t.operator(c)
        override fun suffix(c: Char) = t.suffix(c)
        override fun parenthesis() = t.parenthesis()
        override fun point() = t.point()
        override fun backspace() = t.backspace()
        override fun clear(): Feel { t.clear(); return Feel.DONE }
    }
}

private fun goldRow(adv: AdvancedViewModel, onQuit: () -> Unit, open: (Menu) -> Unit): List<KeyDef?> = listOf(
    KeyDef("MATH", KeyKind.GOLD, "Math functions", dim = adv.page == Page.BASES) { if (adv.page == Page.BASES) Feel.REJECT else { open(Menu.MATH); Feel.TICK } },
    KeyDef("CALC", KeyKind.GOLD, "Calculate on the graph", dim = adv.page == Page.BASES) { if (adv.page == Page.BASES) Feel.REJECT else { open(Menu.CALC); Feel.TICK } },
    KeyDef("ZOOM", KeyKind.GOLD, "Zoom", dim = adv.page == Page.BASES) { if (adv.page == Page.BASES) Feel.REJECT else { open(Menu.ZOOM); Feel.TICK } },
    KeyDef("PRGM", KeyKind.GOLD, "Programs") { adv.page = Page.PROGRAMS; adv.open(null); Feel.TICK },
    KeyDef(null, KeyKind.GOLD, "Back to the calculator", icon = AppIcons.Close) { onQuit(); Feel.DONE }
)

private fun functionRow(calc: CalcViewModel, adv: AdvancedViewModel): List<KeyDef?> =
    listOf("sin", "cos", "tan", "ln", "log").map { f ->
        KeyDef(f, KeyKind.FUNCTION, f) { key(calc, adv) { it.piece("$f(") } }
    }

private fun mainKeys(calc: CalcViewModel, adv: AdvancedViewModel, style: NumberStyle): List<List<KeyDef?>> {
    fun d(c: Char) = KeyDef(c.toString(), KeyKind.DIGIT, c.toString()) { key(calc, adv) { it.digit(c) } }
    fun op(c: Char, name: String) = KeyDef(c.toString(), KeyKind.OPERATOR, name) { key(calc, adv) { it.operator(c) } }
    val onCalc = adv.page == Page.CALC
    return listOf(
        listOf(
            // x in a function, or in nDeriv, fnInt, Σ, fMin, fMax on the Calc page.
            KeyDef("x", KeyKind.FUNCTION, "x") { key(calc, adv) { it.piece("x") } },
            KeyDef("( )", KeyKind.FUNCTION, "Parenthesis") { key(calc, adv) { it.parenthesis() } },
            KeyDef(if (style.point == ',') ";" else ",", KeyKind.FUNCTION, "Next argument") { key(calc, adv) { it.piece(Sym.ARG.toString()) } },
            KeyDef(null, KeyKind.BACK, "Delete", icon = AppIcons.Backspace) { key(calc, adv) { it.backspace() } },
            op(Sym.DIVIDE, "Divide")
        ),
        listOf(KeyDef("^", KeyKind.FUNCTION, "Power") { key(calc, adv) { it.operator(Sym.POWER) } }, d('7'), d('8'), d('9'), op(Sym.TIMES, "Multiply")),
        listOf(KeyDef("√", KeyKind.FUNCTION, "Square root") { key(calc, adv) { it.piece("√") } }, d('4'), d('5'), d('6'), op(Sym.MINUS, "Minus")),
        listOf(KeyDef("x²", KeyKind.FUNCTION, "Square") { key(calc, adv) { it.suffix(Sym.SQUARE) } }, d('1'), d('2'), d('3'), op(Sym.PLUS, "Plus")),
        listOf(
            KeyDef("π", KeyKind.FUNCTION, "Pi") { key(calc, adv) { it.piece("π") } },
            d('0'),
            KeyDef(style.point.toString(), KeyKind.DIGIT, "Point") { key(calc, adv) { it.point() } },
            KeyDef("AC", KeyKind.CLEAR, "Clear") { key(calc, adv) { it.clear() } },
            KeyDef(if (onCalc || adv.page == Page.PROGRAMS) "=" else null, KeyKind.EQUALS, if (onCalc) "Equals" else if (adv.page == Page.PROGRAMS) "Run the program" else if (adv.page == Page.STATS) "Next row" else "Next function", icon = if (onCalc || adv.page == Page.PROGRAMS) null else AppIcons.KeyboardReturn) {
                when (adv.page) {
                    Page.CALC -> calc.equals()
                    Page.PROGRAMS -> if (adv.running != null) adv.submit() else adv.runProgram()
                    Page.STATS -> adv.nextCell()
                    Page.FUNCTIONS -> adv.next()
                    else -> { adv.page = Page.FUNCTIONS; Feel.TICK }
                }
            }
        )
    )
}

// ---------- the menus ----------

/** The TI-82's MATH key: its MATH, NUM, PROB menus and the trigonometric ones, each item typed where the cursor is. */
@Composable
private fun MathMenu(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "MATH" to listOf(
            "∛(x)" to "∛(", "root(x, n)" to "root(", "logBASE(x, b)" to "logBASE(", "eˣ" to "exp(", "x!" to "!",
            "nDeriv(f, x, a)" to "nDeriv(", "fnInt(f, x, a, b)" to "fnInt(", "Σ(f, x, a, b)" to "Σ(",
            "fMin(f, x, a, b)" to "fMin(", "fMax(f, x, a, b)" to "fMax("
        ),
        "NUM" to listOf(
            "abs(x)" to "abs(", "round(x, n)" to "round(", "iPart(x)" to "iPart(", "fPart(x)" to "fPart(", "int(x)" to "int(",
            "min(a, b, …)" to "min(", "max(a, b, …)" to "max(", "gcd(a, b)" to "gcd(", "lcm(a, b)" to "lcm(", "remainder(a, b)" to "remainder("
        ),
        "PROB" to listOf("nPr(n, r)" to "nPr(", "nCr(n, r)" to "nCr(", "rand()" to "rand()", "randInt(a, b)" to "randInt(", "x!" to "!"),
        "TRIG" to listOf(
            "sin⁻¹" to "sin⁻¹(", "cos⁻¹" to "cos⁻¹(", "tan⁻¹" to "tan⁻¹(", "sinh" to "sinh(", "cosh" to "cosh(", "tanh" to "tanh(",
            "sinh⁻¹" to "sinh⁻¹(", "cosh⁻¹" to "cosh⁻¹(", "tanh⁻¹" to "tanh⁻¹("
        )
    )
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { QuietButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, (name, _) ->
                    val on = tab == i
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).then(if (on) Modifier.border(1.dp, Gold, CircleShape) else Modifier)
                            .clickable { tab = i }.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tabs[tab].second.forEach { (label, piece) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(piece) }.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun ListMenu(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { QuietButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { (label, action) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onDismiss(); action() }.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun WindowDialog(window: Window, style: NumberStyle, onDismiss: () -> Unit, onDone: (Window) -> Unit) {
    val fields = listOf("Xmin" to window.xMin, "Xmax" to window.xMax, "Ymin" to window.yMin, "Ymax" to window.yMax)
        .map { (name, v) -> name to rememberTextFieldStateOf(number(v, style, 6).replace('−', '-')) }
    NumbersDialog("Window", fields, style, onDismiss) { v ->
        if (v[0] < v[1] && v[2] < v[3]) { onDone(Window(v[0], v[1], v[2], v[3])); true } else false
    }
}

@Composable
private fun TableDialog(start: Double, step: Double, style: NumberStyle, onDismiss: () -> Unit, onDone: (Double, Double) -> Unit) {
    val fields = listOf("Start" to start, "Step" to step).map { (name, v) -> name to rememberTextFieldStateOf(number(v, style, 6).replace('−', '-')) }
    NumbersDialog("Table", fields, style, onDismiss) { v -> if (v[1] != 0.0) { onDone(v[0], v[1]); true } else false }
}

@Composable
private fun rememberTextFieldStateOf(text: String) = rememberTextFieldState(text)

/** A few numbers typed with the phone's number keyboard; [onDone] says whether they go together. */
@Composable
private fun NumbersDialog(
    title: String,
    fields: List<Pair<String, androidx.compose.foundation.text.input.TextFieldState>>,
    style: NumberStyle,
    onDismiss: () -> Unit,
    onDone: (List<Double>) -> Boolean
) {
    var wrong by remember { mutableStateOf(false) }
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            QuietButton(onClick = {
                val values = fields.map { (_, s) -> style.read(s.text.toString()).replace(Sym.MINUS, '-').toDoubleOrNull() }
                wrong = values.any { it == null || !it.isFinite() } || !onDone(values.map { it!! })
            }) { Text("OK") }
        },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { (name, state) ->
                    OutlinedTextField(
                        state = state,
                        label = { Text(name) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = wrong,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}
