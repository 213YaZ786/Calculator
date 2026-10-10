package com.yaz.calculator.feature.sheet

import com.yaz.calculator.ui.component.sink
import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.data.sheets.SheetStore
import com.yaz.calculator.engine.Format
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.Outcome
import com.yaz.calculator.engine.SheetEval
import android.icu.text.MeasureFormat
import android.icu.util.MeasureUnit
import com.yaz.calculator.engine.Moment
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import android.icu.util.Measure as IcuMeasure
import com.yaz.calculator.data.rates.Rates
import com.yaz.calculator.engine.Measure
import com.yaz.calculator.engine.Money
import com.yaz.calculator.engine.Physical
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import com.yaz.calculator.engine.UnifiedReal
import com.yaz.calculator.engine.Written
import com.yaz.calculator.engine.compute
import com.yaz.calculator.navigation.LocalReadableInset
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.component.FloatingFrame
import com.yaz.calculator.ui.component.FloatingTop
import com.yaz.calculator.ui.component.PillItem
import com.yaz.calculator.ui.component.PillMenu
import com.yaz.calculator.ui.component.PillMotion
import com.yaz.calculator.ui.component.QuietButton
import com.yaz.calculator.ui.component.ZoneAlertDialog
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.rememberPillMenu
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** A line's result as shown: its digits in the app's form, what it counts, and whether it is a total. */
private data class Shown(val text: String, val measure: Measure?, val total: Boolean, val ready: Boolean = false)

/**
 * A sheet: lines typed freely on the left, each line's result on the right,
 * level with it, worked out as the user types. A tap on a result copies it.
 * Over the keyboard, a row of the signs a phone keyboard hides.
 */
