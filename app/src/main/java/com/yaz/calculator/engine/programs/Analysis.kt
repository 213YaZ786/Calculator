package com.yaz.calculator.engine.programs

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import org.hipparchus.analysis.UnivariateFunction
import org.hipparchus.analysis.integration.IterativeLegendreGaussIntegrator
import org.hipparchus.analysis.solvers.BrentSolver

// Programs of analysis written from the curricula (Première 2026, Terminale
// spécialité, AP Calculus BC, A-level Further Pure), every method worked
// step by step; a function is typed with the x key.

private fun d1(f: (Double) -> Double, x: Double): Double {
    val h = 1e-5 * max(1.0, abs(x))
    return (f(x + h) - f(x - h)) / (2 * h)
}

private fun d2(f: (Double) -> Double, x: Double): Double {
    val h = 1e-4 * max(1.0, abs(x))
    return (f(x + h) - 2 * f(x) + f(x - h)) / (h * h)
}

private fun brent(f: (Double) -> Double, a: Double, b: Double): Double? =
    runCatching { BrentSolver(1e-13, 1e-14).solve(10_000, UnivariateFunction { f(it) }, a, b) }.getOrNull()

/** Where [g] changes sign between [a] and [b], each refined by Brent. */
private fun signChanges(g: (Double) -> Double, a: Double, b: Double, steps: Int = 600): List<Double> {
    val out = ArrayList<Double>()
    var x0 = a
    var g0 = g(a)
    for (i in 1..steps) {
        val x1 = a + (b - a) * i / steps
        val g1 = g(x1)
        if (g0.isFinite() && g1.isFinite() && g0 * g1 < 0) brent(g, x0, x1)?.let { out += it }
        x0 = x1; g0 = g1
    }
    return out
}

private fun terms(values: List<Double>, more: Boolean) = values.joinToString("  ") { show(it) } + if (more) "  …" else ""

/** Taylor's coefficients of [f] at [a], from its Chebyshev interpolation on [a − r, a + r]: accurate for smooth functions. */
private fun taylor(f: (Double) -> Double, a: Double, order: Int, r: Double = 0.5): DoubleArray {
    val nodesCount = 40
    val nodes = DoubleArray(nodesCount) { k -> cos(PI * (k + 0.5) / nodesCount) }
    val values = DoubleArray(nodesCount) { k -> f(a + r * nodes[k]) }
    require(values.all { it.isFinite() })
    // Chebyshev coefficients c_j of f(a + r·t), t in [−1, 1], kept while they matter:
    // turning many of them into powers of t would amplify rounding.
    val all = DoubleArray(nodesCount) { j -> 2.0 / nodesCount * (0 until nodesCount).sumOf { k -> values[k] * cos(PI * j * (k + 0.5) / nodesCount) } }
    all[0] /= 2
    val biggest = all.maxOf { abs(it) }
    val m = ((all.indices.lastOrNull { abs(all[it]) > 1e-15 * biggest } ?: 0) + 1).coerceIn(order + 1, 22)
    val c = all.copyOf(m)
    // T_j in powers of t, by T_{j+1} = 2t·T_j − T_{j−1}, then t = (x − a)/r.
    val poly = DoubleArray(m)
    var prev = DoubleArray(m).also { it[0] = 1.0 }
    var cur = DoubleArray(m).also { it[1] = 1.0 }
    for (k in 0 until m) poly[k] += c[0] * prev[k]
    for (k in 0 until m) poly[k] += c[1] * cur[k]
    for (j in 2 until m) {
        val next = DoubleArray(m)
        for (k in 0 until m - 1) next[k + 1] += 2 * cur[k]
        for (k in 0 until m) next[k] -= prev[k]
        for (k in 0 until m) poly[k] += c[j] * next[k]
        prev = cur; cur = next
    }
    return DoubleArray(order + 1) { k -> poly[k] / r.pow(k) }
}

