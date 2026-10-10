package com.yaz.calculator.feature.advanced

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.engine.programs.Scene
import com.yaz.calculator.engine.programs.Shape3
import com.yaz.calculator.engine.programs.Vec3
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * A program's figure in space, on a pane of glass: turned by a drag, zoomed
 * by a pinch, put back by a double tap. It comes in turning a quarter round
 * so it reads as space at once. Perspective, nearer things brighter, planes
 * cut to the box around the figure, arrows with their heads, labels.
 */
@Composable
fun SceneView(scene: Scene, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 320.dp) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val home = remember(scene) { homeView(scene) }
    val yaw = remember(scene) { Animatable(home.first - 85f) }
    var pitch by remember(scene) { mutableFloatStateOf(home.second) }
    var zoom by remember(scene) { mutableFloatStateOf(1f) }
    LaunchedEffect(scene) { yaw.animateTo(home.first, spring(dampingRatio = 0.75f, stiffness = 40f)) }
    // A staged scene: one shape after another, 0.6 s each; else all at once.
    val drawn = remember(scene) { Animatable(if (scene.staged) 0f else 1f) }
    val count = scene.shapes.size.coerceAtLeast(1)
    suspend fun play() {
        if (!scene.staged) return
        drawn.snapTo(0f)
        drawn.animateTo(1f, androidx.compose.animation.core.tween((600 * count).coerceAtMost(6000), easing = androidx.compose.animation.core.LinearEasing))
    }
    LaunchedEffect(scene) { play() }
    val palette = curveColors(MaterialTheme.colorScheme.primary)
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    // The box the figure sits in: centred on what it names, the origin included.
    val (centre, reach) = remember(scene) {
        val pts = scene.anchors
        val lo = Vec3(pts.minOf { it.x }, pts.minOf { it.y }, pts.minOf { it.z })
        val hi = Vec3(pts.maxOf { it.x }, pts.maxOf { it.y }, pts.maxOf { it.z })
        val c = (lo + hi) * 0.5
        c to max(1.0, pts.maxOf { (it - c).norm }) * 1.15
    }
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(scene) {
                        detectSceneTurns(
                            onTurn = { dx -> scope.launch { yaw.snapTo(yaw.value - dx * 0.4f) } },
                            onTilt = { dy -> pitch = (pitch + dy * 0.4f).coerceIn(-85f, 85f) },
                            onZoom = { k -> zoom = (zoom * k).coerceIn(0.4f, 4f) }
                        )
                    }
                    .pointerInput(scene) {
                        detectTapGestures(onDoubleTap = {
                            haptics.tick()
                            pitch = home.second; zoom = 1f
                            scope.launch { yaw.animateTo(home.first, spring(dampingRatio = 0.8f, stiffness = 120f)) }
                            scope.launch { play() }
                        })
                    }
            ) {
                val view = View(centre, reach, yaw.value.toDouble(), pitch.toDouble(), zoom.toDouble(), size.width, size.height)
                drawFloor(view, faint)
                drawAxes(view, faint, measurer)
                // Planes and faces far to near, then lines, arrows and points on top.
                val flat = scene.shapes.filter { it is Shape3.Plane || it is Shape3.Face }.sortedByDescending { s ->
                    when (s) { is Shape3.Plane -> view.depth(s.through); is Shape3.Face -> s.corners.map(view::depth).average(); else -> 0.0 }
                }
                fun shown(s: Shape3) = (drawn.value * count - scene.shapes.indexOf(s)).coerceIn(0f, 1f)
                flat.filter { shown(it) > 0f }.forEach { s -> drawFlat(view, s, palette[s.tone % palette.size].let { c -> c.copy(alpha = c.alpha * shown(s)) }) }
                scene.shapes.filter { it !is Shape3.Plane && it !is Shape3.Face && shown(it) > 0f }.forEach { s ->
                    // An arrow or a segment grows from its start.
                    val t = shown(s).toDouble()
                    val grown = when (s) {
                        is Shape3.Arrow -> s.copy(to = s.from + (s.to - s.from) * t)
                        is Shape3.Segment -> s.copy(b = s.a + (s.b - s.a) * t)
                        else -> s
                    }
                    drawShape(view, grown, palette[s.tone % palette.size])
                }
                scene.shapes.filter { shown(it) >= 1f }.forEach { s -> drawLabel(view, s, palette[s.tone % palette.size], ink, measurer) }
            }
            ZoneSurface(shape = CircleShape, accent = true, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                Text("3D", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}

/** Where a point of space lands on the screen, and how near it is. */
private class View(val centre: Vec3, val reach: Double, yawDeg: Double, pitchDeg: Double, val zoom: Double, val w: Float, val h: Float) {
    private val cy = cos(Math.toRadians(yawDeg)); private val sy = sin(Math.toRadians(yawDeg))
    private val cp = cos(Math.toRadians(pitchDeg)); private val sp = sin(Math.toRadians(pitchDeg))
    private val distance = reach * 3.4
    private val scale = minOf(w, h) * 0.34 * zoom / reach * (distance + reach) / distance

    /** Turned about z (yaw), then tilted (pitch): x right, y into the screen, z up. */
    private fun turn(p: Vec3): Vec3 {
        val q = p - centre
        val x = q.x * cy - q.y * sy
        val y = q.x * sy + q.y * cy
        return Vec3(x, y * cp - q.z * sp, y * sp + q.z * cp)
    }

    fun depth(p: Vec3): Double = turn(p).y

    fun screen(p: Vec3): Offset {
        val t = turn(p)
        val k = distance / (distance + t.y)
        return Offset((w / 2 + t.x * scale * k).toFloat(), (h / 2 - t.z * scale * k).toFloat())
    }

    /** 1 nearest, 0 farthest within the box. */
    fun nearness(p: Vec3): Float = ((1 - depth(p) / reach) / 2).toFloat().coerceIn(0f, 1f)

    val box: Double get() = reach / 1.15
}

private fun DrawScope.drawFloor(v: View, color: Color) {
    // A grid on z = 0 around the origin, faint, fading with distance.
    val r = v.box
    val step = gridStep(r)
    val n = (r / step).toInt()
    for (i in -n..n) {
        val a = i * step
        listOf(Vec3(a, -n * step, 0.0) to Vec3(a, n * step, 0.0), Vec3(-n * step, a, 0.0) to Vec3(n * step, a, 0.0)).forEach { (p, q) ->
            drawLine(color.copy(alpha = 0.10f + 0.10f * v.nearness((p + q) * 0.5)), v.screen(p), v.screen(q), strokeWidth = 1.dp.toPx())
        }
    }
}

private fun gridStep(r: Double): Double {
    val raw = r / 4
    val p = Math.pow(10.0, kotlin.math.floor(Math.log10(raw)))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * p }.first { it >= raw }
}