@OptIn(FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
fun SheetScreen(id: String, style: NumberStyle, onBack: () -> Unit) {
    val store: SheetStore = koinInject()
    val settings: SettingsStore = koinInject()
    val rates: Rates = koinInject()
    val sheets by store.sheets.collectAsState()
    val sheet = sheets.firstOrNull { it.id == id }
    val state = remember(id) { TextFieldState(sheet?.text.orEmpty()) }
    var results by remember { mutableStateOf<List<Shown?>>(emptyList()) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val menu = rememberPillMenu()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(id) {
        val typed = snapshotFlow { state.text.toString() }.debounce(150).onEach { store.write(id, it) }
        // Worked out again when rates arrive for a line converting currencies.
        combine(typed, rates.table) { text, table -> text to table }.collectLatest { (text, table) ->
            val outcome = compute(SHEET_MS) {
                val eval = SheetEval(style, table?.rates)
                val values = eval.evaluate(text, settings.current.degrees)
                if (eval.needsRates) rates.refreshIfOld()
                Outcome.Value(UnifiedReal.ZERO, Written("", true, false)).also {
                    results = values.map { v ->
                        v?.let { it.moment?.let { m -> Shown(written(m), null, false, ready = true) } ?: Shown(write(it.value, it.measure), it.measure, it.total) }
                    }
                }
            }
            if (outcome is Outcome.Failed) results = emptyList()
        }
    }
    LaunchedEffect(Unit) { if (state.text.isEmpty()) runCatching { focus.requestFocus() } }
    DisposableEffect(id) { onDispose { store.dropIfEmpty(id) } }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop(
                sheet?.title?.takeIf { it.isNotEmpty() } ?: "New sheet",
                leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) },
                trailing = {
                    Box(menu.tracker) {
                        FloatingAction(AppIcons.More, "More", onClick = { menu.open() })
                        PillMenu(
                            menu,
                            listOf(
                                PillItem(AppIcons.Share, "Share", PillMotion.BOUNCE) {
                                    val text = shareText(state.text.toString(), results, style)
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                },
                                PillItem(AppIcons.Delete, "Delete", PillMotion.WIGGLE, tint = colors.error) { deleting = true }
                            )
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalReadableInset.current)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
                ZoneSurface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
                        val resultWidth = maxWidth * 0.36f
                        Row(Modifier.fillMaxWidth()) {
                            BasicTextField(
                                state = state,
                                lineLimits = TextFieldLineLimits.MultiLine(),
                                textStyle = LINE.copy(color = colors.onSurface),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                onTextLayout = { result -> layout = result() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 288.dp)
                                    .focusRequester(focus)
                                    .semantics { contentDescription = "Sheet" }
                            )
                            Spacer(Modifier.width(12.dp))
                            // As tall as the lines, so every result can be touched.
                            Box(Modifier.width(resultWidth).height(with(density) { (layout?.size?.height ?: 0).toDp() })) {
                                val lines = state.text.toString().split('\n')
                                val l = layout
                                var start = 0
                                lines.forEachIndexed { i, line ->
                                    val shown = results.getOrNull(i)
                                    if (shown != null && l != null && start <= l.layoutInput.text.length) {
                                        // Level with its line: the result's baseline on the line's own.
                                        val baseline = l.getLineBaseline(l.getLineForOffset(start.coerceAtMost(l.layoutInput.text.length)))
                                        Text(
                                            shownText(shown, style),
                                            style = LINE.copy(textAlign = TextAlign.End, fontWeight = if (shown.total) FontWeight.Bold else FontWeight.Medium),
                                            color = colors.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .layout { measurable, constraints ->
                                                    val placeable = measurable.measure(constraints)
                                                    val own = placeable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: 0
                                                    layout(placeable.width, placeable.height) {
                                                        placeable.place(0, (baseline - own).toInt())
                                                    }
                                                }
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(onClickLabel = "Copy") {
                                                    haptics.done()
                                                    val text = shownText(shown.copy(text = shown.text.removeSuffix("…")), style)
                                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                                                }
                                        )
                                    }
                                    start += line.length + 1
                                }
                            }
                        }
                        if (state.text.isEmpty()) {
                            Text(
                                "Rent = 800\nFood: 320.50\nTotal",
                                style = LINE,
                                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = resultWidth)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            // The signs a phone keyboard keeps on its second page, over it.
            AnimatedVisibility(
                visible = WindowInsets.isImeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SIGNS.forEach { sign ->
                        ZoneSurface(
                            shape = CircleShape,
                            onClick = {
                                haptics.tick()
                                val at = state.selection
                                state.edit {
                                    replace(at.min, at.max, sign)
                                    selection = TextRange(at.min + sign.length)
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp).sink(0.86f).semantics { contentDescription = sign }
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(sign, fontSize = 20.sp, color = colors.primary) }
                        }
                    }
                }
            }
        }
    }

    if (deleting) {
        ZoneAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this sheet?") },
            confirmButton = {
                QuietButton(onClick = {
                    deleting = false
                    haptics.done()
                    store.delete(id)
                    onBack()
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = { QuietButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

private fun write(value: UnifiedReal, measure: Measure?): String {
    if (measure is Money) Format.money(value, measure.code, 18)?.let { return it.text }
    // The digits leave room for the unit in the column.
    val w = Format.write(value, if (measure is Physical) maxOf(6, 11 - measure.unit.symbol.length) else 13)
    return w.text + if (w.more) "…" else ""
}

/** A day, a time or a duration as the phone writes them: Fri 8 Jan 2027, 17:15, 76 days, 8 hr 30 min. */
private fun written(moment: Moment, locale: Locale = Locale.getDefault()): String = when (moment) {
    is Moment.Day -> {
        // The year only when it is not this one.
        val skeleton = if (moment.date.year == java.time.LocalDate.now().year) "EEEMMMd" else "EEEMMMdy"
        val millis = moment.date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale).format(java.util.Date(millis))
    }
    is Moment.Clock -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(moment.time)
    is Moment.Count -> {
        val unit = when (moment.unit) {
            ChronoUnit.WEEKS -> MeasureUnit.WEEK
            ChronoUnit.MONTHS -> MeasureUnit.MONTH
            ChronoUnit.YEARS -> MeasureUnit.YEAR
            else -> MeasureUnit.DAY
        }
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(IcuMeasure(moment.amount, unit))
    }
    is Moment.Span -> {
        val h = moment.duration.toHours()
        val m = moment.duration.toMinutesPart()
        val parts = listOfNotNull(IcuMeasure(h, MeasureUnit.HOUR).takeIf { h > 0 }, IcuMeasure(m, MeasureUnit.MINUTE).takeIf { m > 0 || h == 0L })
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*parts.toTypedArray())
    }
}

/** A result as the user reads it: their digits, then its unit, or its currency where their country writes it. */
private fun shownText(shown: Shown, style: NumberStyle, locale: Locale = Locale.getDefault()): String {
    if (shown.ready) return shown.text
    val number = style.show(shown.text)
    return when (val m = shown.measure) {
        null -> number
        is Physical -> "$number ${m.unit.symbol}"
        is Money -> {
            val sign = runCatching { Currency.getInstance(m.code).getSymbol(locale) }.getOrDefault(m.code)
            val before = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern()?.startsWith('¤') == true
            when {
                before && sign.none { it.isLetter() } -> "$sign$number"
                before -> "$sign $number"
                else -> "$number $sign"
            }
        }
    }
}

/** The sheet as text, each result after its line. */
private fun shareText(text: String, results: List<Shown?>, style: NumberStyle): String =
    text.split('\n').mapIndexed { i, line ->
        val r = results.getOrNull(i)
        if (r != null && line.isNotBlank()) "$line  →  ${shownText(r.copy(text = r.text.removeSuffix("…")), style)}" else line
    }.joinToString("\n")

private val LINE = TextStyle(fontSize = 18.sp, lineHeight = 30.sp)
private val SIGNS = listOf("+", "−", "×", "÷", "=", "(", ")", "%", "^")
private const val SHEET_MS = 3_000L