/**
 * A Taylor coefficient as a student expects it: a fraction when it is one
 * (1/24, found by continued fractions, denominators up to 10⁶), else eight
 * digits, as far as the numerical method is sure.
 */
private fun coefficient(v: Double): String {
    if (abs(v) < 1e-11) return "0"
    var x = abs(v)
    var (p0, q0, p1, q1) = listOf(0L, 1L, 1L, 0L)
    repeat(20) {
        val a = kotlin.math.floor(x).toLong()
        val p2 = a * p1 + p0; val q2 = a * q1 + q0
        if (q2 > 1_000_000) return@repeat
        p0 = p1; q0 = q1; p1 = p2; q1 = q2
        if (abs(p1.toDouble() / q1 - abs(v)) <= 1e-8 * abs(v)) {
            val sign = if (v < 0) "−" else ""
            return if (q1 == 1L) "$sign$p1" else "$sign$p1/$q1"
        }
        val f = x - a
        if (f < 1e-12) return@repeat
        x = 1 / f
    }
    return java.math.BigDecimal(v).round(java.math.MathContext(8)).stripTrailingZeros().toPlainString().replace('-', '−')
}

private fun polynomial(c: DoubleArray, a: Double): String = c.withIndex().filter { abs(it.value) > 1e-11 }.joinToString(" + ") { (k, v) ->
    val power = when (k) { 0 -> ""; 1 -> if (a == 0.0) "x" else "(x − ${show(a)})"; else -> (if (a == 0.0) "x" else "(x − ${show(a)})") + superscript(k) }
    val text = coefficient(v)
    if (k == 0) text else if (text == "1") power else if (text == "−1") "−$power" else if ('/' in text) "($text)$power" else "$text$power"
}.ifEmpty { "0" }.replace("+ −", "− ")

private fun superscript(k: Int) = k.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

