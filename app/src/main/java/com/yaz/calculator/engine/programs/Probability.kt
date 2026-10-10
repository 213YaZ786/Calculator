package com.yaz.calculator.engine.programs

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.hipparchus.distribution.discrete.BinomialDistribution

// Probability as the 2026 programmes list it, each drawn: Seconde (crossed
// table, false positives, sensitivity and specificity), Première (weighted
// trees up to four Bernoulli trials, samples and 2σ/√n), Terminale (Pascal's
// list, Galton board, overbooking, sums of variables, Bienaymé-Tchebychev
// against the binomial, random walk; eduscol 2017 for the walk's returns).

private fun rng() = kotlin.random.Random(System.nanoTime())

private fun pct(x: Double) = show(Math.round(x * 10_000) / 100.0) + " %"

val probability: List<Program> = listOf(
    Rich(
        "bernoulli-tree", Subject.STATS, title("Arbre de Bernoulli", "Bernoulli tree"), "P(X = k) = C(n,k)pᵏ(1 − p)ⁿ⁻ᵏ",
        listOf(num("n", "Épreuves (≤ 4)", "Trials (≤ 4)", whole = true, slider = 1.0..4.0, step = 1.0), num("p", "Succès p", "Success p", slider = 0.0..1.0, step = 0.05), num("k", "Succès k", "Successes k", whole = true, slider = 0.0..4.0, step = 1.0))
    ) { v ->
        val n = v.int("n"); val p = v["p"]; val k = v.int("k")
        require(n in 1..4 && p in 0.0..1.0 && k in 0..n)
        val q = 1 - p
        // Leaves top to bottom, S before E at each node.
        val leaves = 1 shl n
        val shapes = ArrayList<Shape2>()
        var paths = 0
        fun y(level: Int, index: Int): Double { val span = leaves shr level; return leaves - (index * span + (span - 1) / 2.0) }
        for (level in 0 until n) for (i in 0 until (1 shl level)) {
            val from = P2(level * 2.0, y(level, i))
            for ((child, success) in listOf(2 * i to true, 2 * i + 1 to false)) {
                // A branch on a path with k successes is lit.
                val to = P2((level + 1) * 2.0, y(level + 1, child))
                val successesSoFar = Integer.bitCount((child.inv() and ((1 shl (level + 1)) - 1)))
                val canReach = successesSoFar <= k && successesSoFar + (n - level - 1) >= k
                shapes += Shape2.Segment(from, to, label = if (n <= 3 || level == 0) (if (success) show(p) else show(q)) else "", tone = if (canReach) 0 else 3)
            }
        }
        for (i in 0 until leaves) {
            val s = n - Integer.bitCount(i)
            val word = (0 until n).joinToString("") { b -> if ((i shr (n - 1 - b)) and 1 == 0) "S" else "E" }
            val hit = s == k
            if (hit) paths++
            shapes += if (hit) Shape2.Point(P2(n * 2.0, y(n, i)), if (n <= 3) word else "", 1) else Shape2.Dots(listOf(P2(n * 2.0, y(n, i))), if (n <= 3) word else "", 3)
        }
        val prob = BinomialDistribution(n, p).probability(k)
        Outcome(
            listOf(
                res("P(X = $k) = ${show(prob)}"),
                stp(say("$paths chemins à $k succès, chacun de probabilité p^$k(1 − p)^${n - k} = ${show(p.pow(k) * q.pow(n - k))}", "$paths paths with $k successes, each of probability p^$k(1 − p)^${n - k} = ${show(p.pow(k) * q.pow(n - k))}")),
                stp(say("Épreuves indépendantes : on multiplie le long d'un chemin, on ajoute les chemins", "Independent trials: multiply along a path, add the paths")),
                stp(say("S : succès, E : échec", "S: success, E: failure"))
            ),
            figure = Figure(shapes, staged = true, plain = true, window = Box2(-0.6, n * 2.0 + 1.6, 0.0, leaves + 1.0))
        )
    },
    Rich(
        "conditional-tree", Subject.STATS, title("Probabilités conditionnelles", "Conditional probability"), "P(B) = P(A)P_A(B) + P(Ā)P_Ā(B)",
        listOf(num("P(A)", "P(A)", "P(A)", slider = 0.0..1.0, step = 0.01), num("P_A(B)", "P_A(B)", "P_A(B)", slider = 0.0..1.0, step = 0.01), num("P_Ā(B)", "P_Ā(B)", "P_Ā(B)", slider = 0.0..1.0, step = 0.01))
    ) { v ->
        val a = v["P(A)"]; val ba = v["P_A(B)"]; val bn = v["P_Ā(B)"]
        require(listOf(a, ba, bn).all { it in 0.0..1.0 })
        val ab = a * ba; val anb = a * (1 - ba); val nab = (1 - a) * bn; val nanb = (1 - a) * (1 - bn)
        val b = ab + nab
        val shapes = listOf(
            Shape2.Segment(P2(0.0, 2.5), P2(2.0, 4.0), show(a)), Shape2.Segment(P2(0.0, 2.5), P2(2.0, 1.0), show(1 - a), tone = 3),
            Shape2.Point(P2(2.0, 4.0), "A"), Shape2.Point(P2(2.0, 1.0), "Ā", 3),
            Shape2.Segment(P2(2.0, 4.0), P2(4.0, 4.75), show(ba), 1), Shape2.Segment(P2(2.0, 4.0), P2(4.0, 3.25), show(1 - ba), 3),
            Shape2.Segment(P2(2.0, 1.0), P2(4.0, 1.75), show(bn), 1), Shape2.Segment(P2(2.0, 1.0), P2(4.0, 0.25), show(1 - bn), 3),
            Shape2.Point(P2(4.0, 4.75), "A∩B ${brief(ab)}", 1), Shape2.Point(P2(4.0, 3.25), "A∩B̄ ${brief(anb)}", 3),
            Shape2.Point(P2(4.0, 1.75), "Ā∩B ${brief(nab)}", 1), Shape2.Point(P2(4.0, 0.25), "Ā∩B̄ ${brief(nanb)}", 3)
        )
        Outcome(
            listOf(
                res("P(B) = ${show(ab)} + ${show(nab)} = ${show(b)}"),
                if (b > 0) res("P_B(A) = P(A∩B)/P(B) = ${show(ab / b)}") else stp("P(B) = 0"),
                stp(say("Tableau croisé : A∩B ${show(ab)}  A∩B̄ ${show(anb)}  Ā∩B ${show(nab)}  Ā∩B̄ ${show(nanb)}", "Two-way table: A∩B ${show(ab)}  A∩B̄ ${show(anb)}  Ā∩B ${show(nab)}  Ā∩B̄ ${show(nanb)}")),
                stp(if (abs(ab - a * b) < 1e-12) say("A et B sont indépendants : P(A∩B) = P(A)P(B)", "A and B are independent: P(A∩B) = P(A)P(B)") else say("A et B ne sont pas indépendants : P_B(A) ≠ P(A)", "A and B are not independent: P_B(A) ≠ P(A)"))
            ),
            figure = Figure(shapes, staged = true, plain = true, window = Box2(-0.4, 7.2, -0.3, 5.3))
        )
    },
    Rich(
        "diagnostic", Subject.STATS, title("Test de dépistage", "Screening test"), "VPP = P(malade | positif)",
        listOf(
            num("prév", "Prévalence (0,01 pour 1 %)", "Prevalence (0.01 for 1%)", slider = 0.001..0.5, step = 0.001),
            num("sens", "Sensibilité", "Sensitivity", slider = 0.5..1.0, step = 0.01), num("spéc", "Spécificité", "Specificity", slider = 0.5..1.0, step = 0.01),
            num("N", "Population (facultatif)", "Population (optional)", whole = true, optional = true)
        )
    ) { v ->
        val prev = v["prév"]; val se = v["sens"]; val sp = v["spéc"]
        require(listOf(prev, se, sp).all { it in 0.0..1.0 })
        val n = if (v.has("N")) v["N"] else 10_000.0
        val tp = n * prev * se; val fn = n * prev * (1 - se); val fp = n * (1 - prev) * (1 - sp); val tn = n * (1 - prev) * sp
        val ppv = tp / (tp + fp); val npv = tn / (tn + fn)
        fun c(x: Double) = show(Math.round(x).toDouble())
        // A square split as the population: ill on the left, then the positives on top.
        val w = prev.coerceAtLeast(0.02)
        val shapes = listOf(
            Shape2.rect(0.0, 1 - se, w, 1.0, tone = 1, label = say("VP", "TP")), Shape2.rect(0.0, 0.0, w, 1 - se, tone = 3),
            Shape2.rect(w, sp, 1.0, 1.0, tone = 2, label = say("FP", "FP")), Shape2.rect(w, 0.0, 1.0, sp, tone = 0, label = say("VN", "TN"))
        )
        Outcome(
            listOf(
                res(say("Positif : malade dans ${pct(ppv)} des cas (VPP)", "Positive: ill in ${pct(ppv)} of cases (PPV)")),
                res(say("Négatif : sain dans ${pct(npv)} des cas (VPN)", "Negative: healthy in ${pct(npv)} of cases (NPV)")),
                stp(say("Sur ${c(n)} personnes : vrais positifs ${c(tp)}, faux positifs ${c(fp)}, faux négatifs ${c(fn)}, vrais négatifs ${c(tn)}", "Out of ${c(n)} people: true positives ${c(tp)}, false positives ${c(fp)}, false negatives ${c(fn)}, true negatives ${c(tn)}")),
                stp(say("Parmi les positifs, ${pct(fp / (tp + fp))} sont des faux positifs", "Among the positives, ${pct(fp / (tp + fp))} are false positives")),
                stp(say("VPP = P(M)·Se / (P(M)·Se + (1 − P(M))(1 − Sp)), formule de Bayes", "PPV = P(D)·Se / (P(D)·Se + (1 − P(D))(1 − Sp)), Bayes' formula"))
            ),
            figure = Figure(shapes, staged = true, square = true, plain = true, window = Box2(-0.05, 1.05, -0.05, 1.05))
        )
    },
    Rich(
        "pascal", Subject.STATS, title("Triangle de Pascal", "Pascal's triangle"), "C(n,k) = C(n−1,k−1) + C(n−1,k)",
        listOf(num("n", "Lignes (≤ 64)", "Rows (≤ 64)", whole = true, slider = 1.0..64.0, step = 1.0))
    ) { v ->
        val n = v.int("n")
        require(n in 1..64)
        // Pascal's list, row by row from the one above.
        var row = listOf(java.math.BigInteger.ONE)
        val rows = arrayListOf(row)
        repeat(n) { row = listOf(java.math.BigInteger.ONE) + row.zipWithNext { a, b -> a + b } + java.math.BigInteger.ONE; rows += row }
        val out = mutableListOf(res("n = $n : " + if (n <= 12) row.joinToString(" ") else row.take(4).joinToString(" ") + " … " + row.takeLast(2).joinToString(" ")))
        rows.take(8).forEachIndexed { i, r -> out += stp("n = $i : ${r.joinToString(" ")}") }
        out += stp(say("Somme de la ligne n : 2ⁿ = ${java.math.BigInteger.TWO.pow(n)}", "Sum of row n: 2ⁿ = ${java.math.BigInteger.TWO.pow(n)}"))
        // The odd entries: Sierpiński's triangle appears.
        val odd = ArrayList<P2>(); val even = ArrayList<P2>()
        rows.forEachIndexed { i, r -> r.forEachIndexed { k, c -> (if (c.testBit(0)) odd else even) += P2(k - i / 2.0, -i.toDouble()) } }
        out += stp(say("En couleur les nombres impairs : le triangle de Sierpiński", "In colour the odd entries: Sierpiński's triangle"))
        Outcome(out, figure = Figure(listOf(Shape2.Dots(even, tone = 3), Shape2.Dots(odd)), square = true, plain = true, window = Box2(-n / 2.0 - 1, n / 2.0 + 1, -n - 1.0, 1.0)))
    },
    Rich(
        "galton", Subject.STATS, title("Planche de Galton", "Galton board"), "X ~ B(n ; 1/2)",
        listOf(num("n", "Rangées de clous (≤ 20)", "Rows of pegs (≤ 20)", whole = true, slider = 1.0..20.0, step = 1.0), num("N", "Billes (≤ 5000)", "Balls (≤ 5000)", whole = true, slider = 10.0..5000.0, step = 10.0))
    ) { v ->
        val n = v.int("n"); val balls = v.int("N")
        require(n in 1..20 && balls in 1..5000)
        val r = rng()
        val counts = IntArray(n + 1)
        val shown = ArrayList<List<P2>>()
        repeat(balls) { b ->
            var x = 0.0; val path = arrayListOf(P2(0.0, 0.0))
            var rights = 0
            for (row in 1..n) { val right = r.nextBoolean(); if (right) rights++; x += if (right) 0.5 else -0.5; path += P2(x, -row.toDouble()) }
            counts[rights]++
            if (b < 6) shown += path
        }
        val law = BinomialDistribution(n, 0.5)
        val shapes = ArrayList<Shape2>()
        // The pegs, then a few balls' paths, then the boxes filling up.
        val pegs = (0 until n).flatMap { row -> (0..row).map { k -> P2(k - row / 2.0, -row - 0.5) } }
        shapes += Shape2.Dots(pegs, tone = 3)
        shown.forEachIndexed { i, p -> shapes += Shape2.Polyline(p, tone = 1 + i % 3) }
        // The boxes fill up from the floor, under the last row of pegs.
        val floor = -n * 1.9 - 1.0
        val scale = n * 0.8 / max(1, counts.max())
        counts.forEachIndexed { k, c -> shapes += Shape2.rect(k - n / 2.0 - 0.4, floor, k - n / 2.0 + 0.4, floor + c * scale, tone = 0) }
        // The binomial law over the boxes.
        shapes += Shape2.Polyline((0..n).map { k -> P2(k - n / 2.0, floor + law.probability(k) * balls * scale) }, say("B(n ; 1/2)", "B(n, 1/2)"), 2, dashed = true)
        val mean = counts.withIndex().sumOf { (k, c) -> k * c }.toDouble() / balls
        Outcome(
            listOf(
                res(say("Moyenne observée ${show(mean)}, attendue n/2 = ${show(n / 2.0)}", "Observed mean ${show(mean)}, expected n/2 = ${show(n / 2.0)}")),
                stp(say("Chaque clou : gauche ou droite, 1/2 chacun ; la case est le nombre de « droite » : X ~ B($n ; 1/2)", "Each peg: left or right, 1/2 each; the box is the number of rights: X ~ B($n, 1/2)")),
                stp(say("Cases : ", "Boxes: ") + counts.joinToString(" "))
            ),
            figure = Figure(shapes, staged = true, square = true, plain = true, window = Box2(-n / 2.0 - 1, n / 2.0 + 1, -n * 1.9 - 1.6, 1.0))
        )
    },
    Rich(
        "overbooking", Subject.STATS, title("Surréservation", "Overbooking"), "plus petit k : P(X > k) ≤ α",
        listOf(num("n", "Billets vendus", "Tickets sold", whole = true, slider = 1.0..500.0, step = 1.0), num("p", "Probabilité de se présenter", "Show-up probability", slider = 0.5..1.0, step = 0.01), num("α", "Risque α (0,05)", "Risk α (0.05)"), num("s", "Places (facultatif)", "Seats (optional)", whole = true, optional = true))
    ) { v ->
        val n = v.int("n"); val p = v["p"]; val a = v["α"]
        require(n in 1..100_000 && p in 0.0..1.0 && a in 0.0..1.0)
        val law = BinomialDistribution(n, p)
        // The programme's algorithm: k climbs until the risk is small enough.
        var k = 0
        while (k < n && 1 - law.cumulativeProbability(k) > a) k++
        val out = mutableListOf(
            res(say("Il faut $k places : P(X > $k) = ${show(1 - law.cumulativeProbability(k))} ≤ ${show(a)}", "$k seats needed: P(X > $k) = ${show(1 - law.cumulativeProbability(k))} ≤ ${show(a)}")),
            stp(say("Avec ${k - 1} places : P(X > ${k - 1}) = ${show(1 - law.cumulativeProbability(k - 1))}", "With ${k - 1} seats: P(X > ${k - 1}) = ${show(1 - law.cumulativeProbability(k - 1))}")),
            stp(say("X, le nombre de passagers présents, suit B($n ; ${show(p)})", "X, the passengers who come, follows B($n, ${show(p)})"))
        )
        val seats = if (v.has("s")) v.int("s") else k
        if (v.has("s")) out.add(1, res(say("Avec $seats places, risque de refuser quelqu'un : ${show(1 - law.cumulativeProbability(seats))}", "With $seats seats, risk of turning someone away: ${show(1 - law.cumulativeProbability(seats))}")))
        val sd = sqrt(n * p * (1 - p))
        val from = max(0, (n * p - 4.5 * sd - 1).toInt()); val to = min(n, (n * p + 4.5 * sd + 2).toInt())
        val shapes = (from..to).map { j -> Shape2.rect(j - 0.4, 0.0, j + 0.4, law.probability(j), tone = if (j > seats) 2 else 0) }
        Outcome(out, figure = Figure(shapes + Shape2.Segment(P2(seats + 0.5, 0.0), P2(seats + 0.5, (from..to).maxOf { law.probability(it) } * 1.1), say("$seats places", "$seats seats"), 2, dashed = true)))
    },
    Rich(
        "dice-sum", Subject.STATS, title("Somme de dés", "Sum of dice"), "S = X₁ + … + Xₙ, E(S) = 3,5n, V(S) = 35n/12",
        listOf(num("n", "Nombre de dés (≤ 30)", "Number of dice (≤ 30)", whole = true, slider = 1.0..30.0, step = 1.0))
    ) { v ->
        val n = v.int("n")
        require(n in 1..30)
        // The law of the sum, die after die (a convolution).
        var law = doubleArrayOf(1.0)
        repeat(n) { val next = DoubleArray(law.size + 6); law.forEachIndexed { s, q -> for (f in 1..6) next[s + f] += q / 6 }; law = next }
        val mean = 3.5 * n; val var2 = 35.0 * n / 12
        val shapes = ArrayList<Shape2>()
        for (s in n..6 * n) shapes += Shape2.rect(s - 0.4, 0.0, s + 0.4, law[s])
        // The bell the sums tend to (central limit).
        if (n >= 2) shapes += Shape2.Curve({ x -> exp(-(x - mean).pow(2) / (2 * var2)) / sqrt(2 * Math.PI * var2) }, n - 0.5, 6 * n + 0.5, say("normale", "normal"), 2)
        val mode = (n..6 * n).maxBy { law[it] }
        Outcome(
            listOf(
                res("E(S) = 3,5 × $n = ${show(mean)}   V(S) = $n × 35/12 = ${show(var2)}   σ = ${show(sqrt(var2))}"),
                stp(say("Variables indépendantes : E et V s'ajoutent ; somme la plus probable $mode (${show(law[mode])})", "Independent variables: E and V add up; most likely sum $mode (${show(law[mode])})")),
                stp(say("Quand n grandit, la loi de S prend la forme d'une cloche (théorème central limite)", "As n grows, the law of S takes a bell shape (central limit theorem)"))
            ),
            figure = Figure(shapes)
        )
    },
    Rich(
        "concentration-law", Subject.STATS, title("Concentration : binomiale et Bienaymé-Tchebychev", "Concentration: binomial and Chebyshev"), "P(|Xₙ/n − p| ≥ δ) ≤ p(1 − p)/(nδ²)",
        listOf(num("n", "n", "n", whole = true, slider = 10.0..2000.0, step = 10.0), num("p", "p", "p", slider = 0.01..0.99, step = 0.01), num("δ", "Écart δ", "Gap δ", slider = 0.01..0.3, step = 0.01))
    ) { v ->
        val n = v.int("n"); val p = v["p"]; val d = v["δ"]
        require(n in 1..100_000 && p in 0.0..1.0 && d > 0)
        val law = BinomialDistribution(n, p)
        // Exactly: the frequencies at least δ away from p.
        // Strictly within δ: from the first whole number above n(p − δ) to the last below n(p + δ).
        val lo = kotlin.math.floor(n * (p - d) + 1e-9).toInt() + 1; val hi = kotlin.math.ceil(n * (p + d) - 1e-9).toInt() - 1
        val inside = if (hi < lo) 0.0 else law.cumulativeProbability(hi) - law.cumulativeProbability(lo - 1)
        val exact = (1 - inside).coerceIn(0.0, 1.0)
        val bound = p * (1 - p) / (n * d * d)
        val sd = sqrt(n * p * (1 - p))
        val from = max(0, (n * p - 4.5 * sd - 1).toInt()); val to = min(n, (n * p + 4.5 * sd + 2).toInt())
        val shapes = (from..to).map { j -> Shape2.rect(j / n.toDouble() - 0.4 / n, 0.0, j / n.toDouble() + 0.4 / n, law.probability(j), tone = if (abs(j / n.toDouble() - p) >= d - 1e-12) 2 else 0) }
        Outcome(
            listOf(
                res(say("Valeur exacte (binomiale) : ${show(exact)}", "Exact value (binomial): ${show(exact)}")),
                res(say("Majorant de Bienaymé-Tchebychev : ${show(min(1.0, bound))}", "Chebyshev's bound: ${show(min(1.0, bound))}")),
                stp(say("La fréquence se concentre autour de p quand n grandit (loi des grands nombres) ; la borne est sûre mais large", "The frequency gathers around p as n grows (law of large numbers); the bound is safe but wide")),
                stp(say("Taille pour un risque α : n ≥ p(1 − p)/(αδ²)", "Size for a risk α: n ≥ p(1 − p)/(αδ²)"))
            ),
            figure = Figure(shapes)
        )
    },
    Rich(
        "random-walk", Subject.STATS, title("Marche aléatoire", "Random walk"), "Sₙ = X₁ + … + Xₙ, Xᵢ = ±1",
        listOf(num("n", "Pas (≤ 2000)", "Steps (≤ 2000)", whole = true, slider = 10.0..2000.0, step = 10.0), num("N", "Marches (≤ 2000)", "Walks (≤ 2000)", whole = true))
    ) { v ->
        val n = v.int("n"); val walks = v.int("N")
        require(n in 1..2000 && walks in 1..2000)
        val r = rng()
        val shown = ArrayList<List<P2>>()
        var returns = 0L; var far = 0L; var sumAbs = 0.0
        repeat(walks) { w ->
            var s = 0; val path = if (w < 6) arrayListOf(P2(0.0, 0.0)) else null
            for (i in 1..n) { s += if (r.nextBoolean()) 1 else -1; if (s == 0) returns++; path?.add(P2(i.toDouble(), s.toDouble())) }
            if (abs(s) > 2 * sqrt(n.toDouble())) far++
            sumAbs += abs(s)
            path?.let { shown += it }
        }
        val shapes = ArrayList<Shape2>()
        val edge = 2 * sqrt(n.toDouble())
        shapes += Shape2.Curve({ x -> 2 * sqrt(x) }, 0.0, n.toDouble(), "2√n", 3, dashed = true)
        shapes += Shape2.Curve({ x -> -2 * sqrt(x) }, 0.0, n.toDouble(), tone = 3, dashed = true)
        shown.forEachIndexed { i, p -> shapes += Shape2.Polyline(p, tone = i % 3) }
        Outcome(
            listOf(
                res(say("Retours à l'origine : ${show(returns.toDouble() / walks)} par marche en moyenne", "Returns to the origin: ${show(returns.toDouble() / walks)} per walk on average")),
                res(say("|Sₙ| > 2√n dans ${pct(far.toDouble() / walks)} des marches (environ 5 %)", "|Sₙ| > 2√n in ${pct(far.toDouble() / walks)} of the walks (about 5 %)")),
                stp(say("E(Sₙ) = 0, V(Sₙ) = n : la marche s'écarte comme √n ; |Sₙ| moyen ${show(sumAbs / walks)} ≈ √(2n/π) = ${show(sqrt(2 * n / Math.PI))}", "E(Sₙ) = 0, V(Sₙ) = n: the walk spreads as √n; mean |Sₙ| ${show(sumAbs / walks)} ≈ √(2n/π) = ${show(sqrt(2 * n / Math.PI))}"))
            ),
            figure = Figure(shapes, staged = true, window = Box2(-n * 0.03, n * 1.05, -edge * 1.6, edge * 1.6))
        )
    },
    Rich(
        "samples", Subject.STATS, title("Échantillons et fluctuation", "Samples and fluctuation"), "|f − p| ≤ 2σ/√n, σ = √(p(1 − p))",
        listOf(num("p", "Proportion p", "Proportion p", slider = 0.01..0.99, step = 0.01), num("n", "Taille de l'échantillon", "Sample size", whole = true, slider = 10.0..2000.0, step = 10.0), num("N", "Échantillons (≤ 1000)", "Samples (≤ 1000)", whole = true))
    ) { v ->
        val p = v["p"]; val n = v.int("n"); val samples = v.int("N")
        require(p in 0.0..1.0 && n in 1..100_000 && samples in 1..1000)
        val r = rng()
        val sigma = sqrt(p * (1 - p)); val half = 2 * sigma / sqrt(n.toDouble())
        var inside = 0
        val ins = ArrayList<P2>(); val outs = ArrayList<P2>()
        for (i in 1..samples) {
            var hits = 0
            repeat(n) { if (r.nextDouble() < p) hits++ }
            val f = hits.toDouble() / n
            if (abs(f - p) <= half) { inside++; ins += P2(i.toDouble(), f) } else outs += P2(i.toDouble(), f)
        }
        Outcome(
            listOf(
                res(say("${pct(inside.toDouble() / samples)} des fréquences dans [p − 2σ/√n ; p + 2σ/√n]", "${pct(inside.toDouble() / samples)} of the frequencies in [p − 2σ/√n, p + 2σ/√n]")),
                stp("[${show(p - half)} ; ${show(p + half)}]   σ = ${show(sigma)}"),
                stp(say("Environ 95 % attendus : la fluctuation d'échantillonnage diminue comme 1/√n", "About 95 % expected: sampling fluctuation shrinks as 1/√n"))
            ),
            figure = Figure(
                listOf(
                    Shape2.Curve({ p + half }, 0.0, samples.toDouble(), "p + 2σ/√n", 3, dashed = true), Shape2.Curve({ p - half }, 0.0, samples.toDouble(), "p − 2σ/√n", 3, dashed = true),
                    Shape2.Curve({ p }, 0.0, samples.toDouble(), "p", 2), Shape2.Dots(ins), Shape2.Dots(outs, tone = 1)
                ),
                window = Box2(-samples * 0.03, samples * 1.05, max(0.0, p - 3.5 * half), min(1.0, p + 3.5 * half))
            )
        )
    }
)
