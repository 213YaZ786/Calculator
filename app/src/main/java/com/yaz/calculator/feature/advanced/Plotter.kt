package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.data.advanced.Window
import com.yaz.calculator.engine.Curve
import com.yaz.calculator.engine.NumberStyle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Curves in a window, as the TI's GRAPH screen draws them: grid, axes and
 * their numbers, each curve in its colour, broken at its poles. Pinch to
 * zoom, drag to move, a touch at x ([onTouch]). [marks] are CALC's
 * answers, [shade] the area of ∫, [traced] the x read on every curve.
 */
@Composable
fun Plotter(
    curves: List<Pair<Curve, Color>>,
    window: Window,
    onWindow: (Window) -> Unit,
    style: NumberStyle,
    modifier: Modifier = Modifier,
    marks: List<Pair<Mark, Color>> = emptyList(),
    shade: Pair<Shade, Curve>? = null,
    shadeColor: Color = Color.Unspecified,
    traced: Double? = null,
    bound: Double? = null,
    points: List<Pair<Double, Double>> = emptyList(),
    onTouch: (Double) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    val strong = TextStyle(fontSize = 13.sp, color = colors.onSurface)
    val current by rememberUpdatedState(window)
    val change by rememberUpdatedState(onWindow)
    val touch by rememberUpdatedState(onTouch)
    val grid = colors.outlineVariant.copy(alpha = 0.5f)
    val axis = colors.onSurfaceVariant
    val paper = colors.surface
    Canvas(
        modifier
            .semantics { contentDescription = "Graph" }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val w = current
                    val sx = (w.xMax - w.xMin) / size.width
                    val sy = (w.yMax - w.yMin) / size.height
                    // The point under the fingers stays where it is.
                    val cx = w.xMin + centroid.x * sx
                    val cy = w.yMax - centroid.y * sy
                    val k = 1.0 / zoom.toDouble().coerceIn(0.2, 5.0)
                    val width = ((w.xMax - w.xMin) * k).coerceIn(1e-6, 1e9)
                    val height = ((w.yMax - w.yMin) * k).coerceIn(1e-6, 1e9)
                    val xMin = cx - (centroid.x / size.width) * width - pan.x * width / size.width
                    val yMax = cy + (centroid.y / size.height) * height + pan.y * height / size.height
                    change(Window(xMin, xMin + width, yMax - height, yMax))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p -> current.let { w -> touch(w.xMin + p.x / size.width * (w.xMax - w.xMin)) } }
            }
    ) {
        val w = window
        fun sx(x: Double) = ((x - w.xMin) / (w.xMax - w.xMin) * size.width).toFloat()
        fun sy(y: Double) = (size.height - (y - w.yMin) / (w.yMax - w.yMin) * size.height).toFloat()

        // The area of ∫, under its curve down to the axis.
        if (shade != null) {
            val (s, c) = shade
            val path = Path()
            val n = 200
            path.moveTo(sx(s.from), sy(0.0))
            for (i in 0..n) {
                val x = s.from + (s.to - s.from) * i / n
                val y = c(x)
                path.lineTo(sx(x), sy(if (y.isFinite()) y.coerceIn(w.yMin - (w.yMax - w.yMin), w.yMax + (w.yMax - w.yMin)) else 0.0))
            }
            path.lineTo(sx(s.to), sy(0.0))
            path.close()
            drawPath(path, shadeColor.copy(alpha = 0.28f))
        }

        gridAndAxes(w, measurer, label, style, grid, axis, ::sx, ::sy)

        for ((c, color) in curves) {
            val path = Path()
            var drawing = false
            var last = 0f
            var px = 0f
            while (px <= size.width + 2f) {
                val x = w.xMin + px / size.width * (w.xMax - w.xMin)
                val y = c(x)
                val py = if (y.isFinite()) sy(y) else Float.NaN
                val ok = py.isFinite() && abs(py) < size.height * 20
                if (ok && drawing && abs(py - last) < size.height) path.lineTo(px, py) else if (ok) path.moveTo(px, py)
                drawing = ok
                last = py
                px += 2f
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        }

        // STAT's points, as the TI's Stat Plot.
        for ((x, y) in points) {
            val at = Offset(sx(x), sy(y))
            if (at.x.isFinite() && at.y.isFinite()) {
                drawCircle(paper, 5.dp.toPx(), at)
                drawCircle(axis, 3.5.dp.toPx(), at)
            }
        }

        // ∫ waiting for its upper bound: the lower one drawn.
        bound?.let { b -> drawLine(axis, Offset(sx(b), 0f), Offset(sx(b), size.height), 2f) }

        for ((m, color) in marks) {
            val at = Offset(sx(m.x), sy(m.y))
            if (!at.x.isFinite() || !at.y.isFinite()) continue
            drawCircle(paper, 7.dp.toPx(), at)
            drawCircle(color, 5.dp.toPx(), at)
        }

        traced?.let { x ->
            drawLine(axis.copy(alpha = 0.6f), Offset(sx(x), 0f), Offset(sx(x), size.height), 1.5f)
            var row = 8f
            val head = measurer.measure("x = " + number(x, style, 5), strong)
            drawText(head, topLeft = Offset(8f, row))
            row += head.size.height + 2f
            for ((c, color) in curves) {
                val y = c(x)
                if (y.isFinite()) drawCircle(color, 5.dp.toPx(), Offset(sx(x), sy(y)))
                val t = measurer.measure("y = " + (if (y.isFinite()) number(y, style, 5) else "—"), strong.copy(color = color))
                drawText(t, topLeft = Offset(8f, row))
                row += t.size.height + 2f
            }
        }
    }
}