val analysis: List<Program> = listOf(
    Rich(
        "threshold", Subject.ANALYSIS, title("Seuil d'une suite", "Sequence threshold"), "uₙ₊₁ = f(uₙ), uₙ > S",
        listOf(num("u₀", "Premier terme", "First term"), fn("f", "uₙ₊₁ = f(x), x pour uₙ", "uₙ₊₁ = f(x), x for uₙ"), num("S", "Seuil", "Threshold"))
    ) { v ->
        val f = v.f("f"); var u = v["u₀"]; val s = v["S"]
        val up = u <= s
        val seen = ArrayList<Double>()
        var n = 0
        while (if (up) u <= s else u >= s) {
            if (seen.size < 8) seen += u
            u = f(u); n++
            require(u.isFinite() && n <= 1_000_000)
        }
        lines(
            res(if (up) "n = $n  (u$n = ${show(u)} > ${show(s)})" else "n = $n  (u$n = ${show(u)} < ${show(s)})"),
            stp("u₀, u₁, … : ${terms(seen, n > 8)}"),
            stp(say("Algorithme : n ← 0 ; tant que u ${if (up) "≤" else "≥"} S : u ← f(u), n ← n + 1", "Algorithm: n ← 0; while u ${if (up) "≤" else "≥"} S: u ← f(u), n ← n + 1"))
        )
    },
    Rich(
        "tangent", Subject.ANALYSIS, title("Tangente et approximation", "Tangent and linear approximation"), "y = f′(a)(x − a) + f(a)",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "Abscisse a", "Point a"), num("h", "Écart h (facultatif)", "Step h (optional)", optional = true))
    ) { v ->
        val f = v.f("f"); val a = v["a"]
        val fa = f(a); val slope = d1(f, a)
        require(fa.isFinite() && slope.isFinite())
        val out = mutableListOf(
            res("f(a) = ${show(fa)}   f′(a) ≈ ${show(slope)}"),
            res("y = ${show(slope)}(x − ${show(a)}) + ${show(fa)}"),
            res("y = ${show(slope)}x + ${show(fa - slope * a)}"),
            stp(say("Pentes des sécantes, (f(a + h) − f(a)) / h :", "Slopes of the secants, (f(a + h) − f(a)) / h:"))
        )
        for (h in listOf(1.0, 0.1, 0.01, 0.001)) out += stp("h = ${show(h)} : ${show((f(a + h) - fa) / h)}")
        if (v.has("h")) {
            val h = v["h"]
            out += res(say("f(a + h) ≈ f(a) + f′(a)·h = ${show(fa + slope * h)}  (exact ${show(f(a + h))})", "f(a + h) ≈ f(a) + f′(a)·h = ${show(fa + slope * h)}  (exact ${show(f(a + h))})"))
        }
        Outcome(out)
    },
    Rich(
        "newton", Subject.ANALYSIS, title("Méthode de Newton", "Newton's method"), "xₙ₊₁ = xₙ − f(xₙ) / f′(xₙ)",
        listOf(fn("f", "f(x)", "f(x)"), num("x₀", "Départ", "Start"))
    ) { v ->
        val f = v.f("f"); var x = v["x₀"]
        val out = mutableListOf<Line>()
        for (k in 1..40) {
            val slope = d1(f, x)
            require(slope != 0.0 && slope.isFinite())
            val next = x - f(x) / slope
            require(next.isFinite())
            if (k <= 8) out += stp("x$k = ${show(next)}")
            val done = abs(next - x) < 1e-12 * max(1.0, abs(next))
            x = next
            if (done) {
                out.add(0, res("x ≈ ${show(x)}   f(x) = ${show(f(x))}"))
                out.add(1, stp(say("$k itérations", "$k iterations")))
                return@Rich Outcome(out)
            }
        }
        lines(stp(say("Pas de convergence en 40 itérations : un autre départ ?", "No convergence in 40 iterations: another start?")))
    },
    Rich(
        "bisection", Subject.ANALYSIS, title("Dichotomie", "Bisection"), "f(a)·f(b) < 0, m = (a + b)/2",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"), num("ε", "Précision", "Precision"))
    ) { v ->
        val f = v.f("f"); var a = v["a"]; var b = v["b"]; val e = abs(v["ε"])
        require(e > 0 && f(a) * f(b) < 0)
        val out = mutableListOf<Line>()
        var k = 0
        while (b - a > e && k < 200) {
            val m = (a + b) / 2
            if (f(a) * f(m) <= 0) b = m else a = m
            k++
            if (k <= 6) out += stp("[${show(a)} ; ${show(b)}]")
        }
        out.add(0, res("${show(a)} ≤ x ≤ ${show(b)}"))
        out.add(1, stp(say("$k étapes, amplitude ${show(b - a)}", "$k steps, width ${show(b - a)}")))
        Outcome(out)
    },
    Rich(
        "convexity", Subject.ANALYSIS, title("Variations, convexité, inflexion", "Variations, convexity, inflection"), "f′(x) = 0, f″(x) = 0",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val b = v["b"]
        require(a < b)
        val extrema = signChanges({ d1(f, it) }, a, b)
        val inflections = signChanges({ d2(f, it) }, a, b)
        val out = mutableListOf<Line>()
        extrema.forEach { x -> out += res((if (d2(f, x) > 0) say("Minimum", "Minimum") else say("Maximum", "Maximum")) + " : f(${show(x)}) = ${show(f(x))}") }
        inflections.forEach { x -> out += res(say("Point d'inflexion", "Inflection point") + " : (${show(x)} ; ${show(f(x))})") }
        // The intervals between the inflections, convex or concave.
        val cuts = listOf(a) + inflections + listOf(b)
        for (i in 0 until cuts.size - 1) {
            val mid = (cuts[i] + cuts[i + 1]) / 2
            out += stp("[${show(cuts[i])} ; ${show(cuts[i + 1])}] : " + if (d2(f, mid) > 0) say("convexe (f″ > 0)", "convex (f″ > 0)") else say("concave (f″ < 0)", "concave (f″ < 0)"))
        }
        if (extrema.isEmpty() && inflections.isEmpty()) out += res(say("Ni extremum ni inflexion sur [a ; b]", "No extremum nor inflection on [a ; b]"))
        Outcome(out)
    },
    Rich(
        "integral", Subject.ANALYSIS, title("Intégrale : rectangles, trapèzes, Simpson", "Integral: rectangles, trapezoids, Simpson"), "∫ₐᵇ f(x) dx",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"), num("n", "Subdivisions", "Subintervals", whole = true))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val b = v["b"]; val n = v.int("n").let { if (it % 2 == 1) it + 1 else it }
        require(n in 2..100_000)
        val h = (b - a) / n
        val ys = DoubleArray(n + 1) { f(a + it * h) }
        require(ys.all { it.isFinite() })
        val left = h * (0 until n).sumOf { ys[it] }
        val right = h * (1..n).sumOf { ys[it] }
        val mid = h * (0 until n).sumOf { f(a + (it + 0.5) * h) }
        val trap = h * (ys.first() / 2 + (1 until n).sumOf { ys[it] } + ys.last() / 2)
        val simpson = h / 3 * (ys.first() + ys.last() + (1 until n).sumOf { if (it % 2 == 1) 4 * ys[it] else 2 * ys[it] })
        val exact = runCatching { IterativeLegendreGaussIntegrator(16, 1e-14, 1e-14).integrate(1_000_000, UnivariateFunction { f(it) }, minOf(a, b), max(a, b)) * (if (a <= b) 1 else -1) }.getOrNull()
        Outcome(listOfNotNull(
            res("Simpson : ${show(simpson)}"),
            stp(say("Rectangles à gauche ${show(left)}, à droite ${show(right)}, au milieu ${show(mid)}", "Rectangles left ${show(left)}, right ${show(right)}, midpoint ${show(mid)}")),
            stp(say("Trapèzes ${show(trap)}", "Trapezoids ${show(trap)}")),
            exact?.let { res(say("Valeur (Gauss-Legendre) ${show(it)}", "Value (Gauss–Legendre) ${show(it)}")) },
            stp(say("Valeur moyenne ${show((exact ?: simpson) / (b - a))}", "Mean value ${show((exact ?: simpson) / (b - a))}"))
        ))
    },
    Rich(
        "linear-ode", Subject.ANALYSIS, title("Équation y′ = ay + b", "Equation y′ = ay + b"), "y = (y₀ + b/a)·e^(a(x − x₀)) − b/a",
        listOf(num("a", "a", "a"), num("b", "b", "b"), num("x₀", "x₀", "x₀"), num("y₀", "y(x₀)", "y(x₀)"), num("x", "x cherché", "x wanted"), num("h", "Pas d'Euler (facultatif)", "Euler step (optional)", optional = true))
    ) { v ->
        val a = v["a"]; val b = v["b"]; val x0 = v["x₀"]; val y0 = v["y₀"]; val x = v["x"]
        val out = mutableListOf<Line>()
        val exact = if (a == 0.0) y0 + b * (x - x0) else (y0 + b / a) * exp(a * (x - x0)) - b / a
        if (a == 0.0) out += res("y = ${show(y0)} + ${show(b)}(x − ${show(x0)})")
        else {
            out += res("y = ${show(y0 + b / a)}·e^(${show(a)}(x − ${show(x0)})) + ${show(-b / a)}")
            out += stp(say("Solution constante (équilibre) y = −b/a = ${show(-b / a)}", "Constant solution (equilibrium) y = −b/a = ${show(-b / a)}"))
        }
        out += res("y(${show(x)}) = ${show(exact)}")
        if (v.has("h")) {
            val h = abs(v["h"]).let { if (x >= x0) it else -it }
            require(h != 0.0)
            var t = x0; var y = y0; var k = 0
            while ((x - t) / h > 1e-9 && k < 1_000_000) { y += h * (a * y + b); t += h; k++ }
            out += stp(say("Euler (h = ${show(abs(h))}, $k pas) : ${show(y)}, écart ${show(y - exact)}", "Euler (h = ${show(abs(h))}, $k steps): ${show(y)}, error ${show(y - exact)}"))
        }
        Outcome(out)
    },
    Rich(
        "logistic", Subject.ANALYSIS, title("Modèle logistique", "Logistic model"), "P′ = rP(1 − P/K)",
        listOf(num("K", "Capacité K", "Capacity K"), num("r", "Taux r", "Rate r"), num("P₀", "P(0)", "P(0)"), num("t", "Instant t", "Time t"), num("P", "Effectif cherché (facultatif)", "Population wanted (optional)", optional = true))
    ) { v ->
        val k = v["K"]; val r = v["r"]; val p0 = v["P₀"]; val t = v["t"]
        require(k > 0 && p0 > 0 && r != 0.0)
        val c = (k - p0) / p0
        val out = mutableListOf(
            res("P(t) = ${show(k)} / (1 + ${show(c)}·e^(−${show(r)}t))"),
            res("P(${show(t)}) = ${show(k / (1 + c * exp(-r * t)))}")
        )
        if (c > 0) out += stp(say("Croissance la plus rapide en t = ln(${show(c)}) / r = ${show(ln(c) / r)}, où P = K/2", "Fastest growth at t = ln(${show(c)}) / r = ${show(ln(c) / r)}, where P = K/2"))
        if (v.has("P")) {
            val p = v["P"]
            require(p in 0.0..k)
            out += res(say("P = ${show(p)} en t = ${show(ln(c * p / (k - p)) / r)}", "P = ${show(p)} at t = ${show(ln(c * p / (k - p)) / r)}"))
        }
        Outcome(out)
    },
    Rich(
        "taylor", Subject.ANALYSIS, title("Série de Taylor", "Taylor series"), "f(x) ≈ Σ f⁽ᵏ⁾(a)/k!·(x − a)ᵏ",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "Centre a (0 : Maclaurin)", "Centre a (0: Maclaurin)"), num("n", "Ordre (≤ 10)", "Order (≤ 10)", whole = true), num("x", "x pour comparer (facultatif)", "x to compare (optional)", optional = true))
    ) { v ->
        val f = v.f("f"); val a = v["a"]; val n = v.int("n")
        require(n in 0..10)
        val c = taylor(f, a, n)
        val out = mutableListOf(res("Tₙ(x) = ${polynomial(c, a)}"))
        c.forEachIndexed { k, ck -> if (k <= 6) out += stp("f⁽${k}⁾(a) / $k! = ${coefficient(ck)}") }
        if (v.has("x")) {
            val x = v["x"]
            val tx = c.withIndex().sumOf { (k, ck) -> ck * (x - a).pow(k) }
            out += res(say("Tₙ(${show(x)}) = ${show(tx)}   f(${show(x)}) = ${show(f(x))}   écart ${show(f(x) - tx)}", "Tₙ(${show(x)}) = ${show(tx)}   f(${show(x)}) = ${show(f(x))}   error ${show(f(x) - tx)}"))
        }
        Outcome(out)
    },
    Rich(
        "series", Subject.ANALYSIS, title("Série numérique", "Numerical series"), "Sₙ = Σₖ u(k)",
        listOf(fn("u", "u(x), x pour k", "u(x), x for k"), num("k₀", "Premier rang", "First index", whole = true), num("N", "Dernier rang", "Last index", whole = true))
    ) { v ->
        val u = v.f("u"); val k0 = v.long("k₀"); val n = v.long("N")
        require(n >= k0 && n - k0 <= 2_000_000)
        fun sum(to: Long): Double { var s = 0.0; var k = k0; while (k <= to) { s += u(k.toDouble()); k++ }; return s }
        val s1 = sum(n); val s2 = sum(k0 + 2 * (n - k0)); val s4 = sum(k0 + 4 * (n - k0))
        require(s1.isFinite())
        val ratio = abs(u((n + 1).toDouble()) / u(n.toDouble()))
        val out = mutableListOf(
            res("S${n} = ${show(s1)}"),
            stp("S(2N) = ${show(s2)}   S(4N) = ${show(s4)}"),
            stp(say("|uₙ₊₁ / uₙ| ≈ ${show(ratio)} au rang N", "|uₙ₊₁ / uₙ| ≈ ${show(ratio)} at N"))
        )
        out += stp(
            when {
                ratio < 0.95 -> say("Rapport < 1 : la série converge (critère de d'Alembert)", "Ratio < 1: the series converges (ratio test)")
                abs(u(n.toDouble())) > 1e-3 * abs(u(k0.toDouble())) && ratio >= 1 -> say("Le terme ne tend pas vers 0 : la série diverge", "The term does not go to 0: the series diverges")
                abs(s4 - s2) < abs(s2 - s1) * 0.6 -> say("Les écarts diminuent : convergence probable (rapport ≈ 1, critère non concluant)", "The gaps shrink: likely convergence (ratio ≈ 1, test inconclusive)")
                else -> say("Rapport ≈ 1 : critère non concluant, comparer à 1/kᵅ", "Ratio ≈ 1: inconclusive, compare with 1/kᵅ")
            }
        )
        Outcome(out)
    },
    Rich(
        "polar", Subject.ANALYSIS, title("Courbe polaire : aire et longueur", "Polar curve: area and length"), "A = ½∫ r² dθ",
        listOf(fn("r", "r = f(x), x pour θ (radians)", "r = f(x), x for θ (radians)"), num("α", "θ de départ", "Start θ"), num("β", "θ de fin", "End θ"))
    ) { v ->
        val r = v.f("r"); val a = v["α"]; val b = v["β"]
        val integrator = IterativeLegendreGaussIntegrator(16, 1e-12, 1e-12)
        val area = 0.5 * integrator.integrate(1_000_000, UnivariateFunction { r(it).pow(2) }, a, b)
        val length = integrator.integrate(1_000_000, UnivariateFunction { sqrt(r(it).pow(2) + d1(r, it).pow(2)) }, a, b)
        lines(res(say("Aire ${show(area)}", "Area ${show(area)}")), res(say("Longueur ${show(length)}", "Length ${show(length)}")))
    }
)

