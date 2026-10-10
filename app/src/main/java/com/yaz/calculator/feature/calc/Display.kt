package com.yaz.calculator.feature.calc

import com.yaz.calculator.ui.component.sink
import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.engine.CalcError
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.Sym
import com.yaz.calculator.ui.component.ZoneSurface
import kotlinx.coroutines.awaitCancellation

/**
 * The top of the calculator: the expression, written as the user's language
 * writes numbers, with its cursor and selection; the result as it is typed
 * under it; after "=", the result in its place and the expression small
 * above. The phone's keyboard never opens: the keys below are the keyboard,
 * and a hardware keyboard types directly (Enter or = gives the result).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Display(vm: CalcViewModel, style: NumberStyle, memoryOn: Boolean, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    // A new transformation whenever a stored result's digits arrive, so the line shows them.
    val refs = vm.shown.toMap()
    val output = remember(style, refs) { OutputTransformation { showIn(this, style) { refs[it] ?: "…" } } }
    val input = remember(style) { InputTransformation { cleanIn(this, style) } }
    val clipboard = LocalClipboard.current
    val spelling = remember(style, clipboard) { SpelledClipboard(clipboard) { style.show(vm.spelled(it)) } }

    BoxWithConstraints(modifier.padding(horizontal = 20.dp)) {
        val width = maxWidth
        // How many digits a result line holds, at the size results are written.
        val zero = measurer.measure("0", TextStyle(fontSize = RESULT_SIZE)).size.width
        val chars = with(density) { (width.toPx() / zero).toInt() } - 1
        LaunchedEffect(chars) { vm.measured(chars) }

        val shownText = style.show(vm.text.text.map { c -> if (Sym.isRef(c)) refs[c] ?: "…" else c.toString() }.joinToString(""))
        val size = fitSize(measurer, shownText, with(density) { width.roundToPx() }, MAX_SIZE, MIN_SIZE)
        val hasTrig = vm.text.text.contains("sin") || vm.text.text.contains("cos") || vm.text.text.contains("tan")

        Row(Modifier.align(Alignment.TopStart).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasTrig) Badge(if (vm.degrees) "DEG" else "RAD")
            if (memoryOn) Badge("M")
        }
        // The actions stay under the result; the lines above them scroll when digits are unfolded.
        Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedContent(
                targetState = vm.previous,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "previous"
            ) { previous ->
                if (previous != null) {
                    Text(
                        style.show(vm.spelled(previous)),
                        style = TextStyle(fontSize = 22.sp, textAlign = TextAlign.End),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.fillMaxWidth())
                }
            }
            CompositionLocalProvider(LocalClipboard provides spelling) {
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    // Drawn again when a stored result's digits arrive: the field keeps its first rendering otherwise.
                    key(refs) {
                    BasicTextField(
                        state = vm.text,
                        inputTransformation = input,
                        outputTransformation = output,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        textStyle = TextStyle(
                            fontSize = size,
                            lineHeight = size * 1.12f,
                            textAlign = TextAlign.End,
                            color = colors.onBackground,
                            fontWeight = FontWeight.Light
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = with(density) { size.toDp() } * 1.2f)
                            .focusRequester(focus)
                            .semantics { contentDescription = "Expression" }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.NumPadEquals ||
                                        e.key == Key.Equals && !e.isShiftPressed -> { vm.equals(); true }
                                    e.key == Key.Escape -> { vm.clear(); true }
                                    else -> false
                                }
                            }
                    )
                    }
                }
            }
            LaunchedEffect(refs) { runCatching { focus.requestFocus() } }

            val error = vm.error
            val line: String? = when {
                error != null -> errorText(error)
                vm.evaluated -> vm.exactForm?.let { "= " + style.show(it) }
                else -> vm.live?.let { style.show(it) }
            }
            AnimatedContent(targetState = line, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "live") { text ->
                Text(
                    text.orEmpty(),
                    style = TextStyle(fontSize = RESULT_SIZE, textAlign = TextAlign.End),
                    color = if (error != null) colors.error else colors.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                )
            }
        }
        actions()
        }
    }
}

@Composable
private fun Badge(text: String) {
    ZoneSurface(shape = CircleShape, accent = true) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** A round action under the result: copy, share, memory ([sign] instead of an icon: M). */
