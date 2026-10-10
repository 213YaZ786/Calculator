package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.cas.Ex
import com.yaz.calculator.engine.cas.Mul
import com.yaz.calculator.engine.cas.Num
import com.yaz.calculator.engine.cas.PI as PI_EX
import com.yaz.calculator.engine.cas.Pow
import com.yaz.calculator.engine.cas.Q
import com.yaz.calculator.engine.cas.at
import com.yaz.calculator.engine.cas.definite
import com.yaz.calculator.engine.cas.integrate
import com.yaz.calculator.engine.cas.minus
import com.yaz.calculator.engine.cas.num as cnum
import com.yaz.calculator.engine.cas.sub as at_
import com.yaz.calculator.engine.cas.simplify
import com.yaz.calculator.engine.cas.tidy
import java.math.BigInteger
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.integration.IterativeLegendreGaussIntegrator
import org.hipparchus.analysis.solvers.BrentSolver
import com.yaz.calculator.engine.cas.show as exact

// Integrals as the curricula teach them (Terminale spécialité "calcul
// intégral", AP Calculus BC units 6 and 8, A-level integration): each
// program drawn, exact through the symbolic engine when it finds a
// primitive, numerical always.

private fun fr() = Locale.getDefault().language == "fr"

/** ∫ₐᵇ f by Gauss-Legendre, signed. */
internal fun gauss(f: (Double) -> Double, a: Double, b: Double): Double {
    if (a == b) return 0.0
    val v = IterativeLegendreGaussIntegrator(16, 1e-13, 1e-13).integrate(10_000_000, UnivariateFunction { f(it) }, min(a, b), max(a, b))
    require(v.isFinite())
    return if (a <= b) v else -v
}

/** ∫ from [a] + ε to [b] (ε = e·(b − a)) when f has a pole at a: decade by decade, each piece smooth at its own scale. */
private fun nearPole(f: (Double) -> Double, a: Double, b: Double, e: Double, atStart: Boolean): Double {
    val w = b - a
    var sum = 0.0
    var lo = e
    while (lo < 1.0) {
        val hi = min(1.0, lo * 10)
        sum += if (atStart) gauss(f, a + lo * w, a + hi * w) else gauss(f, b - hi * w, b - lo * w)
        lo = hi
    }
    return sum
}

private fun slope(f: (Double) -> Double, x: Double): Double {
    val h = 1e-5 * max(1.0, abs(x))
    return (f(x + h) - f(x - h)) / (2 * h)
}

/** Where [g] changes sign strictly inside (a, b). */
private fun crossings(g: (Double) -> Double, a: Double, b: Double, steps: Int = 800): List<Double> {
    val out = ArrayList<Double>()
    var x0 = a; var g0 = g(a)
    for (i in 1..steps) {
        val x1 = a + (b - a) * i / steps
        val g1 = g(x1)
        // A zero right on a step (x² − x at 0): taken as it is.
        if (g1 == 0.0 && i < steps) out += x1
        else if (g0.isFinite() && g1.isFinite() && g0 * g1 < 0)
            runCatching { BrentSolver(1e-13, 1e-14).solve(10_000, UnivariateFunction { g(it) }, x0, x1) }.getOrNull()?.let { out += it }
        x0 = x1; g0 = g1
    }
    return out.filter { it > a + 1e-12 && it < b - 1e-12 }
}

/**
 * The exact integral of the text in [f] from the texts in [a] to [b], with
 * its primitive, when the symbolic engine finds one that agrees with
 * [numeric] (the angle unit, a pole crossed: it would not).
 */
private fun exactly(v: Inputs, f: String, a: String, b: String, numeric: Double): Pair<Ex, Ex>? = runCatching {
    val F = integrate(v.exact(f), fr()).result ?: return null
    val value = definite(F, v.exact(a), v.exact(b))
    if (abs(value.at(0.0) - numeric) <= 1e-7 * max(1.0, abs(numeric))) F to value else null
}.getOrNull()

/** "∫ = exact ≈ value", or the value alone. */
private fun valueLine(name: String, value: Double, ex: Ex?): String =
    if (ex == null || exact(ex).length > 36 || Regex("\\d{10,}").containsMatchIn(exact(ex))) "$name ≈ ${show(value)}" else if (ex is Num) "$name = ${exact(ex)}" else "$name = ${exact(ex)} ≈ ${show(value)}"

/** [x] as a fraction p/q, q ≤ 100, when it is one to 10⁻¹⁰. */
private fun fraction(x: Double): Q? {
    for (q in 1L..100L) {
        val p = Math.round(x * q)
        if (abs(p.toDouble() / q - x) < 1e-10 * max(1.0, abs(x))) return Q.of(p, q)
    }
    return null
}

/**
 * Where f is not defined or blows up on [a, b] (1/x at 0), else null: the
 * integral of the lycée does not exist there, an improper one may.
 */
internal fun discontinuity(f: (Double) -> Double, a: Double, b: Double): Double? = pole(f, a, b)?.let { x -> fraction(x)?.let { it.n.toDouble() / it.d.toDouble() } ?: x }

