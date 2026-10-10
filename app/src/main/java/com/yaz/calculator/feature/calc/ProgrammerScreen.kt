package com.yaz.calculator.feature.calc

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.engine.Base
import com.yaz.calculator.engine.ProgExpr
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.sink
import com.yaz.calculator.ui.icon.AppIcons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import com.yaz.calculator.feature.advanced.Gold
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Windows Calculator's Programmer display: the expression in the chosen
 * base, then the value in the four bases together; a tap on a base types in
 * it. The chip at the top goes back to the calculator.
 */
@Composable
fun ProgrammerDisplay(vm: CalcViewModel, modifier: Modifier = Modifier, chip: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val value = remember(vm.prog) { runCatching { vm.prog.value() }.getOrNull() }
    BoxWithConstraints(modifier.padding(horizontal = 20.dp)) {
        val width = maxWidth
        if (chip) ZoneSurface(
            shape = CircleShape,
            accent = true,
            onClick = { feelIt(haptics, vm.toggleProgrammer()) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp).sink(0.9f)
        ) {
            Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Programmer", style = MaterialTheme.typography.labelLarge)
                Icon(AppIcons.Close, contentDescription = "Back to the calculator", modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            val written = vm.prog.written(vm.base).ifEmpty { "0" }
            val room = with(androidx.compose.ui.platform.LocalDensity.current) { width.roundToPx() }
            val measured = measurer.measure(written, TextStyle(fontSize = 56.sp)).size.width
            val size = if (measured <= room) 56f else (56f * room * 0.97f / measured).coerceAtLeast(28f)
            Text(
                written,
                style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.12f).sp, textAlign = TextAlign.End, fontWeight = FontWeight.Light),
                color = colors.onBackground,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expression" }
            )
            // CAFEBABE (Java's class files) and DEADBEEF: a cup of coffee, steaming.
            if (value == 0xCAFEBABEL || value == 0xDEADBEEFL) Coffee()
            vm.progError?.let {
                Text(errorText(it), style = MaterialTheme.typography.titleMedium, color = colors.error)
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (b in Base.entries) BaseRow(b, value, chosen = b == vm.base) {
                    haptics.tick()
                    vm.base = b
                }
            }
        }
    }
}

@Composable
private fun BaseRow(base: Base, value: Long?, chosen: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink by animateColorAsState(if (chosen) colors.primary else colors.onSurfaceVariant, label = "base")
    Row(
        Modifier
            .fillMaxWidth()
            .sink(0.97f)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = chosen
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(52.dp)) {
            if (chosen) {
                ZoneSurface(shape = RoundedCornerShape(50), accent = true) {
                    Text(base.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            } else {
                Text(base.label, style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text(
            value?.let { ProgExpr.show(it, base) } ?: "",
            style = TextStyle(
                fontSize = 17.sp,
                textAlign = TextAlign.End,
                fontFamily = if (base == Base.BIN) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal
            ),
            color = if (chosen) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The band of bitwise keys (Windows, PCalc). */
fun programmerBand(vm: CalcViewModel): List<List<KeyDef?>> {
    fun bit(label: String, op: String, name: String) = KeyDef(label, KeyKind.FUNCTION, name) { vm.progOperator(op) }
    return listOf(
        listOf(
            bit("AND", ProgExpr.AND, "And"),
            bit("OR", ProgExpr.OR, "Or"),
            bit("XOR", ProgExpr.XOR, "Exclusive or"),
            bit("NOT", ProgExpr.NOT, "Not"),
            bit("≪", ProgExpr.SHL, "Shift left"),
            bit("≫", ProgExpr.SHR, "Shift right")
        )
    )
}

/** The validated layout: A to F down the left and along the bottom row, digits faded outside the base. */
fun programmerKeys(vm: CalcViewModel): List<List<KeyDef?>> {
    val radix = vm.base.radix
    fun d(n: Int): KeyDef {
        val label = Character.forDigit(n, 16).uppercaseChar().toString()
        return KeyDef(label, KeyKind.DIGIT, label, dim = n >= radix) { vm.progDigit(n) }
    }
    fun op(label: String, name: String) = KeyDef(label, KeyKind.OPERATOR, name) { vm.progOperator(label) }
    return listOf(
        listOf(d(10), d(7), d(8), d(9), op("÷", "Divide")),
        listOf(d(11), d(4), d(5), d(6), op("×", "Multiply")),
        listOf(d(12), d(1), d(2), d(3), op("−", "Minus")),
        listOf(d(13), d(14), d(15), d(0), op("+", "Plus")),
        listOf(
            KeyDef("AC", KeyKind.CLEAR, "Clear") { vm.progClear() },
            KeyDef("( )", KeyKind.FUNCTION, "Parenthesis") { vm.progParenthesis() },
            KeyDef("%", KeyKind.OPERATOR, "Remainder") { vm.progOperator("%") },
            KeyDef(null, KeyKind.BACK, "Delete", icon = AppIcons.Backspace) { vm.progBackspace() },
            KeyDef("=", KeyKind.EQUALS, "Equals") { vm.progEquals() }
        )
    )
}

@Composable
private fun Coffee() {
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f)) }
    val steam = androidx.compose.animation.core.rememberInfiniteTransition(label = "steam")
    val rise by steam.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "rise")
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(72.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.BottomCenter) {
        androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
            // Three wisps rising and fading over the cup.
            for (k in 0..2) {
                val t = (rise + k / 3f) % 1f
                val x = size.width * (0.32f + 0.14f * k)
                val y = size.height * (0.45f - 0.4f * t)
                val wobble = kotlin.math.sin((t * 2 * Math.PI).toFloat()) * size.width * 0.04f
                drawCircle(colors.onSurfaceVariant.copy(alpha = 0.45f * (1f - t)), size.width * (0.04f + 0.04f * t), androidx.compose.ui.geometry.Offset(x + wobble, y))
            }
        }
        Icon(AppIcons.Cafe, contentDescription = "Coffee", tint = Gold, modifier = Modifier.size(40.dp))
    }
}

