package com.yaz.calculator.feature.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaz.calculator.engine.Curve
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.ui.component.DialogPane
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.icon.AppIcons
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A sheet's curve: the line as written and its function. */
class Plotted(val legend: String, val curve: Curve)

/**
 * The sheet's curves on one graph (Apple Math Notes, Desmos): pinch to zoom,
 * drag to move, a touch reads every curve at that x.
 */
@Composable
fun GraphDialog(curves: List<Plotted>, style: NumberStyle, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // The accent, then its hue turned a third and two thirds round: apart at a glance, by day and by night.
    val palette = remember(colors.primary) { listOf(0f, 120f, 240f, 60f).map { turned(colors.primary, it) } }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogPane {
            Column(Modifier.fillMaxWidth(0.94f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        curves.forEachIndexed { i, c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(10.dp)) { drawCircle(palette[i % palette.size]) }
                                Spacer(Modifier.size(6.dp))
                                Text(c.legend, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                            }
                        }
                    }
                    FloatingAction(AppIcons.Close, "Close", onDismiss)
                }
                Spacer(Modifier.height(8.dp))
                Plot(curves, palette, style, Modifier.fillMaxWidth().height(height))
            }
        }
    }
}

@Composable
private fun Plot(curves: List<Plotted>, palette: List<Color>, style: NumberStyle, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    // The world's point at the middle of the plot, and pixels per unit.
    var cx by remember { mutableFloatStateOf(0f) }
    var cy by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(0f) }
    var traced by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))) {
        Canvas(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = "Graph" }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val s = scale
                        if (s <= 0f) return@detectTransformGestures
                        // Zoom about the fingers: the point under them stays.
                        val wx = cx + (centroid.x - size.width / 2f) / s
                        val wy = cy - (centroid.y - size.height / 2f) / s
                        val next = (s * zoom).coerceIn(size.width / 2e6f, size.width / 1e-4f)
                        cx = wx - (centroid.x - size.width / 2f) / next - pan.x / next
                        cy = wy + (centroid.y - size.height / 2f) / next + pan.y / next
                        scale = next
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p -> traced = if (scale > 0f) cx + (p.x - size.width / 2f) / scale else null }
                }
        ) {
            if (scale <= 0f) scale = size.width / 20f
            val s = scale
            fun sx(x: Float) = size.width / 2f + (x - cx) * s
            fun sy(y: Float) = size.height / 2f - (y - cy) * s
            val left = cx - size.width / 2f / s
            val right = cx + size.width / 2f / s
            val bottom = cy - size.height / 2f / s
            val top = cy + size.height / 2f / s
            val step = niceStep((right - left) / 8f)

            // The grid, its numbers along the axes.
            val grid = colors.outlineVariant.copy(alpha = 0.5f)
            var gx = floor(left / step) * step
            while (gx <= right) {
                drawLine(grid, Offset(sx(gx), 0f), Offset(sx(gx), size.height), 1f)
                if (abs(gx) > step / 2) {
                    val t = measurer.measure(number(gx, style), label)
                    val at = sx(gx) - t.size.width / 2f
                    // Only the numbers that fit whole.
                    if (at >= 2f && at + t.size.width <= size.width - 2f) {
                        drawText(t, topLeft = Offset(at, (sy(0f) + 4f).coerceIn(4f, size.height - t.size.height - 4f)))
                    }
                }
                gx += step
            }
            var gy = floor(bottom / step) * step
            while (gy <= top) {
                drawLine(grid, Offset(0f, sy(gy)), Offset(size.width, sy(gy)), 1f)
                if (abs(gy) > step / 2) {
                    val t = measurer.measure(number(gy, style), label)
                    val at = sy(gy) - t.size.height / 2f
                    if (at >= 2f && at + t.size.height <= size.height - 2f) {
                        drawText(t, topLeft = Offset((sx(0f) + 6f).coerceIn(4f, size.width - t.size.width - 4f), at))
                    }
                }
                gy += step
            }
            val axis = colors.onSurfaceVariant
            drawLine(axis, Offset(sx(0f), 0f), Offset(sx(0f), size.height), 2f)
            drawLine(axis, Offset(0f, sy(0f)), Offset(size.width, sy(0f)), 2f)

            // Each curve point by point; broken where it has no value or leaps off the plot.
            curves.forEachIndexed { i, c ->
                val path = Path()
                var drawing = false
                var lastY = 0f
                var px = 0f
                while (px <= size.width) {
                    val x = cx + (px - size.width / 2f) / s
                    val y = c.curve(x.toDouble())
                    val py = if (y.isFinite()) sy(y.toFloat()) else Float.NaN
                    val ok = py.isFinite() && abs(py) < size.height * 20
                    if (ok && drawing && abs(py - lastY) < size.height) path.lineTo(px, py)
                    else if (ok) path.moveTo(px, py)
                    drawing = ok
                    lastY = py
                    px += 2f
                }
                drawPath(path, palette[i % palette.size], style = Stroke(width = 3.dp.toPx()))
            }

            // The values at the x touched.
            traced?.let { tx ->
                drawLine(axis.copy(alpha = 0.6f), Offset(sx(tx), 0f), Offset(sx(tx), size.height), 1.5f)
                var row = 8f
                val head = measurer.measure("x = " + number(tx, style, exact = true), label.copy(fontSize = 13.sp, color = colors.onSurface))
                drawText(head, topLeft = Offset(8f, row))
                row += head.size.height + 2f
                curves.forEachIndexed { i, c ->
                    val y = c.curve(tx.toDouble())
                    val color = palette[i % palette.size]
                    if (y.isFinite()) drawCircle(color, 5.dp.toPx(), Offset(sx(tx), sy(y.toFloat())))
                    val t = measurer.measure("y = " + (if (y.isFinite()) number(y.toFloat(), style, exact = true) else "—"), label.copy(fontSize = 13.sp, color = color))
                    drawText(t, topLeft = Offset(8f, row))
                    row += t.size.height + 2f
                }
            }
        }
    }
}

private fun turned(c: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 1, 2 or 5 times a power of ten, near [raw]. */
private fun niceStep(raw: Float): Float {
    val p = 10f.pow(floor(log10(raw)))
    val f = raw / p
    return p * when {
        f < 1.5f -> 1f
        f < 3.5f -> 2f
        f < 7.5f -> 5f
        else -> 10f
    }
}

private fun number(v: Float, style: NumberStyle, exact: Boolean = false): String {
    val digits = if (exact) 4 else 3
    val rounded = "%.${digits}f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
    return style.show(rounded.replace('-', '−'))
}
