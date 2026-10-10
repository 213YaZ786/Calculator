package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.cas.Fn
import com.yaz.calculator.engine.cas.Num
import com.yaz.calculator.engine.cas.simplify
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import com.yaz.calculator.engine.cas.show as exact

// The analysis algorithms of the programmes (Première and Terminale 2026,
// maths complémentaires): the secant method, Euler for y′ = f, Briggs'
// logarithms (square roots until x is near 1), Heron, π e √2 φ ln 2 by
// sequences, the unit circle wrapping the real line, Newton's cooling.

/** Correct decimals of [x] against [truth]. */
private fun digits(x: Double, truth: Double): Int = if (x == truth) 15 else max(0, (-log10(abs(x - truth) / max(1.0, abs(truth)))).toInt()).coerceAtMost(15)

val approximations: List<Program> = listOf(
    Rich(
        "secant", Subject.ANALYSIS, title("Méthode de la sécante", "Secant method"), "xₙ₊₁ = xₙ − f(xₙ)(xₙ − xₙ₋₁)/(f(xₙ) − f(xₙ₋₁))",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "x₀", "x₀"), num("b", "x₁", "x₁"))
    ) { v ->
        val f = v.f("f"); var x0 = v["a"]; var x1 = v["b"]
        val visits = arrayListOf(x0, x1)
        val out = mutableListOf<Line>()
        for (k in 2..60) {
            val d = f(x1) - f(x0)
            require(d != 0.0 && d.isFinite())
            val x2 = x1 - f(x1) * (x1 - x0) / d
            require(x2.isFinite())
            if (k <= 8) out += stp("x$k = ${show(x2)}")
            if (visits.size < 7) visits += x2
            val done = abs(x2 - x1) < 1e-12 * max(1.0, abs(x2))
            x0 = x1; x1 = x2
            if (done) {
                out.add(0, res("x ≈ ${show(x1)}   f(x) = ${show(f(x1))}"))
                out.add(1, stp(say("$k itérations ; Newton sans dérivée : la corde remplace la tangente", "$k iterations; Newton without a derivative: the chord replaces the tangent")))
                val lo = visits.min(); val hi = visits.max(); val pad = max(0.5, (hi - lo) * 0.3)
                val shapes = mutableListOf<Shape2>(Shape2.Curve(f, lo - pad, hi + pad, "f"))
                for (i in 0 until visits.size - 2) {
                    val p = visits[i]; val q = visits[i + 1]; val next = visits[i + 2]
                    // The chord through the last two points, then on to the axis, where the next start is.
                    shapes += Shape2.Segment(P2(p, f(p)), P2(q, f(q)), tone = 1)
                    val far = if (abs(next - p) > abs(next - q)) P2(p, f(p)) else P2(q, f(q))
                    shapes += Shape2.Segment(far, P2(next, 0.0), tone = 1, dashed = true, label = if (i < 3) "x${i + 2}" else "")
                    shapes += Shape2.Point(P2(next, 0.0), tone = 2)
                }
                return@Rich Outcome(out, figure = Figure(shapes, staged = true, window = around(f, lo - pad, hi + pad)))
            }
        }
        lines(stp(say("Pas de convergence en 60 itérations : d'autres départs ?", "No convergence in 60 iterations: other starts?")))
    },
    Rich(
        "euler-primitive", Subject.ANALYSIS, title("Méthode d'Euler pour y′ = f(x)", "Euler's method for y′ = f(x)"), "yₙ₊₁ = yₙ + h·f(xₙ)",
        listOf(fn("f", "f(x)", "f(x)"), num("x₀", "x₀", "x₀"), num("y₀", "y(x₀)", "y(x₀)"), num("h", "Pas h", "Step h", slider = 0.01..1.0, step = 0.01), num("x", "Jusqu'à x", "Up to x"))
    ) { v ->
        val f = v.f("f"); val x0 = v["x₀"]; val y0 = v["y₀"]; val h = abs(v["h"]).let { if (v["x"] >= x0) it else -it }; val to = v["x"]
        require(h != 0.0 && abs((to - x0) / h) <= 100_000)
        var x = x0; var y = y0
        val broken = arrayListOf(P2(x, y))
        while ((to - x) / h > 1e-9) { y += h * f(x); x += h; if (broken.size <= 500) broken += P2(x, y) }
        // The true solution: y₀ + ∫ f from x₀.
        val truth = y0 + gauss(f, x0, to)
        val exactF = runCatching { com.yaz.calculator.engine.cas.integrate(v.exact("f"), false).result }.getOrNull()
        val out = mutableListOf(
            res("y(${show(to)}) ≈ ${show(y)}"),
            stp(say("Valeur exacte y₀ + ∫ f = ${show(truth)}, écart ${show(y - truth)} (proportionnel à h)", "Exact value y₀ + ∫ f = ${show(truth)}, error ${show(y - truth)} (proportional to h)"))
        )
        exactF?.let { out += stp(say("Solution : y = F(x) − F(x₀) + y₀, F(x) = ${exact(it)}", "Solution: y = F(x) − F(x₀) + y₀, F(x) = ${exact(it)}")) }
        val solution = { t: Double -> y0 + gauss(f, x0, t) }
        Outcome(out, figure = Figure(listOf(Shape2.Curve(solution, min(x0, to), max(x0, to), "y"), Shape2.Polyline(broken, "Euler", 1), Shape2.Dots(broken.take(80), tone = 1)), staged = true))
    },
    Rich(
        "briggs", Subject.ANALYSIS, title("Logarithme par la méthode de Briggs", "Logarithm by Briggs' method"), "ln x = 2ⁿ ln(x^(1/2ⁿ)), ln u ≈ u − 1 près de 1",
        listOf(num("x", "x > 0", "x > 0"), num("p", "Précision 10⁻ᵖ (p ≤ 12)", "Precision 10⁻ᵖ (p ≤ 12)", whole = true, slider = 1.0..12.0, step = 1.0))
    ) { v ->
        val x = v["x"]; val p = v.int("p")
        require(x > 0 && p in 1..12)
        // Square roots until the number is within 10⁻ᵖ of 1, then (u − 1)/u ≤ ln u ≤ u − 1.
        var u = x; var n = 0
        val steps = ArrayList<Line>()
        while (abs(u - 1) > 10.0.let { Math.pow(it, -p.toDouble()) } && n < 60) { u = sqrt(u); n++; if (n <= 6) steps += stp(say("√ $n fois : ${show(u)}", "√ $n times: ${show(u)}")) }
        val lo = Math.pow(2.0, n.toDouble()) * (u - 1) / u
        val hi = Math.pow(2.0, n.toDouble()) * (u - 1)
        Outcome(listOf(
            res("${show(lo)} ≤ ln ${show(x)} ≤ ${show(hi)}"),
            stp(say("$n racines carrées : u = x^(1/2ⁿ) = ${show(u)}, ln x = 2ⁿ ln u, (u − 1)/u ≤ ln u ≤ u − 1", "$n square roots: u = x^(1/2ⁿ) = ${show(u)}, ln x = 2ⁿ ln u, (u − 1)/u ≤ ln u ≤ u − 1")),
            stp("ln ${show(x)} = ${show(ln(x))}")
        ) + steps)
    },
    Rich(
        "heron", Subject.ANALYSIS, title("Méthode de Héron", "Heron's method"), "xₙ₊₁ = (xₙ + a/xₙ)/2 → √a",
        listOf(num("a", "a > 0", "a > 0"), num("x₀", "Départ x₀ > 0", "Start x₀ > 0"), num("n", "Étapes (≤ 10)", "Steps (≤ 10)", whole = true, slider = 1.0..10.0, step = 1.0))
    ) { v ->
        val a = v["a"]; var x = v["x₀"]; val n = v.int("n")
        require(a > 0 && x > 0 && n in 1..10)
        // Exactly, as fractions, while they stay small: the digits double at each step.
        var q = java.math.BigDecimal(x)
        val out = mutableListOf<Line>()
        val path = arrayListOf(x)
        val mc = java.math.MathContext(60)
        val big = java.math.BigDecimal(a)
        for (k in 1..n) {
            q = q.add(big.divide(q, mc)).divide(java.math.BigDecimal(2), mc)
            x = (x + a / x) / 2; path += x
            out += stp("x$k = ${q.round(java.math.MathContext(20)).toPlainString()}   (${digits(x, sqrt(a))} ${say("décimales justes", "correct decimals")})")
        }
        out.add(0, res("√${show(a)} ≈ ${q.round(java.math.MathContext(20)).toPlainString()}"))
        out += stp(say("Newton pour x² − a : le nombre de décimales justes double à chaque étape", "Newton for x² − a: the number of correct decimals doubles at each step"))
        val f = { t: Double -> (t + a / t) / 2 }
        val lo = 0.0; val hi = max(path.max(), sqrt(a)) * 1.2
        val web = ArrayList<P2>(); web += P2(path[0], 0.0)
        for (i in 0 until min(path.size - 1, 6)) { web += P2(path[i], path[i + 1]); web += P2(path[i + 1], path[i + 1]) }
        Outcome(out, figure = Figure(listOf(Shape2.Curve({ it }, lo, hi, "y = x", 2, dashed = true), Shape2.Curve(f, sqrt(a) * 0.2, hi, "(x + a/x)/2"), Shape2.Polyline(web, tone = 1), Shape2.Point(P2(sqrt(a), sqrt(a)), "√a", 2)), staged = true, square = true, window = Box2(-hi * 0.05, hi, -hi * 0.05, hi)))
    },
    Rich(
        "constants", Subject.ANALYSIS, title("π, e, √2, φ, ln 2 par des suites", "π, e, √2, φ, ln 2 by sequences"), "Leibniz, Σ 1/k!, Héron, Fibonacci, Σ (−1)ᵏ⁺¹/k",
        listOf(num("n", "Rang n (≤ 60)", "Index n (≤ 60)", whole = true, slider = 1.0..60.0, step = 1.0))
    ) { v ->
        val n = v.int("n")
        require(n in 1..60)
        // Each sequence term by term, the correct digits counted.
        val pi = DoubleArray(n + 1); val e = DoubleArray(n + 1); val r2 = DoubleArray(n + 1); val phi = DoubleArray(n + 1); val l2 = DoubleArray(n + 1)
        var s = 0.0; var fact = 1.0; var se = 0.0; var h = 1.0; var fa = 1.0; var fb = 1.0; var sl = 0.0
        for (k in 0..n) {
            s += (if (k % 2 == 0) 4.0 else -4.0) / (2 * k + 1); pi[k] = s
            if (k > 0) fact *= k; se += 1 / fact; e[k] = se
            if (k > 0) h = (h + 2 / h) / 2; r2[k] = h
            if (k > 0) { val t = fa + fb; fa = fb; fb = t }; phi[k] = fb / fa
            if (k > 0) sl += (if (k % 2 == 1) 1.0 else -1.0) / k; l2[k] = sl
        }
        val truth = listOf(PI, Math.E, sqrt(2.0), (1 + sqrt(5.0)) / 2, ln(2.0))
        val names = listOf("π", "e", "√2", "φ", "ln 2")
        val seqs = listOf(pi, e, r2, phi, l2)
        val how = listOf(say("Leibniz 4(1 − 1/3 + 1/5 − …)", "Leibniz 4(1 − 1/3 + 1/5 − …)"), "Σ 1/k!", say("Héron", "Heron"), "Fₙ₊₁/Fₙ", "1 − 1/2 + 1/3 − …")
        val out = names.indices.map { i -> stp("${names[i]} ≈ ${show(seqs[i][n])}   ${how[i]}, ${digits(seqs[i][n], truth[i])} ${say("décimales", "decimals")}") }
        val shapes = names.indices.map { i -> Shape2.Polyline((0..n).map { k -> P2(k.toDouble(), digits(seqs[i][k], truth[i]).toDouble()) }, names[i], i) }
        Outcome(
            listOf(res(say("Décimales justes au rang $n : ", "Correct decimals at n = $n: ") + names.indices.joinToString("  ") { "${names[it]} ${digits(seqs[it][n], truth[it])}" })) + out +
                stp(say("La figure : décimales justes selon n ; Héron double, Σ 1/k! accélère, Leibniz et ln 2 rampent (une décimale de plus pour 10 fois plus de termes)", "The figure: correct decimals against n; Heron doubles, Σ 1/k! speeds up, Leibniz and ln 2 crawl (one more decimal for ten times the terms)")),
            figure = Figure(shapes, staged = true, window = Box2(-n * 0.04, n * 1.05, -0.8, 16.5))
        )
    },
    Rich(
        "unit-circle", Subject.ANALYSIS, title("Cercle trigonométrique", "Unit circle"), "M(θ) = (cos θ ; sin θ)",
        listOf(num("θ", "θ (radians)", "θ (radians)", slider = -6.3..6.3, step = 0.01))
    ) { v ->
        val t = v["θ"]
        val c = cos(t); val s = sin(t)
        // Exact values when θ is a known angle (π/3, 3π/4…): from the symbolic engine.
        val ex = runCatching { v.exact("θ") }.getOrNull()
        val cx = ex?.let { simplify(Fn("cos", it)) }?.takeIf { it is Num || it.toString().length < 40 }
        val sx = ex?.let { simplify(Fn("sin", it)) }
        fun write(e: com.yaz.calculator.engine.cas.Ex?, value: Double) = e?.let { if (it is Fn) show(value) else exact(it) } ?: show(value)
        val wraps = Math.floor(t / (2 * PI)).toInt()
        val arc = (0..120).map { i -> val a = t * i / 120; P2(cos(a), sin(a)) }
        val shapes = listOf(
            Shape2.Circle(P2(0.0, 0.0), 1.0, tone = 3),
            // The real line wound round the circle from (1 ; 0): the arc has length |θ|.
            Shape2.Segment(P2(1.0, 0.0), P2(1.0, t), "θ", 1, dashed = true),
            Shape2.Polyline(arc, tone = 0),
            Shape2.Segment(P2(0.0, 0.0), P2(c, s), tone = 0),
            Shape2.Segment(P2(c, s), P2(c, 0.0), "sin", 2, dashed = true),
            Shape2.Segment(P2(c, s), P2(0.0, s), "cos", 4, dashed = true),
            Shape2.Point(P2(c, s), "M", 0)
        )
        Outcome(
            listOf(
                res("cos θ = ${write(cx, c)}   sin θ = ${write(sx, s)}"),
                stp(if (abs(c) > 1e-12) "tan θ = ${write(ex?.let { simplify(Fn("tan", it)) }, tan(t))}" else say("tan θ n'existe pas (cos θ = 0)", "tan θ does not exist (cos θ = 0)")),
                stp("cos²θ + sin²θ = 1")
            ) + listOfNotNull(
                if (wraps != 0) stp(say("θ et θ + 2kπ donnent le même point : θ = ${show(t - wraps * 2 * PI)} + ${wraps}×2π", "θ and θ + 2kπ give the same point: θ = ${show(t - wraps * 2 * PI)} + ${wraps}×2π").replace("+ -", "− ")) else null
            ),
            figure = Figure(shapes, staged = true, square = true, window = Box2(-1.5, 1.8, min(-1.4, t - 0.2), max(1.4, t + 0.2)))
        )
    },
    Rich(
        "newton-cooling", Subject.ANALYSIS, title("Refroidissement de Newton", "Newton's cooling"), "T′ = −k(T − Tₐ), T = Tₐ + (T₀ − Tₐ)e⁻ᵏᵗ",
        listOf(num("T₀", "Température initiale", "Initial temperature", "°"), num("Tₐ", "Température ambiante", "Room temperature", "°"), num("k", "Constante k (par minute)", "Constant k (per minute)", slider = 0.01..1.0, step = 0.01), num("t", "Instant t (minutes)", "Time t (minutes)"))
    ) { v ->
        val t0 = v["T₀"]; val ta = v["Tₐ"]; val k = v["k"]; val t = v["t"]
        require(k > 0 && t >= 0)
        val temp = { x: Double -> ta + (t0 - ta) * exp(-k * x) }
        // The discrete model of maths complémentaires: Tₙ₊₁ = Tₙ − k(Tₙ − Tₐ).
        val steps = ArrayList<P2>(); var tn = t0
        for (n in 0..t.toInt().coerceAtMost(500)) { steps += P2(n.toDouble(), tn); tn -= k * (tn - ta) }
        val half = ln(2.0) / k
        Outcome(
            listOf(
                res("T(${show(t)}) = ${show(temp(t))}°"),
                stp(say("Écart à l'ambiante divisé par 2 toutes les ln 2/k = ${show(half)} minutes", "Gap to the room halved every ln 2/k = ${show(half)} minutes")),
                stp(say("Modèle discret Tₙ₊₁ = Tₙ − k(Tₙ − Tₐ) : T${t.toInt()} = ${show(steps.last().y)}", "Discrete model Tₙ₊₁ = Tₙ − k(Tₙ − Tₐ): T${t.toInt()} = ${show(steps.last().y)}"))
            ),
            figure = Figure(listOf(Shape2.Curve({ ta }, 0.0, max(t, 5 / k), "Tₐ", 2, dashed = true), Shape2.Curve(temp, 0.0, max(t, 5 / k), "T"), Shape2.Dots(steps.take(200), tone = 1), Shape2.Point(P2(t, temp(t)), tone = 1)))
        )
    }
)
