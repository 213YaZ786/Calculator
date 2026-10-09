package com.yaz.calculator.feature.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.yaz.calculator.ui.component.Haptics
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.glass.GlassLook
import com.yaz.calculator.ui.glass.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a key is, which gives its colour and its motion. */
enum class KeyKind { DIGIT, OPERATOR, EQUALS, CLEAR, BACK, FUNCTION, TOGGLE }

class KeyDef(
    val label: String?,
    val kind: KeyKind,
    val description: String,
    val icon: ImageVector? = null,
    /** A toggle switched on (DEG, INV): washed in the accent. */
    val on: Boolean = false,
    val press: () -> Feel
)

/**
 * Keys in a grid that fills the room it is given: every cell the same size,
 * the key a pill or a disc depending on the cell. On first showing, the
 * keys pop in row after row.
 */
@Composable
fun KeyGrid(rows: List<List<KeyDef?>>, modifier: Modifier = Modifier, gap: Dp = 10.dp, firstIndex: Int = 0) {
    BoxWithConstraints(modifier) {
        val columns = rows.maxOf { it.size }
        val cellW = (maxWidth - gap * (columns - 1)) / columns
        val cellH = (maxHeight - gap * (rows.size - 1)) / rows.size
        val side = min(cellW, cellH)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { c, key ->
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (key != null) CalcKey(key, side, firstIndex + r * columns + c, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(key: KeyDef, side: Dp, index: Int, modifier: Modifier) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // Under the finger the disc squares a little and sinks.
    val corner by animateFloatAsState(if (pressed) 30f else 50f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "corner")
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "sink")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 14L)
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    val kick = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = corner.toInt())
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current

    val (tint, ink) = when (key.kind) {
        KeyKind.EQUALS -> colors.primary to colors.onPrimary
        KeyKind.CLEAR -> colors.tertiaryContainer to colors.onTertiaryContainer
        KeyKind.OPERATOR -> colors.secondaryContainer to colors.primary
        KeyKind.TOGGLE -> if (key.on) colors.primaryContainer to colors.onPrimaryContainer else Color.Unspecified to colors.primary
        KeyKind.FUNCTION, KeyKind.BACK -> Color.Unspecified to colors.primary
        KeyKind.DIGIT -> Color.Unspecified to colors.onSurface
    }
    // Coloured glass for the loud keys: the same pane, tinted.
    val look = when {
        glass == null || tint == Color.Unspecified -> glass
        key.kind == KeyKind.OPERATOR || key.kind == KeyKind.TOGGLE -> tinted(glass, tint.copy(alpha = if (glass.dark) 0.42f else 0.62f))
        else -> tinted(glass, tint.copy(alpha = if (glass.dark) 0.80f else 0.88f))
    }

    Box(
        modifier
            .graphicsLayer {
                val s = (0.4f + 0.6f * appear.value) * sink * (1f + if (key.kind == KeyKind.DIGIT) kick.value * 0.08f else 0f)
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
                rotationZ = when (key.kind) {
                    KeyKind.OPERATOR -> kick.value * 14f
                    else -> 0f
                }
                translationX = with(density) {
                    shake.value * 8.dp.toPx() + if (key.kind == KeyKind.BACK) -kick.value * 10.dp.toPx() else 0f
                }
                translationY = with(density) { if (key.kind == KeyKind.FUNCTION) -kick.value * 5.dp.toPx() else 0f }
                rotationX = if (key.kind == KeyKind.TOGGLE) kick.value * 180f else 0f
            },
        contentAlignment = Alignment.Center
    ) {
        // "=" sends a ring of light out when it gives a result.
        if (key.kind == KeyKind.EQUALS && kick.value > 0f) {
            val ring = colors.primary
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f * (1f + (1f - kick.value) * 0.45f)
                drawCircle(ring.copy(alpha = kick.value * 0.45f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            }
        }
        CompositionLocalProvider(LocalGlass provides look) {
            ZoneSurface(
                shape = shape,
                color = if (tint == Color.Unspecified) colors.surfaceContainerHigh else tint,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .semantics { contentDescription = key.description }
                    .clickable(interactionSource = press, indication = null, role = Role.Button) {
                        val feel = key.press()
                        feelIt(haptics, feel)
                        scope.launch {
                            if (feel == Feel.REJECT) {
                                shake.animateTo(0f, keyframes { durationMillis = 260; 1f at 50; -1f at 110; 0.6f at 170; -0.3f at 220 })
                            } else {
                                kick.snapTo(1f)
                                kick.animateTo(0f, if (key.kind == KeyKind.EQUALS) tween(520) else spring(dampingRatio = 0.35f, stiffness = 500f))
                            }
                        }
                    }
            ) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Labels sized from the key, so a tablet's keys read as well as a phone's.
                        val big = key.kind == KeyKind.DIGIT || key.kind == KeyKind.OPERATOR || key.kind == KeyKind.EQUALS || key.kind == KeyKind.CLEAR
                        val textSize = with(density) { (side * if (big) 0.36f else 0.28f).toSp() }
                        if (key.icon != null) {
                            Icon(key.icon, contentDescription = null, modifier = Modifier.size(side * 0.34f))
                        } else {
                            Text(
                                key.label.orEmpty(),
                                fontSize = textSize,
                                fontWeight = if (big) FontWeight.Normal else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tinted(glass: GlassLook, tint: Color) =
    GlassLook(glass.dark, glass.ground, glass.halos, tint, glass.floatTint, glass.accentTint)

fun feelIt(haptics: Haptics, feel: Feel) = when (feel) {
    Feel.TICK -> haptics.tick()
    Feel.DONE -> haptics.done()
    Feel.REJECT -> haptics.reject()
}

/** A row of space between groups of keys. */
@Composable
fun KeyGap(height: Dp) = Spacer(Modifier.size(height))