private fun DrawScope.drawAxes(v: View, color: Color, measurer: androidx.compose.ui.text.TextMeasurer) {
    val r = v.box
    listOf(Vec3(1.0, 0.0, 0.0) to "x", Vec3(0.0, 1.0, 0.0) to "y", Vec3(0.0, 0.0, 1.0) to "z").forEach { (axis, name) ->
        val a = v.screen(axis * -r); val b = v.screen(axis * r)
        drawLine(color.copy(alpha = 0.55f), a, b, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
        sceneArrowHead(a, b, color.copy(alpha = 0.55f), 8.dp.toPx())
        drawText(measurer, name, b + Offset(6f, -6f), style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

private fun DrawScope.sceneArrowHead(from: Offset, to: Offset, color: Color, size: Float) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val left = to - Offset((size * cos(angle - 0.45)).toFloat(), (size * sin(angle - 0.45)).toFloat())
    val right = to - Offset((size * cos(angle + 0.45)).toFloat(), (size * sin(angle + 0.45)).toFloat())
    drawPath(Path().apply { moveTo(to.x, to.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }, color)
}

/** The plane cut by the box around the figure: where it crosses the box's twelve edges, in turn. */
private fun planePolygon(v: View, through: Vec3, normal: Vec3): List<Vec3> {
    val l = v.box * 1.05
    val c = v.centre
    val corners = (0 until 8).map { i -> c + Vec3(if (i and 1 == 0) -l else l, if (i and 2 == 0) -l else l, if (i and 4 == 0) -l else l) }
    val edges = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 1 to 3, 4 to 6, 5 to 7, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
    val n = normal.unit()
    val hits = ArrayList<Vec3>()
    for ((i, j) in edges) {
        val s = n dot (corners[i] - through); val t = n dot (corners[j] - through)
        if (s == 0.0) hits += corners[i]
        if (s * t < 0) hits += corners[i] + (corners[j] - corners[i]) * (s / (s - t))
    }
    if (hits.size < 3) return emptyList()
    val mid = hits.reduce { a, b -> a + b } * (1.0 / hits.size)
    val e1 = (hits[0] - mid).unit(); val e2 = n cross e1
    return hits.distinctBy { listOf(Math.round(it.x * 1e6), Math.round(it.y * 1e6), Math.round(it.z * 1e6)) }
        .sortedBy { p -> atan2((p - mid) dot e2, (p - mid) dot e1) }
}

private fun DrawScope.drawFlat(v: View, s: Shape3, color: Color) {
    val corners = when (s) { is Shape3.Plane -> planePolygon(v, s.through, s.normal); is Shape3.Face -> s.corners; else -> emptyList() }
    if (corners.size < 3) return
    val path = Path().apply {
        corners.map(v::screen).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
    if (s is Shape3.Face && s.soft) {
        // Lit from above left: brighter where the face looks at the light.
        val n = ((corners[1] - corners[0]) cross (corners[corners.size - 1] - corners[0])).unit()
        val light = abs(n dot Vec3(-0.45, -0.35, 0.82))
        val k = 0.35f + 0.65f * light.toFloat()
        drawPath(path, androidx.compose.ui.graphics.lerp(color.copy(alpha = 0.55f), Color.White.copy(alpha = 0.7f), (k - 0.35f) * 0.55f).copy(alpha = 0.22f + 0.38f * k))
        drawPath(path, color.copy(alpha = 0.10f), style = Stroke(width = 0.6.dp.toPx()))
        return
    }
    val pts = corners.map(v::screen)
    val top = pts.minOf { it.y }; val bottom = pts.maxOf { it.y }
    // Glass in the shape's tone: lighter at the top, a rim of light along its edges.
    drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.12f)), startY = top, endY = bottom.coerceAtLeast(top + 1)))
    drawPath(path, color.copy(alpha = 0.65f), style = Stroke(width = 1.4.dp.toPx()))
}

/** The part of a line inside the box, by the slab method. */
private fun clip(v: View, p: Vec3, u: Vec3): Pair<Vec3, Vec3>? {
    val l = v.box * 1.05
    var lo = Double.NEGATIVE_INFINITY; var hi = Double.POSITIVE_INFINITY
    val c = v.centre
    for ((pi, ui, ci) in listOf(Triple(p.x, u.x, c.x), Triple(p.y, u.y, c.y), Triple(p.z, u.z, c.z))) {
        if (abs(ui) < 1e-12) { if (abs(pi - ci) > l) return null; continue }
        val t1 = (ci - l - pi) / ui; val t2 = (ci + l - pi) / ui
        lo = max(lo, minOf(t1, t2)); hi = minOf(hi, max(t1, t2))
    }
    return if (lo > hi) null else (p + u * lo) to (p + u * hi)
}

private fun DrawScope.drawShape(v: View, s: Shape3, color: Color) {
    when (s) {
        is Shape3.Line -> clip(v, s.through, s.direction)?.let { (a, b) ->
            drawLine(color.copy(alpha = 0.9f), v.screen(a), v.screen(b), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        is Shape3.Segment -> drawLine(
            color.copy(alpha = 0.9f), v.screen(s.a), v.screen(s.b), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null
        )
        is Shape3.Arrow -> {
            val a = v.screen(s.from); val b = v.screen(s.to)
            val near = 0.65f + 0.35f * v.nearness(s.to)
            // A soft glow under the shaft, then the shaft and its head.
            drawLine(color.copy(alpha = 0.18f * near), a, b, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color.copy(alpha = near), a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            sceneArrowHead(a, b, color.copy(alpha = near), 13.dp.toPx())
        }
        is Shape3.Point -> {
            val p = v.screen(s.at)
            val near = 0.6f + 0.4f * v.nearness(s.at)
            drawCircle(color.copy(alpha = 0.22f * near), 11.dp.toPx(), p)
            drawCircle(color.copy(alpha = near), 5.dp.toPx(), p)
            drawCircle(Color.White.copy(alpha = 0.7f * near), 2.dp.toPx(), p - Offset(1.5f, 1.5f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawLabel(v: View, s: Shape3, color: Color, ink: Color, measurer: androidx.compose.ui.text.TextMeasurer) {
    if (s.label.isEmpty()) return
    val at = when (s) {
        is Shape3.Point -> s.at
        is Shape3.Arrow -> s.to
        is Shape3.Line -> clip(v, s.through, s.direction)?.second ?: return
        is Shape3.Plane -> planePolygon(v, s.through, s.normal).maxByOrNull { v.screen(it).y } ?: return
        is Shape3.Segment -> (s.a + s.b) * 0.5
        is Shape3.Face -> s.corners.reduce { a, b -> a + b } * (1.0 / s.corners.size)
    }
    val p = v.screen(at)
    val text = measurer.measure(s.label, TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    val place = Offset((p.x + 8).coerceIn(0f, size.width - text.size.width), (p.y - text.size.height - 4).coerceIn(0f, size.height - text.size.height))
    drawText(text, topLeft = place, color = color.copy(alpha = 1f).let { androidx.compose.ui.graphics.lerp(it, ink, 0.35f) })
}

/**
 * The first view: −35° and 22° as a rule; when the scene shows a face (a
 * section, a triangle), turned so that face is seen at a slant, never on
 * its edge (|n·d| near 0.55, d where the eye looks).
 */
private fun homeView(scene: Scene): Pair<Float, Float> {
    val face = scene.shapes.filterIsInstance<Shape3.Face>().filter { !it.soft && it.corners.size >= 3 }
        .maxByOrNull { f -> ((f.corners[1] - f.corners[0]) cross (f.corners[2] - f.corners[0])).norm } ?: return -35f to 22f
    val n = ((face.corners[1] - face.corners[0]) cross (face.corners[2] - face.corners[0])).unit()
    fun look(yaw: Double, pitch: Double): Double {
        val cy = cos(Math.toRadians(yaw)); val sy = sin(Math.toRadians(yaw)); val cp = cos(Math.toRadians(pitch)); val sp = sin(Math.toRadians(pitch))
        return abs(n dot Vec3(sy * cp, cy * cp, -sp))
    }
    if (look(-35.0, 22.0) > 0.45) return -35f to 22f
    var best = -35.0 to 22.0; var score = Double.MAX_VALUE
    for (yaw in -180 until 180 step 5) for (pitch in 15..40 step 5) {
        // Near 0.55, as a textbook draws it, and close to the usual view.
        val s = abs(look(yaw.toDouble(), pitch.toDouble()) - 0.55) + (180 - abs(((yaw + 35 + 540) % 360) - 180.0)) / 600.0 + abs(pitch - 22) / 600.0
        if (s < score) { score = s; best = yaw.toDouble() to pitch.toDouble() }
    }
    return best.first.toFloat() to best.second.toFloat()
}