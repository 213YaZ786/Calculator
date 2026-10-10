package com.yaz.calculator.engine.programs

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.hipparchus.distribution.continuous.ExponentialDistribution
import org.hipparchus.distribution.continuous.NormalDistribution
import org.hipparchus.distribution.discrete.BinomialDistribution
import org.hipparchus.distribution.discrete.PoissonDistribution

// The law calculator, as GeoGebra's and NumWorks' probability apps: the
// law's histogram or density, P(a ≤ X ≤ b) shaded, a and b each left empty
// for an open side, the parameters on sliders, and chips to switch from
// one law to another keeping a and b. Laws as the programmes list them
// (Première, Terminale, maths complémentaires; AP Statistics).

private const val LAW = "law"

private fun aField() = num("a", "a (vide : −∞)", "a (empty: −∞)", optional = true)
private fun bField() = num("b", "b (vide : +∞)", "b (empty: +∞)", optional = true)

/** The event's name: P(a ≤ X ≤ b), P(X ≤ b), P(X ≥ a), or P(X = k) when a = b. */
private fun event(a: Double?, b: Double?): String = when {
    a != null && b != null && a == b -> "P(X = ${show(a)})"
    a != null && b != null -> "P(${show(a)} ≤ X ≤ ${show(b)})"
    a != null -> "P(X ≥ ${show(a)})"
    b != null -> "P(X ≤ ${show(b)})"
    else -> ""
}

/** The probability asked, or, with a and b both empty, what to give for one. */
private fun answer(a: Double?, b: Double?, p: Double): Line =
    if (a == null && b == null) stp(say("Donnez a et/ou b pour P(a ≤ X ≤ b)", "Give a and/or b for P(a ≤ X ≤ b)")) else res("${event(a, b)} = ${show(p.coerceIn(0.0, 1.0))}")

/** The histogram of a law on the integers: bars in the event in the accent, the others faint. */
private fun discrete(pmf: (Int) -> Double, from: Int, to: Int, a: Double?, b: Double?): Figure {
    val shapes = (from..to).map { k ->
        val inside = (a == null || k >= a - 1e-9) && (b == null || k <= b + 1e-9)
        Shape2.rect(k - 0.4, 0.0, k + 0.4, pmf(k), tone = if (inside) 0 else 3)
    }
    val top = (from..to).maxOf { pmf(it) }
    return Figure(shapes, window = Box2(from - 1.0, to + 1.0, -top * 0.08, top * 1.15))
}

/** A density with the event's area under it. */
private fun continuous(pdf: (Double) -> Double, from: Double, to: Double, a: Double?, b: Double?): Figure {
    val lo = max(a ?: from, from); val hi = min(b ?: to, to)
    val top = (0..300).maxOf { pdf(from + (to - from) * it / 300) }
    val shapes = mutableListOf<Shape2>()
    if (hi > lo) shapes += Shape2.Area(pdf, lo, hi)
    shapes += Shape2.Curve(pdf, from, to, "f")
    return Figure(shapes, window = Box2(from - (to - from) * 0.05, to + (to - from) * 0.05, -top * 0.08, top * 1.2))
}

/** Results first, the steps under them. */
private fun law(lines: List<Line>, figure: Figure) = Outcome(lines.sortedBy { !it.result }, figure = figure)

private fun Inputs.opt(s: String): Double? = if (has(s)) get(s) else null

