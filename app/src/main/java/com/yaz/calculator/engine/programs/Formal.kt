package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.cas.Ex
import com.yaz.calculator.engine.cas.Num
import com.yaz.calculator.engine.cas.at
import com.yaz.calculator.engine.cas.derivative
import com.yaz.calculator.engine.cas.sameAs
import com.yaz.calculator.engine.giac.Giac
import com.yaz.calculator.engine.giac.fromGiac
import com.yaz.calculator.engine.giac.splitTop
import com.yaz.calculator.engine.giac.tidyGiac
import com.yaz.calculator.engine.giac.toGiac
import kotlin.math.abs
import kotlin.math.max
import com.yaz.calculator.engine.cas.show as exact

// What our own engine does not reach, asked of Giac (GPL-3.0, Bernard
// Parisse) in its isolated process: limits, exact equations, Taylor
// expansions, differential equations, sums of series. Answers are read
// back and written as Calculator writes them.

internal fun noGiac() = Outcome(listOf(res(say("Calcul formel indisponible", "Computer algebra unavailable"))))

/** Giac's answer as Calculator writes it, else its own text tidied. */
internal fun written(text: String): String = fromGiac(text)?.let { exact(it) } ?: tidyGiac(text)

/** A limit's text: ±∞ as students write them. */
private fun limitText(t: String): String = when (t.trim()) {
    "+infinity", "inf", "+inf" -> "+∞"
    "-infinity", "-inf" -> "−∞"
    "infinity" -> "∞"
    "undef" -> say("pas de limite", "no limit")
    else -> written(t)
}

private fun approx(e: Ex): String = e.at(0.0).let { if (it.isFinite() && e !is Num) " ≈ ${show(it)}" else "" }

