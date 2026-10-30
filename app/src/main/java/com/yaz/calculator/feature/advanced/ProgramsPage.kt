package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.programs.Subject
import com.yaz.calculator.engine.programs.programs
import com.yaz.calculator.feature.calc.showIn
import com.yaz.calculator.ui.component.GlassSlider
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.sink
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.awaitCancellation

/** PRGM: the programs by subject, then the one open, its fields typed with the keys. */
@Composable
fun ProgramsPage(adv: AdvancedViewModel, style: NumberStyle) {
    when {
        adv.editing != null -> ProgramEditor(adv)
        adv.running != null -> ProgramRun(adv, style)
        adv.program != null -> ProgramView(adv, style)
        else -> ProgramList(adv)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramList(adv: AdvancedViewModel) {
    val haptics = rememberHaptics()
    val query = rememberTextFieldState()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        // Search, in French and in English alike, over titles and formulas.
        ZoneSurface(shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (query.text.isEmpty()) Text(SEARCH.get(), color = colors.onSurfaceVariant)
                    BasicTextField(
                        state = query,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = SEARCH.get() }
                    )
                }
                if (query.text.isNotEmpty()) Icon(
                    AppIcons.Close, contentDescription = CLEAR.get(), tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { query.clearText() }.padding(6.dp)
                )
            }
        }
        val q = query.text.toString().trim()
        val shown = if (q.isEmpty()) programs else programs.filter { p ->
            listOf(p.title.fr, p.title.en, p.formula, p.subject.title.fr, p.subject.title.en).any { it.contains(q, ignoreCase = true) }
        }
        if (q.isEmpty()) MyPrograms(adv)
        if (shown.isEmpty()) Text(NOTHING.get(), color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        Subject.entries.forEach { subject ->
            val list = shown.filter { it.subject == subject }
            if (list.isEmpty()) return@forEach
            Text(
                subject.title.get(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp)
            )
            // Cards that wrap, never a row that scrolls sideways.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { p ->
                    ZoneSurface(
                        shape = RoundedCornerShape(18.dp),
                        onClick = { haptics.tick(); adv.open(p) },
                        modifier = Modifier.widthIn(min = 140.dp, max = 260.dp).sink(0.95f)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(p.title.get(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(p.formula, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private val SEARCH = com.yaz.calculator.engine.programs.Words("Chercher un programme", "Search programs")
private val CLEAR = com.yaz.calculator.engine.programs.Words("Effacer", "Clear")
private val NOTHING = com.yaz.calculator.engine.programs.Words("Aucun programme", "No program")

@Composable
private fun ProgramView(adv: AdvancedViewModel, style: NumberStyle) {
    val p = adv.program ?: return
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    // A figure came: down to it, the keys folded meanwhile.
    // Where the answer starts in the scrolled column.
    var answerTop by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(adv.outcome, adv.computing) {
        // No figure: the answer brought up under the fields, its first line in sight.
        if (adv.outcome == null || adv.computing || adv.showingScene) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        val target = (answerTop - with(density) { 120.dp.roundToPx() }).coerceIn(0, scroll.maxValue)
        if (target > scroll.value) scroll.animateScrollTo(target)
    }
    LaunchedEffect(adv.outcome, adv.showingScene) {
        if (!adv.showingScene || adv.instant) return@LaunchedEffect
        // Once the keys have folded and the figure is laid out.
        kotlinx.coroutines.delay(350)
        scroll.animateScrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.ArrowBack, contentDescription = "Programs",
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tick(); adv.open(null) }.padding(8.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(p.title.get(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        // The other laws of the family, one tap away, a and b kept (GeoGebra's probability calculator).
        p.family?.let { family ->
            val siblings = remember(family) { programs.filter { it.family == family } }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                siblings.forEach { s ->
                    val chosen = s.id == p.id
                    ZoneSurface(
                        shape = RoundedCornerShape(50),
                        accent = chosen,
                        // The chosen one stays a tap target as in a Material choice group: it keeps the choice, with the press feedback.
                        onClick = { haptics.tick(); if (!chosen) adv.switchTo(s) },
                        modifier = Modifier.height(40.dp).sink(0.94f)
                    ) {
                        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text((s.short ?: s.title).get(), style = MaterialTheme.typography.labelLarge, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        // A long formula a size down, so it holds on its line.
        Text(p.formula, style = if (p.formula.length > 26) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = colors.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        val output = remember(style) { OutputTransformation { showIn(this, style) { "" } } }
        p.fields.forEachIndexed { i, field ->
            val chosen = adv.programField == i
            val focus = remember(p.id, i) { FocusRequester() }
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            LaunchedEffect(chosen, p.id) {
                if (!chosen) return@LaunchedEffect
                runCatching { focus.requestFocus() }
                if (field.kind == com.yaz.calculator.engine.programs.FieldKind.TEXT) keyboard?.show()
            }
            // A function or a list takes the whole width under its name; a number sits beside it.
            val wide = field.kind != com.yaz.calculator.engine.programs.FieldKind.NUMBER
            val input: @Composable (Modifier) -> Unit = { m ->
                if (field.kind == com.yaz.calculator.engine.programs.FieldKind.TEXT) BasicTextField(
                    // Words: the phone's own keyboard, in capitals as the ciphers use them.
                    state = adv.programFields[i],
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    // The keypad is away: the keyboard's own key runs the program.
                    onKeyboardAction = { haptics.tick(); adv.runProgram() },
                    textStyle = TextStyle(fontSize = 20.sp, color = colors.onSurface, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(Gold),
                    modifier = m.focusRequester(focus)
                        .onFocusChanged { if (it.isFocused && adv.programField != i) { adv.programField = i; adv.showingScene = false } }
                        .semantics { contentDescription = field.symbol }
                ) else InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    BasicTextField(
                        state = adv.programFields[i],
                        outputTransformation = output,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(fontSize = 20.sp, color = colors.onSurface, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(if (chosen) Gold else Color.Transparent),
                        modifier = m.focusRequester(focus)
                            .onFocusChanged { if (it.isFocused && adv.programField != i) { adv.programField = i; adv.showingScene = false } }
                            .semantics { contentDescription = field.symbol }
                    )
                }
            }
            val row = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(if (chosen) Modifier.border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(14.dp)) else Modifier)
                .clickable { haptics.tick(); adv.programField = i; adv.showingScene = false }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            if (wide) Column(row) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(field.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(field.name.get(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, modifier = Modifier.weight(1f))
                }
                input(Modifier.fillMaxWidth().padding(top = 2.dp))
            } else Row(row, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.45f)) {
                    Text(field.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (field.name.get() != field.symbol) Text(field.name.get(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                }
                input(Modifier.weight(0.45f))
                Text(field.unit, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(0.15f))
            }
        }
        if (adv.computing) androidx.compose.material3.LinearProgressIndicator(color = Gold, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
        adv.outcome?.takeIf { !adv.computing }?.let { o ->
            Spacer(Modifier.height(8.dp).onGloballyPositioned { answerTop = it.positionInParent().y.toInt() })
            o.lines.forEach { line ->
                Text(
                    // Numbers in the user's way; a comma of the sentence stays one (", ").
                    prose(style, line.text),
                    style = if (line.result) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line.result) FontWeight.Bold else FontWeight.Normal,
                    color = if (line.result) colors.primary else colors.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
            o.scene?.let { scene ->
                Spacer(Modifier.height(10.dp))
                SceneView(scene, height = figureHeight())
            }
            o.figure?.let { figure ->
                Spacer(Modifier.height(10.dp))
                FigureView(figure, style, height = figureHeight(), instant = adv.instant, key = p.id)
            }
            // The parameters on sliders, under the figure they move.
            if (o.figure != null || o.scene != null) p.fields.forEachIndexed { i, field ->
                val range = field.slider ?: return@forEachIndexed
                val now = adv.programFields[i].text.toString().replace('−', '-').toDoubleOrNull() ?: return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text("${field.symbol} = ${style.show(adv.programFields[i].text.toString())}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 92.dp))
                    GlassSlider(
                        value = now.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
                        onValueChange = { adv.slide(i, it.toDouble()) },
                        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = field.symbol }
                    )
                }
            }
            if (o.scene != null || o.figure != null) Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * A program's line in the user's way: the marks of its numbers as theirs,
 * the commas and full stops of its sentences left as they are.
 */
private fun prose(style: NumberStyle, text: String): String {
    val t = text.replace('-', '−')
    val kept = StringBuilder()
    for ((i, c) in t.withIndex()) {
        val inNumber = i > 0 && i + 1 < t.length && t[i - 1].isDigit() && t[i + 1].isDigit()
        kept.append(if ((c == '.' || c == ',') && !inNumber) (if (c == '.') '\uE0FE' else '\uE0FF') else c)
    }
    return style.show(kept.toString()).replace('\uE0FE', '.').replace('\uE0FF', ',')
}

/** A figure's height: 300 dp on a phone, more on a taller screen, never an empty half page. */
@Composable
private fun figureHeight(): androidx.compose.ui.unit.Dp =
    (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceIn(300.dp, 620.dp)