val laws: List<Program> = listOf(
    Rich(
        "law-binomial", Subject.STATS, title("Loi binomiale", "Binomial distribution"), "P(X = k) = C(n,k)·pᵏ(1 − p)ⁿ⁻ᵏ",
        listOf(num("n", "Nombre d'épreuves n", "Trials n", whole = true, slider = 1.0..200.0, step = 1.0), num("p", "Probabilité de succès p", "Success probability p", slider = 0.0..1.0, step = 0.01), aField(), bField()),
        family = LAW, short = title("Binomiale", "Binomial")
    ) { v ->
        val n = v.int("n"); val p = v["p"]
        require(n in 1..100_000 && p in 0.0..1.0)
        val law = BinomialDistribution(n, p)
        val a = v.opt("a"); val b = v.opt("b")
        val lo = a?.let { kotlin.math.ceil(it - 1e-9).toInt() }; val hi = b?.let { floor(it + 1e-9).toInt() }
        val prob = when {
            lo != null && hi != null -> if (hi < lo) 0.0 else law.cumulativeProbability(hi) - law.cumulativeProbability(lo - 1)
            lo != null -> 1 - law.cumulativeProbability(lo - 1)
            hi != null -> law.cumulativeProbability(hi)
            else -> 1.0
        }
        val mean = n * p; val sd = sqrt(n * p * (1 - p))
        val from = max(0, floor(mean - 4.5 * sd - 1).toInt()); val to = min(n, kotlin.math.ceil(mean + 4.5 * sd + 1).toInt())
        law(
            listOf(
                answer(a, b, prob),
                res("E(X) = np = ${show(mean)}   V(X) = np(1 − p) = ${show(sd * sd)}   σ = ${show(sd)}"),
                stp(say("Intervalle à environ 95 % : [μ − 2σ ; μ + 2σ] = [${show(mean - 2 * sd)} ; ${show(mean + 2 * sd)}]", "About 95 %: [μ − 2σ, μ + 2σ] = [${show(mean - 2 * sd)}, ${show(mean + 2 * sd)}]"))
            ),
            figure = discrete({ law.probability(it) }, from, to, a, b)
        )
    },
    Rich(
        "law-normal", Subject.STATS, title("Loi normale", "Normal distribution"), "X ~ N(μ, σ²)",
        listOf(num("μ", "Espérance μ", "Mean μ", slider = -20.0..20.0, step = 0.1), num("σ", "Écart type σ", "Standard deviation σ", slider = 0.1..10.0, step = 0.1), aField(), bField()),
        family = LAW, short = title("Normale", "Normal")
    ) { v ->
        val mu = v["μ"]; val sd = v["σ"]
        require(sd > 0)
        val law = NormalDistribution(mu, sd)
        val a = v.opt("a"); val b = v.opt("b")
        val prob = (b?.let { law.cumulativeProbability(it) } ?: 1.0) - (a?.let { law.cumulativeProbability(it) } ?: 0.0)
        law(
            listOf(
                answer(a, b, prob),
                res("E(X) = μ = ${show(mu)}   σ = ${show(sd)}"),
                stp(say("P(μ − σ ≤ X ≤ μ + σ) ≈ 0,683 ; 2σ : 0,954 ; 3σ : 0,997", "P(μ − σ ≤ X ≤ μ + σ) ≈ 0.683; 2σ: 0.954; 3σ: 0.997")),
                stp("Z = (X − μ)/σ ~ N(0, 1)" + (a?.let { "   z(a) = ${brief((it - mu) / sd)}" } ?: "") + (b?.let { "   z(b) = ${brief((it - mu) / sd)}" } ?: ""))
            ) + listOfNotNull(
                // Whole bounds: the law may stand for a discrete one (a binomial), corrected by half a unit.
                if ((a == null || a == Math.rint(a)) && (b == null || b == Math.rint(b)) && (a != null || b != null)) {
                    val corrected = (b?.let { law.cumulativeProbability(it + 0.5) } ?: 1.0) - (a?.let { law.cumulativeProbability(it - 0.5) } ?: 0.0)
                    stp(say("Pour approcher une loi discrète, correction de continuité : ${show(corrected)}", "Approximating a discrete law, with continuity correction: ${show(corrected)}"))
                } else null
            ),
            figure = continuous({ law.density(it) }, mu - 4 * sd, mu + 4 * sd, a, b)
        )
    },
    Rich(
        "law-poisson", Subject.STATS, title("Loi de Poisson", "Poisson distribution"), "X ~ P(λ), P(X = k) = e⁻λλᵏ/k!",
        listOf(num("λ", "Paramètre λ", "Rate λ", slider = 0.1..50.0, step = 0.1), aField(), bField()),
        family = LAW, short = title("Poisson", "Poisson")
    ) { v ->
        val l = v["λ"]
        require(l > 0)
        val law = PoissonDistribution(l)
        val a = v.opt("a"); val b = v.opt("b")
        val lo = a?.let { kotlin.math.ceil(it - 1e-9).toInt().coerceAtLeast(0) }; val hi = b?.let { floor(it + 1e-9).toInt() }
        val prob = when {
            lo != null && hi != null -> if (hi < lo) 0.0 else law.cumulativeProbability(hi) - law.cumulativeProbability(lo - 1)
            lo != null -> 1 - law.cumulativeProbability(lo - 1)
            hi != null -> if (hi < 0) 0.0 else law.cumulativeProbability(hi)
            else -> 1.0
        }
        val to = kotlin.math.ceil(l + 4.5 * sqrt(l) + 2).toInt()
        law(
            listOf(answer(a, b, prob), res("E(X) = V(X) = λ = ${show(l)}   σ = ${show(sqrt(l))}")),
            figure = discrete({ law.probability(it) }, 0, to, a, b)
        )
    },
    Rich(
        "law-geometric", Subject.STATS, title("Loi géométrique", "Geometric distribution"), "P(X = k) = (1 − p)ᵏ⁻¹p, k ≥ 1",
        listOf(num("p", "Probabilité de succès p", "Success probability p", slider = 0.01..1.0, step = 0.01), aField(), bField()),
        family = LAW, short = title("Géométrique", "Geometric")
    ) { v ->
        val p = v["p"]
        require(p > 0 && p <= 1)
        val cdf = { k: Int -> if (k < 1) 0.0 else 1 - (1 - p).pow(k) }
        val a = v.opt("a"); val b = v.opt("b")
        val lo = a?.let { kotlin.math.ceil(it - 1e-9).toInt().coerceAtLeast(1) }; val hi = b?.let { floor(it + 1e-9).toInt() }
        val prob = (hi?.let(cdf) ?: 1.0) - ((lo ?: 1) - 1).let(cdf)
        val to = min(200, kotlin.math.ceil(ln(0.002) / ln(1 - p).coerceAtMost(-1e-9)).toInt().coerceAtLeast(5))
        law(
            listOf(
                answer(a, b, prob),
                res("E(X) = 1/p = ${show(1 / p)}   V(X) = (1 − p)/p² = ${show((1 - p) / (p * p))}"),
                stp(say("Rang du premier succès dans des épreuves de Bernoulli ; sans mémoire : P(X > m + k | X > m) = P(X > k)", "Trial of the first success in Bernoulli trials; memoryless: P(X > m + k | X > m) = P(X > k)"))
            ),
            figure = discrete({ k -> if (k < 1) 0.0 else (1 - p).pow(k - 1) * p }, 1, to, a, b)
        )
    },
    Rich(
        "law-exponential", Subject.STATS, title("Loi exponentielle", "Exponential distribution"), "f(t) = λe⁻λᵗ, t ≥ 0",
        listOf(num("λ", "Paramètre λ", "Rate λ", slider = 0.05..5.0, step = 0.05), aField(), bField()),
        family = LAW, short = title("Exponentielle", "Exponential")
    ) { v ->
        val l = v["λ"]
        require(l > 0)
        val law = ExponentialDistribution(1 / l)
        val a = v.opt("a"); val b = v.opt("b")
        val prob = (b?.let { law.cumulativeProbability(it) } ?: 1.0) - (a?.let { law.cumulativeProbability(it) } ?: 0.0)
        law(
            listOf(
                answer(a, b, prob),
                res(say("E(X) = 1/λ = ${show(1 / l)}   médiane ln 2/λ = ${show(ln(2.0) / l)}", "E(X) = 1/λ = ${show(1 / l)}   median ln 2/λ = ${show(ln(2.0) / l)}")),
                stp(say("Sans mémoire : P(X > s + t | X > s) = P(X > t) = e⁻λᵗ", "Memoryless: P(X > s + t | X > s) = P(X > t) = e⁻λᵗ"))
            ),
            figure = continuous({ t -> if (t < 0) 0.0 else l * exp(-l * t) }, -0.3 / l, 5 / l, a, b)
        )
    },
    Rich(
        "law-uniform", Subject.STATS, title("Loi uniforme", "Uniform distribution"), "X ~ U([α ; β]), f = 1/(β − α)",
        listOf(num("α", "α", "α", slider = -20.0..20.0, step = 0.5), num("β", "β", "β", slider = -20.0..40.0, step = 0.5), aField(), bField()),
        family = LAW, short = title("Uniforme", "Uniform")
    ) { v ->
        val al = v["α"]; val be = v["β"]
        require(be > al)
        val a = v.opt("a"); val b = v.opt("b")
        val prob = ((min(b ?: be, be) - max(a ?: al, al)) / (be - al)).coerceIn(0.0, 1.0)
        val w = be - al
        law(
            listOf(
                answer(a, b, prob),
                res("E(X) = (α + β)/2 = ${show((al + be) / 2)}   V(X) = (β − α)²/12 = ${show(w * w / 12)}")
            ),
            figure = continuous({ t -> if (t in al..be) 1 / w else 0.0 }, al - w * 0.25, be + w * 0.25, a, b)
        )
    }
)

