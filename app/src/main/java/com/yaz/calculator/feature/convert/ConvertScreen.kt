package com.yaz.calculator.feature.convert

import com.yaz.calculator.ui.component.sink
import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.yaz.calculator.feature.calc.fitSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import com.yaz.calculator.feature.advanced.balancedPerRow
import com.yaz.calculator.feature.advanced.measuredWidth
import com.yaz.calculator.feature.advanced.rememberGroupFontSize
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaz.calculator.data.rates.RatesState
import com.yaz.calculator.engine.Category
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.feature.calc.KeyDef
import com.yaz.calculator.feature.calc.KeyGrid
import com.yaz.calculator.feature.calc.KeyKind
import com.yaz.calculator.feature.calc.cleanIn
import com.yaz.calculator.feature.calc.feelIt
import com.yaz.calculator.feature.calc.showIn
import com.yaz.calculator.ui.component.BoldButton
import com.yaz.calculator.ui.component.DialogPane
import com.yaz.calculator.ui.component.LoadingMark
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** The amount and its result: the largest size that fits one line, and the smallest a number is read at. */
private val AMOUNT_MAX = 40.sp
private val AMOUNT_MIN = 24.sp

/**
 * The converter: the category on top, the amount and its unit, the result
 * and its unit, a swap between them, and the calculator's keys below.
 */
@Composable
fun ConvertScreen(vm: ConvertViewModel, style: NumberStyle, padding: PaddingValues) {
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var categories by remember { mutableStateOf(false) }
    LaunchedEffect(vm.category) { if (vm.category == Category.CURRENCY) vm.rates.refreshIfOld() }

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        val wide = maxWidth > maxHeight * 1.1f
        val maxW = maxWidth
        val maxH = maxHeight
        val keys = keys(vm, style)
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    Sides(vm, style, onCategories = { categories = true }, onPick = { picking = it }, compact = maxH < 480.dp)
                }
                KeyGrid(keys, Modifier.weight(1f).fillMaxHeight().padding(bottom = 12.dp), gap = 8.dp)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().widthIn(max = 640.dp).align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.Center) {
                    Sides(vm, style, onCategories = { categories = true }, onPick = { picking = it })
                }
                KeyGrid(keys, Modifier.fillMaxWidth().height(minOf(maxH * 0.48f, maxW * 1.2f)).padding(bottom = 12.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (categories) CategoryDialog(vm.category, onPick = { vm.choose(it); categories = false }, onDismiss = { categories = false })
    picking?.let { fromSide ->
        UnitDialog(
            title = if (fromSide) "Convert from" else "Convert to",
            choices = vm.choices(),
            selected = if (fromSide) vm.from else vm.to,
            onPick = { id ->
                if (fromSide) vm.chooseFrom(id) else vm.chooseTo(id)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sides(vm: ConvertViewModel, style: NumberStyle, onCategories: () -> Unit, onPick: (Boolean) -> Unit, compact: Boolean = false) {
    // A phone on its side has little height: tighter rows, and the column scrolls if it still does not fit.
    val numberMin = if (compact) 40.dp else 56.dp
    val haptics = rememberHaptics()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val turn = remember { Animatable(0f) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The category, a pill that opens every category.
        ZoneSurface(shape = CircleShape, accent = true, onClick = { haptics.tick(); onCategories() }, modifier = Modifier.heightIn(min = 48.dp).sink()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(iconOf(vm.category), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(nameOf(vm.category), style = MaterialTheme.typography.titleMedium)
                Icon(AppIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
        Box(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
                // From: the amount, typed with the keys below.
                Side(vm.choice(vm.from), onUnit = { haptics.tick(); onPick(true) }, compact = compact) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // The digits a line holds, counted at the smallest size a number takes here (grouping marks need a little room).
                        val zero = measurer.measure("0", TextStyle(fontSize = AMOUNT_MIN)).size.width
                        val chars = with(density) { (maxWidth.toPx() * 0.88f / zero).toInt() } - 1
                        LaunchedEffect(chars) { vm.measured(chars) }
                        // One line, the amount shrinking to fit, then moving with the cursor (never wrapped under the swap).
                        val size = fitSize(measurer, style.show(vm.input.text.toString()), constraints.maxWidth, AMOUNT_MAX, AMOUNT_MIN)
                        InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                            BasicTextField(
                                state = vm.input,
                                inputTransformation = remember(style) { InputTransformation { cleanIn(this, style) } },
                                outputTransformation = remember(style) { OutputTransformation { showIn(this, style) { "" } } },
                                lineLimits = TextFieldLineLimits.SingleLine,
                                textStyle = TextStyle(fontSize = size, textAlign = TextAlign.End, color = colors.onSurface, fontWeight = FontWeight.Light),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = numberMin)
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .semantics { contentDescription = "Amount" }
                                    .onPreviewKeyEvent { e ->
                                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { vm.equals(); true } else false
                                    }
                            )
                        }
                    }
                }
                // To: the result, a tap copies it.
                Side(vm.choice(vm.to), onUnit = { haptics.tick(); onPick(false) }, compact = compact) {
                    val out = vm.output
                    // Copied: the number pops, as a key does.
                    val pop = remember { Animatable(0f) }
                    OneLine(
                        out?.let { style.show(it) } ?: "",
                        max = AMOUNT_MAX,
                        min = AMOUNT_MIN,
                        weight = FontWeight.Normal,
                        color = colors.primary,
                        modifier = Modifier
                            .heightIn(min = numberMin)
                            .graphicsLayer {
                                val k = 1f + 0.08f * pop.value
                                scaleX = k
                                scaleY = k
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            }
                            .clickable(enabled = out != null, onClickLabel = "Copy") {
                                haptics.done()
                                scope.launch {
                                    pop.snapTo(1f)
                                    pop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                                }
                                val text = style.show(out!!.removeSuffix("…"))
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                            }
                    )
                    // The exact fraction under the decimals, when the result is a small one (NumWorks' exact beside approximate).
                    vm.fraction?.takeIf { out != null }?.let { exact ->
                        OneLine("= " + style.show(exact), max = 22.sp, min = 14.sp, weight = FontWeight.Normal, color = colors.onSurfaceVariant)
                    }
                }
            }
            // Between the two, the swap, turning half a turn each time.
            ZoneSurface(
                shape = CircleShape,
                accent = true,
                onClick = {
                    feelIt(haptics, vm.swap())
                    scope.launch {
                        turn.snapTo(0f)
                        turn.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
                    }
                },
                modifier = Modifier.align(Alignment.Center).size(48.dp).sink(0.86f).semantics { contentDescription = "Swap units" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Swap, contentDescription = null, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn.value * 180f })
                }
            }
        }
        if (vm.category == Category.CURRENCY) RatesLine(vm)
    }
}

/** One side: its unit as a pill to change it, then its number. */
@Composable
private fun Side(choice: Choice?, onUnit: () -> Unit, compact: Boolean = false, number: @Composable () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 12.dp), horizontalAlignment = Alignment.End) {
            ZoneSurface(shape = CircleShape, onClick = onUnit, modifier = Modifier.heightIn(min = if (compact) 36.dp else 40.dp).sink()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Name and symbol joined with a comma, as AOSP Dialer's CallLogEntryText.java joins its parts (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
                    Text(
                        choice?.let { if (it.symbol != it.name) "${it.name}, ${it.symbol}" else it.name } ?: "Choose",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        // Wraps to a second line rather than cutting a long name (another language, 200 % font).
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                    Icon(AppIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            number()
        }
    }
}

// Source: ExactCalculator's CalculatorResult scrolls a result too long for its line, never cuts it
// (https://github.com/LineageOS/android_packages_apps_ExactCalculator/blob/lineage-22.2/app/src/main/java/com/android/calculator2/CalculatorResult.java);
// the size is the calculator's fitSize (feature/calc/Display.kt), the largest that holds the line.
/** A number on one line: the largest size from [max] to [min] that fits, scrolled sideways when even [min] does not (never cut). */
@Composable
private fun OneLine(text: String, max: TextUnit, min: TextUnit, weight: FontWeight, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val size = fitSize(measurer, text, constraints.maxWidth, max, min)
        val scroll = rememberScrollState()
        LaunchedEffect(text) { scroll.scrollTo(0) }
        Text(
            text,
            style = TextStyle(fontSize = size, textAlign = TextAlign.End, fontWeight = weight),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.horizontalScroll(scroll)
        )
    }
}

