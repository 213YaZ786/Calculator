package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yaz.calculator.engine.RegKind
import com.yaz.calculator.engine.Stats
import com.yaz.calculator.engine.programs.Words
import com.yaz.calculator.feature.calc.feelIt
import com.yaz.calculator.feature.calc.showIn
import com.yaz.calculator.ui.component.QuietButton
import com.yaz.calculator.ui.component.rememberHaptics
import java.util.Locale
import kotlinx.coroutines.awaitCancellation

/** STAT: L1 and L2 typed with the keys, then 1-Var Stats or a regression, as the TI's STAT EDIT and CALC. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsPage(adv: AdvancedViewModel, style: NumberStyle) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val output = remember(style) { OutputTransformation { showIn(this, style) { "" } } }
    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip(ONE.get(), !adv.twoVar) { haptics.tick(); adv.twoVar = false }
            Chip(TWO.get(), adv.twoVar) { haptics.tick(); adv.twoVar = true }
            if (adv.twoVar) RegKind.entries.forEach { k ->
                Chip(k.name.lowercase().replaceFirstChar { it.uppercase() }, adv.regKind == k) { haptics.tick(); adv.regKind = k }
            }
            Chip(CLEAR.get(), false) { feelIt(haptics, adv.clearLists()) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("", modifier = Modifier.width(32.dp))
            Text("L1 (x)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(if (adv.twoVar) "L2 (y)" else COUNTS.get(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        val list = rememberLazyListState()
        LaunchedEffect(adv.cellRow) { list.animateScrollToItem(adv.cellRow) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            itemsIndexed(adv.rows) { r, (a, b) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r + 1}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.width(32.dp))
                    listOf(a, b).forEachIndexed { c, state ->
                        val chosen = adv.cellRow == r && adv.cellColumn == c
                        Box(
                            Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (chosen) Gold else colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { haptics.tick(); adv.cellRow = r; adv.cellColumn = c }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            // Only the chosen cell is a field: a tap elsewhere reaches its cell.
                            if (chosen) InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                                BasicTextField(
                                    state = state,
                                    outputTransformation = output,
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    textStyle = TextStyle(fontSize = 17.sp, color = colors.onSurface, textAlign = TextAlign.End),
                                    cursorBrush = SolidColor(Gold),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "L${c + 1} ${r + 1}" }
                                )
                            } else Text(
                                style.show(state.text.toString()),
                                style = TextStyle(fontSize = 17.sp, color = colors.onSurface, textAlign = TextAlign.End),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "L${c + 1} ${r + 1}" }
                            )
                        }
                    }
                }
            }
        }
        Results(adv, style)
    }
}

@Composable
private fun Results(adv: AdvancedViewModel, style: NumberStyle) {
    val haptics = rememberHaptics()
    val (xs, ys) = adv.data()
    val french = Locale.getDefault().language == "fr"
    fun n(v: Double) = style.show(Stats.show(v))
    Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(top = 6.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!adv.twoVar) {
            val counts = ys.takeIf { list -> list.isNotEmpty() && list.all { it != null } }?.map { it!! }
            val s = Stats.oneVar(xs, counts, french)
            if (s == null) Text(EMPTY.get(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Line("n = ${n(s.n)}   x̄ = ${n(s.mean)}", true)
                Line("Σx = ${n(s.sum)}   Σx² = ${n(s.sumSquares)}")
                Line("σx = ${n(s.sigma)}   sx = ${if (s.s.isNaN()) "—" else n(s.s)}")
                Line("min = ${n(s.min)}   Q1 = ${n(s.q1)}   Med = ${n(s.median)}   Q3 = ${n(s.q3)}   max = ${n(s.max)}")
            }
        } else {
            val pairs = xs.indices.filter { ys[it] != null }
            val reg = Stats.regression(adv.regKind, pairs.map { xs[it] }, pairs.map { ys[it]!! })
            if (reg == null) Text(EMPTY_TWO.get(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Line(style.show(reg.shown), true)
                Line((reg.r?.let { "r = ${n(it)}   " } ?: "") + "r² = ${n(reg.r2)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton(onClick = { feelIt(haptics, adv.storeRegression(reg.equation)) }) { Text(DRAW.get()) }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).then(if (on) Modifier.border(1.dp, Gold, CircleShape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

private val ONE = Words("1 variable", "1 variable")
private val TWO = Words("2 variables", "2 variables")
private val COUNTS = Words("L2 (effectifs)", "L2 (counts)")
private val EMPTY = Words("Tapez des valeurs dans L1.", "Type values in L1.")
private val EMPTY_TWO = Words("Tapez des couples (x, y) dans L1 et L2.", "Type (x, y) pairs in L1 and L2.")
private val CLEAR = Words("Effacer les listes", "Clear the lists")
private val DRAW = Words("Tracer la courbe", "Draw the curve")