private fun pole(f: (Double) -> Double, a: Double, b: Double): Double? {
    val lo = min(a, b); val hi = max(a, b)
    val n = 4000
    for (i in 0..n) {
        val x = lo + (hi - lo) * i / n
        if (!f(x).isFinite()) return x
    }
    // A sign change through a pole: Brent lands where |f| is huge.
    for (x in crossings(f, lo, hi, n)) if (abs(f(x)) > 1e6) return x
    // A pole between two samples of the same sign (1/x² at 0): closing in on
    // the largest |f|, it keeps growing instead of settling.
    val ys = (0..n).map { abs(f(lo + (hi - lo) * it / n)) }
    val i = ys.indices.maxBy { ys[it] }
    var l = lo + (hi - lo) * (i - 1).coerceAtLeast(0) / n; var r = lo + (hi - lo) * (i + 1).coerceAtMost(n) / n
    val start = ys[i]
    var peak = start; var at = lo + (hi - lo) * i / n
    repeat(8) {
        for (k in 0..40) {
            val x = l + (r - l) * k / 40
            val y = abs(f(x))
            if (!y.isFinite()) return x
            if (y > peak) { peak = y; at = x }
        }
        val w = (r - l) / 20
        l = max(lo, at - w); r = min(hi, at + w)
    }
    return if (peak > 1e3 * max(1.0, start) && peak > 1e6) at else null
}

/** The lines when f has a pole on [a, b]. */
private fun notContinuous(x: Double) = Outcome(listOf(
    res(say("f n'est pas continue en x = ${show(x)} : pas d'intégrale sur [a ; b]", "f is not continuous at x = ${show(x)}: no integral on [a, b]")),
    stp(say("Une intégrale impropre peut exister : programme « Intégrale impropre », jusqu'à ${show(x)}", "An improper integral may exist: the \"Improper integral\" program, up to ${show(x)}"))
))

/** Rounding noise of a sum of terms of size [scale] taken to zero. */
private fun clean(v: Double, scale: Double) = if (abs(v) < 1e-12 * max(1.0, scale)) 0.0 else v

private fun pad(a: Double, b: Double) = max(0.5, abs(b - a) * 0.2)