val sequences: List<Program> = listOf(
    Rich(
        "syracuse", Subject.MATHS, title("Suite de Syracuse", "Collatz sequence"), "uₙ/2, 3uₙ + 1",
        listOf(num("N", "Départ", "Start", whole = true))
    ) { v ->
        var u = BigInteger.valueOf(v.long("N"))
        require(u.signum() > 0 && u.bitLength() < 62)
        val first = ArrayList<String>()
        var top = u; var steps = 0
        while (u != BigInteger.ONE && steps < 1_000_000) {
            if (first.size < 12) first += u.toString()
            u = if (u.testBit(0)) u.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE) else u.shiftRight(1)
            if (u > top) top = u
            steps++
        }
        lines(
            res(say("Durée de vol $steps, altitude maximale $top", "Flight time $steps, highest value $top")),
            stp(first.joinToString("  ") + if (steps > 12) "  …  1" else "  1")
        )
    },
    Rich(
        "fibonacci", Subject.MATHS, title("Suite de Fibonacci", "Fibonacci sequence"), "Fₙ₊₂ = Fₙ₊₁ + Fₙ",
        listOf(num("n", "Rang", "Index", whole = true))
    ) { v ->
        val n = v.int("n")
        require(n in 0..5000)
        var a = BigInteger.ZERO; var b = BigInteger.ONE
        repeat(n) { val c = a.add(b); a = b; b = c }
        val text = a.toString()
        val ratio = if (n >= 2) b.toBigDecimal().divide(a.toBigDecimal(), java.math.MathContext(12)).toDouble() else Double.NaN
        lines(
            res("F$n = " + if (text.length <= 60) text else text.take(30) + "… (${text.length} " + say("chiffres", "digits") + ")"),
            stp("Fₙ₊₁ / Fₙ = ${show(ratio)} → φ = ${show((1 + sqrt(5.0)) / 2)}")
        )
    },
    Rich(
        "power-sums", Subject.MATHS, title("Sommes des entiers, carrés, cubes", "Sums of integers, squares, cubes"), "1 + 2 + … + n = n(n + 1)/2",
        listOf(num("n", "n", "n", whole = true))
    ) { v ->
        val n = BigInteger.valueOf(v.long("n"))
        require(n.signum() >= 0)
        val s1 = n.multiply(n.add(BigInteger.ONE)).shiftRight(1)
        val s2 = n.multiply(n.add(BigInteger.ONE)).multiply(n.shiftLeft(1).add(BigInteger.ONE)).divide(BigInteger.valueOf(6))
        lines(
            res("1 + 2 + … + n = n(n + 1)/2 = $s1"),
            res("1² + … + n² = n(n + 1)(2n + 1)/6 = $s2"),
            res("1³ + … + n³ = (n(n + 1)/2)² = ${s1.multiply(s1)}")
        )
    },
    Rich(
        "loan", Subject.MATHS, title("Emprunt à mensualités constantes", "Loan with fixed monthly payments"), "m = C·i / (1 − (1 + i)⁻ᴺ)",
        listOf(num("C", "Capital emprunté", "Amount borrowed"), num("t", "Taux annuel (%)", "Annual rate (%)", "%"), num("n", "Durée (années)", "Term (years)"))
    ) { v ->
        val c = v["C"]; val i = v["t"] / 100 / 12; val months = Math.round(v["n"] * 12).toInt()
        require(c > 0 && months in 1..1200)
        val m = if (i == 0.0) c / months else c * i / (1 - (1 + i).pow(-months))
        val out = mutableListOf(
            res(say("Mensualité ${show(m)}", "Monthly payment ${show(m)}")),
            res(say("Coût total du crédit ${show(m * months - c)}", "Total interest ${show(m * months - c)}"))
        )
        var left = c
        for (k in 1..3) {
            val interest = left * i
            left -= m - interest
            out += stp(say("Mois $k : intérêts ${show(interest)}, capital ${show(m - interest)}, reste ${show(left)}", "Month $k: interest ${show(interest)}, principal ${show(m - interest)}, left ${show(left)}"))
        }
        Outcome(out)
    },
    Rich(
        "archimedes", Subject.MATHS, title("π par Archimède, e par (1 + 1/n)ⁿ", "π by Archimedes, e by (1 + 1/n)ⁿ"), "6·2ᵏ, (1 + 1/n)ⁿ",
        listOf(num("k", "Doublements des côtés", "Side doublings", whole = true))
    ) { v ->
        val k = v.int("k")
        require(k in 0..25)
        // Perimeters of the inscribed and circumscribed polygons of a circle of diameter 1 (Archimedes' recurrence).
        var outer = 2 * sqrt(3.0); var inner = 3.0
        repeat(k) { outer = 2 * outer * inner / (outer + inner); inner = sqrt(outer * inner) }
        val sides = 6L shl k
        // Beyond 10⁸ the double's rounding spoils (1 + 1/n)ⁿ.
        val n = 10.0.pow(k.coerceIn(1, 8))
        lines(
            res("${show(inner)} < π < ${show(outer)}  ($sides " + say("côtés", "sides") + ")"),
            stp("(1 + 1/n)ⁿ, n = ${show(n)} : ${show((1 + 1 / n).pow(n))}   e = ${show(Math.E)}")
        )
    }
)
