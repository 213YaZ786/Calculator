package com.yaz.calculator.feature.calc

import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.ui.component.PillItem
import com.yaz.calculator.ui.component.PillMenu
import com.yaz.calculator.ui.component.PillMotion
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.rememberPillMenu
import com.yaz.calculator.ui.icon.AppIcons
import com.yaz.calculator.ui.glass.LocalGlass
import com.yaz.calculator.ui.glass.groundHere
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import org.koin.compose.koinInject

/**
 * The calculator. A phone held upright: the display over the keys, the
 * scientific keys on a band that unfolds. Turned sideways: every key at
 * once. A large tablet: the history beside it, always there.
 */
@Composable
fun CalcScreen(
    vm: CalcViewModel,
    style: NumberStyle,
    padding: PaddingValues,
    historyOpen: Boolean,
    onHistory: (Boolean) -> Unit
) {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val ledger by vm.entries.collectAsState()
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    // AOSP Calculator's clear: a disc of the accent grows from the key over the
    // display (and the status bar), the expression cleared under it, then it fades.
    var displayRect by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var origin by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(vm.reveals) {
        if (vm.reveals == 0) return@LaunchedEffect
        fade.snapTo(1f)
        reveal.snapTo(0f)
        // Cleared as the reveal starts (AOSP's onAnimationStart): what is typed meanwhile stays.
        vm.clear()
        reveal.animateTo(1f, tween(REVEAL_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        fade.animateTo(0f, tween(FADE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        reveal.snapTo(0f)
    }
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithContent {
                drawContent()
                if (reveal.value > 0f && fade.value > 0f) {
                    val area = androidx.compose.ui.geometry.Rect(displayRect.left - origin.x, 0f, displayRect.right - origin.x, displayRect.bottom - origin.y)
                    val centre = vm.clearFrom - origin
                    val far = listOf(area.topLeft, area.topRight, area.bottomLeft, area.bottomRight).maxOf { (it - centre).getDistance() }
                    clipRect(area.left, area.top, area.right, area.bottom) {
                        drawCircle(accent.copy(alpha = fade.value), radius = far * reveal.value, center = centre)
                    }
                }
            }
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val wide = maxWidth > maxHeight * 1.1f
        val sideHistory = wide && maxWidth >= 840.dp
        val tablet = !wide && maxWidth >= 600.dp
        val sciOpen = tablet || settings.scientificOpen

        val basic = basicKeys(vm, style)
        val bottom = Modifier.padding(bottom = 12.dp)

        Row(Modifier.fillMaxSize()) {
            if (sideHistory) {
                Box(Modifier.width(maxW * 0.34f).fillMaxHeight()) {
                    HistoryList(vm, style, PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp), onTaken = {})
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 12.dp)
            ) {
                // Drawn down from the display, the history comes, as on a paper roll.
                var pulled by remember { mutableFloatStateOf(0f) }
                Display(
                    vm,
                    style,
                    memoryOn = ledger.memory != null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { displayRect = it.boundsInRoot() }
                        .pointerInput(sideHistory) {
                            if (sideHistory) return@pointerInput
                            detectVerticalDragGestures(
                                onDragEnd = { pulled = 0f },
                                onDragCancel = { pulled = 0f }
                            ) { _, dy ->
                                pulled += dy
                                if (pulled > with(density) { 72.dp.toPx() }) {
                                    pulled = Float.NEGATIVE_INFINITY
                                    haptics.firm()
                                    onHistory(true)
                                }
                            }
                        }
                ) { ResultActions(vm, style) }
                Spacer(Modifier.height(8.dp))
                if (wide) {
                    Row(Modifier.fillMaxWidth().height(maxH * 0.62f).then(bottom), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyGrid(wideScientific(vm), Modifier.weight(3f).fillMaxHeight(), gap = 8.dp)
                        KeyGrid(basic, Modifier.weight(4f).fillMaxHeight(), gap = 8.dp, firstIndex = 15)
                    }
                } else {
                    // Unfolded, the scientific band takes room from the keys, not from the display.
                    val keyHeight = minOf(maxH * if (sciOpen) 0.42f else 0.50f, maxW * 1.25f)
                    val band = if (sciOpen) 3 else 1
                    Box(Modifier.fillMaxWidth().animateContentSize()) {
                        KeyGrid(
                            scientific(vm, sciOpen, showFold = !tablet) { store.update { it.copy(scientificOpen = !it.scientificOpen) } },
                            Modifier.fillMaxWidth().height(keyHeight / 5 * 0.72f * band + 8.dp * (band - 1)),
                            gap = 8.dp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    KeyGrid(basic, Modifier.fillMaxWidth().height(keyHeight).then(bottom), firstIndex = 15)
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        // On a phone or a small tablet the history comes over the calculator.
        if (!sideHistory) {
            AnimatedVisibility(
                visible = historyOpen,
                enter = androidx.compose.animation.slideInVertically { -it } + fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { -it } + fadeOut()
            ) {
                Box(Modifier.fillMaxSize().groundHere(LocalGlass.current, MaterialTheme.colorScheme.background)) {
                    HistoryList(
                        vm,
                        style,
                        PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp + with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }),
                        onTaken = { onHistory(false) }
                    )
                }
            }
        }
    }
}

/** Under a result: copy it, share it, keep it in the memory. */
@Composable
private fun ResultActions(vm: CalcViewModel, style: NumberStyle) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val menu = rememberPillMenu()
    val ledger by vm.entries.collectAsState()
    val result = if (vm.evaluated) vm.resultText() else null
    AnimatedVisibility(visible = result != null || ledger.memory != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        Row(Modifier.padding(top = 4.dp, bottom = 4.dp).then(menu.tracker), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result != null) {
                val text = style.show(result)
                var copied by remember { mutableStateOf(false) }
                LaunchedEffect(copied) {
                    if (copied) {
                        delay(1_200)
                        copied = false
                    }
                }
                ResultAction(if (copied) AppIcons.CheckCircle else AppIcons.Copy, if (copied) "Copied" else "Copy") {
                    haptics.done()
                    copied = true
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                }
                ResultAction(AppIcons.Share, "Share") {
                    haptics.tick()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, style.show(vm.spelled(vm.previous.orEmpty())) + " = " + text)
                    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            if (vm.unfoldable) ResultAction(AppIcons.ExpandMore, "More digits") { if (vm.unfold()) haptics.tick() }
            ResultAction(null, "Memory", sign = "M") { menu.open() }
            PillMenu(
                menu,
                buildList {
                    if (result != null) add(PillItem(AppIcons.Memory, "Keep in memory", PillMotion.DROP) { feelIt(haptics, vm.memoryStore()) })
                    if (result != null) add(PillItem(AppIcons.Add, "Add to memory", PillMotion.BOUNCE) { feelIt(haptics, vm.memoryAdd()) })
                    if (result != null) add(PillItem(AppIcons.Undo, "Subtract from memory", PillMotion.WIGGLE) { feelIt(haptics, vm.memorySubtract()) })
                    if (ledger.memory != null) add(PillItem(AppIcons.Calculate, "Use the memory", PillMotion.DROP) { feelIt(haptics, vm.memoryRecall()) })
                    if (ledger.memory != null) add(PillItem(AppIcons.Delete, "Clear the memory", PillMotion.WIGGLE) { feelIt(haptics, vm.memoryClear()) })
                },
                columns = 2
            )
        }
    }
}

private fun basicKeys(vm: CalcViewModel, style: NumberStyle): List<List<KeyDef?>> {
    fun d(c: Char) = KeyDef(c.toString(), KeyKind.DIGIT, c.toString()) { vm.digit(c) }
    fun op(c: Char, name: String) = KeyDef(c.toString(), KeyKind.OPERATOR, name) { vm.operator(c) }
    return listOf(
        listOf(
            KeyDef("AC", KeyKind.CLEAR, "Clear", pressAt = { vm.clearWithReveal(it) }),
            KeyDef("( )", KeyKind.FUNCTION, "Parenthesis") { vm.parenthesis() },
            KeyDef("%", KeyKind.OPERATOR, "Percent") { vm.suffix(Sym.PERCENT) },
            op(Sym.DIVIDE, "Divide")
        ),
        listOf(d('7'), d('8'), d('9'), op(Sym.TIMES, "Multiply")),
        listOf(d('4'), d('5'), d('6'), op(Sym.MINUS, "Minus")),
        listOf(d('1'), d('2'), d('3'), op(Sym.PLUS, "Plus")),
        listOf(
            d('0'),
            KeyDef(style.point.toString(), KeyKind.DIGIT, "Point") { vm.point() },
            KeyDef(null, KeyKind.BACK, "Delete, hold to clear", icon = AppIcons.Backspace, longPress = { vm.clearWithReveal(it) }) { vm.backspace() },
            KeyDef("=", KeyKind.EQUALS, "Equals") { vm.equals() }
        )
    )
}

/** The band above the keys: one row folded, three unfolded. */
private fun scientific(vm: CalcViewModel, open: Boolean, showFold: Boolean, fold: () -> Unit): List<List<KeyDef?>> {
    val inv = vm.inverse
    val root = if (inv) KeyDef("x²", KeyKind.FUNCTION, "Square") { vm.suffix(Sym.SQUARE) } else KeyDef("√", KeyKind.FUNCTION, "Square root") { vm.piece("√") }
    val first = listOf(
        root,
        KeyDef("π", KeyKind.FUNCTION, "Pi") { vm.piece("π") },
        KeyDef("^", KeyKind.FUNCTION, "Power") { vm.operator(Sym.POWER) },
        KeyDef("!", KeyKind.FUNCTION, "Factorial") { vm.suffix(Sym.FACT) },
        if (showFold) KeyDef(null, KeyKind.TOGGLE, if (open) "Fewer functions" else "More functions", icon = if (open) AppIcons.ExpandLess else AppIcons.ExpandMore) { fold(); Feel.TICK }
        else KeyDef("e", KeyKind.FUNCTION, "Euler's number") { vm.piece("e") }
    )
    if (!open) return listOf(first)
    return listOf(
        first,
        listOf(
            KeyDef(if (vm.degrees) "DEG" else "RAD", KeyKind.TOGGLE, if (vm.degrees) "Degrees, switch to radians" else "Radians, switch to degrees") { vm.toggleDegrees(); Feel.TICK },
            fn(vm, inv, "sin", "sin⁻¹"),
            fn(vm, inv, "cos", "cos⁻¹"),
            fn(vm, inv, "tan", "tan⁻¹"),
            KeyDef("(", KeyKind.FUNCTION, "Open parenthesis") { vm.piece("(") }
        ),
        listOf(
            KeyDef("INV", KeyKind.TOGGLE, "Inverse functions", on = inv) { vm.inverse = !vm.inverse; Feel.TICK },
            if (showFold) KeyDef("e", KeyKind.FUNCTION, "Euler's number") { vm.piece("e") } else KeyDef("x²", KeyKind.FUNCTION, "Square") { vm.suffix(Sym.SQUARE) },
            if (inv) KeyDef("eˣ", KeyKind.FUNCTION, "e to the power") { vm.piece("e^") } else KeyDef("ln", KeyKind.FUNCTION, "Natural logarithm") { vm.piece("ln(") },
            if (inv) KeyDef("10ˣ", KeyKind.FUNCTION, "Ten to the power") { vm.piece("10^") } else KeyDef("log", KeyKind.FUNCTION, "Logarithm") { vm.piece("log(") },
            KeyDef(")", KeyKind.FUNCTION, "Close parenthesis") { vm.suffix(Sym.CLOSE) }
        )
    )
}

/** Every scientific key at once, beside the others, when the screen is wide. */
private fun wideScientific(vm: CalcViewModel): List<List<KeyDef?>> {
    val inv = vm.inverse
    return listOf(
        listOf(
            KeyDef("INV", KeyKind.TOGGLE, "Inverse functions", on = inv) { vm.inverse = !vm.inverse; Feel.TICK },
            KeyDef(if (vm.degrees) "DEG" else "RAD", KeyKind.TOGGLE, if (vm.degrees) "Degrees, switch to radians" else "Radians, switch to degrees") { vm.toggleDegrees(); Feel.TICK },
            KeyDef("π", KeyKind.FUNCTION, "Pi") { vm.piece("π") }
        ),
        listOf(fn(vm, inv, "sin", "sin⁻¹"), fn(vm, inv, "cos", "cos⁻¹"), fn(vm, inv, "tan", "tan⁻¹")),
        listOf(
            if (inv) KeyDef("eˣ", KeyKind.FUNCTION, "e to the power") { vm.piece("e^") } else KeyDef("ln", KeyKind.FUNCTION, "Natural logarithm") { vm.piece("ln(") },
            if (inv) KeyDef("10ˣ", KeyKind.FUNCTION, "Ten to the power") { vm.piece("10^") } else KeyDef("log", KeyKind.FUNCTION, "Logarithm") { vm.piece("log(") },
            KeyDef("e", KeyKind.FUNCTION, "Euler's number") { vm.piece("e") }
        ),
        listOf(
            if (inv) KeyDef("x²", KeyKind.FUNCTION, "Square") { vm.suffix(Sym.SQUARE) } else KeyDef("√", KeyKind.FUNCTION, "Square root") { vm.piece("√") },
            KeyDef("^", KeyKind.FUNCTION, "Power") { vm.operator(Sym.POWER) },
            KeyDef("!", KeyKind.FUNCTION, "Factorial") { vm.suffix(Sym.FACT) }
        ),
        listOf(
            KeyDef("(", KeyKind.FUNCTION, "Open parenthesis") { vm.piece("(") },
            KeyDef(")", KeyKind.FUNCTION, "Close parenthesis") { vm.suffix(Sym.CLOSE) },
            KeyDef("x²", KeyKind.FUNCTION, "Square") { vm.suffix(Sym.SQUARE) }
        )
    )
}

private fun fn(vm: CalcViewModel, inv: Boolean, name: String, inverse: String) =
    if (inv) KeyDef(inverse, KeyKind.FUNCTION, "Inverse $name") { vm.piece("$inverse(") }
    else KeyDef(name, KeyKind.FUNCTION, name) { vm.piece("$name(") }

/** AOSP Calculator's reveal: android's long and medium animation times. */
private const val REVEAL_MS = 500
private const val FADE_MS = 400