val integrals: List<Program> = listOf(
    Rich(
        "area-integral", Subject.ANALYSIS, title("Intégrale et aire sous la courbe", "Integral and area under the curve"), "∫ₐᵇ f(x) dx = F(b) − F(a)",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val b = v["b"]
        discontinuity(f, a, b)?.let { return@Rich notContinuous(it) }
        val value = gauss(f, a, b)
        val ex = exactly(v, "f", "a", "b", value)
        val lo = min(a, b); val hi = max(a, b)
        val cuts = listOf(lo) + crossings(f, lo, hi) + listOf(hi)
        val pieces = cuts.zipWithNext().map { (p, q) -> gauss(f, p, q) }
        val above = pieces.filter { it > 0 }.sum(); val below = -pieces.filter { it < 0 }.sum()
        val out = mutableListOf(res(valueLine("∫", value, ex?.second)))
        ex?.let { out += stp("F(x) = ${exact(it.first)}") }
        if (cuts.size > 2) {
            out += stp(say("f change de signe en x = ", "f changes sign at x = ") + cuts.drop(1).dropLast(1).joinToString(" ; ") { show(it) })
            out += res(say("Aire entre la courbe et l'axe ${show(above + below)} u.a.", "Area between the curve and the axis ${show(above + below)} square units"))
            out += stp(say("Au-dessus de l'axe ${show(above)}, au-dessous ${show(below)} : ∫ = ${show(above)} − ${show(below)}", "Above the axis ${show(above)}, below ${show(below)}: ∫ = ${show(above)} − ${show(below)}"))
        } else out += stp(say(if (value >= 0) "f ≥ 0 sur [a ; b] : l'intégrale est l'aire sous la courbe, en u.a." else "f ≤ 0 sur [a ; b] : l'aire est −∫ = ${show(-value)} u.a.", if (value >= 0) "f ≥ 0 on [a, b]: the integral is the area under the curve, in square units" else "f ≤ 0 on [a, b]: the area is −∫ = ${show(-value)} square units"))
        val p = pad(a, b)
        Outcome(out, figure = Figure(listOf(Shape2.Area(f, lo, hi), Shape2.Curve(f, lo - p, hi + p, "f")), window = around(f, lo - p, hi + p)))
    },
    Rich(
        "chasles-integral", Subject.ANALYSIS, title("Relation de Chasles (intégrales)", "Additivity of integrals (Chasles)"), "∫ₐᶜ f + ∫꜀ᵇ f = ∫ₐᵇ f",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("c", "c", "c"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val c = v["c"]; val b = v["b"]
        discontinuity(f, minOf(a, b, c), maxOf(a, b, c))?.let { return@Rich notContinuous(it) }
        val i1 = gauss(f, a, c); val i2 = gauss(f, c, b); val all = gauss(f, a, b)
        val e1 = exactly(v, "f", "a", "c", i1)?.second; val e2 = exactly(v, "f", "c", "b", i2)?.second; val e = exactly(v, "f", "a", "b", all)?.second
        val lo = minOf(a, b, c); val hi = maxOf(a, b, c); val p = pad(lo, hi)
        Outcome(
            listOf(
                res(valueLine("∫ₐᵇ", all, e)),
                stp(valueLine("∫ₐᶜ", i1, e1)),
                stp(valueLine("∫꜀ᵇ", i2, e2)),
                stp(if (e1 != null && e2 != null) "${exact(e1)} + ${exact(e2)} = ${exact(simplify(com.yaz.calculator.engine.cas.Add(listOf(e1, e2))))}" else "${show(i1)} + ${show(i2)} = ${show(clean(i1 + i2, abs(i1) + abs(i2)))}"),
                stp(say(if (c in min(a, b)..max(a, b)) "c entre a et b : les deux aires se juxtaposent" else "c hors de [a ; b] : la relation vaut encore, une intégrale se retranche", if (c in min(a, b)..max(a, b)) "c between a and b: the two areas sit side by side" else "c outside [a, b]: it still holds, one integral is taken away"))
            ),
            figure = Figure(
                listOf(Shape2.Curve(f, lo - p, hi + p, "f"), Shape2.Area(f, min(a, c), max(a, c), label = "∫ₐᶜ"), Shape2.Area(f, min(c, b), max(c, b), label = "∫꜀ᵇ", tone = 2), Shape2.Point(P2(c, f(c)), tone = 2)),
                staged = true, window = around(f, lo - p, hi + p)
            )
        )
    },
    Rich(
        "area-between", Subject.ANALYSIS, title("Aire entre deux courbes", "Area between two curves"), "A = ∫ₐᵇ |f(x) − g(x)| dx",
        listOf(fn("f", "f(x)", "f(x)"), fn("g", "g(x)", "g(x)"), num("a", "a (vide : intersections)", "a (empty: intersections)", optional = true), num("b", "b (vide : intersections)", "b (empty: intersections)", optional = true))
    ) { v ->
        val f = v.f("f"); val g = v.f("g")
        val d = { x: Double -> f(x) - g(x) }
        val given = v.has("a") && v.has("b")
        val (a, b) = if (given) min(v["a"], v["b"]) to max(v["a"], v["b"]) else {
            // The outermost meeting points within ±50.
            val meet = crossings(d, -50.0, 50.0, 4000)
            if (meet.size < 2) return@Rich lines(
                res(say(if (meet.isEmpty()) "Les courbes ne se coupent pas entre −50 et 50 : donnez a et b" else "Les courbes ne se coupent qu'en x = ${show(meet[0])} : donnez a et b", if (meet.isEmpty()) "The curves do not meet between −50 and 50: give a and b" else "The curves only meet at x = ${show(meet[0])}: give a and b")),
                stp(say(if (v.degrees) "Angles en degrés (DEG)" else "Angles en radians (RAD)", if (v.degrees) "Angles in degrees (DEG)" else "Angles in radians (RAD)"))
            )
            meet.first() to meet.last()
        }
        discontinuity(d, a, b)?.let { return@Rich notContinuous(it) }
        val cuts = listOf(a) + crossings(d, a, b) + listOf(b)
        val pieces = cuts.zipWithNext().map { (p, q) -> gauss(d, p, q) }
        val area = pieces.sumOf { abs(it) }
        val out = mutableListOf<Line>()
        // Exact when the bounds are typed and the crossings are fractions (0 and 1 for x and x²): |∫| piece by piece.
        val ex = runCatching {
            val F = integrate(simplify(v.exact("f") - v.exact("g")), fr()).result!!
            val bounds: List<Ex> = cuts.mapIndexed { i, x ->
                if (given && i == 0) v.exact(if (v["a"] <= v["b"]) "a" else "b")
                else if (given && i == cuts.lastIndex) v.exact(if (v["a"] <= v["b"]) "b" else "a")
                else Num(fraction(x)!!)
            }
            val total = simplify(com.yaz.calculator.engine.cas.Add(bounds.zipWithNext().map { (p, q) -> definite(F, p, q).let { d -> if (d.at(0.0) < 0) simplify(Mul(listOf(cnum(-1), d))) else d } }))
            total.takeIf { abs(it.at(0.0) - area) < 1e-7 * max(1.0, area) }
        }.getOrNull()
        out += res(say("Aire", "Area") + if (ex != null) " = ${exact(ex)}" + (if (ex is Num && ex.q.d.bitLength() < 8) "" else " ≈ ${show(area)}") else " ≈ ${show(area)}")
        if (!given) out += stp(say("Bornes : les intersections x = ${show(a)} et x = ${show(b)}", "Bounds: the intersections x = ${show(a)} and x = ${show(b)}"))
        if (cuts.size > 2) out += stp(say("Les courbes se croisent en ", "The curves cross at ") + cuts.drop(1).dropLast(1).joinToString(" ; ") { show(it) } + say(" : on ajoute |∫| de chaque morceau", ": |∫| of each piece added"))
        cuts.zipWithNext().forEachIndexed { i, (p, q) -> if (i < 6) out += stp("[${show(p)} ; ${show(q)}] : " + (if (pieces[i] >= 0) "f ≥ g" else "g ≥ f") + ", ${show(abs(pieces[i]))}") }
        val p = pad(a, b)
        val shapes = mutableListOf<Shape2>(Shape2.Area(f, a, b, bottom = g), Shape2.Curve(f, a - p, b + p, "f"), Shape2.Curve(g, a - p, b + p, "g", tone = 2))
        cuts.forEach { x -> if (abs(d(x)) < 1e-6 * max(1.0, abs(f(x)))) shapes += Shape2.Point(P2(x, f(x)), tone = 2) }
        Outcome(out, figure = Figure(shapes, staged = true, window = Figure(listOf(Shape2.Curve(f, a - p, b + p), Shape2.Curve(g, a - p, b + p))).frame()))
    },
    Rich(
        "mean-value", Subject.ANALYSIS, title("Valeur moyenne et encadrement", "Average value and bounds"), "μ = 1/(b − a) ∫ₐᵇ f(x) dx",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val a = min(v["a"], v["b"]); val b = max(v["a"], v["b"])
        require(b > a)
        discontinuity(f, a, b)?.let { return@Rich notContinuous(it) }
        val value = gauss(f, a, b)
        val mu = value / (b - a)
        val ex = exactly(v, "f", "a", "b", gauss(f, v["a"], v["b"]))?.second?.let { e ->
            runCatching { simplify(Mul(listOf(e, Pow(simplify(v.exact("b") - v.exact("a")), cnum(-1))))) }.getOrNull()
        }
        val samples = (0..2000).map { f(a + (b - a) * it / 2000) }.filter { it.isFinite() }
        val m = samples.min(); val mm = samples.max()
        val at = crossings({ f(it) - mu }, a, b)
        val out = mutableListOf(res(valueLine("μ", mu, ex)))
        at.take(3).forEach { c -> out += stp(say("f(c) = μ en c = ${show(c)}", "f(c) = μ at c = ${show(c)}")) }
        out += stp(say("Le rectangle de hauteur μ sur [a ; b] a l'aire de l'intégrale, ${show(value)}", "The rectangle of height μ on [a, b] has the integral's area, ${show(value)}"))
        out += res("${show(m)} ≤ f ≤ ${show(mm)}  ⇒  ${show(m * (b - a))} ≤ ∫ ≤ ${show(mm * (b - a))}")
        val p = pad(a, b)
        val shapes = mutableListOf<Shape2>(Shape2.Curve(f, a - p, b + p, "f"), Shape2.Area(f, a, b), Shape2.rect(a, 0.0, b, mu, tone = 2, label = "μ"))
        at.take(3).forEach { c -> shapes += Shape2.Point(P2(c, mu), "c", tone = 2) }
        shapes += Shape2.Segment(P2(a, m), P2(b, m), tone = 3, dashed = true, label = "m")
        shapes += Shape2.Segment(P2(a, mm), P2(b, mm), tone = 3, dashed = true, label = "M")
        Outcome(out, figure = Figure(shapes, staged = true, window = around(f, a - p, b + p)))
    },
    Rich(
        "accumulation", Subject.ANALYSIS, title("Fonction intégrale F(x) = ∫ₐˣ f", "Accumulation function F(x) = ∫ₐˣ f"), "F(x) = ∫ₐˣ f(t) dt, F′ = f, F(a) = 0",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("x", "x (facultatif)", "x (optional)", optional = true))
    ) { v ->
        val f = v.f("f"); val a = v["a"]
        val x = if (v.has("x")) v["x"] else a + 3
        discontinuity(f, a, x)?.let { return@Rich notContinuous(it) }
        val out = mutableListOf<Line>()
        // F exact: G(x) − G(a), G a primitive.
        val exactF = runCatching {
            val G = integrate(v.exact("f"), fr()).result ?: return@runCatching null
            val F = tidy(simplify(G - G.at_(v.exact("a"))))
            F.takeIf { abs(it.at(x) - gauss(f, a, x)) < 1e-7 * max(1.0, abs(it.at(x))) }
        }.getOrNull()
        exactF?.let { out += res("F(x) = ${exact(it)}") }
        val fx = gauss(f, a, x)
        if (v.has("x")) out += res("F(${show(x)}) = ${show(fx)}")
        out += stp(say("F est la primitive de f qui s'annule en a : F′(x) = f(x), F(a) = 0", "F is the antiderivative of f that is 0 at a: F′(x) = f(x), F(a) = 0"))
        out += stp(say("F croît où f > 0, décroît où f < 0", "F increases where f > 0, decreases where f < 0"))
        // F on the window from a fine cumulative sum, read between its knots.
        val lo = min(a, x) - pad(a, x); val hi = max(a, x) + pad(a, x)
        val n = 1200
        val xs = DoubleArray(n + 1) { lo + (hi - lo) * it / n }
        val ys = DoubleArray(n + 1)
        val k0 = ((a - lo) / (hi - lo) * n).toInt().coerceIn(0, n)
        ys[k0] = gauss(f, a, xs[k0])
        for (k in k0 + 1..n) ys[k] = ys[k - 1] + gauss(f, xs[k - 1], xs[k])
        for (k in k0 - 1 downTo 0) ys[k] = ys[k + 1] - gauss(f, xs[k], xs[k + 1])
        val F = { t: Double -> val u = ((t - lo) / (hi - lo) * n).coerceIn(0.0, n - 1e-9); val i = u.toInt(); ys[i] + (ys[i + 1] - ys[i]) * (u - i) }
        Outcome(out, figure = Figure(
            listOf(Shape2.Curve(f, lo, hi, "f"), Shape2.Area(f, min(a, x), max(a, x)), Shape2.Curve(F, lo, hi, "F", tone = 2), Shape2.Point(P2(a, 0.0), "a", tone = 2), Shape2.Point(P2(x, fx), "F(x)", tone = 2)),
            staged = true
        ))
    },
    Rich(
        "monte-carlo", Subject.ANALYSIS, title("Aire par Monte-Carlo", "Area by Monte Carlo"), "A ≈ (n / N)·(b − a)·M",
        listOf(fn("f", "f(x) ≥ 0", "f(x) ≥ 0"), num("a", "a", "a"), num("b", "b", "b"), num("N", "Points tirés", "Points drawn", whole = true))
    ) { v ->
        val f = v.f("f"); val a = min(v["a"], v["b"]); val b = max(v["a"], v["b"]); val n = v.int("N")
        require(n in 10..2_000_000 && b > a)
        discontinuity(f, a, b)?.let { return@Rich notContinuous(it) }
        val top = (0..1000).maxOf { f(a + (b - a) * it / 1000).let { y -> if (y.isFinite()) y else 0.0 } } * 1.05
        require(top > 0)
        val rng = kotlin.random.Random(System.nanoTime())
        val under = ArrayList<P2>(); val over = ArrayList<P2>()
        var hits = 0
        repeat(n) {
            val x = a + (b - a) * rng.nextDouble(); val y = top * rng.nextDouble()
            val inside = y <= f(x)
            if (inside) hits++
            if (under.size + over.size < 3000) (if (inside) under else over) += P2(x, y)
        }
        val box = (b - a) * top
        val p = hits.toDouble() / n
        val estimate = p * box
        val truth = gauss(f, a, b)
        val half = 1.96 * sqrt(p * (1 - p) / n) * box
        Outcome(
            listOf(
                res("A ≈ ${show(estimate)}"),
                stp(say("n = $hits points sous la courbe sur N = $n, rectangle ${show(b - a)} × M, M = ${show(top)}", "n = $hits points under the curve out of N = $n, rectangle ${show(b - a)} × M, M = ${show(top)}")),
                stp(say("Intervalle à 95 % : [${show(estimate - half)} ; ${show(estimate + half)}]", "95 % interval: [${show(estimate - half)}, ${show(estimate + half)}]")),
                stp(valueLine("∫ₐᵇ f", truth, exactly(v, "f", "a", "b", gauss(f, v["a"], v["b"]))?.second?.takeIf { v["a"] <= v["b"] }) + say(", écart ${show(estimate - truth)} ; l'erreur décroît comme 1/√N", ", error ${show(estimate - truth)}; the error shrinks as 1/√N"))
            ),
            figure = Figure(listOf(Shape2.rect(a, 0.0, b, top, tone = 3), Shape2.Dots(over, tone = 2), Shape2.Dots(under), Shape2.Curve(f, a, b, "f")))
        )
    },
    Rich(
        "brouncker", Subject.ANALYSIS, title("ln 2 par l'algorithme de Brouncker", "ln 2 by Brouncker's algorithm"), "ln 2 = 1/(1·2) + 1/(3·4) + 1/(5·6) + …",
        listOf(num("k", "Étapes (≤ 20)", "Steps (≤ 20)", whole = true, slider = 0.0..8.0, step = 1.0))
    ) { v ->
        val k = v.int("k")
        require(k in 0..20)
        // Step k: 2ᵏ rectangles under 1/x on [1 ; 2], their area 1/(2ᵏ + 1) + … + 1/2ᵏ⁺¹.
        var s = Q.ZERO
        val from = 1L shl k
        val exactSum = k <= 3
        var d = 0.0
        for (m in from + 1..2 * from) { d += 1.0 / m; if (exactSum) s = s + Q.of(BigInteger.ONE, BigInteger.valueOf(m)) }
        val shapes = mutableListOf<Shape2>(Shape2.rect(1.0, 0.0, 2.0, 0.5, label = "1/(1·2)"))
        for (level in 1..min(k, 6)) {
            val w = 1.0 / (1 shl level)
            for (j in 1 until (1 shl level) step 2) {
                val x0 = 1 + (j - 1) * w; val x1 = 1 + j * w
                shapes += Shape2.rect(x0, 1 / (1 + (j + 1) * w), x1, 1 / x1, tone = level, label = if (level == 1) "1/(3·4)" else if (level == 2 && j == 1) "1/(5·6)" else "")
            }
        }
        shapes += Shape2.Curve({ 1 / it }, 0.95, 2.05, "1/x", tone = 0)
        Outcome(
            listOf(
                res("S$k = " + (if (exactSum) "${exact(Num(s))} ≈ " else "") + show(d)),
                stp("ln 2 = ${show(ln(2.0))}, ${say("écart", "error")} ${show(ln(2.0) - d)}"),
                stp(say("Chaque étape ajoute dans les trous, sous la courbe, un rectangle par intervalle : 1/(1·2), puis 1/(3·4), puis 1/(5·6) + 1/(7·8)…", "Each step adds, in the gaps under the curve, one rectangle per interval: 1/(1·2), then 1/(3·4), then 1/(5·6) + 1/(7·8)…")),
                stp(say("S$k = 1/${from + 1} + … + 1/${2 * from}, $from termes", "S$k = 1/${from + 1} + … + 1/${2 * from}, $from terms"))
            ),
            figure = Figure(shapes, staged = true, window = Box2(0.9, 2.1, -0.08, 1.1))
        )
    },
    Rich(
        "integral-sequence", Subject.ANALYSIS, title("Suite d'intégrales", "Sequence of integrals"), "Iₙ = ∫ₐᵇ g(x)·h(x)ⁿ dx",
        listOf(fn("g", "g(x)", "g(x)"), fn("h", "h(x) (souvent x)", "h(x) (often x)"), num("a", "a", "a"), num("b", "b", "b"), num("N", "Dernier rang (≤ 40)", "Last index (≤ 40)", whole = true))
    ) { v ->
        val g = v.f("g"); val h = v.f("h"); val a = v["a"]; val b = v["b"]; val n = v.int("N")
        require(n in 1..40)
        discontinuity({ x -> g(x) * h(x).pow(n) }, a, b)?.let { return@Rich notContinuous(it) }
        val values = (0..n).map { k -> gauss({ x -> g(x) * h(x).pow(k) }, a, b) }
        val out = mutableListOf<Line>()
        // The first ones exact: their pattern shows the recurrence.
        val ge = runCatching { v.exact("g") }.getOrNull(); val he = runCatching { v.exact("h") }.getOrNull()
        val exacts = (0..min(n, 4)).map { k ->
            runCatching {
                val F = integrate(simplify(Mul(listOf(ge!!, Pow(he!!, cnum(k.toLong()))))), fr()).result!!
                definite(F, v.exact("a"), v.exact("b")).takeIf { abs(it.at(0.0) - values[k]) < 1e-7 * max(1.0, abs(values[k])) }
            }.getOrNull()
        }
        exacts.forEachIndexed { k, e -> if (e != null) out += res("I${index(k)} = ${exact(e)}") }
        out += stp(values.withIndex().filter { it.index <= 6 || it.index == n }.joinToString("   ") { (k, x) -> "I${index(k)} ≈ ${show(x)}" })
        val diffs = values.zipWithNext { p, q -> q - p }
        out += stp(when {
            diffs.all { it < 0 } -> say("(Iₙ) décroissante", "(Iₙ) decreasing")
            diffs.all { it > 0 } -> say("(Iₙ) croissante", "(Iₙ) increasing")
            else -> say("(Iₙ) non monotone", "(Iₙ) not monotonic")
        } + if (values.all { it >= 0 }) say(", positive : elle converge si elle décroît", ", positive: it converges when it decreases") else "")
        out += stp("I${index(n)} ≈ ${show(values[n])}   n·I${index(n)} ≈ ${show(n * values[n])}")
        val p = pad(a, b)
        val lo = min(a, b); val hi = max(a, b)
        val shown = (0..min(n, 5)).toList()
        Outcome(out, figure = Figure(
            listOf<Shape2>(Shape2.Area({ x -> g(x) * h(x).pow(n) }, lo, hi, tone = 2)) + shown.map { k -> Shape2.Curve({ x -> g(x) * h(x).pow(k) }, lo - p * 0.2, hi + p * 0.2, "n=$k", tone = k % 6) },
            staged = true
        ))
    },
    Rich(
        "revolution", Subject.ANALYSIS, title("Volume de révolution", "Volume of revolution"), "V = π ∫ₐᵇ (f(x)² − g(x)²) dx",
        listOf(fn("f", "f(x), rayon", "f(x), radius"), num("a", "a", "a"), num("b", "b", "b"), Field("g", Words("g(x), rayon intérieur (facultatif)", "g(x), inner radius (optional)"), kind = FieldKind.FUNCTION, optional = true))
    ) { v ->
        val f = v.f("f"); val a = min(v["a"], v["b"]); val b = max(v["a"], v["b"])
        val g = if (v.has("g")) v.f("g") else null
        discontinuity(f, a, b)?.let { return@Rich notContinuous(it) }
        val volume = PI * gauss({ x -> f(x).pow(2) - (g?.invoke(x)?.pow(2) ?: 0.0) }, a, b)
        val ex = runCatching {
            val fe = v.exact("f")
            val inner = if (g != null) simplify(Pow(v.exact("g"), cnum(2))) else cnum(0)
            val F = integrate(simplify(Pow(fe, cnum(2)) - inner), fr()).result!!
            simplify(Mul(listOf(PI_EX, definite(F, v.exact("a"), v.exact("b"))))).takeIf { abs(abs(it.at(0.0)) - abs(volume)) < 1e-7 * max(1.0, abs(volume)) }
        }.getOrNull()
        val out = mutableListOf(res(valueLine("V", volume, ex)))
        out += stp(say(if (g == null) "Disques de rayon f(x), d'aire πf(x)², empilés de a à b" else "Anneaux entre g(x) et f(x), d'aire π(f² − g²), empilés de a à b", if (g == null) "Discs of radius f(x), area πf(x)², stacked from a to b" else "Washers between g(x) and f(x), area π(f² − g²), stacked from a to b"))
        // The surface turned about the x axis, in bands.
        val shapes = ArrayList<Shape3>()
        val slices = 22; val turns = 18
        fun ring(r: (Double) -> Double, tone: Int) {
            for (i in 0 until slices) for (j in 0 until turns) {
                val x0 = a + (b - a) * i / slices; val x1 = a + (b - a) * (i + 1) / slices
                val t0 = 2 * PI * j / turns; val t1 = 2 * PI * (j + 1) / turns
                val r0 = abs(r(x0)); val r1 = abs(r(x1))
                if (!r0.isFinite() || !r1.isFinite()) continue
                shapes += Shape3.Face(listOf(Vec3(x0, r0 * cos(t0), r0 * sin(t0)), Vec3(x1, r1 * cos(t0), r1 * sin(t0)), Vec3(x1, r1 * cos(t1), r1 * sin(t1)), Vec3(x0, r0 * cos(t1), r0 * sin(t1))), tone = tone, soft = true)
            }
        }
        ring(f, 0)
        g?.let { ring(it, 1) }
        // The profile y = f(x) in the plane z = 0, the curve turned.
        for (i in 0 until 40) {
            val x0 = a + (b - a) * i / 40; val x1 = a + (b - a) * (i + 1) / 40
            shapes += Shape3.Segment(Vec3(x0, f(x0), 0.0), Vec3(x1, f(x1), 0.0), label = if (i == 39) "f" else "", tone = 2)
        }
        Outcome(out, scene = Scene(shapes))
    },
    Rich(
        "improper", Subject.ANALYSIS, title("Intégrale impropre", "Improper integral"), "∫ₐ^+∞ f(x) dx = lim F(R) − F(a)",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b (vide : +∞)", "b (empty: +∞)", optional = true))
    ) { v ->
        val f = v.f("f"); val a = v["a"]
        val out = mutableListOf<Line>()
        val primitive = runCatching { integrate(v.exact("f"), fr()).result }.getOrNull()
        primitive?.let { out += stp("F(x) = ${exact(it)}") }
        val figureTo: Double
        if (!v.has("b")) {
            // ∫ₐᴿ for R growing, by pieces of growing length.
            val rs = (0..6).map { a + 10.0.pow(it) }
            var acc = 0.0; var from = a
            val partial = rs.map { r -> acc += gauss(f, from, r); from = r; acc }
            partial.forEachIndexed { i, s -> if (i % 2 == 0 || i == partial.lastIndex) out += stp("R = ${show(rs[i])} : ∫ₐᴿ ≈ ${show(s)}") }
            val d1 = abs(partial[6] - partial[5]); val d0 = abs(partial[5] - partial[4])
            val converges = d1 < 1e-6 * max(1.0, abs(partial[6])) || (d1 < d0 * 0.5 && d1 < 1e-3 * max(1.0, abs(partial[6])))
            out.add(0, res(if (converges) say("Converge : ∫ ≈ ${show(partial[6])}", "Converges: ∫ ≈ ${show(partial[6])}") else say("Semble diverger : ∫ₐᴿ ne se stabilise pas", "Seems to diverge: ∫ₐᴿ does not settle")))
            figureTo = a + max(6.0, abs(a))
        } else {
            val b = v["b"]
            // A pole at an end: the integral from a + ε or up to b − ε.
            val atA = !f(a).isFinite(); val atB = !f(b).isFinite()
            val es = (1..8).map { 10.0.pow(-it) }
            val partial = es.map { e ->
                when {
                    atA && atB -> nearPole(f, a, (a + b) / 2, e, true) + nearPole(f, (a + b) / 2, b, e, false)
                    atA -> nearPole(f, a, b, e, true)
                    atB -> nearPole(f, a, b, e, false)
                    else -> gauss(f, a, b)
                }
            }
            partial.forEachIndexed { i, s -> if (i % 2 == 1) out += stp("ε = ${show(es[i])} : ${show(s)}") }
            val d1 = abs(partial[7] - partial[6]); val d0 = abs(partial[6] - partial[5])
            val converges = d1 < 1e-5 * max(1.0, abs(partial[7])) || d1 < d0 * 0.5
            out.add(0, res(if (converges) say("Converge : ∫ ≈ ${show(partial[7])}", "Converges: ∫ ≈ ${show(partial[7])}") else say("Semble diverger près de la borne", "Seems to diverge near the bound")))
            figureTo = b
        }
        out += stp(say("Référence : ∫₁^+∞ 1/xᵅ converge pour α > 1, ∫₀¹ 1/xᵅ pour α < 1", "Reference: ∫₁^+∞ 1/xᵅ converges for α > 1, ∫₀¹ 1/xᵅ for α < 1"))
        Outcome(out, figure = Figure(listOf(Shape2.Area(f, a, figureTo), Shape2.Curve(f, a - 0.5, figureTo, "f")), window = around(f, a - 0.5, figureTo).let { w -> w.copy(yMax = min(w.yMax, max(5.0, abs(w.yMin) + 1))) }))
    },
    Rich(
        "arc-length", Subject.ANALYSIS, title("Longueur d'une courbe", "Arc length"), "L = ∫ₐᵇ √(1 + f′(x)²) dx",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val b = v["b"]
        discontinuity(f, a, b)?.let { return@Rich notContinuous(it) }
        val length = abs(gauss({ x -> sqrt(1 + slope(f, x).pow(2)) }, a, b))
        fun chords(n: Int) = (0 until n).sumOf { i -> val x0 = a + (b - a) * i / n; val x1 = a + (b - a) * (i + 1) / n; sqrt((x1 - x0).pow(2) + (f(x1) - f(x0)).pow(2)) }
        val broken = (0..6).map { i -> val x = a + (b - a) * i / 6; P2(x, f(x)) }
        Outcome(
            listOf(
                res("L ≈ ${show(length)}"),
                stp(say("6 cordes ${show(chords(6))}, 24 cordes ${show(chords(24))}, 96 cordes ${show(chords(96))}", "6 chords ${show(chords(6))}, 24 chords ${show(chords(24))}, 96 chords ${show(chords(96))}")),
                stp(say("Chaque corde mesure √(Δx² + Δy²) ; à la limite √(1 + f′²)·dx", "Each chord is √(Δx² + Δy²); in the limit √(1 + f′²)·dx"))
            ),
            figure = Figure(listOf(Shape2.Curve(f, min(a, b), max(a, b), "f"), Shape2.Polyline(broken, say("6 cordes", "6 chords"), tone = 2), Shape2.Dots(broken, tone = 2)), staged = true, square = true)
        )
    }
)

