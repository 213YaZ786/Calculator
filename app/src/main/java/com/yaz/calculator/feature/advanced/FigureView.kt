package com.yaz.calculator.feature.advanced

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.data.advanced.Window
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.programs.Box2
import com.yaz.calculator.engine.programs.Figure
import com.yaz.calculator.engine.programs.P2
import com.yaz.calculator.engine.programs.Shape2
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * A program's figure in the plane, on a pane of glass, over the graph's
 * grid: pinch to zoom, drag to move, a double tap puts it back and plays it
 * again. It comes drawn: curves from left to right, areas and rectangles
 * rising from the axis, arrows growing, points popping; a staged figure
 * one shape after another.
 */
@Composable
fun FigureView(figure: Figure, style: NumberStyle, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 300.dp) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val home = remember(figure) { figure.frame() }
    var box by remember(figure) { mutableStateOf(home) }
    val drawn = remember(figure) { Animatable(0f) }
    val count = figure.shapes.size.coerceAtLeast(1)
    val length = if (figure.staged) (550 * count).coerceAtMost(6000) else 1100
    suspend fun play() {
        drawn.snapTo(0f)
        drawn.animateTo(1f, tween(length, easing = if (figure.staged) LinearEasing else FastOutSlowInEasing))
    }
    LaunchedEffect(figure) { play() }
    val colors = MaterialTheme.colorScheme
    val palette = curveColors(colors.primary)
    val ink = colors.onSurface
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    val grid = colors.outlineVariant.copy(alpha = 0.5f)
    val axis = colors.onSurfaceVariant
    val paper = colors.surface
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
            Canvas(
                Modifier.fillMaxSize()
                    .semantics { contentDescription = "Figure" }
                    .pointerInput(figure) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val w = shown(box, figure.square, size.width.toFloat(), size.height.toFloat())
                            val k = 1.0 / zoom.toDouble().coerceIn(0.2, 5.0)
                            val cx = w.xMin + centroid.x / size.width * (w.xMax - w.xMin)
                            val cy = w.yMax - centroid.y / size.height * (w.yMax - w.yMin)
                            val dx = -pan.x / size.width * (w.xMax - w.xMin)
                            val dy = pan.y / size.height * (w.yMax - w.yMin)
                            // The point under the fingers stays where it is.
                            box = Box2(
                                cx + (box.xMin - cx) * k + dx, cx + (box.xMax - cx) * k + dx,
                                cy + (box.yMin - cy) * k + dy, cy + (box.yMax - cy) * k + dy
                            )
                        }
                    }
                    .pointerInput(figure) {
                        detectTapGestures(onDoubleTap = { haptics.tick(); box = home; scope.launch { play() } })
                    }
            ) {
                val w = shown(box, figure.square, size.width, size.height)
                fun sx(x: Double) = ((x - w.xMin) / (w.xMax - w.xMin) * size.width).toFloat()
                fun sy(y: Double) = (size.height - (y - w.yMin) / (w.yMax - w.yMin) * size.height).toFloat()
                fun at(p: P2) = Offset(sx(p.x), sy(p.y))
                gridAndAxes(Window(w.xMin, w.xMax, w.yMin, w.yMax), measurer, label, style, grid, axis, ::sx, ::sy)
                val p = drawn.value
                // Flat shapes under the lines, in the figure's order.
                figure.shapes.forEachIndexed { i, s ->
                    val t = if (figure.staged) (p * count - i).coerceIn(0f, 1f) else p
                    if (t <= 0f) return@forEachIndexed
                    val color = palette[s.tone % palette.size]
                    when (s) {
                        is Shape2.Area -> drawArea(s, t, color, palette[(s.tone + 1) % palette.size], w, ::sx, ::sy)
                        is Shape2.Polygon -> {
                            // Rising from the axis.
                            val path = Path()
                            s.corners.forEachIndexed { j, c -> val o = Offset(sx(c.x), sy(c.y * t)); if (j == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                            path.close()
                            drawPath(path, color.copy(alpha = 0.22f))
                            drawPath(path, color.copy(alpha = 0.8f), style = Stroke(width = 1.4.dp.toPx()))
                        }
                        is Shape2.Circle -> {
                            drawCircle(color.copy(alpha = 0.10f * t), (s.radius / (w.xMax - w.xMin) * size.width).toFloat(), at(s.centre))
                            drawArc(
                                color, -90f, 360f * t, false,
                                topLeft = at(P2(s.centre.x - s.radius, s.centre.y + s.radius)),
                                size = androidx.compose.ui.geometry.Size(sx(s.centre.x + s.radius) - sx(s.centre.x - s.radius), sy(s.centre.y - s.radius) - sy(s.centre.y + s.radius)),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        else -> Unit
                    }
                }
                figure.shapes.forEachIndexed { i, s ->
                    val t = if (figure.staged) (p * count - i).coerceIn(0f, 1f) else p
                    if (t <= 0f) return@forEachIndexed
                    val color = palette[s.tone % palette.size]
                    when (s) {
                        is Shape2.Curve -> drawCurve(s, t, color, w, ::sx, ::sy)
                        is Shape2.Polyline -> if (s.points.size >= 2) {
                            // Drawn along its length as it comes.
                            val upto = (s.points.size - 1) * t
                            val path = Path()
                            path.moveTo(sx(s.points[0].x), sy(s.points[0].y))
                            for (j in 1..upto.toInt()) path.lineTo(sx(s.points[j].x), sy(s.points[j].y))
                            val k = upto.toInt()
                            if (k < s.points.size - 1) {
                                val q = s.points[k] + (s.points[k + 1] - s.points[k]) * (upto - k).toDouble()
                                path.lineTo(sx(q.x), sy(q.y))
                            }
                            drawPath(path, color, style = Stroke(
                                width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null
                            ))
                        }
                        is Shape2.Segment -> {
                            val b = s.a + (s.b - s.a) * t.toDouble()
                            drawLine(
                                color.copy(alpha = 0.9f), at(s.a), at(b), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null
                            )
                        }
                        is Shape2.Arrow -> {
                            val a = at(s.from); val b = at(s.from + (s.to - s.from) * t.toDouble())
                            drawLine(color.copy(alpha = 0.18f), a, b, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            if ((b - a).getDistance() > 4f) figureArrowHead(a, b, color, 13.dp.toPx())
                        }
                        is Shape2.Dots -> {
                            // Falling in as rain: the first ones first.
                            val upto = (s.points.size * t).toInt()
                            for (j in 0 until upto) drawCircle(color.copy(alpha = 0.85f), 2.2.dp.toPx(), at(s.points[j]))
                        }
                        is Shape2.Point -> {
                            // A pop: past its size, then back.
                            val k = if (t < 0.7f) t / 0.7f * 1.3f else 1.3f - (t - 0.7f) / 0.3f * 0.3f
                            val o = at(s.at)
                            drawCircle(color.copy(alpha = 0.22f), 11.dp.toPx() * k, o)
                            drawCircle(paper, 6.5.dp.toPx() * k, o)
                            drawCircle(color, 5.dp.toPx() * k, o)
                        }
                        else -> Unit
                    }
                    if (t >= 1f) drawShapeLabel(s, w, color, ink, measurer, ::sx, ::sy)
                }
            }
        }
    }
}

/** The box widened on one axis so that one unit has the same length on both. */
private fun shown(b: Box2, square: Boolean, width: Float, height: Float): Box2 {
    if (!square || width <= 0f || height <= 0f) return b
    val perX = (b.xMax - b.xMin) / width; val perY = (b.yMax - b.yMin) / height
    return if (perX > perY) {
        val half = perX * height / 2; val c = (b.yMin + b.yMax) / 2
        Box2(b.xMin, b.xMax, c - half, c + half)
    } else {
        val half = perY * width / 2; val c = (b.xMin + b.xMax) / 2
        Box2(c - half, c + half, b.yMin, b.yMax)
    }
}

private fun value(f: (Double) -> Double, x: Double) = runCatching { f(x) }.getOrDefault(Double.NaN)

private fun DrawScope.drawCurve(s: Shape2.Curve, t: Float, color: Color, w: Box2, sx: (Double) -> Float, sy: (Double) -> Float) {
    val a = max(s.from, w.xMin); val b = min(s.to, w.xMax)
    if (a >= b) return
    val end = a + (b - a) * t
    val path = Path()
    var drawing = false; var last = 0f
    val n = (size.width / 2).toInt().coerceAtLeast(50)
    for (i in 0..n) {
        val x = a + (end - a) * i / n
        val y = value(s.f, x)
        val py = if (y.isFinite()) sy(y) else Float.NaN
        val ok = py.isFinite() && abs(py) < size.height * 20
        if (ok && drawing && abs(py - last) < size.height) path.lineTo(sx(x), py) else if (ok) path.moveTo(sx(x), py)
        drawing = ok; last = py
    }
    drawPath(
        path, color, style = Stroke(
            width = 3.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())) else null
        )
    )
}