/**
 * Switching laws keeps the mean and the variance: from B(n, p) the normal
 * law gets μ = np and σ = √(np(1 − p)), Poisson λ = np, the approximations
 * the programmes teach. The parameters of [to], or nothing when they do not
 * exist (a geometric law needs a mean of at least 1).
 */
fun matchMoments(from: String, values: Map<String, Double>, to: String): Map<String, Double> {
    fun v(s: String) = values[s]
    val (m, var2) = when (from) {
        "law-binomial" -> { val n = v("n") ?: return emptyMap(); val p = v("p") ?: return emptyMap(); n * p to n * p * (1 - p) }
        "law-normal" -> { val mu = v("μ") ?: return emptyMap(); val s = v("σ") ?: return emptyMap(); mu to s * s }
        "law-poisson" -> { val l = v("λ") ?: return emptyMap(); l to l }
        "law-geometric" -> { val p = v("p") ?: return emptyMap(); 1 / p to (1 - p) / (p * p) }
        "law-exponential" -> { val l = v("λ") ?: return emptyMap(); 1 / l to 1 / (l * l) }
        "law-uniform" -> { val a = v("α") ?: return emptyMap(); val b = v("β") ?: return emptyMap(); (a + b) / 2 to (b - a) * (b - a) / 12 }
        else -> return emptyMap()
    }
    if (!m.isFinite() || !var2.isFinite() || var2 < 0) return emptyMap()
    fun r(x: Double) = Math.round(x * 10_000) / 10_000.0
    return when (to) {
        "law-normal" -> if (var2 > 0) mapOf("μ" to r(m), "σ" to r(sqrt(var2))) else emptyMap()
        "law-poisson" -> if (m > 0) mapOf("λ" to r(m)) else emptyMap()
        "law-geometric" -> if (m >= 1) mapOf("p" to r(1 / m)) else emptyMap()
        "law-exponential" -> if (m > 0) mapOf("λ" to r(1 / m)) else emptyMap()
        "law-uniform" -> if (var2 > 0) mapOf("α" to r(m - sqrt(3 * var2)), "β" to r(m + sqrt(3 * var2))) else emptyMap()
        "law-binomial" -> if (m > var2 && var2 > 0) {
            val n = Math.round(m * m / (m - var2)).coerceAtLeast(1)
            mapOf("n" to n.toDouble(), "p" to r((m / n).coerceIn(0.0, 1.0)))
        } else emptyMap()
        else -> emptyMap()
    }
}