/** The date of the rates, fetching them again, or why there are none. */
@Composable
private fun RatesLine(vm: ConvertViewModel) {
    val table by vm.rates.table.collectAsState()
    val state by vm.rates.state.collectAsState()
    val haptics = rememberHaptics()
    val date = table?.date?.let { formatDate(it) }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            state == RatesState.LOADING -> {
                LoadingMark(size = 24.dp)
                Text("Fetching rates", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text(
                    when {
                        state == RatesState.FAILED && date != null -> "Offline, rates of $date"
                        state == RatesState.FAILED -> "No connection, no rates yet"
                        date != null -> "Rates of $date"
                        else -> "No rates yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state == RatesState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                ZoneSurface(shape = CircleShape, onClick = { haptics.tick(); vm.rates.refresh() }, modifier = Modifier.size(40.dp).sink(0.86f).semantics { contentDescription = "Fetch the rates again" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun formatDate(iso: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso)!!
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
}.getOrDefault(iso)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(current: Category, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
        DialogPane {
            Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Convert", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                // Tiles at least 96 dp and as wide as their longest word (TextMeasurer, measured at this font
                // scale and language), in balanced rows: FlowRow with maxItemsInEachRow and weight
                // (https://developer.android.com/develop/ui/compose/layouts/flow; SMS's PersonActions). Names wrap
                // between words; all the names are drawn at one size, the largest at which every longest word
                // fits (rememberGroupFontSize, the ladder of TextAutoSize.StepBased,
                // https://developer.android.com/develop/ui/compose/text/autosize).
                val labelStyle = MaterialTheme.typography.labelMedium
                val wordWidth = measuredWidth(Category.entries.flatMap { nameOf(it).split(' ') }, labelStyle) + 12.dp
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val available = maxWidth
                    val perRow = balancedPerRow(Category.entries.size, maxOf(96.dp, wordWidth), available, 10.dp)
                    val inRow = minOf(perRow, Category.entries.size)
                    val nameSize = rememberGroupFontSize(
                        Category.entries.map { nameOf(it) }, labelStyle,
                        maxOf(minOf(maxOf(96.dp, wordWidth), available), (available - 10.dp * (inRow - 1)) / inRow) - 12.dp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = perRow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Category.entries.forEach { c ->
                            ZoneSurface(
                                shape = RoundedCornerShape(20.dp),
                                accent = c == current,
                                onClick = { haptics.tick(); onPick(c) },
                                modifier = Modifier.weight(1f).fillMaxRowHeight().widthIn(min = minOf(maxOf(96.dp, wordWidth), available)).sink()
                            ) {
                                Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(iconOf(c), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    BasicText(
                                        nameOf(c),
                                        style = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontSize = nameSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun UnitDialog(title: String, choices: List<Choice>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val query = rememberTextFieldState()
    val haptics = rememberHaptics()
    Dialog(onDismissRequest = onDismiss) {
        DialogPane {
            Column(Modifier.padding(vertical = 20.dp).heightIn(max = 560.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp))
                if (choices.size > 12) {
                    ZoneSurface(shape = CircleShape, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.text.isEmpty()) Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BasicTextField(
                                    state = query,
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val q = query.text.toString().trim()
                val shown = if (q.isEmpty()) choices else choices.filter { it.name.contains(q, ignoreCase = true) || it.symbol.contains(q, ignoreCase = true) }
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val on = c.id == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { haptics.tick(); onPick(c.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(c.symbol, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun keys(vm: ConvertViewModel, style: NumberStyle): List<List<KeyDef?>> {
    val t = vm.typing
    fun d(c: Char) = KeyDef(c.toString(), KeyKind.DIGIT, c.toString()) { t.digit(c) }
    fun op(c: Char, name: String) = KeyDef(c.toString(), KeyKind.OPERATOR, name) { t.operator(c) }
    return listOf(
        listOf(
            KeyDef("AC", KeyKind.CLEAR, "Clear") { t.clear(); com.yaz.calculator.feature.calc.Feel.DONE },
            KeyDef("( )", KeyKind.FUNCTION, "Parenthesis") { t.parenthesis() },
            KeyDef("%", KeyKind.OPERATOR, "Percent") { t.suffix(Sym.PERCENT) },
            op(Sym.DIVIDE, "Divide")
        ),
        listOf(d('7'), d('8'), d('9'), op(Sym.TIMES, "Multiply")),
        listOf(d('4'), d('5'), d('6'), op(Sym.MINUS, "Minus")),
        listOf(d('1'), d('2'), d('3'), op(Sym.PLUS, "Plus")),
        listOf(
            d('0'),
            KeyDef(style.point.toString(), KeyKind.DIGIT, "Point") { t.point() },
            KeyDef(null, KeyKind.BACK, "Delete", icon = AppIcons.Backspace) { t.backspace() },
            KeyDef("=", KeyKind.EQUALS, "Equals") { vm.equals() }
        )
    )
}

fun nameOf(c: Category): String = when (c) {
    Category.CURRENCY -> "Currency"
    Category.LENGTH -> "Length"
    Category.AREA -> "Area"
    Category.VOLUME -> "Volume"
    Category.MASS -> "Mass"
    Category.TEMPERATURE -> "Temperature"
    Category.SPEED -> "Speed"
    Category.TIME -> "Time"
    Category.DATA -> "Data"
    Category.PRESSURE -> "Pressure"
    Category.ENERGY -> "Energy"
    Category.POWER -> "Power"
    Category.FUEL -> "Fuel economy"
    Category.ANGLE -> "Angle"
}

fun iconOf(c: Category): ImageVector = when (c) {
    Category.CURRENCY -> AppIcons.Currency
    Category.LENGTH -> AppIcons.Ruler
    Category.AREA -> AppIcons.Area
    Category.VOLUME -> AppIcons.Drink
    Category.MASS -> AppIcons.Scale
    Category.TEMPERATURE -> AppIcons.Thermostat
    Category.SPEED -> AppIcons.Speed
    Category.TIME -> AppIcons.Time
    Category.DATA -> AppIcons.Data
    Category.PRESSURE -> AppIcons.Pressure
    Category.ENERGY -> AppIcons.Bolt
    Category.POWER -> AppIcons.Bolt
    Category.FUEL -> AppIcons.Fuel
    Category.ANGLE -> AppIcons.Angle
}
