package com.yaz.calculator.engine.programs

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import org.hipparchus.distribution.continuous.ChiSquaredDistribution
import org.hipparchus.distribution.continuous.FDistribution
import org.hipparchus.distribution.continuous.NormalDistribution
import org.hipparchus.distribution.continuous.TDistribution
import org.hipparchus.distribution.discrete.PoissonDistribution

// Probability and statistics from Terminale, maths complémentaires, AP
// Statistics and A-level Further Statistics; tests two-sided at 5 %.

private fun bi(v: Long): BigInteger = BigInteger.valueOf(v)

private fun factorial(n: Int): BigInteger = (2..n).fold(BigInteger.ONE) { f, k -> f.multiply(bi(k.toLong())) }

private fun choose(n: Int, k: Int): BigInteger = if (k < 0 || k > n) BigInteger.ZERO else factorial(n).divide(factorial(k).multiply(factorial(n - k)))

private fun big(b: BigInteger): String = b.toString().let { if (it.length <= 40) it else it.take(20) + "… (${it.length} " + say("chiffres", "digits") + ")" }

private fun decision(p: Double, alpha: Double = 0.05): Line = stp(
    if (p < alpha) say("p = ${show(p)} < ${show(alpha)} : on rejette H₀ au seuil de 5 %", "p = ${show(p)} < ${show(alpha)}: reject H₀ at the 5% level")
    else say("p = ${show(p)} ≥ ${show(alpha)} : on ne rejette pas H₀ au seuil de 5 %", "p = ${show(p)} ≥ ${show(alpha)}: do not reject H₀ at the 5% level")
)

private fun mean(xs: List<Double>) = xs.sum() / xs.size

private fun ranks(xs: List<Double>): DoubleArray {
    val order = xs.indices.sortedBy { xs[it] }
    val r = DoubleArray(xs.size)
    var i = 0
    while (i < order.size) {
        var j = i
        while (j + 1 < order.size && xs[order[j + 1]] == xs[order[i]]) j++
        val avg = (i + j) / 2.0 + 1
        for (k in i..j) r[order[k]] = avg
        i = j + 1
    }
    return r
}