/** Above the lower edge in its tone, below it in the next: the signed area. */
private fun DrawScope.drawArea(s: Shape2.Area, t: Float, color: Color, under: Color, w: Box2, sx: (Double) -> Float, sy: (Double) -> Float) {
    val n = 240
    val lim = (w.yMax - w.yMin) * 3
    fun clamp(v: Double) = if (v.isFinite()) v.coerceIn(w.yMin - lim, w.yMax + lim) else 0.0
    val xs = (0..n).map { s.from + (s.to - s.from) * it / n }
    val low = xs.map { x -> clamp(s.bottom?.let { value(it, x) } ?: 0.0) }
    // Rising from the lower edge.
    val high = xs.mapIndexed { i, x -> low[i] + (clamp(value(s.top, x)) - low[i]) * t }
    for ((tone, keep) in listOf(color to { h: Double, l: Double -> max(h, l) }, under to { h: Double, l: Double -> min(h, l) })) {
        val path = Path()
        path.moveTo(sx(xs[0]), sy(low[0]))
        for (i in 0..n) path.lineTo(sx(xs[i]), sy(keep(high[i], low[i])))
        for (i in n downTo 0) path.lineTo(sx(xs[i]), sy(low[i]))
        path.close()
        drawPath(path, tone.copy(alpha = 0.30f))
    }
}

