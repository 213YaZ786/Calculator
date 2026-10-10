package com.yaz.calculator.engine.programs

import com.yaz.calculator.engine.cas.Ex
import com.yaz.calculator.engine.cas.Num
import com.yaz.calculator.engine.cas.Q
import com.yaz.calculator.engine.cas.X
import com.yaz.calculator.engine.cas.at
import com.yaz.calculator.engine.cas.definite
import com.yaz.calculator.engine.cas.degreeOf
import com.yaz.calculator.engine.cas.derivative
import com.yaz.calculator.engine.cas.expand
import com.yaz.calculator.engine.cas.hasX
import com.yaz.calculator.engine.cas.integrate
import com.yaz.calculator.engine.cas.minus
import com.yaz.calculator.engine.cas.parse
import com.yaz.calculator.engine.cas.poly
import com.yaz.calculator.engine.cas.polyEx
import com.yaz.calculator.engine.cas.rationalRoots
import com.yaz.calculator.engine.cas.simplify
import com.yaz.calculator.engine.cas.tidy
import com.yaz.calculator.engine.cas.times
import com.yaz.calculator.engine.cas.together
import java.util.Locale
import com.yaz.calculator.engine.cas.show as exact

// Exact calculus on Calculator's own symbolic engine (engine/cas): the
// derivative, the primitive with its methods, the exact integral, by parts.

private fun french() = Locale.getDefault().language == "fr"

private fun noPrimitive() = say(
    "Pas de primitive trouvée par les méthodes du lycée et de la licence (table, u′·f(u), éléments simples, linéarisation, par parties)",
    "No primitive found by school and first-year methods (table, u′·f(u), partial fractions, linearisation, by parts)"
)

val symbolic: List<Program> = listOf(
    Rich(
        "exact-derivative", Subject.ANALYSIS, title("Dérivée exacte", "Exact derivative"), "f′(x)",
        listOf(fn("f", "f(x)", "f(x)"))
    ) { v ->
        val f = v.exact("f")
        // The quotient rule's terms over one denominator: −2/(x − 1)².
        val d1 = derivative(f).let { together(it) ?: it }
        val d2 = derivative(d1).let { together(it) ?: it }
        lines(
            res("f′(x) = ${exact(d1)}"),
            stp("f″(x) = ${exact(d2)}"),
            stp(say("Calcul formel en radians", "Symbolic, in radians"))
        )
    },
    Rich(
        "exact-primitive", Subject.ANALYSIS, title("Primitive exacte", "Exact antiderivative"), "F′ = f",
        listOf(fn("f", "f(x)", "f(x)"))
    ) { v ->
        val f = v.exact("f")
        val p = integrate(f, french())
        val r = p.result ?: return@Rich lines(res(noPrimitive()))
        Outcome(listOf(res("F(x) = ${exact(r)} + C")) + p.steps.map { stp(it) } + stp(say("Vérifié : F′(x) = f(x)", "Checked: F′(x) = f(x)")))
    },
    Rich(
        "exact-integral", Subject.ANALYSIS, title("Intégrale exacte", "Exact integral"), "∫ₐᵇ f = F(b) − F(a)",
        listOf(fn("f", "f(x)", "f(x)"), num("a", "a", "a"), num("b", "b", "b"))
    ) { v ->
        val f = v.exact("f")
        val a = v.exact("a"); val b = v.exact("b")
        val p = integrate(f, french())
        val r = p.result ?: return@Rich lines(res(noPrimitive()))
        val value = definite(r, a, b)
        Outcome(
            listOf(
                res("∫ = ${exact(value)}" + if (value is Num) "" else "  ≈ ${show(value.at(0.0))}"),
                stp("F(x) = ${exact(r)}"),
                stp("F(${exact(b)}) − F(${exact(a)})")
            ) + p.steps.map { stp(it) }
        )
    },
    Rich(
        "by-parts", Subject.ANALYSIS, title("Intégration par parties", "Integration by parts"), "∫ u·v′ = u·v − ∫ u′·v",
        listOf(fn("u", "u(x)", "u(x)"), fn("v′", "v′(x)", "v′(x)"))
    ) { v ->
        val u = v.exact("u"); val dv = v.exact("v′")
        val fr = french()
        val vv = integrate(dv, fr).result ?: return@Rich lines(res(say("Pas de primitive v trouvée pour v′", "No primitive v found for v′")))
        val du = derivative(u)
        val rest = integrate(simplify(du * vv), fr)
        val out = mutableListOf(
            stp("u = ${exact(u)}   v′ = ${exact(dv)}"),
            stp("u′ = ${exact(du)}   v = ${exact(vv)}"),
            stp("∫ u′v = ∫ ${exact(simplify(du * vv))}")
        )
        val r = rest.result
        if (r == null) out.add(0, res(say("u·v = ${exact(simplify(u * vv))}, mais ∫ u′v reste sans primitive : choisir u et v′ autrement", "u·v = ${exact(simplify(u * vv))}, but ∫ u′v has no primitive found: choose u and v′ the other way")))
        else out.add(0, res("∫ u·v′ = ${exact(tidy(u * vv - r))} + C"))
        Outcome(out)
    },
    Rich(
        "expand-factor", Subject.ANALYSIS, title("Développer et factoriser", "Expand and factor"), "(x − a)(x − b)…",
        listOf(fn("f", "Expression en x", "Expression in x"))
    ) { v ->
        val f = v.exact("f")
        val e = expand(f)
        val out = mutableListOf(res(say("Développé : ${exact(e)}", "Expanded: ${exact(e)}")))
        val p = poly(f)
        if (p != null && degreeOf(p) >= 1) {
            val (roots, rest) = rationalRoots(p)
            val lead = rest[degreeOf(rest)] ?: Q.ONE
            val parts = ArrayList<String>()
            if (!lead.isOne) parts += exact(Num(lead))
            for ((r, m) in roots) {
                val factor = exact(simplify(X - Num(r)))
                parts += "($factor)" + if (m > 1) m.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("") else ""
            }
            if (degreeOf(rest) >= 1) parts += "(${exact(polyEx(rest.mapValues { it.value / lead }))})"
            out += res(say("Factorisé : ", "Factored: ") + parts.joinToString(""))
            if (roots.isNotEmpty()) out += stp(say("Racines rationnelles : ", "Rational roots: ") + roots.joinToString("  ") { (r, m) -> exact(Num(r)) + if (m > 1) " (×$m)" else "" })
        }
        Outcome(out)
    }
)