/** From the same texts: the harmonic sum between two integrals, volumes by known cross sections, Archimedes' quadrature. */
val integralsMore: List<Program> = listOf(
    Rich(
        "harmonic-bounds", Subject.ANALYSIS, title("Somme harmonique et intégrales", "Harmonic sum and integrals"), "ln(n + 1) ≤ Hₙ ≤ 1 + ln n",
        listOf(num("n", "n", "n", whole = true, slider = 1.0..40.0, step = 1.0))
    ) { v ->
        val n = v.long("n")
        require(n in 1..100_000_000)
        var h = 0.0
        for (k in 1..n) h += 1.0 / k
        val shown = min(n, 12L).toInt()
        // Rectangles of width 1 and height 1/k on [k ; k + 1], over the curve; the same shifted left, under it.
        val shapes = mutableListOf<Shape2>()
        for (k in 1..shown) shapes += Shape2.rect(k.toDouble(), 0.0, k + 1.0, 1.0 / k, tone = 1)
        for (k in 2..shown) shapes += Shape2.rect(k - 1.0, 0.0, k.toDouble(), 1.0 / k, tone = 2)
        shapes += Shape2.Curve({ 1 / it }, 0.6, shown + 1.4, "1/x")
        Outcome(
            listOf(
                res("H$n = ${show(h)}"),
                res("${show(ln(n + 1.0))} ≤ H$n ≤ ${show(1 + ln(n.toDouble()))}"),
                stp(say("1/(k + 1) ≤ ∫ₖᵏ⁺¹ dx/x ≤ 1/k, ajouté de k = 1 à n", "1/(k + 1) ≤ ∫ₖᵏ⁺¹ dx/x ≤ 1/k, summed from k = 1 to n")),
                stp(say("Hₙ − ln n = ${show(h - ln(n.toDouble()))} → γ = 0.5772156649 (constante d'Euler)", "Hₙ − ln n = ${show(h - ln(n.toDouble()))} → γ = 0.5772156649 (Euler's constant)")),
                stp(say("Hₙ tend vers +∞, comme ln n", "Hₙ goes to +∞, as ln n"))
            ),
            figure = Figure(shapes, staged = shown <= 6, window = Box2(0.5, shown + 1.5, -0.12, 1.15))
        )
    },
    Rich(
        "cross-sections", Subject.ANALYSIS, title("Volume par sections connues", "Volume by known cross sections"), "V = ∫ₐᵇ A(x) dx, côté s = f(x) − g(x)",
        listOf(fn("f", "f(x), bord haut", "f(x), top edge"), fn("g", "g(x), bord bas", "g(x), bottom edge"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val g = v.f("g"); val a = min(v["a"], v["b"]); val b = max(v["a"], v["b"])
        discontinuity({ x -> f(x) - g(x) }, a, b)?.let { return@Rich notContinuous(it) }
        val s2 = gauss({ x -> (f(x) - g(x)).pow(2) }, a, b)
        val ex = runCatching {
            val F = integrate(simplify(Pow(simplify(v.exact("f") - v.exact("g")), cnum(2))), fr()).result!!
            definite(F, v.exact(if (v["a"] <= v["b"]) "a" else "b"), v.exact(if (v["a"] <= v["b"]) "b" else "a")).takeIf { abs(it.at(0.0) - s2) < 1e-7 * max(1.0, s2) }
        }.getOrNull()
        fun times(k: Ex, label: String, kd: Double) = res(label + " " + valueLine("V", kd * s2, ex?.let { simplify(Mul(listOf(k, it))) }))
        // The base between the curves on the floor, a square standing on each slice.
        val shapes = ArrayList<Shape3>()
        val n = 14
        for (i in 0 until n) {
            val x0 = a + (b - a) * i / n; val x1 = a + (b - a) * (i + 1) / n
            val lo0 = g(x0); val hi0 = f(x0); val lo1 = g(x1); val hi1 = f(x1)
            shapes += Shape3.Face(listOf(Vec3(x0, lo0, 0.0), Vec3(x1, lo1, 0.0), Vec3(x1, hi1, 0.0), Vec3(x0, hi0, 0.0)), tone = 2, soft = true)
            val side = abs(hi0 - lo0)
            if (i % 2 == 0) shapes += Shape3.Face(listOf(Vec3(x0, lo0, 0.0), Vec3(x0, hi0, 0.0), Vec3(x0, hi0, side), Vec3(x0, lo0, side)), tone = 0)
        }
        Outcome(
            listOf(
                times(cnum(1), say("Carrés :", "Squares:"), 1.0),
                times(simplify(Mul(listOf(PI_EX, Pow(cnum(8), cnum(-1))))), say("Demi-disques :", "Semicircles:"), PI / 8),
                times(simplify(Mul(listOf(Pow(cnum(3), Num(Q.HALF)), Pow(cnum(4), cnum(-1))))), say("Triangles équilatéraux :", "Equilateral triangles:"), sqrt(3.0) / 4),
                times(simplify(Pow(cnum(4), cnum(-1))), say("Triangles rectangles isocèles (côté = hypoténuse) :", "Isosceles right triangles (side = hypotenuse):"), 0.25),
                stp(say("Chaque tranche perpendiculaire à l'axe des x a pour côté s = f(x) − g(x) ; A(x) = s², πs²/8, (√3/4)s², s²/4", "Each slice across the x axis has side s = f(x) − g(x); A(x) = s², πs²/8, (√3/4)s², s²/4"))
            ),
            scene = Scene(shapes)
        )
    },
    Rich(
        "archimedes-parabola", Subject.ANALYSIS, title("Quadrature de la parabole (Archimède)", "Quadrature of the parabola (Archimedes)"), "A = 4/3 × triangle",
        listOf(num("p", "p, parabole y = px²", "p, parabola y = px²"), num("u", "Abscisse u", "Point u"), num("w", "Abscisse w", "Point w"), num("k", "Étapes (≤ 6)", "Steps (≤ 6)", whole = true, slider = 1.0..6.0, step = 1.0))
    ) { v ->
        val p = v["p"]; val u = min(v["u"], v["w"]); val w = max(v["u"], v["w"]); val k = v.int("k")
        require(p != 0.0 && w > u && k in 1..6)
        val f = { x: Double -> p * x * x }
        // Each step puts, on every chord, the triangle whose apex is where the tangent is parallel to it: the midpoint's abscissa.
        val shapes = mutableListOf<Shape2>(Shape2.Curve(f, u - (w - u) * 0.3, w + (w - u) * 0.3, "y = px²"), Shape2.Segment(P2(u, f(u)), P2(w, f(w)), tone = 3))
        var chords = listOf(u to w)
        var total = 0.0; var first = 0.0
        val sums = ArrayList<Double>()
        for (step in 1..k) {
            val next = ArrayList<Pair<Double, Double>>()
            for ((x0, x1) in chords) {
                val m = (x0 + x1) / 2
                val t = abs((x1 - x0) * (f(m) - f(x0)) - (m - x0) * (f(x1) - f(x0))) / 2
                if (step == 1) first = t
                total += t
                shapes += Shape2.Polygon(listOf(P2(x0, f(x0)), P2(m, f(m)), P2(x1, f(x1))), tone = (step - 1) % 6, label = if (step == 1) "T" else "")
                next += x0 to m; next += m to x1
            }
            sums += total
            chords = next
        }
        val exactArea = abs(p) * (w - u).pow(3) / 6
        Outcome(
            listOf(
                res(say("Aire du segment ", "Segment's area ") + show(exactArea) + " = 4/3 × T"),
                stp("T = ${show(first)}; T(1 + 1/4 + 1/16 + …) = 4T/3"),
                stp(sums.withIndex().joinToString("   ") { (i, x) -> "S${i + 1} = ${show(x)}" }),
                stp(say("Chaque étape ajoute 2ⁿ triangles d'aire totale le quart de la précédente", "Each step adds 2ⁿ triangles, a quarter of the previous ones' area"))
            ),
            figure = Figure(shapes, staged = true)
        )
    }
)

private fun index(k: Int) = k.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")