private fun DrawScope.figureArrowHead(from: Offset, to: Offset, color: Color, size: Float) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val left = to - Offset((size * cos(angle - 0.45)).toFloat(), (size * sin(angle - 0.45)).toFloat())
    val right = to - Offset((size * cos(angle + 0.45)).toFloat(), (size * sin(angle + 0.45)).toFloat())
    drawPath(Path().apply { moveTo(to.x, to.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, color)
}

private fun DrawScope.drawShapeLabel(s: Shape2, w: Box2, color: Color, ink: Color, measurer: TextMeasurer, sx: (Double) -> Float, sy: (Double) -> Float) {
    if (s.label.isEmpty()) return
    val p: P2 = when (s) {
        is Shape2.Point -> s.at
        is Shape2.Arrow -> (s.from + s.to) * 0.5
        // Towards the end, clear of the middle where areas carry their names.
        is Shape2.Segment -> s.a + (s.b - s.a) * 0.85
        is Shape2.Polyline -> s.points.last()
        is Shape2.Dots -> s.points.firstOrNull() ?: return
        is Shape2.Circle -> s.centre + P2(s.radius * 0.7071, s.radius * 0.7071)
        is Shape2.Polygon -> s.corners.reduce { a, b -> a + b } * (1.0 / s.corners.size)
        is Shape2.Area -> {
            // Where the area is tallest; above the curve when it is too thin to hold the name.
            val x = (0..40).map { s.from + (s.to - s.from) * it / 40.0 }.maxBy { x -> abs(value(s.top, x) - (s.bottom?.let { value(it, x) } ?: 0.0)).takeIf { it.isFinite() } ?: -1.0 }
            val top = value(s.top, x); val low = s.bottom?.let { value(it, x) } ?: 0.0
            val tall = abs(sy(top) - sy(low))
            if (tall > 44.dp.toPx()) P2(x, (top + low) / 2) else P2(x, max(top, low) + (w.yMax - w.yMin) * 0.07)
        }
        is Shape2.Curve -> {
            // Near the right edge, the last place where the curve is well in sight.
            val right = min(s.to, w.xMax) - (w.xMax - w.xMin) * 0.08
            val left = max(s.from, w.xMin)
            val h = w.yMax - w.yMin
            (0..60).map { right - (right - left) * it / 60.0 }
                .firstOrNull { x -> value(s.f, x).let { it.isFinite() && it > w.yMin + h * 0.12 && it < w.yMax - h * 0.12 } }
                ?.let { P2(it, value(s.f, it)) } ?: return
        }
    }
    if (!p.x.isFinite() || !p.y.isFinite()) return
    val text = measurer.measure(s.label, TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    val o = Offset(sx(p.x), sy(p.y))
    // An area's or a polygon's name in its middle; the others beside their point.
    val centred = s is Shape2.Area || s is Shape2.Polygon
    // A curve's name on the free side: below a rising curve, above a falling one.
    val rising = when (s) {
        is Shape2.Curve -> { val h = (w.xMax - w.xMin) * 0.01; value(s.f, p.x + h) > value(s.f, p.x - h) }
        is Shape2.Polyline -> s.points.size >= 2 && s.points.last().y > s.points[s.points.size - 2].y
        else -> false
    }
    val place = if (rising) Offset((o.x + 10).coerceIn(0f, size.width - text.size.width), (o.y + 6).coerceIn(0f, size.height - text.size.height))
    else if (centred) Offset((o.x - text.size.width / 2).coerceIn(0f, size.width - text.size.width), (o.y - text.size.height / 2).coerceIn(0f, size.height - text.size.height)) else Offset((o.x + 8).coerceIn(0f, size.width - text.size.width), (o.y - text.size.height - 4).coerceIn(0f, size.height - text.size.height))
    drawText(text, topLeft = place, color = lerp(color, ink, 0.35f))
}
