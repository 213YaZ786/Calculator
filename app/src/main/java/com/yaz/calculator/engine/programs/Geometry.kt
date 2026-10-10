package com.yaz.calculator.engine.programs

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

// Geometry from Première (dot product), Terminale (space) and IB HL /
// Further Pure (cross product, lines and planes), each shown in space.

private fun deg(rad: Double) = Math.toDegrees(rad)

private fun angle(u: Vec3, v: Vec3) = deg(acos(((u dot v) / (u.norm * v.norm)).coerceIn(-1.0, 1.0)))

/** ax + by + cz + d = 0, written as a student does. */
private fun plane(n: Vec3, d: Double): String {
    val terms = listOf(n.x to "x", n.y to "y", n.z to "z").filter { it.first != 0.0 }.joinToString(" + ") { (k, s) -> "${show(k)}$s" }
    return ("$terms + ${show(d)} = 0").replace("+ −", "− ")
}

private fun pv(x: Vec3) = x.toString()

val geometry: List<Program> = listOf(
    Rich(
        "dot-product", Subject.GEOMETRY, title("Produit scalaire : trois méthodes", "Dot product: three ways"), "u·v = xx′ + yy′ + zz′ = ‖u‖‖v‖cos θ",
        listOf(list("u", "u (x, y, z)", "u (x, y, z)"), list("v", "v (x, y, z)", "v (x, y, z)"))
    ) { v ->
        val u = v.vec("u"); val w = v.vec("v")
        val dot = u dot w
        val polar = ((u + w).norm * (u + w).norm - u.norm * u.norm - w.norm * w.norm) / 2
        val theta = angle(u, w)
        // The projection of v on u: the dot product as ‖u‖ times a signed length.
        val proj = if (u.norm > 0) u * (dot / (u dot u)) else Vec3.ZERO
        Outcome(
            listOf(
                res("u·v = ${show(dot)}"),
                stp(say("Coordonnées : ${show(u.x)}×${show(w.x)} + ${show(u.y)}×${show(w.y)} + ${show(u.z)}×${show(w.z)}", "Coordinates: ${show(u.x)}×${show(w.x)} + ${show(u.y)}×${show(w.y)} + ${show(u.z)}×${show(w.z)}")),
                stp(say("Normes et angle : ${show(u.norm)} × ${show(w.norm)} × cos ${show(theta)}°", "Norms and angle: ${show(u.norm)} × ${show(w.norm)} × cos ${show(theta)}°")),
                stp(say("Polarisation : (‖u + v‖² − ‖u‖² − ‖v‖²) / 2 = ${show(polar)}", "Polarisation: (‖u + v‖² − ‖u‖² − ‖v‖²) / 2 = ${show(polar)}")),
                res("θ = ${show(theta)}°" + if (abs(dot) < 1e-12) "   u ⊥ v" else "")
            ),
            scene = Scene(listOf(
                Shape3.Arrow(Vec3.ZERO, u, "u", 0),
                Shape3.Arrow(Vec3.ZERO, w, "v", 1),
                Shape3.Segment(w, proj, dashed = true, tone = 1),
                Shape3.Point(proj, say("projeté", "projection"), 2)
            ))
        )
    },
    Rich(
        "sines", Subject.GEOMETRY, title("Triangle : loi des sinus", "Triangle: law of sines"), "a / sin α = b / sin β = 2R",
        listOf(num("a", "Côté a", "Side a"), num("α", "Angle α opposé (degrés)", "Opposite angle α (degrees)", "°"), num("β", "Angle β (degrés)", "Angle β (degrees)", "°"))
    ) { v ->
        val a = v["a"]; val al = Math.toRadians(v["α"]); val be = Math.toRadians(v["β"])
        require(a > 0 && al > 0 && be > 0 && al + be < Math.PI)
        val ga = Math.PI - al - be
        val k = a / sin(al)
        lines(
            res("γ = ${show(deg(ga))}°"),
            res("b = ${show(k * sin(be))}   c = ${show(k * sin(ga))}"),
            stp(say("Rayon du cercle circonscrit R = ${show(k / 2)}", "Circumradius R = ${show(k / 2)}")),
            stp(say("Aire ${show(0.5 * k * sin(be) * k * sin(ga) * sin(al))}", "Area ${show(0.5 * k * sin(be) * k * sin(ga) * sin(al))}"))
        )
    },
    Rich(
        "cross-product", Subject.GEOMETRY, title("Produit vectoriel, aire et volume", "Cross product, area and volume"), "u ∧ v, ‖u ∧ v‖, (u ∧ v)·w",
        listOf(list("u", "u (x, y, z)", "u (x, y, z)"), list("v", "v (x, y, z)", "v (x, y, z)"), list("w", "w (facultatif)", "w (optional)", optional = true))
    ) { v ->
        val u = v.vec("u"); val w = v.vec("v")
        val c = u cross w
        val out = mutableListOf(
            res("u ∧ v = ${pv(c)}"),
            res(say("Aire du parallélogramme ‖u ∧ v‖ = ${show(c.norm)}, du triangle ${show(c.norm / 2)}", "Parallelogram area ‖u ∧ v‖ = ${show(c.norm)}, triangle ${show(c.norm / 2)}"))
        )
        val shapes = mutableListOf<Shape3>(
            Shape3.Face(listOf(Vec3.ZERO, u, u + w, w), tone = 0),
            Shape3.Arrow(Vec3.ZERO, u, "u", 0),
            Shape3.Arrow(Vec3.ZERO, w, "v", 1),
            Shape3.Arrow(Vec3.ZERO, c, "u ∧ v", 2)
        )
        if (v.has("w")) {
            val z = v.vec("w")
            val mixed = c dot z
            out += res(say("Produit mixte (u ∧ v)·w = ${show(mixed)} : volume ${show(abs(mixed))}, tétraèdre ${show(abs(mixed) / 6)}", "Triple product (u ∧ v)·w = ${show(mixed)}: volume ${show(abs(mixed))}, tetrahedron ${show(abs(mixed) / 6)}"))
            if (abs(mixed) < 1e-12) out += stp(say("u, v, w coplanaires", "u, v, w coplanar"))
            shapes += Shape3.Arrow(Vec3.ZERO, z, "w", 3)
            shapes += Shape3.Face(listOf(z, u + z, u + w + z, w + z), tone = 3)
        }
        Outcome(out, scene = Scene(shapes))
    },
    Rich(
        "plane-3-points", Subject.GEOMETRY, title("Plan passant par trois points", "Plane through three points"), "n = AB ∧ AC",
        listOf(list("A", "A (x, y, z)", "A (x, y, z)"), list("B", "B (x, y, z)", "B (x, y, z)"), list("C", "C (x, y, z)", "C (x, y, z)"))
    ) { v ->
        val a = v.vec("A"); val b = v.vec("B"); val c = v.vec("C")
        val n = (b - a) cross (c - a)
        if (n.norm < 1e-12) return@Rich lines(res(say("A, B, C alignés : pas un plan unique", "A, B, C collinear: no single plane")))
        val d = -(n dot a)
        Outcome(
            listOf(
                res(plane(n, d)),
                res(say("Normal n = ${pv(n)}", "Normal n = ${pv(n)}")),
                stp(say("Aire du triangle ABC = ${show(n.norm / 2)}", "Area of triangle ABC = ${show(n.norm / 2)}")),
                stp(say("Distance de l'origine au plan ${show(abs(d) / n.norm)}", "Distance from the origin ${show(abs(d) / n.norm)}"))
            ),
            scene = Scene(listOf(
                Shape3.Plane(a, n, "P", 0),
                Shape3.Face(listOf(a, b, c), tone = 1),
                Shape3.Point(a, "A", 1), Shape3.Point(b, "B", 1), Shape3.Point(c, "C", 1),
                Shape3.Arrow((a + b + c) * (1.0 / 3), (a + b + c) * (1.0 / 3) + n.unit() * ((b - a).norm.coerceAtLeast(1.0)), "n", 2)
            ))
        )
    },
    Rich(
        "point-plane", Subject.GEOMETRY, title("Distance d'un point à un plan", "Distance from a point to a plane"), "d = |ax + by + cz + d| / ‖n‖",
        listOf(list("M", "Point M (x, y, z)", "Point M (x, y, z)"), list("P", "Plan a, b, c, d (ax + by + cz + d = 0)", "Plane a, b, c, d (ax + by + cz + d = 0)"))
    ) { v ->
        val m = v.vec("M"); val p = v.list("P")
        require(p.size == 4)
        val n = Vec3(p[0], p[1], p[2]); val d = p[3]
        require(n.norm > 0)
        val k = ((n dot m) + d) / (n dot n)
        val h = m - n * k
        // A point of the plane, for the scene.
        val o = n * (-d / (n dot n))
        Outcome(
            listOf(
                res(say("Distance ${show(abs((n dot m) + d) / n.norm)}", "Distance ${show(abs((n dot m) + d) / n.norm)}")),
                res(say("Projeté orthogonal H = ${pv(h)}", "Orthogonal projection H = ${pv(h)}")),
                stp(say("Symétrique de M par rapport au plan ${pv(h * 2.0 - m)}", "Reflection of M in the plane ${pv(h * 2.0 - m)}"))
            ),
            scene = Scene(listOf(
                Shape3.Plane(o, n, "P", 0),
                Shape3.Segment(m, h, tone = 2, dashed = true),
                Shape3.Point(m, "M", 1),
                Shape3.Point(h, "H", 2)
            ))
        )
    },
    Rich(
        "line-plane", Subject.GEOMETRY, title("Droite et plan : intersection", "Line and plane: intersection"), "(A + t·u) ∩ (ax + by + cz + d = 0)",
        listOf(list("A", "Point A de la droite", "Point A of the line"), list("u", "Direction u", "Direction u"), list("P", "Plan a, b, c, d", "Plane a, b, c, d"))
    ) { v ->
        val a = v.vec("A"); val u = v.vec("u"); val p = v.list("P")
        require(p.size == 4 && u.norm > 0)
        val n = Vec3(p[0], p[1], p[2]); val d = p[3]
        require(n.norm > 0)
        val o = n * (-d / (n dot n))
        val shapes = mutableListOf<Shape3>(Shape3.Plane(o, n, "P", 0), Shape3.Line(a, u, "D", 1), Shape3.Point(a, "A", 1))
        val nu = n dot u
        val out = mutableListOf<Line>()
        if (abs(nu) < 1e-12) {
            out += res(if (abs((n dot a) + d) < 1e-12) say("La droite est contenue dans le plan", "The line lies in the plane") else say("Droite parallèle au plan, sans point commun", "Line parallel to the plane, no common point"))
        } else {
            val t = -((n dot a) + d) / nu
            val i = a + u * t
            out += res(say("Point d'intersection I = ${pv(i)}  (t = ${show(t)})", "Intersection point I = ${pv(i)}  (t = ${show(t)})"))
            out += stp(say("Angle droite-plan ${show(90 - angle(n, u).let { if (it > 90) 180 - it else it })}°", "Angle between line and plane ${show(90 - angle(n, u).let { if (it > 90) 180 - it else it })}°"))
            if (abs(abs(nu) - n.norm * u.norm) < 1e-9) out += stp(say("Droite orthogonale au plan", "Line perpendicular to the plane"))
            shapes += Shape3.Point(i, "I", 2)
        }
        out += stp(say("Représentation : x = ${show(a.x)} + ${show(u.x)}t, y = ${show(a.y)} + ${show(u.y)}t, z = ${show(a.z)} + ${show(u.z)}t", "Parametric: x = ${show(a.x)} + ${show(u.x)}t, y = ${show(a.y)} + ${show(u.y)}t, z = ${show(a.z)} + ${show(u.z)}t").replace("+ −", "− "))
        Outcome(out, scene = Scene(shapes))
    },
    Rich(
        "two-lines", Subject.GEOMETRY, title("Deux droites de l'espace", "Two lines in space"), "A + t·u, B + s·v",
        listOf(list("A", "Point A", "Point A"), list("u", "Direction u", "Direction u"), list("B", "Point B", "Point B"), list("v", "Direction v", "Direction v"))
    ) { v ->
        val a = v.vec("A"); val u = v.vec("u"); val b = v.vec("B"); val w = v.vec("v")
        require(u.norm > 0 && w.norm > 0)
        val n = u cross w
        val ab = b - a
        val shapes = mutableListOf<Shape3>(Shape3.Line(a, u, "D₁", 0), Shape3.Line(b, w, "D₂", 1), Shape3.Point(a, "A", 0), Shape3.Point(b, "B", 1))
        val out = mutableListOf<Line>()
        if (n.norm < 1e-12) {
            val dist = (ab cross u).norm / u.norm
            out += res(if (dist < 1e-12) say("Droites confondues", "Same line") else say("Droites parallèles, distance ${show(dist)}", "Parallel lines, distance ${show(dist)}"))
        } else {
            val dist = abs(ab dot n) / n.norm
            // The feet of the common perpendicular: (A + tu − B − sv) orthogonal to u and v.
            val uu = u dot u; val vv = w dot w; val uv = u dot w
            val den = uu * vv - uv * uv
            val t = ((ab dot u) * vv - (ab dot w) * uv) / den
            val s = ((ab dot u) * uv - (ab dot w) * uu) / den
            val p = a + u * t; val q = b + w * s
            if (dist < 1e-12) {
                out += res(say("Droites sécantes en ${pv(p)}", "Lines meet at ${pv(p)}"))
                shapes += Shape3.Point(p, "I", 2)
            } else {
                out += res(say("Droites non coplanaires, distance ${show(dist)}", "Skew lines, distance ${show(dist)}"))
                out += stp(say("Perpendiculaire commune de ${pv(p)} à ${pv(q)}", "Common perpendicular from ${pv(p)} to ${pv(q)}"))
                shapes += Shape3.Segment(p, q, tone = 2)
                shapes += Shape3.Point(p, "", 2); shapes += Shape3.Point(q, "", 2)
            }
            out += stp(say("Angle ${show(angle(u, w).let { if (it > 90) 180 - it else it })}°", "Angle ${show(angle(u, w).let { if (it > 90) 180 - it else it })}°"))
        }
        Outcome(out, scene = Scene(shapes))
    },
    Rich(
        "sphere", Subject.GEOMETRY, title("Sphère et plan", "Sphere and plane"), "‖M − Ω‖ = R",
        listOf(list("Ω", "Centre Ω (x, y, z)", "Centre Ω (x, y, z)"), num("R", "Rayon R", "Radius R"), list("P", "Plan a, b, c, d", "Plane a, b, c, d"))
    ) { v ->
        val c = v.vec("Ω"); val r = v["R"]; val p = v.list("P")
        require(r > 0 && p.size == 4)
        val n = Vec3(p[0], p[1], p[2]); val d = p[3]
        require(n.norm > 0)
        val dist = abs((n dot c) + d) / n.norm
        val h = c - n * (((n dot c) + d) / (n dot n))
        val o = n * (-d / (n dot n))
        val out = mutableListOf(res(say("Distance du centre au plan ${show(dist)}", "Distance from the centre to the plane ${show(dist)}")))
        out += res(when {
            dist > r + 1e-12 -> say("Le plan ne coupe pas la sphère", "The plane misses the sphere")
            abs(dist - r) <= 1e-12 -> say("Plan tangent en ${pv(h)}", "Tangent plane at ${pv(h)}")
            else -> say("Cercle de centre ${pv(h)} et de rayon ${show(sqrt(r * r - dist * dist))}", "Circle centred at ${pv(h)} with radius ${show(sqrt(r * r - dist * dist))}")
        })
        out += stp(say("Équation : (x − ${show(c.x)})² + (y − ${show(c.y)})² + (z − ${show(c.z)})² = ${show(r * r)}", "Equation: (x − ${show(c.x)})² + (y − ${show(c.y)})² + (z − ${show(c.z)})² = ${show(r * r)}").replace("− −", "+ "))
        // The sphere drawn as three great circles, flattened into faces of many sides.
        val rings = (0 until 3).map { axis ->
            Shape3.Face((0 until 48).map { k ->
                val a = 2 * Math.PI * k / 48
                val ca = kotlin.math.cos(a) * r; val sa = kotlin.math.sin(a) * r
                c + when (axis) { 0 -> Vec3(ca, sa, 0.0); 1 -> Vec3(ca, 0.0, sa); else -> Vec3(0.0, ca, sa) }
            }, tone = 1)
        }
        Outcome(out, scene = Scene(rings + listOf(Shape3.Plane(o, n, "P", 0), Shape3.Point(c, "Ω", 1), Shape3.Point(h, "H", 2))))
    }
)