val statistics: List<Program> = listOf(
    Rich(
        "counting", Subject.STATS, title("Dénombrement", "Counting"), "nᵏ, n!/(n − k)!, C(n, k)",
        listOf(num("n", "n objets", "n objects", whole = true), num("k", "k choisis", "k chosen", whole = true))
    ) { v ->
        val n = v.int("n"); val k = v.int("k")
        require(n in 0..2000 && k in 0..2000)
        lines(
            res(say("k-uplets (avec répétition, ordre) nᵏ = ${big(bi(n.toLong()).pow(k))}", "k-tuples (repetition, order) nᵏ = ${big(bi(n.toLong()).pow(k))}")),
            res(say("Arrangements (sans répétition, ordre) A = ${big(if (k > n) BigInteger.ZERO else factorial(n).divide(factorial(n - k)))}", "Arrangements (no repetition, order) A = ${big(if (k > n) BigInteger.ZERO else factorial(n).divide(factorial(n - k)))}")),
            res(say("Combinaisons (sans ordre) C(n, k) = ${big(choose(n, k))}", "Combinations (no order) C(n, k) = ${big(choose(n, k))}")),
            stp(say("Avec répétition, sans ordre C(n + k − 1, k) = ${big(choose(n + k - 1, k))}", "With repetition, no order C(n + k − 1, k) = ${big(choose(n + k - 1, k))}")),
            stp(say("Permutations n! = ${big(factorial(n))}", "Permutations n! = ${big(factorial(n))}"))
        )
    },
    Rich(
        "bayes", Subject.STATS, title("Probabilités conditionnelles (Bayes)", "Conditional probability (Bayes)"), "P(A|B) = P(B|A)·P(A) / P(B)",
        listOf(num("P(A)", "P(A)", "P(A)"), num("P(B|A)", "P(B sachant A)", "P(B given A)"), num("P(B|Ā)", "P(B sachant non A)", "P(B given not A)"))
    ) { v ->
        val pa = v["P(A)"]; val ba = v["P(B|A)"]; val bna = v["P(B|Ā)"]
        require(listOf(pa, ba, bna).all { it in 0.0..1.0 })
        val pb = pa * ba + (1 - pa) * bna
        require(pb > 0)
        lines(
            res(say("P(B) = P(A)P(B|A) + P(Ā)P(B|Ā) = ${show(pb)}", "P(B) = P(A)P(B|A) + P(Ā)P(B|Ā) = ${show(pb)}")),
            res("P(A|B) = ${show(pa * ba / pb)}"),
            stp("P(A ∩ B) = ${show(pa * ba)}   P(Ā ∩ B) = ${show((1 - pa) * bna)}   P(A|B̄) = ${show(if (pb < 1) pa * (1 - ba) / (1 - pb) else Double.NaN)}"),
            stp(if (abs(ba - bna) < 1e-12) say("A et B indépendants", "A and B independent") else say("A et B dépendants", "A and B dependent"))
        )
    },
    Rich(
        "discrete-law", Subject.STATS, title("Loi d'une variable aléatoire", "Law of a random variable"), "E(X) = Σ xᵢpᵢ",
        listOf(list("x", "Valeurs x₁, x₂, …", "Values x₁, x₂, …"), list("p", "Probabilités p₁, p₂, …", "Probabilities p₁, p₂, …"))
    ) { v ->
        val x = v.list("x"); val p = v.list("p")
        require(x.size == p.size && x.isNotEmpty() && p.all { it >= 0 })
        val total = p.sum()
        val e = x.indices.sumOf { x[it] * p[it] }
        val e2 = x.indices.sumOf { x[it] * x[it] * p[it] }
        val variance = e2 - e * e
        lines(
            res("E(X) = ${show(e)}"),
            res("V(X) = E(X²) − E(X)² = ${show(variance)}   σ = ${show(sqrt(variance))}"),
            stp(if (abs(total - 1) < 1e-9) say("Σ pᵢ = 1", "Σ pᵢ = 1") else say("Attention : Σ pᵢ = ${show(total)} ≠ 1", "Note: Σ pᵢ = ${show(total)} ≠ 1"))
        )
    },
    Rich(
        "chebyshev", Subject.STATS, title("Bienaymé-Tchebychev, taille d'échantillon", "Chebyshev, sample size"), "P(|M − μ| ≥ δ) ≤ σ² / (nδ²)",
        listOf(num("σ²", "Variance σ²", "Variance σ²"), num("δ", "Écart δ", "Gap δ"), num("α", "Risque α (0,05 pour 5 %)", "Risk α (0.05 for 5%)"), num("n", "Taille n (facultatif)", "Size n (optional)", whole = true, optional = true))
    ) { v ->
        val s2 = v["σ²"]; val d = v["δ"]; val a = v["α"]
        require(s2 > 0 && d > 0 && a in 0.0..1.0 && a > 0)
        val out = mutableListOf(
            res(say("n ≥ σ² / (αδ²) = ${show(s2 / (a * d * d))} : n = ${kotlin.math.ceil(s2 / (a * d * d)).toLong()}", "n ≥ σ² / (αδ²) = ${show(s2 / (a * d * d))}: n = ${kotlin.math.ceil(s2 / (a * d * d)).toLong()}")),
            stp(say("Une variable : P(|X − μ| ≥ δ) ≤ σ²/δ² = ${show((s2 / (d * d)).coerceAtMost(1.0))}", "One variable: P(|X − μ| ≥ δ) ≤ σ²/δ² = ${show((s2 / (d * d)).coerceAtMost(1.0))}"))
        )
        if (v.has("n")) out += res(say("Pour n = ${v.long("n")} : P(|M − μ| ≥ δ) ≤ ${show((s2 / (v["n"] * d * d)).coerceAtMost(1.0))}", "For n = ${v.long("n")}: P(|M − μ| ≥ δ) ≤ ${show((s2 / (v["n"] * d * d)).coerceAtMost(1.0))}"))
        Outcome(out)
    },
    Rich(
        "t-interval", Subject.STATS, title("Intervalle de confiance d'une moyenne", "Confidence interval for a mean"), "x̄ ± t·s/√n",
        listOf(num("x̄", "Moyenne de l'échantillon", "Sample mean"), num("s", "Écart type de l'échantillon", "Sample standard deviation"), num("n", "Taille", "Size", whole = true), num("c", "Niveau (0,95)", "Level (0.95)"))
    ) { v ->
        val m = v["x̄"]; val s = v["s"]; val n = v.int("n"); val c = v["c"]
        require(n >= 2 && s >= 0 && c > 0 && c < 1)
        val t = TDistribution((n - 1).toDouble()).inverseCumulativeProbability(0.5 + c / 2)
        val z = NormalDistribution(0.0, 1.0).inverseCumulativeProbability(0.5 + c / 2)
        val e = t * s / sqrt(n.toDouble())
        lines(
            res("[${show(m - e)} ; ${show(m + e)}]  (${show(c * 100)} %)"),
            stp(say("t (Student, ${n - 1} degrés de liberté) = ${show(t)}", "t (Student, ${n - 1} degrees of freedom) = ${show(t)}")),
            stp(say("Avec z = ${show(z)} : [${show(m - z * s / sqrt(n.toDouble()))} ; ${show(m + z * s / sqrt(n.toDouble()))}]", "With z = ${show(z)}: [${show(m - z * s / sqrt(n.toDouble()))} ; ${show(m + z * s / sqrt(n.toDouble()))}]"))
        )
    },
    Rich(
        "t-test", Subject.STATS, title("Test sur une moyenne (z, t)", "Test of a mean (z, t)"), "t = (x̄ − μ₀) / (s/√n)",
        listOf(num("x̄", "Moyenne de l'échantillon", "Sample mean"), num("μ₀", "Moyenne supposée H₀", "Mean under H₀"), num("s", "Écart type", "Standard deviation"), num("n", "Taille", "Size", whole = true))
    ) { v ->
        val m = v["x̄"]; val mu = v["μ₀"]; val s = v["s"]; val n = v.int("n")
        require(n >= 2 && s > 0)
        val stat = (m - mu) / (s / sqrt(n.toDouble()))
        val pt = 2 * (1 - TDistribution((n - 1).toDouble()).cumulativeProbability(abs(stat)))
        val pz = 2 * (1 - NormalDistribution(0.0, 1.0).cumulativeProbability(abs(stat)))
        lines(
            res("t = ${show(stat)}"),
            res(say("p (Student, ${n - 1} ddl) = ${show(pt)}", "p (Student, ${n - 1} df) = ${show(pt)}")),
            stp(say("p (normale, σ connu) = ${show(pz)}", "p (normal, σ known) = ${show(pz)}")),
            decision(pt)
        )
    },
    Rich(
        "welch", Subject.STATS, title("Comparaison de deux moyennes", "Comparing two means"), "t = (x̄₁ − x̄₂) / √(s₁²/n₁ + s₂²/n₂)",
        listOf(num("x̄₁", "Moyenne 1", "Mean 1"), num("s₁", "Écart type 1", "Standard deviation 1"), num("n₁", "Taille 1", "Size 1", whole = true), num("x̄₂", "Moyenne 2", "Mean 2"), num("s₂", "Écart type 2", "Standard deviation 2"), num("n₂", "Taille 2", "Size 2", whole = true))
    ) { v ->
        val m1 = v["x̄₁"]; val s1 = v["s₁"]; val n1 = v["n₁"]; val m2 = v["x̄₂"]; val s2 = v["s₂"]; val n2 = v["n₂"]
        require(n1 >= 2 && n2 >= 2)
        val a = s1 * s1 / n1; val b = s2 * s2 / n2
        require(a + b > 0)
        val stat = (m1 - m2) / sqrt(a + b)
        val df = (a + b).pow(2) / (a * a / (n1 - 1) + b * b / (n2 - 1))
        val p = 2 * (1 - TDistribution(df).cumulativeProbability(abs(stat)))
        lines(res("t = ${show(stat)}"), res(say("Welch : ${show(df)} ddl, p = ${show(p)}", "Welch: ${show(df)} df, p = ${show(p)}")), decision(p))
    },
    Rich(
        "chi2-fit", Subject.STATS, title("χ² d'adéquation", "χ² goodness of fit"), "χ² = Σ (O − E)² / E",
        listOf(list("O", "Effectifs observés", "Observed counts"), list("E", "Effectifs ou probabilités attendus", "Expected counts or probabilities"))
    ) { v ->
        val o = v.list("O"); var e = v.list("E")
        require(o.size == e.size && o.size >= 2)
        val total = o.sum()
        // Probabilities summing to 1 are turned into counts.
        if (abs(e.sum() - 1) < 1e-9) e = e.map { it * total }
        require(e.all { it > 0 })
        val chi = o.indices.sumOf { (o[it] - e[it]).pow(2) / e[it] }
        val df = o.size - 1
        val p = 1 - ChiSquaredDistribution(df.toDouble()).cumulativeProbability(chi)
        Outcome(listOfNotNull(
            res("χ² = ${show(chi)}   ($df " + say("ddl", "df") + ")"),
            res("p = ${show(p)}"),
            decision(p),
            if (e.any { it < 5 }) stp(say("Attention : un effectif attendu est sous 5", "Note: an expected count is below 5")) else null
        ))
    },
    Rich(
        "chi2-independence", Subject.STATS, title("χ² d'indépendance", "χ² test of independence"), "Eᵢⱼ = Lᵢ·Cⱼ / N",
        listOf(num("r", "Nombre de lignes", "Rows", whole = true), list("T", "Tableau, ligne après ligne", "Table, row by row"))
    ) { v ->
        val r = v.int("r"); val t = v.list("T")
        require(r >= 2 && t.size % r == 0 && t.size / r >= 2)
        val c = t.size / r
        val rows = (0 until r).map { i -> (0 until c).sumOf { t[i * c + it] } }
        val cols = (0 until c).map { j -> (0 until r).sumOf { t[it * c + j] } }
        val total = rows.sum()
        require(total > 0)
        var chi = 0.0
        val expected = ArrayList<Double>()
        for (i in 0 until r) for (j in 0 until c) {
            val e = rows[i] * cols[j] / total
            require(e > 0)
            expected += e
            chi += (t[i * c + j] - e).pow(2) / e
        }
        val df = (r - 1) * (c - 1)
        val p = 1 - ChiSquaredDistribution(df.toDouble()).cumulativeProbability(chi)
        lines(
            res("χ² = ${show(chi)}   ($df " + say("ddl", "df") + ")"),
            res("p = ${show(p)}"),
            decision(p),
            stp(say("Attendus : ", "Expected: ") + (0 until r).joinToString("  ") { i -> "(" + (0 until c).joinToString(" ; ") { show(expected[i * c + it]) } + ")" })
        )
    },
    Rich(
        "quantiles", Subject.STATS, title("Quantiles : normale, Student, χ²", "Quantiles: normal, Student, χ²"), "P(X ≤ q) = p",
        listOf(num("p", "Probabilité p", "Probability p"), num("ν", "Degrés de liberté ν", "Degrees of freedom ν"))
    ) { v ->
        val p = v["p"]; val nu = v["ν"]
        require(p > 0 && p < 1 && nu > 0)
        lines(
            res("z = ${show(NormalDistribution(0.0, 1.0).inverseCumulativeProbability(p))}  (N(0, 1))"),
            res("t = ${show(TDistribution(nu).inverseCumulativeProbability(p))}  (Student, ν = ${show(nu)})"),
            res("χ² = ${show(ChiSquaredDistribution(nu).inverseCumulativeProbability(p))}  (χ², ν = ${show(nu)})")
        )
    },
    Rich(
        "spearman", Subject.STATS, title("Corrélation de Spearman", "Spearman correlation"), "ρₛ = 1 − 6Σd² / (n(n² − 1))",
        listOf(list("x", "x₁, x₂, …", "x₁, x₂, …"), list("y", "y₁, y₂, …", "y₁, y₂, …"))
    ) { v ->
        val x = v.list("x"); val y = v.list("y")
        require(x.size == y.size && x.size >= 3)
        val rx = ranks(x); val ry = ranks(y)
        val mx = rx.average(); val my = ry.average()
        val cov = rx.indices.sumOf { (rx[it] - mx) * (ry[it] - my) }
        val rho = cov / sqrt(rx.sumOf { (it - mx).pow(2) } * ry.sumOf { (it - my).pow(2) })
        val n = x.size
        val t = rho * sqrt((n - 2) / (1 - rho * rho).coerceAtLeast(1e-300))
        val p = 2 * (1 - TDistribution((n - 2).toDouble()).cumulativeProbability(abs(t)))
        lines(res("ρ = ${show(rho)}"), stp("t = ${show(t)}"), res("p = ${show(p)}"), decision(p))
    },
    Rich(
        "anova", Subject.STATS, title("ANOVA à un facteur", "One-way ANOVA"), "F = (SSB/(k − 1)) / (SSW/(N − k))",
        listOf(list("n", "Taille de chaque groupe", "Size of each group"), list("x", "Toutes les valeurs, groupe après groupe", "Every value, group after group"))
    ) { v ->
        val sizes = v.list("n").map { Math.round(it).toInt() }; val x = v.list("x")
        require(sizes.size >= 2 && sizes.all { it >= 1 } && sizes.sum() == x.size)
        val groups = ArrayList<List<Double>>()
        var at = 0
        sizes.forEach { s -> groups += x.subList(at, at + s); at += s }
        val grand = mean(x)
        val between = groups.sumOf { g -> g.size * (mean(g) - grand).pow(2) }
        val within = groups.sumOf { g -> val m = mean(g); g.sumOf { (it - m).pow(2) } }
        val k = groups.size; val n = x.size
        require(n > k && within > 0)
        val f = (between / (k - 1)) / (within / (n - k))
        val p = 1 - FDistribution((k - 1).toDouble(), (n - k).toDouble()).cumulativeProbability(f)
        lines(
            res("F = ${show(f)}   (${k - 1} ; ${n - k} " + say("ddl", "df") + ")"),
            res("p = ${show(p)}"),
            decision(p),
            stp(say("Moyennes : ", "Means: ") + groups.joinToString("  ") { show(mean(it)) })
        )
    }
)