val formal: List<Program> = listOf(
    Rich(
        "limit", Subject.ANALYSIS, title("Limite", "Limit"), "lim f(x), x → a",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a (vide : ±∞)", "a (empty: ±∞)", optional = true))
    ) { v ->
        val f = v.exact("f"); val g = toGiac(f)
        val out = mutableListOf<Line>()
        val shapes = mutableListOf<Shape2>()
        val fd = v.f("f")
        if (!v.has("a")) {
            val plus = Giac.eval("limit($g,x,+infinity)") ?: return@Rich noGiac()
            val minus = Giac.eval("limit($g,x,-infinity)")
            out += res("x → +∞ : ${limitText(plus)}")
            minus?.let { out += res("x → −∞ : ${limitText(it)}") }
            // A finite limit: the asymptote y = L.
            for ((t, side) in listOfNotNull(plus to 1.0, minus?.let { it to -1.0 })) fromGiac(t)?.at(0.0)?.takeIf { it.isFinite() }?.let { l ->
                shapes += Shape2.Curve({ l }, if (side > 0) 0.0 else -20.0, if (side > 0) 20.0 else 0.0, "y = ${show(l)}", 2, dashed = true)
                out += stp(say("Asymptote horizontale y = ${show(l)} en ${if (side > 0) "+∞" else "−∞"}", "Horizontal asymptote y = ${show(l)} at ${if (side > 0) "+∞" else "−∞"}"))
            }
            shapes.add(0, Shape2.Curve(fd, -20.0, 20.0, "f"))
            Outcome(out, figure = Figure(shapes, window = around(fd, -10.0, 10.0)))
        } else {
            val a = v.exact("a"); val ga = toGiac(a); val ad = v["a"]
            val both = Giac.eval("limit($g,x,$ga)") ?: return@Rich noGiac()
            val right = Giac.eval("limit($g,x,$ga,1)")
            val left = Giac.eval("limit($g,x,$ga,-1)")
            if (right != null && left != null && right != left) {
                out += res("x → ${exact(a)}⁺ : ${limitText(right)}")
                out += res("x → ${exact(a)}⁻ : ${limitText(left)}")
                out += stp(say("Limites à droite et à gauche différentes", "Different limits from the right and the left"))
            } else out += res("x → ${exact(a)} : ${limitText(both)}")
            if (listOf(right, left, both).any { it?.contains("infinity") == true || it == "inf" }) {
                out += stp(say("Asymptote verticale x = ${exact(a)}", "Vertical asymptote x = ${exact(a)}"))
                shapes += Shape2.Segment(P2(ad, -1e4), P2(ad, 1e4), "x = ${exact(a)}", 2, dashed = true)
            }
            val r = max(2.0, abs(ad) * 0.5)
            shapes.add(0, Shape2.Curve(fd, ad - r, ad + r, "f"))
            fromGiac(both)?.at(0.0)?.takeIf { it.isFinite() }?.let { shapes += Shape2.Point(P2(ad, it), tone = 1) }
            Outcome(out, figure = Figure(shapes, window = around(fd, ad - r, ad + r)))
        }
    },
    Rich(
        "solve-exact", Subject.ANALYSIS, title("Résoudre une équation (exact)", "Solve an equation (exact)"), "f(x) = g(x)",
        listOf(fn("f", "f(x)", "f(x)"), Field("g", Words("g(x) (vide : 0)", "g(x) (empty: 0)"), kind = FieldKind.FUNCTION, optional = true))
    ) { v ->
        val f = v.exact("f"); val g = if (v.has("g")) v.exact("g") else Num(com.yaz.calculator.engine.cas.Q.ZERO)
        val text = Giac.eval("solve(${toGiac(f)}=${toGiac(g)},x)") ?: return@Rich noGiac()
        val inner = text.trim().removePrefix("list").trim().removeSurrounding("[", "]")
        val parts = if (inner.isBlank()) emptyList() else splitTop(inner)
        val out = mutableListOf<Line>()
        if (parts.isEmpty()) out += res(say("Aucune solution réelle", "No real solution"))
        val fd = v.f("f"); val gd = if (v.has("g")) v.f("g") else { _: Double -> 0.0 }
        val shapes = mutableListOf<Shape2>(Shape2.Curve(fd, label = "f"), Shape2.Curve(gd, label = if (v.has("g")) "g" else "", tone = 2))
        val xs = ArrayList<Double>()
        parts.forEach { p ->
            val e = fromGiac(p)
            out += res("x = " + (e?.let { exact(it) + approx(it) } ?: tidyGiac(p)))
            e?.at(0.0)?.takeIf { it.isFinite() }?.let { x -> xs += x; shapes += Shape2.Point(P2(x, fd(x)), tone = 1) }
        }
        if (parts.size > 1) out += stp(say("${parts.size} solutions", "${parts.size} solutions"))
        val lo = (xs.minOrNull() ?: -3.0) - 2; val hi = (xs.maxOrNull() ?: 3.0) + 2
        shapes[0] = Shape2.Curve(fd, lo, hi, "f"); shapes[1] = Shape2.Curve(gd, lo, hi, if (v.has("g")) "g" else "", 2)
        Outcome(out, figure = Figure(shapes, window = around(fd, lo, hi)))
    },
    Rich(
        "taylor-exact", Subject.ANALYSIS, title("Développement limité exact", "Exact Taylor expansion"), "f(x) = Σ aₖ(x − a)ᵏ + o((x − a)ⁿ)",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "Au voisinage de a", "Near a"), num("n", "Ordre n (≤ 12)", "Order n (≤ 12)", whole = true))
    ) { v ->
        val f = v.exact("f"); val a = v.exact("a"); val n = v.int("n")
        require(n in 0..12)
        val text = Giac.eval("series(${toGiac(f)},x=${toGiac(a)},$n)") ?: return@Rich noGiac()
        // The polynomial part: every term but Giac's order_size one.
        val poly = text.replace(Regex("[+-]?[^+-]*order_size\\([^)]*\\)"), "").trim().removeSuffix("+")
        val p = fromGiac(poly)
        val ad = v["a"]
        val o = if (ad == 0.0) "o(x${sup(n)})" else "o((x − ${exact(a)})${sup(n)})"
        val out = mutableListOf(res("f(x) = ${p?.let { exact(it) } ?: tidyGiac(poly)} + $o"))
        out += stp(say("Coefficients f⁽ᵏ⁾(a)/k!, calcul formel", "Coefficients f⁽ᵏ⁾(a)/k!, computer algebra"))
        val fd = v.f("f")
        val r = 3.0
        val shapes = mutableListOf<Shape2>(Shape2.Curve(fd, ad - r, ad + r, "f"))
        p?.let { shapes += Shape2.Curve({ x -> it.at(x) }, ad - r, ad + r, "DL$n", 1) }
        shapes += Shape2.Point(P2(ad, fd(ad)), tone = 2)
        Outcome(out, figure = Figure(shapes, staged = true, window = around(fd, ad - r, ad + r)))
    },
    Rich(
        "ode", Subject.ANALYSIS, title("Équation différentielle", "Differential equation"), "a·y″ + b·y′ + c·y = f(x)",
        listOf(
            num("a", "a (0 : premier ordre)", "a (0: first order)"), num("b", "b", "b"), num("c", "c", "c"),
            Field("f", Words("f(x) (vide : 0)", "f(x) (empty: 0)"), kind = FieldKind.FUNCTION, optional = true),
            num("x₀", "x₀ (facultatif)", "x₀ (optional)", optional = true), num("y₀", "y(x₀) (facultatif)", "y(x₀) (optional)", optional = true), num("y₁", "y′(x₀) (facultatif)", "y′(x₀) (optional)", optional = true)
        )
    ) { v ->
        val a = v.exact("a"); val b = v.exact("b"); val c = v.exact("c")
        val rhs = if (v.has("f")) toGiac(v.exact("f")) else "0"
        val eq = "${toGiac(a)}*diff(y(x),x,2)+${toGiac(b)}*diff(y(x),x)+${toGiac(c)}*y(x)=$rhs"
        val second = v["a"] != 0.0
        val conditions = buildList {
            if (v.has("x₀") && v.has("y₀")) add("y(${toGiac(v.exact("x₀"))})=${toGiac(v.exact("y₀"))}")
            if (second && v.has("x₀") && v.has("y₁")) add("y'(${toGiac(v.exact("x₀"))})=${toGiac(v.exact("y₁"))}")
        }
        val query = if (conditions.isEmpty()) "desolve($eq,x,y)" else "desolve([$eq,${conditions.joinToString(",")}],x,y)"
        val text = Giac.eval(query) ?: return@Rich noGiac()
        val sol = text.trim().removePrefix("list").trim().removeSurrounding("[", "]").let { splitTop(it).firstOrNull() ?: it }
        // Giac's constants c_0, c_1 written C₁, C₂.
        val pretty = tidyGiac(sol.replace("c_0", "C₁").replace("c_1", "C₂"))
        val parsed = fromGiac(sol)
        val out = mutableListOf(res("y = " + (parsed?.let { exact(it) } ?: pretty)))
        if (second) {
            val ad = v["a"]; val bd = v["b"]; val cd = v["c"]
            val delta = bd * bd - 4 * ad * cd
            val r = com.yaz.calculator.engine.cas.simplify(com.yaz.calculator.engine.cas.Add(listOf(
                com.yaz.calculator.engine.cas.Mul(listOf(a, com.yaz.calculator.engine.cas.Pow(com.yaz.calculator.engine.cas.X, com.yaz.calculator.engine.cas.num(2)))),
                com.yaz.calculator.engine.cas.Mul(listOf(b, com.yaz.calculator.engine.cas.X)), c
            )))
            val roots = when {
                delta > 1e-12 -> "r = ${show((-bd - kotlin.math.sqrt(delta)) / (2 * ad))} ; ${show((-bd + kotlin.math.sqrt(delta)) / (2 * ad))}"
                delta < -1e-12 -> "r = ${show(-bd / (2 * ad))} ± ${show(kotlin.math.sqrt(-delta) / (2 * ad))}i"
                else -> "r = ${show(-bd / (2 * ad))}"
            }
            out += stp(say("Équation caractéristique ", "Characteristic equation ") + exact(r).replace('x', 'r') + " = 0, Δ = ${show(delta)}, $roots")
        }
        if (parsed == null && sol.contains("c_")) out += stp(say("C₁, C₂ : constantes réelles, fixées par les conditions initiales", "C₁, C₂: real constants, set by the initial conditions"))
        val figure = parsed?.let { p ->
            val x0 = if (v.has("x₀")) v["x₀"] else 0.0
            Figure(listOfNotNull(Shape2.Curve({ x -> p.at(x) }, x0 - 4, x0 + 6, "y"), if (v.has("y₀")) Shape2.Point(P2(x0, v["y₀"]), "y₀", 2) else null))
        }
        Outcome(out, figure = figure)
    },
    Rich(
        "series-sum", Subject.ANALYSIS, title("Somme exacte d'une série", "Exact sum of a series"), "Σ u(k), k = k₀ … N ou ∞",
        listOf(fn("u", "u(x), x pour k", "u(x), x for k"), num("k₀", "Premier rang", "First index", whole = true), num("N", "Dernier rang (vide : ∞)", "Last index (empty: ∞)", whole = true, optional = true))
    ) { v ->
        val u = v.exact("u"); val k0 = v.long("k₀")
        val top = if (v.has("N")) v.long("N").toString() else "+infinity"
        val text = Giac.eval("sum(${toGiac(u)},x,$k0,$top)") ?: return@Rich noGiac()
        val e = fromGiac(text)
        val out = mutableListOf(res("Σ = " + when {
            text.contains("infinity") -> say("+∞ (la série diverge)", "+∞ (the series diverges)")
            e != null -> exact(e) + approx(e)
            else -> tidyGiac(text)
        }))
        // The partial sums climbing to it.
        val ud = v.f("u")
        val sums = ArrayList<P2>(); var s = 0.0
        val last = if (v.has("N")) v.long("N") else k0 + 30
        var k = k0
        while (k <= last && sums.size < 60) { s += ud(k.toDouble()); sums += P2(k.toDouble(), s); k++ }
        val shapes = mutableListOf<Shape2>(Shape2.Dots(sums), Shape2.Polyline(sums, "Sₙ"))
        e?.at(0.0)?.takeIf { it.isFinite() }?.let { l -> shapes.add(0, Shape2.Curve({ l }, k0.toDouble(), last.toDouble(), "Σ", 2, dashed = true)) }
        Outcome(out, figure = Figure(shapes, staged = true))
    }
)

private fun sup(n: Int) = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

/** A primitive from Giac when our engine has none, checked by its derivative. */
internal fun giacPrimitive(f: Ex): Ex? {
    val text = Giac.eval("integrate(${toGiac(f)},x)") ?: return null
    val p = fromGiac(text) ?: return null
    return p.takeIf { derivative(it).sameAs(f) }
}

/** The complete factorisation over the rationals, from Giac. */
internal fun giacFactor(f: Ex): Ex? = Giac.eval("factor(${toGiac(f)})")?.let { fromGiac(it) }
