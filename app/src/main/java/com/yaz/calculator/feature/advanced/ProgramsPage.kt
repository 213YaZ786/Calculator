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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.sink
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.awaitCancellation

/** PRGM: the programs by subject, then the one open, its fields typed with the keys. */
@Composable
fun ProgramsPage(adv: AdvancedViewModel, style: NumberStyle) {
    val open = adv.program
    if (open == null) ProgramList(adv) else ProgramView(adv, style)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.ArrowBack, contentDescription = "Programs",
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tick(); adv.open(null) }.padding(8.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(p.title.get(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text(p.formula, style = MaterialTheme.typography.titleLarge, color = colors.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        val output = remember(style) { OutputTransformation { showIn(this, style) { "" } } }
        p.fields.forEachIndexed { i, field ->
            val chosen = adv.programField == i
            val focus = remember(p.id, i) { FocusRequester() }
            LaunchedEffect(chosen, p.id) { if (chosen) runCatching { focus.requestFocus() } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (chosen) Modifier.border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(14.dp)) else Modifier)
                    .clickable { haptics.tick(); adv.programField = i }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(0.45f)) {
                    Text(field.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (field.name.get() != field.symbol) Text(field.name.get(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                }
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    BasicTextField(
                        state = adv.programFields[i],
                        outputTransformation = output,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(fontSize = 20.sp, color = colors.onSurface, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(if (chosen) Gold else Color.Transparent),
                        modifier = Modifier.weight(0.45f).focusRequester(focus).semantics { contentDescription = field.symbol }
                    )
                }
                Text(field.unit, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(0.15f))
            }
        }
        adv.outcome?.let { o ->
            Spacer(Modifier.height(8.dp))
            o.lines.forEach { line ->
                Text(
                    style.show(line.text.replace('-', '−')),
                    style = if (line.result) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line.result) FontWeight.Bold else FontWeight.Normal,
                    color = if (line.result) colors.primary else colors.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
