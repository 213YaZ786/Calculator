package com.yaz.calculator.feature.calc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.feature.advanced.Gold
import com.yaz.calculator.ui.component.SparkleRipple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** How long each egg plays. */
fun ScienceEgg.millis() = when (this) {
    ScienceEgg.GOLDEN -> 2_800
    ScienceEgg.LIGHT -> 1_500
    ScienceEgg.AVOGADRO -> 1_900
}

/**
 * Draws [egg] at [p] (0 to 1) over the screen: [display] is the display's
 * rectangle, [result] where the result stands. [ripple] is Android's sparkle
 * ripple (null before Android 13), [phase] its noise phase.
 */
fun DrawScope.scienceEgg(
    egg: ScienceEgg, p: Float, display: Rect, result: Offset, ripple: SparkleRipple?, phase: Float,
    measurer: TextMeasurer, accent: Color
) {
    when (egg) {
        ScienceEgg.GOLDEN -> golden(p, display, result, ripple, phase, measurer)
        ScienceEgg.LIGHT -> light(p, display, accent)
        ScienceEgg.AVOGADRO -> mole(p, display, result, measurer, accent)
    }
}

/** φ: the sparkle ripple in gold from the result, the golden spiral drawing itself, φ in the middle. */
private fun DrawScope.golden(p: Float, display: Rect, result: Offset, ripple: SparkleRipple?, phase: Float, measurer: TextMeasurer) {
    if (ripple != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).maxOf { (it - result).getDistance() }
        // RippleAnimationSession's shape: spread to 0.5 in the first part, fade to 1 after.
        val rp = if (p < 0.45f) 0.5f * ease(p / 0.45f) else 0.5f + 0.5f * ((p - 0.45f) / 0.55f)
        ripple.update(result, size, far * 2.3f, rp, phase, Gold.copy(alpha = 0.5f), Color(0xFFFFF3C4))
        drawRect(ShaderBrush(ripple.shader))
    }
    // r = a·e^(bθ), b = ln φ / (π/2): a quarter turn makes it φ times wider.
    val s = ((p - 0.12f) / 0.62f).coerceIn(0f, 1f)
    if (s > 0f) {
        val turns = 3.0
        val b = ln(1.6180339887) / (PI / 2)
        // Over the whole screen, not crowded into the display.
        val rMax = minOf(size.width, size.height) * 0.4
        val a = rMax / exp(b * 2 * PI * turns)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val path = Path()
        val steps = 360
        val end = (steps * ease(s)).toInt().coerceAtLeast(1)
        for (k in 0..end) {
            val th = 2 * PI * turns * k / steps
            val r = a * exp(b * th)
            val pt = Offset((centre.x + r * cos(th)).toFloat(), (centre.y - r * sin(th)).toFloat())
            if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        val fade = if (p > 0.8f) 1f - (p - 0.8f) / 0.2f else 1f
        drawPath(path, Gold.copy(alpha = 0.95f * fade), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
    val textIn = ((p - 0.3f) / 0.2f).coerceIn(0f, 1f)
    val textOut = if (p > 0.82f) 1f - (p - 0.82f) / 0.18f else 1f
    val alpha = textIn * textOut
    if (alpha > 0f) {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val t = measurer.measure("φ", TextStyle(fontSize = 160.sp, color = Gold.copy(alpha = alpha), fontWeight = FontWeight.Light))
        withTransform({ scale(0.8f + 0.2f * textIn, 0.8f + 0.2f * textIn, middle) }) {
            drawText(t, topLeft = middle - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }
}

/** c: streaks of light shooting across the display, each at its own height and moment. */
private fun DrawScope.light(p: Float, display: Rect, accent: Color) {
    val n = 42
    for (i in 0 until n) {
        val y = size.height * frac(i * 0.618034f + 0.11f)
        val delay = 0.45f * frac(i * 0.414214f)
        val t = ((p - delay) / 0.45f).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) continue
        val length = size.width * (0.18f + 0.25f * frac(i * 0.732f))
        val head = -length + (size.width + 2 * length) * t
        val thick = (3f + 4f * frac(i * 0.271f)).dp.toPx()
        // A tail fading into the screen, the head in the accent with a light core: seen by day and by night.
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.55f), accent), startX = head - length, endX = head),
            Offset(head - length, y), Offset(head, y), thick, StrokeCap.Round
        )
        drawLine(Color.White.copy(alpha = 0.85f), Offset(head - length * 0.25f, y), Offset(head, y), thick * 0.35f, StrokeCap.Round)
    }
}

/** Avogadro: a mole of particles bursting from the result, "1 mol" popping in the middle. */
private fun DrawScope.mole(p: Float, display: Rect, result: Offset, measurer: TextMeasurer, accent: Color) {
    val n = 96
    val reach = maxOf(size.width, size.height) * 0.75f
    for (i in 0 until n) {
        val angle = i * 2.39996323f
        val speed = 0.45f + 0.55f * frac(i * 0.618034f)
        val d = reach * speed * ease(p)
        val at = result + Offset(cos(angle) * d, sin(angle) * d)
        val color = if (i % 3 == 0) Gold else accent
        drawCircle(color.copy(alpha = (1f - p).coerceIn(0f, 1f)), (2f + 3f * frac(i * 0.37f)).dp.toPx(), at)
    }
    val pop = ((p - 0.1f) / 0.25f).coerceIn(0f, 1f)
    val alpha = pop * if (p > 0.8f) 1f - (p - 0.8f) / 0.2f else 1f
    if (alpha > 0f) {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val t = measurer.measure("1 mol", TextStyle(fontSize = 88.sp, color = accent.copy(alpha = alpha), fontWeight = FontWeight.Medium))
        val k = 0.6f + 0.4f * overshoot(pop)
        withTransform({ scale(k, k, middle) }) {
            drawText(t, topLeft = middle - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }
}

private fun frac(x: Float) = x - kotlin.math.floor(x)
private fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun overshoot(x: Float): Float { val c = 1.7f; val t = x - 1f; return 1f + (c + 1) * t * t * t + c * t * t }
