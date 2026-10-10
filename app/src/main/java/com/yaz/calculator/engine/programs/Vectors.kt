package com.yaz.calculator.engine.programs

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Vectors and geometry from the official texts: Seconde 2026 (vectors,
// determinant, lines; alignment and line through two points as its
// algorithms; triangle centres as approfondissements), Première 2026
// (normal vector, projection, circle, MA·MB = 0, parabola), Terminale
// (space: Chasles, bases, coplanarity, projection, sections) and AQA
// Further Maths F (angles). Plane figures when every point has two
// coordinates, space otherwise.

/** A point or a vector typed with two or three numbers; z = 0 for two. */
private fun Inputs.point(symbol: String): Vec3 = list(symbol).let { require(it.size in 2..3); Vec3(it[0], it[1], it.getOrElse(2) { 0.0 }) }

private fun Inputs.flat(vararg symbols: String) = symbols.filter { has(it) }.all { list(it).size == 2 }

private fun P(v: Vec3) = P2(v.x, v.y)

/** (x ; y) or (x ; y ; z). */
private fun co(v: Vec3, flat: Boolean) = if (flat) "(${show(v.x)} ; ${show(v.y)})" else v.toString()

private fun near0(x: Double, scale: Double = 1.0) = abs(x) < 1e-10 * max(1.0, scale)

private fun det2(a: Vec3, b: Vec3) = a.x * b.y - a.y * b.x

private fun det3(a: Vec3, b: Vec3, c: Vec3) = (a cross b) dot c

/** ax + by + c = 0, written as a student does. */
private fun lineEq(a: Double, b: Double, c: Double): String =
    (listOf(a to "x", b to "y").filter { it.first != 0.0 }.joinToString(" + ") { (k, s) -> if (k == 1.0) s else if (k == -1.0) "−$s" else "${show(k)}$s" } +
        (if (c != 0.0) " + ${show(c)}" else "") + " = 0").replace("+ −", "− ").replace("+ -", "− ")

/** The line ax + by + c = 0 drawn over the figure. */
private fun lineShape(a: Double, b: Double, c: Double, label: String, tone: Int): Shape2 =
    if (abs(b) > 1e-12) Shape2.Curve({ x -> (-c - a * x) / b }, label = label, tone = tone)
    else Shape2.Segment(P2(-c / a, -1e4), P2(-c / a, 1e4), label = label, tone = tone)

/** The window around [points], one unit the same on both axes. */
private fun around(points: List<P2>): Box2 {
    val x0 = points.minOf { it.x }; val x1 = points.maxOf { it.x }; val y0 = points.minOf { it.y }; val y1 = points.maxOf { it.y }
    val pad = max(1.0, max(x1 - x0, y1 - y0) * 0.25)
    return Box2(min(x0, 0.0) - pad, max(x1, 0.0) + pad, min(y0, 0.0) - pad, max(y1, 0.0) + pad)
}

private val ORIGIN = Vec3.ZERO

val vectors: List<Program> = listOf(
    Rich(
        "chasles-vectors", Subject.GEOMETRY, title("Relation de Chasles (vecteurs)", "Chasles' relation (vectors)"), "AB + BC = AC",
        listOf(list("A", "A (x, y) ou (x, y, z)", "A (x, y) or (x, y, z)"), list("B", "B", "B"), list("C", "C", "C"), list("D", "D (facultatif)", "D (optional)", optional = true))
    ) { v ->
        val names = listOf("A", "B", "C", "D").filter { v.has(it) }
        val pts = names.map { v.point(it) }
        val flat = v.flat(*names.toTypedArray())
        val last = names.last()
        val steps = names.zipWithNext()
        val total = pts.last() - pts.first()
        val out = mutableListOf(res(steps.joinToString(" + ") { (p, q) -> "$p$q" } + " = A$last"))
        steps.forEachIndexed { i, (p, q) -> out += stp("$p$q = ${co(pts[i + 1] - pts[i], flat)}") }
        out += stp(say("Somme des coordonnées : ", "Sum of the coordinates: ") + "A$last = ${co(total, flat)}")
        out += stp(say("On va de A à $last en passant par ", "From A to $last through ") + names.drop(1).dropLast(1).joinToString(", ") + say(" : le chemin ne change pas la flèche", ": the path does not change the arrow"))
        if (flat) {
            val shapes = mutableListOf<Shape2>()
            names.forEachIndexed { i, n -> shapes += Shape2.Point(P(pts[i]), n, tone = 4) }
            steps.forEachIndexed { i, (p, q) -> shapes += Shape2.Arrow(P(pts[i]), P(pts[i + 1]), "$p$q", tone = listOf(0, 1, 3)[i]) }
            shapes += Shape2.Arrow(P(pts.first()), P(pts.last()), "A$last", tone = 2)
            Outcome(out, figure = Figure(shapes, staged = true, square = true, window = around(pts.map(::P))))
        } else {
            val shapes = mutableListOf<Shape3>()
            names.forEachIndexed { i, n -> shapes += Shape3.Point(pts[i], n, 4) }
            steps.forEachIndexed { i, (p, q) -> shapes += Shape3.Arrow(pts[i], pts[i + 1], "$p$q", listOf(0, 1, 3)[i]) }
            shapes += Shape3.Arrow(pts.first(), pts.last(), "A$last", 2)
            Outcome(out, scene = Scene(shapes, staged = true))
        }
    },
    Rich(
        "collinear", Subject.GEOMETRY, title("Vecteurs colinéaires", "Collinear vectors"), "det(u, v) = xy′ − x′y = 0",
        listOf(list("u", "u (x, y) ou (x, y, z)", "u (x, y) or (x, y, z)"), list("v", "v", "v"))
    ) { v ->
        val u = v.point("u"); val w = v.point("v")
        val flat = v.flat("u", "v")
        val out = mutableListOf<Line>()
        val zero = if (flat) near0(det2(u, w), u.norm * w.norm) else (u cross w).norm < 1e-10 * max(1.0, u.norm * w.norm)
        if (flat) out += res("det(u, v) = ${show(u.x)}×${show(w.y)} − ${show(u.y)}×${show(w.x)} = ${show(det2(u, w))}")
        else out += res("u ∧ v = ${co(u cross w, false)}")
        val k = if (u.norm > 0) (u dot w) / (u dot u) else 0.0
        out += res(if (zero) say("Colinéaires : v = ${show(k)}·u", "Collinear: v = ${show(k)}·u") else say("Non colinéaires : (u, v) est une base", "Not collinear: (u, v) is a basis"))
        if (flat) out += stp(say("Coordonnées proportionnelles ⇔ det = 0", "Proportional coordinates ⇔ det = 0"))
        if (flat) Outcome(out, figure = Figure(listOf(Shape2.Arrow(P2(0.0, 0.0), P(u), "u"), Shape2.Arrow(P2(0.0, 0.0), P(w), "v", tone = 1)), square = true, window = around(listOf(P(u), P(w)))))
        else Outcome(out, scene = Scene(listOf(Shape3.Arrow(ORIGIN, u, "u", 0), Shape3.Arrow(ORIGIN, w, "v", 1))))
    },
    Rich(
        "alignment", Subject.GEOMETRY, title("Trois points alignés ?", "Three points in a line?"), "det(AB, AC) = 0",
        listOf(list("A", "A (x, y)", "A (x, y)"), list("B", "B", "B"), list("C", "C", "C"))
    ) { v ->
        val a = v.point("A"); val b = v.point("B"); val c = v.point("C")
        val ab = b - a; val ac = c - a
        val d = det2(ab, ac)
        val aligned = near0(d, ab.norm * ac.norm)
        Outcome(
            listOf(
                res(if (aligned) say("A, B et C sont alignés", "A, B and C are in a line") else say("A, B et C ne sont pas alignés", "A, B and C are not in a line")),
                stp("AB = ${co(ab, true)}   AC = ${co(ac, true)}"),
                stp("det(AB, AC) = ${show(ab.x)}×${show(ac.y)} − ${show(ab.y)}×${show(ac.x)} = ${show(d)}"),
                stp(say("Aire du triangle ABC = |det| / 2 = ${show(abs(d) / 2)}", "Area of triangle ABC = |det| / 2 = ${show(abs(d) / 2)}"))
            ),
            figure = Figure(
                listOf(Shape2.Point(P(a), "A", 4), Shape2.Point(P(b), "B", 4), Shape2.Point(P(c), "C", 4), Shape2.Arrow(P(a), P(b), "AB"), Shape2.Arrow(P(a), P(c), "AC", tone = 1)) +
                    if (aligned) listOf(Shape2.Segment(P(a) + (P(b) - P(a)) * -10.0, P(a) + (P(b) - P(a)) * 10.0, tone = 2, dashed = true)) else listOf(Shape2.Polygon(listOf(P(a), P(b), P(c)), tone = 2)),
                staged = true, square = true, window = around(listOf(P(a), P(b), P(c)))
            )
        )
    },
    Rich(
        "line-two-points", Subject.GEOMETRY, title("Droite passant par deux points", "Line through two points"), "y = mx + p, m = (y_B − y_A)/(x_B − x_A)",
        listOf(list("A", "A (x, y)", "A (x, y)"), list("B", "B (x, y)", "B (x, y)"))
    ) { v ->
        val a = v.point("A"); val b = v.point("B")
        val u = b - a
        require(u.norm > 0)
        // −u_y·x + u_x·y + c = 0 holds at A.
        val ca = -u.y; val cb = u.x; val cc = -(ca * a.x + cb * a.y)
        val out = mutableListOf<Line>()
        if (abs(u.x) > 1e-12) {
            val m = u.y / u.x; val p = a.y - m * a.x
            out += res(("y = ${show(m)}x + ${show(p)}").replace("+ −", "− "))
            out += stp(say("Coefficient directeur m = ${show(u.y)} / ${show(u.x)} = ${show(m)}, ordonnée à l'origine p = ${show(p)}", "Slope m = ${show(u.y)} / ${show(u.x)} = ${show(m)}, intercept p = ${show(p)}"))
        } else out += res(say("x = ${show(a.x)} (droite parallèle à l'axe des ordonnées)", "x = ${show(a.x)} (parallel to the y axis)"))
        out += res(lineEq(ca, cb, cc))
        out += stp(say("Vecteur directeur AB = ${co(u, true)}, vecteur normal ${co(Vec3(ca, cb, 0.0), true)}", "Direction vector AB = ${co(u, true)}, normal vector ${co(Vec3(ca, cb, 0.0), true)}"))
        Outcome(out, figure = Figure(
            listOf(lineShape(ca, cb, cc, "(AB)", 2), Shape2.Point(P(a), "A", 4), Shape2.Point(P(b), "B", 4), Shape2.Arrow(P(a), P(b), "u")),
            staged = true, square = true, window = around(listOf(P(a), P(b)))
        ))
    },
    Rich(
        "two-lines-plane", Subject.GEOMETRY, title("Deux droites du plan", "Two lines in the plane"), "ax + by + c = 0, a′x + b′y + c′ = 0",
        listOf(list("d", "d : a, b, c", "d: a, b, c"), list("d′", "d′ : a′, b′, c′", "d′: a′, b′, c′"))
    ) { v ->
        val d1 = v.list("d"); val d2 = v.list("d′")
        require(d1.size == 3 && d2.size == 3 && (d1[0] != 0.0 || d1[1] != 0.0) && (d2[0] != 0.0 || d2[1] != 0.0))
        val (a, b, c) = d1; val (a2, b2, c2) = d2
        val det = a * b2 - a2 * b
        val out = mutableListOf(stp("d : ${lineEq(a, b, c)}   d′ : ${lineEq(a2, b2, c2)}"), stp("det = ab′ − a′b = ${show(det)}"))
        val shapes = mutableListOf(lineShape(a, b, c, "d", 0), lineShape(a2, b2, c2, "d′", 1))
        var pts = listOf<P2>()
        if (near0(det, abs(a * b2) + abs(a2 * b))) {
            // Parallel: the same line when the third coefficients agree too.
            val same = near0(a * c2 - a2 * c, abs(a * c2) + abs(a2 * c)) && near0(b * c2 - b2 * c, abs(b * c2) + abs(b2 * c))
            out.add(0, res(if (same) say("d et d′ sont confondues", "d and d′ are the same line") else say("d et d′ sont parallèles, sans point commun", "d and d′ are parallel, with no common point")))
        } else {
            val x = (b * c2 - b2 * c) / det; val y = (a2 * c - a * c2) / det
            val angle = Math.toDegrees(acos((abs(a * a2 + b * b2) / (sqrt(a * a + b * b) * sqrt(a2 * a2 + b2 * b2))).coerceIn(0.0, 1.0)))
            out.add(0, res(say("Sécantes en I(${show(x)} ; ${show(y)})", "Meeting at I(${show(x)} ; ${show(y)})")))
            out += stp(say("Angle entre les droites ${show(angle)}°", "Angle between the lines ${show(angle)}°") + if (abs(angle - 90) < 1e-9) say(" : perpendiculaires", ": perpendicular") else "")
            shapes += Shape2.Point(P2(x, y), "I", 2)
            pts = listOf(P2(x, y))
        }
        Outcome(out, figure = Figure(shapes, staged = true, square = true, window = around(pts.ifEmpty { listOf(P2(0.0, 0.0)) } + P2(1.0, 1.0))))
    },
    Rich(
        "decompose", Subject.GEOMETRY, title("Décomposer dans une base", "Coordinates in a basis"), "w = αu + βv (+ γt)",
        listOf(list("w", "w", "w"), list("u", "u", "u"), list("v", "v", "v"), list("t", "t (dans l'espace)", "t (in space)", optional = true))
    ) { v ->
        val w = v.point("w"); val u = v.point("u"); val vv = v.point("v")
        val flat = v.flat("w", "u", "v") && !v.has("t")
        if (flat) {
            val d = det2(u, vv)
            if (near0(d, u.norm * vv.norm)) return@Rich lines(res(say("u et v sont colinéaires : ils ne forment pas une base", "u and v are collinear: they are not a basis")))
            val al = det2(w, vv) / d; val be = det2(u, w) / d
            val p = u * al
            Outcome(
                listOf(
                    res("w = ${show(al)}u + ${show(be)}v".replace("+ −", "− ")),
                    stp(say("Système ${show(u.x)}α + ${show(vv.x)}β = ${show(w.x)}, ${show(u.y)}α + ${show(vv.y)}β = ${show(w.y)}", "System ${show(u.x)}α + ${show(vv.x)}β = ${show(w.x)}, ${show(u.y)}α + ${show(vv.y)}β = ${show(w.y)}")),
                    stp("α = det(w, v)/det(u, v), β = det(u, w)/det(u, v), det(u, v) = ${show(d)}")
                ),
                figure = Figure(
                    listOf(
                        Shape2.Arrow(P2(0.0, 0.0), P(u), "u", 3), Shape2.Arrow(P2(0.0, 0.0), P(vv), "v", 3),
                        Shape2.Arrow(P2(0.0, 0.0), P(p), "αu", 0), Shape2.Arrow(P(p), P(w), "βv", 1), Shape2.Arrow(P2(0.0, 0.0), P(w), "w", 2)
                    ),
                    staged = true, square = true, window = around(listOf(P(u), P(vv), P(w), P(p)))
                )
            )
        } else {
            val t = v.point("t")
            val d = det3(u, vv, t)
            if (near0(d, u.norm * vv.norm * t.norm)) return@Rich lines(res(say("u, v et t sont coplanaires : ils ne forment pas une base", "u, v and t are coplanar: they are not a basis")))
            val al = det3(w, vv, t) / d; val be = det3(u, w, t) / d; val ga = det3(u, vv, w) / d
            val p1 = u * al; val p2 = p1 + vv * be
            Outcome(
                listOf(
                    res("w = ${show(al)}u + ${show(be)}v + ${show(ga)}t".replace("+ −", "− ")),
                    stp(say("Système 3 × 3 résolu par Cramer, det(u, v, t) = ${show(d)}", "3 × 3 system solved by Cramer, det(u, v, t) = ${show(d)}"))
                ),
                scene = Scene(listOf(
                    Shape3.Arrow(ORIGIN, p1, "αu", 0), Shape3.Arrow(p1, p2, "βv", 1), Shape3.Arrow(p2, w, "γt", 3), Shape3.Arrow(ORIGIN, w, "w", 2)
                ), staged = true)
            )
        }
    },
    Rich(
        "coplanar", Subject.GEOMETRY, title("Vecteurs coplanaires", "Coplanar vectors"), "det(u, v, w) = (u ∧ v)·w = 0",
        listOf(list("u", "u (x, y, z)", "u (x, y, z)"), list("v", "v", "v"), list("w", "w", "w"))
    ) { v ->
        val u = v.vec("u"); val vv = v.vec("v"); val w = v.vec("w")
        val d = det3(u, vv, w)
        val n = u cross vv
        val zero = near0(d, u.norm * vv.norm * w.norm)
        val out = mutableListOf(res("det(u, v, w) = ${show(d)}"))
        if (zero && n.norm > 1e-12) {
            // w = αu + βv: two of the three equations, the pair whose determinant is largest.
            val pairs = listOf(Triple(u.x * vv.y - u.y * vv.x, 0, 1), Triple(u.x * vv.z - u.z * vv.x, 0, 2), Triple(u.y * vv.z - u.z * vv.y, 1, 2))
            val (dd, i, j) = pairs.maxBy { abs(it.first) }
            fun c(x: Vec3, k: Int) = when (k) { 0 -> x.x; 1 -> x.y; else -> x.z }
            val al = (c(w, i) * c(vv, j) - c(w, j) * c(vv, i)) / dd; val be = (c(u, i) * c(w, j) - c(u, j) * c(w, i)) / dd
            out += res(say("Coplanaires : w = ${show(al)}u + ${show(be)}v", "Coplanar: w = ${show(al)}u + ${show(be)}v").replace("+ −", "− "))
        } else out += res(if (zero) say("Coplanaires (u et v colinéaires)", "Coplanar (u and v collinear)") else say("Non coplanaires : (u, v, w) est une base de l'espace", "Not coplanar: (u, v, w) is a basis of space"))
        out += stp("u ∧ v = ${co(n, false)}")
        val shapes = mutableListOf<Shape3>(Shape3.Arrow(ORIGIN, u, "u", 0), Shape3.Arrow(ORIGIN, vv, "v", 1), Shape3.Arrow(ORIGIN, w, "w", 2))
        if (n.norm > 1e-12) shapes.add(0, Shape3.Plane(ORIGIN, n, say("plan (u, v)", "plane (u, v)"), 3))
        Outcome(out, scene = Scene(shapes, staged = true))
    },
    Rich(
        "normal-projection", Subject.GEOMETRY, title("Vecteur normal et projeté orthogonal", "Normal vector and orthogonal projection"), "n(a ; b) ⊥ d : ax + by + c = 0",
        listOf(list("A", "A, point de d", "A, point of d"), list("n", "n, vecteur normal", "n, normal vector"), list("M", "M, à projeter (facultatif)", "M, to project (optional)", optional = true))
    ) { v ->
        val a = v.point("A"); val n = v.point("n")
        require(n.norm > 0)
        val c = -(n.x * a.x + n.y * a.y)
        val out = mutableListOf(res("d : ${lineEq(n.x, n.y, c)}"), stp(say("M(x ; y) ∈ d ⇔ AM·n = 0 ⇔ ${show(n.x)}(x − ${show(a.x)}) + ${show(n.y)}(y − ${show(a.y)}) = 0", "M(x ; y) ∈ d ⇔ AM·n = 0 ⇔ ${show(n.x)}(x − ${show(a.x)}) + ${show(n.y)}(y − ${show(a.y)}) = 0").replace("− −", "+ ")))
        val shapes = mutableListOf(lineShape(n.x, n.y, c, "d", 0), Shape2.Point(P(a), "A", 4), Shape2.Arrow(P(a), P(a + n), "n", 1))
        val pts = mutableListOf(P(a), P(a + n))
        if (v.has("M")) {
            val m = v.point("M")
            val k = ((m - a) dot n) / (n dot n)
            val h = m - n * k
            val dist = abs(n.x * m.x + n.y * m.y + c) / sqrt(n.x * n.x + n.y * n.y)
            out += res(say("Projeté H${co(h, true)}, distance MH = ${show(dist)}", "Projection H${co(h, true)}, distance MH = ${show(dist)}"))
            out += stp("MH = |ax + by + c| / √(a² + b²) = |${show(n.x * m.x + n.y * m.y + c)}| / ${show(sqrt(n.x * n.x + n.y * n.y))}")
            shapes += Shape2.Point(P(m), "M", 2); shapes += Shape2.Segment(P(m), P(h), tone = 2, dashed = true); shapes += Shape2.Point(P(h), "H", 2)
            pts += listOf(P(m), P(h))
        }
        Outcome(out, figure = Figure(shapes, staged = true, square = true, window = around(pts)))
    },
    Rich(
        "circle-equation", Subject.GEOMETRY, title("Équation d'un cercle", "Equation of a circle"), "(x − a)² + (y − b)² = r²",
        listOf(list("Ω", "Centre Ω (vide si équation)", "Centre Ω (empty for an equation)", optional = true), num("r", "Rayon r", "Radius r", optional = true), list("e", "Ou x² + y² + αx + βy + γ = 0 : α, β, γ", "Or x² + y² + αx + βy + γ = 0: α, β, γ", optional = true))
    ) { v ->
        val (cx, cy, r2) = if (v.has("e")) {
            val e = v.list("e")
            require(e.size == 3)
            Triple(-e[0] / 2, -e[1] / 2, e[0] * e[0] / 4 + e[1] * e[1] / 4 - e[2])
        } else {
            val o = v.point("Ω"); val r = v["r"]
            require(r >= 0)
            Triple(o.x, o.y, r * r)
        }
        val out = mutableListOf<Line>()
        when {
            r2 < -1e-12 -> return@Rich lines(res(say("r² = ${show(r2)} < 0 : aucun point, ce n'est pas un cercle", "r² = ${show(r2)} < 0: no point, not a circle")))
            abs(r2) <= 1e-12 -> return@Rich lines(res(say("Un seul point : Ω(${show(cx)} ; ${show(cy)})", "A single point: Ω(${show(cx)} ; ${show(cy)})")))
        }
        val r = sqrt(r2)
        out += res(("(x − ${show(cx)})² + (y − ${show(cy)})² = ${show(r2)}").replace("− −", "+ "))
        out += res(("x² + y² + ${show(-2 * cx)}x + ${show(-2 * cy)}y + ${show(cx * cx + cy * cy - r2)} = 0").replace("+ −", "− "))
        out += stp(say("Centre Ω(${show(cx)} ; ${show(cy)}), rayon ${show(r)}", "Centre Ω(${show(cx)} ; ${show(cy)}), radius ${show(r)}"))
        if (v.has("e")) out += stp(say("Forme canonique : x² + αx = (x + α/2)² − α²/4, de même en y", "Completing the square: x² + αx = (x + α/2)² − α²/4, the same in y"))
        Outcome(out, figure = Figure(
            listOf(Shape2.Circle(P2(cx, cy), r, "C"), Shape2.Point(P2(cx, cy), "Ω", 2), Shape2.Segment(P2(cx, cy), P2(cx + r, cy), "r", 1)),
            staged = true, square = true, window = Box2(min(cx - r, 0.0) - r * 0.3, max(cx + r, 0.0) + r * 0.3, min(cy - r, 0.0) - r * 0.3, max(cy + r, 0.0) + r * 0.3)
        ))
    },
    Rich(
        "right-angle-circle", Subject.GEOMETRY, title("Ensemble des M tels que MA·MB = 0", "Points M with MA·MB = 0"), "MA·MB = 0 ⇔ M sur le cercle de diamètre [AB]",
        listOf(list("A", "A (x, y)", "A (x, y)"), list("B", "B (x, y)", "B (x, y)"))
    ) { v ->
        val a = v.point("A"); val b = v.point("B")
        val i = (a + b) * 0.5; val r = (b - a).norm / 2
        require(r > 0)
        // A point M of the circle, its two segments, the right angle at M.
        val t = atan2(b.y - a.y, b.x - a.x) + 1.1
        val m = P2(i.x + r * cos(t), i.y + r * sin(t))
        val ua = (P(a) - m).let { it * (1 / sqrt(it.x * it.x + it.y * it.y)) }; val ub = (P(b) - m).let { it * (1 / sqrt(it.x * it.x + it.y * it.y)) }
        val k = r * 0.12
        Outcome(
            listOf(
                res(say("Le cercle de centre I${co(i, true)}, milieu de [AB], de rayon ${show(r)}", "The circle with centre I${co(i, true)}, midpoint of [AB], radius ${show(r)}")),
                res(("(x − ${show(i.x)})² + (y − ${show(i.y)})² = ${show(r * r)}").replace("− −", "+ ")),
                stp(say("MA·MB = (MI + IA)·(MI + IB) = MI² − IA² : nul quand MI = IA", "MA·MB = (MI + IA)·(MI + IB) = MI² − IA²: zero when MI = IA"))
            ),
            figure = Figure(
                listOf(
                    Shape2.Circle(P(i), r), Shape2.Point(P(a), "A", 4), Shape2.Point(P(b), "B", 4), Shape2.Point(P(i), "I", 3),
                    Shape2.Segment(m, P(a), tone = 1), Shape2.Segment(m, P(b), tone = 1),
                    Shape2.Polygon(listOf(m, m + ua * k, m + ua * k + ub * k, m + ub * k), tone = 2), Shape2.Point(m, "M", 1)
                ),
                staged = true, square = true, window = Box2(i.x - r * 1.4, i.x + r * 1.4, i.y - r * 1.4, i.y + r * 1.4)
            )
        )
    },
    Rich(
        "triangle-centres", Subject.GEOMETRY, title("Triangle : centres et droite d'Euler", "Triangle: centres and Euler line"), "G, O, H alignés, OH = 3·OG",
        listOf(list("A", "A (x, y)", "A (x, y)"), list("B", "B (x, y)", "B (x, y)"), list("C", "C (x, y)", "C (x, y)"))
    ) { v ->
        val a = P(v.point("A")); val b = P(v.point("B")); val c = P(v.point("C"))
        val d = 2 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
        if (abs(d) < 1e-12) return@Rich lines(res(say("A, B et C sont alignés : pas de triangle", "A, B and C are in a line: no triangle")))
        fun sq(p: P2) = p.x * p.x + p.y * p.y
        val o = P2((sq(a) * (b.y - c.y) + sq(b) * (c.y - a.y) + sq(c) * (a.y - b.y)) / d, (sq(a) * (c.x - b.x) + sq(b) * (a.x - c.x) + sq(c) * (b.x - a.x)) / d)
        val g = (a + b + c) * (1.0 / 3)
        val h = a + b + c - o * 2.0
        val rr = sqrt(sq(a - o))
        fun len(p: P2, q: P2) = sqrt(sq(p - q))
        val area = abs(d) / 4
        val ab = len(a, b); val bc = len(b, c); val ca = len(c, a)
        val angleC = Math.toDegrees(acos(((bc * bc + ca * ca - ab * ab) / (2 * bc * ca)).coerceIn(-1.0, 1.0)))
        fun foot(p: P2, q: P2, s: P2): P2 { val u = s - q; val k = ((p - q).x * u.x + (p - q).y * u.y) / sq(u); return q + u * k }
        val shapes = mutableListOf<Shape2>(Shape2.Polygon(listOf(a, b, c), tone = 3), Shape2.Point(a, "A", 4), Shape2.Point(b, "B", 4), Shape2.Point(c, "C", 4))
        // Medians to G, then the circumscribed circle around O, then the altitudes to H, then Euler's line.
        for ((p, q, s) in listOf(Triple(a, b, c), Triple(b, c, a), Triple(c, a, b))) shapes += Shape2.Segment(p, (q + s) * 0.5, tone = 0, dashed = true)
        shapes += Shape2.Point(g, "G", 0)
        shapes += Shape2.Circle(o, rr, tone = 1); shapes += Shape2.Point(o, "O", 1)
        for ((p, q, s) in listOf(Triple(a, b, c), Triple(b, c, a), Triple(c, a, b))) shapes += Shape2.Segment(p, foot(p, q, s), tone = 2, dashed = true)
        shapes += Shape2.Point(h, "H", 2)
        val euler = if (len(o, h) > 1e-9) listOf(Shape2.Segment(o + (h - o) * -0.6, o + (h - o) * 1.6, say("Euler", "Euler"), 5)) else emptyList()
        shapes += euler
        val all = listOf(a, b, c, h, P2(o.x - rr, o.y - rr), P2(o.x + rr, o.y + rr))
        Outcome(
            listOf(
                res(say("Aire ${show(area)}", "Area ${show(area)}")),
                stp("½·a·b·sin C = ½ × ${brief(bc)} × ${brief(ca)} × sin ${brief(angleC)}°"),
                res(say("Centre de gravité G(${show(g.x)} ; ${show(g.y)})", "Centroid G(${show(g.x)} ; ${show(g.y)})")),
                res(say("Centre du cercle circonscrit O(${show(o.x)} ; ${show(o.y)}), R = ${show(rr)}", "Circumcentre O(${show(o.x)} ; ${show(o.y)}), R = ${show(rr)}")),
                res(say("Orthocentre H(${show(h.x)} ; ${show(h.y)})", "Orthocentre H(${show(h.x)} ; ${show(h.y)})")),
                stp(say("G : les médianes ; O : les médiatrices ; H : les hauteurs", "G: the medians; O: the perpendicular bisectors; H: the altitudes")),
                stp(if (len(o, h) > 1e-9) say("Droite d'Euler : OH = 3·OG (${brief(len(o, h))} = 3 × ${brief(len(o, g))})", "Euler line: OH = 3·OG (${brief(len(o, h))} = 3 × ${brief(len(o, g))})") else say("Triangle équilatéral : G = O = H", "Equilateral triangle: G = O = H"))
            ),
            figure = Figure(shapes, staged = true, square = true, window = around(all))
        )
    },
    Rich(
        "parabola", Subject.GEOMETRY, title("Parabole : sommet, axe, intersections", "Parabola: vertex, axis, intersections"), "y = ax² + bx + c, S(−b/2a ; f(−b/2a))",
        listOf(num("a", "a", "a"), num("b", "b", "b"), num("c", "c", "c"), num("k", "Droite y = k (facultatif)", "Line y = k (optional)", optional = true))
    ) { v ->
        val a = v["a"]; val b = v["b"]; val c = v["c"]
        require(a != 0.0)
        val f = { x: Double -> a * x * x + b * x + c }
        val al = -b / (2 * a); val be = f(al)
        val out = mutableListOf(
            res(say("Sommet S(${show(al)} ; ${show(be)}), axe x = ${show(al)}", "Vertex S(${show(al)} ; ${show(be)}), axis x = ${show(al)}")),
            stp(("y = ${show(a)}(x − ${show(al)})² + ${show(be)}").replace("− −", "+ ").replace("+ −", "− ")),
            stp(if (a > 0) say("a > 0 : tournée vers le haut, minimum ${show(be)}", "a > 0: opens upwards, minimum ${show(be)}") else say("a < 0 : tournée vers le bas, maximum ${show(be)}", "a < 0: opens downwards, maximum ${show(be)}"))
        )
        val shapes = mutableListOf<Shape2>()
        val marks = mutableListOf(P2(al, be))
        fun cuts(k: Double): List<Double> {
            val dd = b * b - 4 * a * (c - k)
            return when { dd < -1e-12 -> emptyList(); abs(dd) <= 1e-12 -> listOf(al); else -> listOf((-b - sqrt(dd)) / (2 * a), (-b + sqrt(dd)) / (2 * a)).sorted() }
        }
        val roots = cuts(0.0)
        out += res(if (roots.isEmpty()) say("Ne coupe pas l'axe des abscisses (Δ < 0)", "Does not cross the x axis (Δ < 0)") else say("Coupe l'axe des abscisses en x = ", "Crosses the x axis at x = ") + roots.joinToString(" ; ") { show(it) })
        roots.forEach { marks += P2(it, 0.0); shapes += Shape2.Point(P2(it, 0.0), tone = 1) }
        if (v.has("k")) {
            val k = v["k"]; val ks = cuts(k)
            out += res(if (ks.isEmpty()) say("Ne coupe pas y = ${show(k)}", "Does not meet y = ${show(k)}") else say("Coupe y = ${show(k)} en x = ", "Meets y = ${show(k)} at x = ") + ks.joinToString(" ; ") { show(it) })
            shapes.add(0, Shape2.Curve({ k }, label = "y = ${show(k)}", tone = 3, dashed = true))
            ks.forEach { marks += P2(it, k); shapes += Shape2.Point(P2(it, k), tone = 3) }
        }
        val span = max(3.0, (marks.maxOf { it.x } - marks.minOf { it.x }) * 0.8)
        shapes.add(0, Shape2.Segment(P2(al, be - 1e4), P2(al, be + 1e4), say("axe", "axis"), 2, dashed = true))
        shapes.add(1, Shape2.Curve(f, al - span, al + span, "P"))
        shapes += Shape2.Point(P2(al, be), "S", 2)
        Outcome(out, figure = Figure(shapes, staged = true, window = Figure(listOf(Shape2.Curve(f, al - span, al + span))).frame()))
    },
    Rich(
        "cube-section", Subject.GEOMETRY, title("Section d'un cube par un plan", "Section of a cube by a plane"), "(PQR) ∩ ABCDEFGH",
        listOf(num("a", "Arête a (A à l'origine)", "Edge a (A at the origin)"), list("P", "P (x, y, z)", "P (x, y, z)"), list("Q", "Q", "Q"), list("R", "R", "R"))
    ) { v ->
        val s = v["a"]
        require(s > 0)
        val p = v.vec("P"); val q = v.vec("Q"); val r = v.vec("R")
        val n = (q - p) cross (r - p)
        if (n.norm < 1e-12) return@Rich lines(res(say("P, Q et R sont alignés : pas de plan", "P, Q and R are in a line: no plane")))
        val d = -(n dot p)
        // The cube's corners, A B C D below, E F G H above.
        val corner = listOf(Vec3(0.0, 0.0, 0.0), Vec3(s, 0.0, 0.0), Vec3(s, s, 0.0), Vec3(0.0, s, 0.0), Vec3(0.0, 0.0, s), Vec3(s, 0.0, s), Vec3(s, s, s), Vec3(0.0, s, s))
        val names = "ABCDEFGH"
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        val hits = ArrayList<Vec3>()
        for ((i, j) in edges) {
            val si = (n dot corner[i]) + d; val sj = (n dot corner[j]) + d
            if (abs(si) < 1e-12 * max(1.0, n.norm * s)) hits += corner[i]
            if (si * sj < 0) hits += corner[i] + (corner[j] - corner[i]) * (si / (si - sj))
        }
        val poly = hits.distinctBy { listOf(Math.round(it.x * 1e7), Math.round(it.y * 1e7), Math.round(it.z * 1e7)) }
        val out = mutableListOf<Line>()
        val nameOf = mapOf(3 to say("un triangle", "a triangle"), 4 to say("un quadrilatère", "a quadrilateral"), 5 to say("un pentagone", "a pentagon"), 6 to say("un hexagone", "a hexagon"))
        out += res(if (poly.size >= 3) say("La section est ", "The section is ") + (nameOf[poly.size] ?: "${poly.size}") else say("Le plan ne coupe pas le cube", "The plane misses the cube"))
        val eq = listOf(n.x to "x", n.y to "y", n.z to "z").filter { it.first != 0.0 }.joinToString(" + ") { (k, t) -> "${show(k)}$t" } + " + ${show(d)} = 0"
        out += stp(say("Plan (PQR) : ", "Plane (PQR): ") + eq.replace("+ −", "− "))
        if (poly.size >= 3) {
            val mid = poly.reduce { x, y -> x + y } * (1.0 / poly.size)
            val e1 = (poly[0] - mid).unit(); val e2 = n.unit() cross e1
            val ordered = poly.sortedBy { atan2((it - mid) dot e2, (it - mid) dot e1) }
            out += stp(say("Sommets : ", "Corners: ") + ordered.joinToString("  ") { it.toString() })
            val shapes = ArrayList<Shape3>()
            for ((i, j) in edges) shapes += Shape3.Segment(corner[i], corner[j], tone = 3)
            corner.forEachIndexed { i, c -> shapes += Shape3.Point(c, names[i].toString(), 3) }
            shapes += Shape3.Point(p, "P", 2); shapes += Shape3.Point(q, "Q", 2); shapes += Shape3.Point(r, "R", 2)
            shapes += Shape3.Face(ordered, say("section", "section"), 0)
            Outcome(out, scene = Scene(shapes, staged = true))
        } else Outcome(out)
    },
    Rich(
        "angles-space", Subject.GEOMETRY, title("Angles entre droites et plans", "Angles between lines and planes"), "cos θ = |u·v| / (‖u‖‖v‖)",
        listOf(list("u", "u : vecteur directeur ou normal", "u: direction or normal vector"), list("v", "v : vecteur directeur ou normal", "v: direction or normal vector"))
    ) { v ->
        val u = v.point("u"); val w = v.point("v")
        require(u.norm > 0 && w.norm > 0)
        val theta = Math.toDegrees(acos((abs(u dot w) / (u.norm * w.norm)).coerceIn(0.0, 1.0)))
        lines(
            res(say("Deux droites (u, v directeurs) : ${show(theta)}°", "Two lines (u, v directions): ${show(theta)}°")),
            res(say("Deux plans (u, v normaux) : ${show(theta)}°", "Two planes (u, v normals): ${show(theta)}°")),
            res(say("Droite (u directeur) et plan (v normal) : ${show(90 - theta)}°", "Line (u direction) and plane (v normal): ${show(90 - theta)}°")),
            stp("u·v = ${show(u dot w)}, ‖u‖ = ${show(u.norm)}, ‖v‖ = ${show(w.norm)}")
        )
    }
)