internal fun DrawScope.gridAndAxes(
    w: Window, measurer: TextMeasurer, label: TextStyle, style: NumberStyle, grid: Color, axis: Color,
    sx: (Double) -> Float, sy: (Double) -> Float
) {
    val stepX = niceStep((w.xMax - w.xMin) / 8)
    val stepY = niceStep((w.yMax - w.yMin) / 8)
    val axisY = sy(0.0).coerceIn(0f, size.height)
    val axisX = sx(0.0).coerceIn(0f, size.width)
    var gx = floor(w.xMin / stepX) * stepX
    while (gx <= w.xMax) {
        drawLine(grid, Offset(sx(gx), 0f), Offset(sx(gx), size.height), 1f)
        if (abs(gx) > stepX / 2) {
            val t = measurer.measure(number(gx, style, 3), label)
            val at = sx(gx) - t.size.width / 2f
            // Only the numbers that fit whole.
            if (at >= 2f && at + t.size.width <= size.width - 2f) drawText(t, topLeft = Offset(at, (axisY + 4f).coerceIn(4f, size.height - t.size.height - 4f)))
        }
        gx += stepX
    }
    var gy = floor(w.yMin / stepY) * stepY
    while (gy <= w.yMax) {
        drawLine(grid, Offset(0f, sy(gy)), Offset(size.width, sy(gy)), 1f)
        if (abs(gy) > stepY / 2) {
            val t = measurer.measure(number(gy, style, 3), label)
            val at = sy(gy) - t.size.height / 2f
            if (at >= 2f && at + t.size.height <= size.height - 2f) drawText(t, topLeft = Offset((axisX + 6f).coerceIn(4f, size.width - t.size.width - 4f), at))
        }
        gy += stepY
    }
    drawLine(axis, Offset(sx(0.0), 0f), Offset(sx(0.0), size.height), 2f)
    drawLine(axis, Offset(0f, sy(0.0)), Offset(size.width, sy(0.0)), 2f)
}

/** 1, 2 or 5 times a power of ten, near [raw]. */
fun niceStep(raw: Double): Double {
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when {
        f < 1.5 -> 1.0
        f < 3.5 -> 2.0
        f < 7.5 -> 5.0
        else -> 10.0
    }
}

/** [v] with up to [digits] decimals, in the user's way. */
fun number(v: Double, style: NumberStyle, digits: Int): String {
    if (!v.isFinite()) return "—"
    val rounded = java.math.BigDecimal(v).setScale(digits, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
        .let { if (it == "-0" || it == "0.0") "0" else it }
    return style.show(rounded.replace('-', '−'))
}

/** The accent turned round the colour wheel: curves apart at a glance, by day and by night. */
fun curveColors(accent: Color): List<Color> = listOf(0f, 120f, 240f, 60f, 180f, 300f).map { degrees ->
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    Color(android.graphics.Color.HSVToColor(hsv))
}