@Composable
fun ResultAction(icon: ImageVector?, label: String, sign: String? = null, onClick: () -> Unit) {
    ZoneSurface(shape = CircleShape, modifier = Modifier.size(44.dp).sink(0.86f).semantics { contentDescription = label }, onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            // A changed icon (copy, then its tick) turns in, as the app's actions answer.
            if (icon != null) AnimatedContent(icon, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "icon") {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            else Text(sign.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun errorText(error: CalcError): String = when (error) {
    CalcError.SYNTAX -> "Bad expression"
    CalcError.DIVIDE_BY_ZERO -> "Can't divide by 0"
    CalcError.DOMAIN -> "Not a number"
    CalcError.TOO_LARGE -> "Too large"
    CalcError.TIMEOUT -> "Value may be infinite or undefined"
}

/** The largest size between [min] and [max] at which [text] holds on one line of [width]; long ones wrap at [min]. */
private fun fitSize(measurer: androidx.compose.ui.text.TextMeasurer, text: String, room: Int, max: TextUnit, min: TextUnit): TextUnit {
    if (text.isEmpty()) return max
    val measured = measurer.measure(text, TextStyle(fontSize = max), softWrap = false).size.width
    if (measured <= 0) return max
    return (max.value * (room * 0.97f / measured)).coerceIn(min.value, max.value).sp
}

/** The app's form shown as the user reads it: their decimal mark, their grouping, stored results written out. */
internal fun showIn(buffer: androidx.compose.foundation.text.input.TextFieldBuffer, style: NumberStyle, ref: (Char) -> String) {
    val original = buffer.asCharSequence().toString()
    val groups = style.groupsIn(original)
    var g = groups.size - 1
    for (i in original.indices.reversed()) {
        val c = original[i]
        when {
            Sym.isRef(c) -> buffer.replace(i, i + 1, style.show(ref(c)))
            c == Sym.POINT -> buffer.replace(i, i + 1, style.point.toString())
            c == Sym.EXP -> buffer.replace(i, i + 1, "E")
        }
        while (g >= 0 && groups[g] == i) {
            buffer.insert(i, style.group.toString())
            g--
        }
    }
}

/** What is typed on a hardware keyboard or pasted, in the app's form; anything else stays out. */
internal fun cleanIn(buffer: androidx.compose.foundation.text.input.TextFieldBuffer, style: NumberStyle) {
    val now = buffer.asCharSequence().toString()
    val clean = style.read(now).filter { it in ALLOWED || it.isDigit() || Sym.isRef(it) }
    if (clean == now) return
    val end = (buffer.selection.end + clean.length - now.length).coerceIn(0, clean.length)
    buffer.replace(0, buffer.length, clean)
    buffer.selection = TextRange(end)
}

private val ALLOWED = setOf(
    Sym.POINT, Sym.EXP, Sym.PLUS, Sym.MINUS, Sym.TIMES, Sym.DIVIDE, Sym.POWER, Sym.SQRT, Sym.PI, Sym.E,
    Sym.FACT, Sym.PERCENT, Sym.SQUARE, Sym.OPEN, Sym.CLOSE
) + "sincotalgxp⁻¹".toSet()

/** Copying from the expression gives its digits as the user reads them, never the app's private characters. */
private class SpelledClipboard(private val base: Clipboard, private val spell: (CharSequence) -> String) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = base.getClipEntry()
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val text = clipEntry?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
        base.setClipEntry(if (text != null) ClipEntry(ClipData.newPlainText("", spell(text))) else clipEntry)
    }
    override val nativeClipboard: NativeClipboard get() = base.nativeClipboard
}

internal val RESULT_SIZE = 34.sp
private val MAX_SIZE = 64.sp
private val MIN_SIZE = 34.sp
