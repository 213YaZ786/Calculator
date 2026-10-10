package com.yaz.calculator.engine.programs

import kotlin.math.abs

/** A point or a vector of the plane. */
data class P2(val x: Double, val y: Double) {
    operator fun plus(o: P2) = P2(x + o.x, y + o.y)
    operator fun minus(o: P2) = P2(x - o.x, y - o.y)
    operator fun times(k: Double) = P2(x * k, y * k)
}

/**
 * What a program shows in the plane, each in one of the figure's tones (0
 * the accent, then the accent turned, as the graph's curves): curves,
 * areas between two curves, rectangles and polygons (sums of rectangles,
 * trapezoids, bars), segments, arrows, points, circles.
 */
sealed interface Shape2 {
    val label: String
    val tone: Int

    data class Curve(val f: (Double) -> Double, val from: Double = Double.NEGATIVE_INFINITY, val to: Double = Double.POSITIVE_INFINITY, override val label: String = "", override val tone: Int = 0, val dashed: Boolean = false) : Shape2
    /** Between [top] and [bottom] (the axis when null), signed: below the axis in the second tone. */
    data class Area(val top: (Double) -> Double, val from: Double, val to: Double, val bottom: ((Double) -> Double)? = null, override val label: String = "", override val tone: Int = 0) : Shape2
    data class Polygon(val corners: List<P2>, override val label: String = "", override val tone: Int = 0) : Shape2
    data class Segment(val a: P2, val b: P2, override val label: String = "", override val tone: Int = 0, val dashed: Boolean = false) : Shape2
    /** Points joined in turn: a parametric or polar curve, Euler's broken line, a cobweb. */
    data class Polyline(val points: List<P2>, override val label: String = "", override val tone: Int = 0, val dashed: Boolean = false) : Shape2
    data class Arrow(val from: P2, val to: P2, override val label: String = "", override val tone: Int = 0) : Shape2
    data class Point(val at: P2, override val label: String = "", override val tone: Int = 0) : Shape2
    /** Many small dots: Monte-Carlo's draws, a cloud of points. */
    data class Dots(val points: List<P2>, override val label: String = "", override val tone: Int = 0) : Shape2
    data class Circle(val centre: P2, val radius: Double, override val label: String = "", override val tone: Int = 0) : Shape2

    companion object {
        fun rect(x0: Double, y0: Double, x1: Double, y1: Double, tone: Int = 0, label: String = "") =
            Polygon(listOf(P2(x0, y0), P2(x1, y0), P2(x1, y1), P2(x0, y1)), label, tone)
    }
}

/**
 * A figure of the plane. [staged]: its shapes come one after another (the
 * arrows of Chasles, a sum's rectangles), else all at once. [window]: the
 * part of the plane shown, else around what the figure names. [square]:
 * one unit the same length on both axes (circles, vectors).
 */
data class Figure(
    val shapes: List<Shape2>,
    val staged: Boolean = false,
    val window: Box2? = null,
    val square: Boolean = false
) {
    /** The part of the plane to show, the axes' origin in sight when near. */
    fun frame(): Box2 {
        window?.let { return it }
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        fun add(p: P2) { if (p.x.isFinite() && p.y.isFinite()) { xs += p.x; ys += p.y } }
        val sampled = ArrayList<Pair<(Double) -> Double, ClosedFloatingPointRange<Double>?>>()
        for (s in shapes) when (s) {
            is Shape2.Curve -> sampled += s.f to if (s.from.isFinite() && s.to.isFinite()) s.from..s.to else null
            is Shape2.Area -> { add(P2(s.from, 0.0)); add(P2(s.to, 0.0)); sampled += s.top to s.from..s.to; s.bottom?.let { sampled += it to s.from..s.to } }
            is Shape2.Polygon -> s.corners.forEach(::add)
            is Shape2.Segment -> { add(s.a); add(s.b) }
            is Shape2.Polyline -> s.points.forEach(::add)
            is Shape2.Arrow -> { add(s.from); add(s.to) }
            is Shape2.Point -> add(s.at)
            is Shape2.Dots -> s.points.forEach(::add)
            is Shape2.Circle -> { add(s.centre - P2(s.radius, s.radius)); add(s.centre + P2(s.radius, s.radius)) }
        }
        // Curves' own bounds count too, else a single point would set the whole window.
        sampled.mapNotNull { it.second }.forEach { r -> xs += r.start; xs += r.endInclusive }
        var x0 = xs.minOrNull() ?: -5.0; var x1 = xs.maxOrNull() ?: 5.0
        if (x1 - x0 < 1e-9) { x0 -= 1; x1 += 1 }
        val pad = (x1 - x0) * 0.12
        x0 -= pad; x1 += pad
        // The origin in sight when it is near.
        if (x0 > 0 && x0 < (x1 - x0) * 0.6) x0 = -(x1 - x0) * 0.06
        if (x1 < 0 && -x1 < (x1 - x0) * 0.6) x1 = (x1 - x0) * 0.06
        for ((f, r) in sampled) {
            val a = r?.start?.coerceAtLeast(x0) ?: x0; val b = r?.endInclusive?.coerceAtMost(x1) ?: x1
            for (i in 0..200) {
                val x = a + (b - a) * i / 200
                val y = runCatching { f(x) }.getOrDefault(Double.NaN)
                if (y.isFinite()) ys += y
            }
        }
        if (ys.isEmpty()) ys += listOf(-1.0, 1.0)
        // Poles left out: the middle 96 % of the values.
        ys.sort()
        val cut = (ys.size * 0.02).toInt()
        var y0 = minOf(0.0, ys[cut]); var y1 = maxOf(0.0, ys[ys.size - 1 - cut])
        if (y1 - y0 < 1e-9) { y0 -= 1; y1 += 1 }
        val padY = (y1 - y0) * 0.12
        y0 -= padY; y1 += padY
        if (abs(y0) < padY * 1.01 && ys[cut] >= 0) y0 = -padY
        return Box2(x0, x1, y0, y1)
    }
}

data class Box2(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double)
