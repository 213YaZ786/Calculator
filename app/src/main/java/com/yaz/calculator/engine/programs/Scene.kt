package com.yaz.calculator.engine.programs

import kotlin.math.sqrt

/** A point or a vector of space. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val norm: Double get() = sqrt(this dot this)
    fun unit(): Vec3 = norm.let { if (it == 0.0) this else this * (1 / it) }
    override fun toString() = "(${show(x)} ; ${show(y)} ; ${show(z)})"

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

/**
 * What a program shows in space: points, vectors drawn as arrows, lines,
 * planes, segments and faces, each in one of the scene's [tone]s (0 the
 * accent, then the accent turned, as the graph's curves).
 */
sealed interface Shape3 {
    val label: String
    val tone: Int

    data class Point(val at: Vec3, override val label: String = "", override val tone: Int = 0) : Shape3
    data class Arrow(val from: Vec3, val to: Vec3, override val label: String = "", override val tone: Int = 0) : Shape3
    data class Line(val through: Vec3, val direction: Vec3, override val label: String = "", override val tone: Int = 0) : Shape3
    data class Plane(val through: Vec3, val normal: Vec3, override val label: String = "", override val tone: Int = 0) : Shape3
    data class Segment(val a: Vec3, val b: Vec3, override val label: String = "", override val tone: Int = 0, val dashed: Boolean = false) : Shape3
    /** [soft]: a piece of a surface (a solid of revolution), shaded by the light, its edges faint. */
    data class Face(val corners: List<Vec3>, override val label: String = "", override val tone: Int = 0, val soft: Boolean = false) : Shape3
}

/** [staged]: the shapes come one after another (Chasles' arrows, then the one that closes them). */
data class Scene(val shapes: List<Shape3>, val staged: Boolean = false) {
    /** Every place the scene names, for its size. */
    val anchors: List<Vec3> get() = shapes.flatMap { s ->
        when (s) {
            is Shape3.Point -> listOf(s.at)
            is Shape3.Arrow -> listOf(s.from, s.to)
            is Shape3.Line -> listOf(s.through)
            is Shape3.Plane -> listOf(s.through)
            is Shape3.Segment -> listOf(s.a, s.b)
            is Shape3.Face -> s.corners
        }
    } + Vec3.ZERO
}
