package com.yaz.calculator.engine.programs

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// Maths expertes (2019 text, still in force): graphs and matrices, Markov
// chains with 2 or 3 states, U(n+1) = A·U(n) + C, and the problems the text
// lists, worked as in the MEN resource "Matrices" (2012): Ehrenfest,
// PageRank, prey and predators, Hill's cipher, Pell; then arithmetic: the
// sieve, Pythagorean triples, affine and Vigenère ciphers.

/** A square matrix typed row after row with the "," key. */
private fun Inputs.square(symbol: String): Array<DoubleArray> {
    val v = list(symbol)
    val n = round(sqrt(v.size.toDouble())).toInt()
    require(n in 1..12 && n * n == v.size)
    return Array(n) { i -> DoubleArray(n) { j -> v[i * n + j] } }
}

private fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val n = a.size
    return Array(n) { i -> DoubleArray(n) { j -> (0 until n).sumOf { k -> a[i][k] * b[k][j] } } }
}

private fun power(a: Array<DoubleArray>, k: Int): Array<DoubleArray> {
    var r = Array(a.size) { i -> DoubleArray(a.size) { j -> if (i == j) 1.0 else 0.0 } }
    repeat(k) { r = mul(r, a) }
    return r
}

private fun rowText(r: DoubleArray) = r.joinToString("  ") { show(it) }

private fun name(i: Int) = ('A' + i).toString()

/** The vertices on a circle (or two side by side), the first at the top. */
private fun circle(n: Int): List<P2> = if (n == 2) listOf(P2(-1.0, 0.0), P2(1.0, 0.0)) else List(n) { i -> P2(sin(2 * PI * i / n), cos(2 * PI * i / n)) }

/**
 * A graph drawn: its vertices, its edges (arrows when it is directed),
 * each weight written on its edge when [weights], loops as small circles.
 */
private fun graphFigure(m: Array<DoubleArray>, weights: Boolean, sizes: List<Double>? = null, labels: List<String>? = null): Figure {
    val n = m.size
    val at = circle(n)
    val directed = (0 until n).any { i -> (0 until n).any { j -> m[i][j] != m[j][i] } }
    val shapes = ArrayList<Shape2>()
    // Each vertex's circle: its size when it has one, else a dot's.
    val rad = List(n) { i -> sizes?.getOrNull(i)?.let { 0.08 + 0.35 * it } ?: 0.1 }
    for (i in 0 until n) for (j in 0 until n) {
        val w = m[i][j]
        if (w == 0.0 || (!directed && j < i)) continue
        if (i == j) {
            // A loop outside its vertex, away from the centre.
            val out = sqrt(at[i].x * at[i].x + at[i].y * at[i].y).let { if (it == 0.0) P2(0.0, 1.0) else at[i] * (1 / it) }
            shapes += Shape2.Circle(at[i] + out * (rad[i] + 0.12), 0.12, if (weights) show(w) else "", 1); continue
        }
        val d = at[j] - at[i]; val len = sqrt(d.x * d.x + d.y * d.y)
        val u = d * (1 / len)
        // Two directions between the same vertices: each arrow set aside.
        val side = if (directed && m[j][i] != 0.0) P2(-u.y, u.x) * 0.07 else P2(0.0, 0.0)
        val from = at[i] + u * (rad[i] + 0.05) + side; val to = at[j] - u * (rad[j] + 0.05) + side
        val tag = if (weights) show(w) else if (w > 1) "×${show(w)}" else ""
        shapes += if (directed) Shape2.Arrow(from, to, tag, 0) else Shape2.Segment(from, to, tag, 0)
    }
    for (i in 0 until n) {
        val text = labels?.getOrNull(i) ?: name(i)
        val size = sizes?.getOrNull(i)
        shapes += if (size != null) Shape2.Circle(at[i], 0.08 + 0.35 * size, text, 2) else Shape2.Point(at[i], text, 2)
    }
    return Figure(shapes, square = true, plain = true, window = Box2(-1.7, 1.7, -1.5, 1.5))
}

/** π·P = π, Σπ = 1, by Gauss on the transposed system. */
private fun invariant(p: Array<DoubleArray>): DoubleArray? {
    val n = p.size
    val a = Array(n) { i -> DoubleArray(n + 1) { j -> if (j == n) 0.0 else p[j][i] - if (i == j) 1.0 else 0.0 } }
    // The last equation replaced by Σπ = 1.
    for (j in 0 until n) a[n - 1][j] = 1.0
    a[n - 1][n] = 1.0
    for (c in 0 until n) {
        val piv = (c until n).maxBy { abs(a[it][c]) }
        if (abs(a[piv][c]) < 1e-12) return null
        val t = a[c]; a[c] = a[piv]; a[piv] = t
        for (r in 0 until n) if (r != c) { val f = a[r][c] / a[c][c]; for (k in c..n) a[r][k] -= f * a[c][k] }
    }
    return DoubleArray(n) { a[it][n] / a[it][it] }
}

val graphs: List<Program> = listOf(
    Rich(
        "graph-paths", Subject.EXPERT, title("Graphe : chemins et matrice d'adjacence", "Graph: paths and adjacency matrix"), "chemins de longueur k de i à j = (Aᵏ)ᵢⱼ",
        listOf(list("A", "Matrice d'adjacence, ligne après ligne", "Adjacency matrix, row after row"), num("k", "Longueur k", "Length k", whole = true, slider = 1.0..8.0, step = 1.0), num("i", "De (1, 2, …) (facultatif)", "From (1, 2, …) (optional)", whole = true, optional = true), num("j", "Vers (facultatif)", "To (optional)", whole = true, optional = true))
    ) { v ->
        val a = v.square("A"); val k = v.int("k"); val n = a.size
        require(k in 0..50)
        val ak = power(a, k)
        val out = mutableListOf<Line>()
        if (v.has("i") && v.has("j")) {
            val i = v.int("i"); val j = v.int("j")
            require(i in 1..n && j in 1..n)
            out += res(say("${show(ak[i - 1][j - 1])} chemins de longueur $k de ${name(i - 1)} à ${name(j - 1)}", "${show(ak[i - 1][j - 1])} paths of length $k from ${name(i - 1)} to ${name(j - 1)}"))
        }
        out += res(say("Matrice A$k :", "Matrix A$k:"))
        ak.forEach { out += stp(rowText(it)) }
        val directed = (0 until n).any { i -> (0 until n).any { j -> a[i][j] != a[j][i] } }
        val degrees = (0 until n).map { i -> a[i].sum() + a[i][i] * if (directed) 0 else 1 }
        out += stp((if (directed) say("Degrés sortants : ", "Out-degrees: ") else say("Degrés : ", "Degrees: ")) + degrees.withIndex().joinToString("  ") { (i, d) -> "${name(i)} ${show(d)}" })
        // Connected: every vertex reached from the first, along edges either way.
        val seen = BooleanArray(n).also { it[0] = true }
        var grew = true
        while (grew) { grew = false; for (x in 0 until n) if (seen[x]) for (y in 0 until n) if ((a[x][y] != 0.0 || a[y][x] != 0.0) && !seen[y]) { seen[y] = true; grew = true } }
        val connected = seen.all { it }
        out += stp(if (connected) say("Connexe", "Connected") else say("Non connexe", "Not connected"))
        if (!directed && connected) {
            val odd = degrees.count { (it.toLong() % 2) != 0L }
            out += stp(when (odd) {
                0 -> say("Tous les degrés pairs : cycle eulérien (théorème d'Euler)", "Every degree even: an Eulerian circuit (Euler's theorem)")
                2 -> say("Deux sommets de degré impair : chaîne eulérienne entre eux", "Two odd vertices: an Eulerian trail between them")
                else -> say("$odd sommets de degré impair : pas de chaîne eulérienne", "$odd odd vertices: no Eulerian trail")
            })
        }
        Outcome(out, figure = graphFigure(a, weights = false))
    },
    Rich(
        "markov", Subject.EXPERT, title("Chaîne de Markov (2 ou 3 états)", "Markov chain (2 or 3 states)"), "πₙ = π₀·Pⁿ, π·P = π",
        listOf(list("P", "Matrice de transition, ligne après ligne", "Transition matrix, row after row"), list("π₀", "Loi initiale π₀", "Initial law π₀"), num("n", "Étape n", "Step n", whole = true, slider = 0.0..30.0, step = 1.0))
    ) { v ->
        val p = v.square("P"); val n = v.int("n"); val pi0 = v.list("π₀")
        val s = p.size
        require(s in 2..3 && pi0.size == s && n in 0..10_000)
        require(p.all { row -> abs(row.sum() - 1) < 1e-9 && row.all { it >= 0 } }) { "stochastic" }
        val pn = power(p, n)
        val law = DoubleArray(s) { j -> (0 until s).sumOf { i -> pi0[i] * pn[i][j] } }
        val stay = invariant(p)
        val out = mutableListOf(res("π$n = (${law.joinToString(" ; ") { show(it) }})"))
        stay?.let { out += res(say("Loi invariante π = (${it.joinToString(" ; ") { x -> show(x) }}) : π·P = π", "Invariant law π = (${it.joinToString(" ; ") { x -> show(x) }}): π·P = π")) }
        out += stp(say("P$n :", "P$n:"))
        pn.forEach { out += stp(rowText(it)) }
        out += stp(say("πₙ₊₁ = πₙ·P ; chaque ligne de P a pour somme 1", "πₙ₊₁ = πₙ·P; every row of P adds up to 1"))
        Outcome(out, figure = graphFigure(p, weights = true, sizes = law.toList(), labels = (0 until s).map { "${name(it)} ${brief(law[it])}" }))
    },
    Rich(
        "affine-recurrence", Subject.EXPERT, title("Suite de matrices Uₙ₊₁ = AUₙ + C", "Matrix sequence Uₙ₊₁ = AUₙ + C"), "U = (I − A)⁻¹C, Uₙ − U = Aⁿ(U₀ − U)",
        listOf(list("A", "A (2 × 2) : a, b, c, d", "A (2 × 2): a, b, c, d"), list("C", "C (2 nombres)", "C (2 numbers)"), list("U₀", "U₀ (2 nombres)", "U₀ (2 numbers)"), num("n", "n", "n", whole = true, slider = 0.0..60.0, step = 1.0))
    ) { v ->
        val a = v.square("A"); val c = v.list("C"); val u0 = v.list("U₀"); val n = v.int("n")
        require(a.size == 2 && c.size == 2 && u0.size == 2 && n in 0..10_000)
        val path = arrayListOf(P2(u0[0], u0[1]))
        var x = u0[0]; var y = u0[1]
        repeat(n) { val nx = a[0][0] * x + a[0][1] * y + c[0]; val ny = a[1][0] * x + a[1][1] * y + c[1]; x = nx; y = ny; if (path.size < 400) path += P2(x, y) }
        val out = mutableListOf(res("U$n = (${show(x)} ; ${show(y)})"))
        // The fixed point, when I − A can be inverted.
        val d = (1 - a[0][0]) * (1 - a[1][1]) - a[0][1] * a[1][0]
        if (abs(d) > 1e-12) {
            val fx = ((1 - a[1][1]) * c[0] + a[0][1] * c[1]) / d; val fy = (a[1][0] * c[0] + (1 - a[0][0]) * c[1]) / d
            out += res(say("Point fixe U = (${show(fx)} ; ${show(fy)})", "Fixed point U = (${show(fx)} ; ${show(fy)})"))
            out += stp(say("Vₙ = Uₙ − U vérifie Vₙ₊₁ = AVₙ, donc Uₙ = U + Aⁿ(U₀ − U)", "Vₙ = Uₙ − U satisfies Vₙ₊₁ = AVₙ, so Uₙ = U + Aⁿ(U₀ − U)"))
            return@Rich Outcome(out, figure = Figure(listOf(Shape2.Polyline(path, tone = 1), Shape2.Dots(path), Shape2.Point(P2(fx, fy), "U", 2), Shape2.Point(path[0], "U₀", 3)), staged = true))
        }
        Outcome(out, figure = Figure(listOf(Shape2.Polyline(path, tone = 1), Shape2.Dots(path), Shape2.Point(path[0], "U₀", 3)), staged = true))
    },
    Rich(
        "ehrenfest", Subject.EXPERT, title("Modèle d'Ehrenfest", "Ehrenfest model"), "P(i → i − 1) = i/N, P(i → i + 1) = (N − i)/N",
        listOf(num("N", "Molécules (≤ 200)", "Molecules (≤ 200)", whole = true, slider = 2.0..200.0, step = 2.0), num("i₀", "Dans l'urne A au départ", "In urn A at the start", whole = true), num("T", "Étapes (≤ 5000)", "Steps (≤ 5000)", whole = true))
    ) { v ->
        val n = v.int("N"); var i = v.int("i₀"); val steps = v.int("T")
        require(n in 1..200 && i in 0..n && steps in 1..5000)
        val r = kotlin.random.Random(System.nanoTime())
        val path = arrayListOf(P2(0.0, i.toDouble()))
        var sum = 0.0
        for (t in 1..steps) { if (r.nextInt(n) < i) i-- else i++; sum += i; path += P2(t.toDouble(), i.toDouble()) }
        Outcome(
            listOf(
                res(say("Moyenne dans A : ${show(sum / steps)}, équilibre N/2 = ${show(n / 2.0)}", "Mean in A: ${show(sum / steps)}, equilibrium N/2 = ${show(n / 2.0)}")),
                stp(say("À chaque étape une molécule tirée au hasard change d'urne", "At each step a molecule drawn at random changes urn")),
                stp(say("Loi invariante : binomiale B(N ; 1/2) ; retour moyen à l'état N : 2ᴺ étapes", "Invariant law: binomial B(N, 1/2); mean return to state N: 2ᴺ steps"))
            ),
            figure = Figure(listOf(Shape2.Curve({ n / 2.0 }, 0.0, steps.toDouble(), "N/2", 2, dashed = true), Shape2.Polyline(path, "A")), window = Box2(-steps * 0.03, steps * 1.05, -n * 0.05, n * 1.08))
        )
    },
    Rich(
        "pagerank", Subject.EXPERT, title("PageRank", "PageRank"), "Uₚ₊₁ = Uₚ·((c/n)J + (1 − c)A)",
        listOf(list("L", "Liens : matrice, 1 si i pointe vers j", "Links: matrix, 1 when i links to j"), num("c", "Téléportation c (0,15)", "Teleportation c (0.15)", slider = 0.0..1.0, step = 0.01))
    ) { v ->
        val l = v.square("L"); val c = v["c"]
        val n = l.size
        require(n >= 2 && c in 0.0..1.0)
        // Each page shares its rank among its links; a page with none shares it with all (the classic pitfall).
        val a = Array(n) { i -> val out = l[i].sum(); DoubleArray(n) { j -> if (out == 0.0) 1.0 / n else l[i][j] / out } }
        var u = DoubleArray(n) { 1.0 / n }
        var steps = 0
        while (steps < 1000) {
            val next = DoubleArray(n) { j -> (0 until n).sumOf { i -> u[i] * (c / n + (1 - c) * a[i][j]) } }
            val gap = next.indices.maxOf { abs(next[it] - u[it]) }
            u = next; steps++
            if (gap < 1e-12) break
        }
        val order = u.indices.sortedByDescending { u[it] }
        Outcome(
            listOf(
                res(say("Classement : ", "Ranking: ") + order.joinToString(" > ") { "${name(it)} (${brief(u[it])})" }),
                stp(say("$steps itérations de la marche du surfeur, téléporté avec la probabilité ${show(c)}", "$steps steps of the surfer's walk, teleported with probability ${show(c)}")),
                stp(say("Une page sans lien partage son rang avec toutes", "A page with no link shares its rank with all"))
            ),
            figure = graphFigure(l, weights = false, sizes = u.map { it * n / 2 }, labels = (0 until n).map { "${name(it)} ${brief(u[it])}" })
        )
    },
    Rich(
        "prey-predator", Subject.EXPERT, title("Proies et prédateurs (Volterra)", "Prey and predators (Volterra)"), "Aₙ₊₁ = Aₙ(1 + a − bBₙ), Bₙ₊₁ = Bₙ(1 − d + cAₙ)",
        listOf(num("A₀", "Proies", "Prey"), num("B₀", "Prédateurs", "Predators"), num("a", "a (0,05)", "a (0.05)"), num("b", "b (0,002)", "b (0.002)"), num("c", "c (0,00004)", "c (0.00004)"), num("d", "d (0,04)", "d (0.04)"), num("n", "Étapes (≤ 5000)", "Steps (≤ 5000)", whole = true))
    ) { v ->
        var x = v["A₀"]; var y = v["B₀"]; val a = v["a"]; val b = v["b"]; val c = v["c"]; val d = v["d"]; val n = v.int("n")
        require(x >= 0 && y >= 0 && n in 1..5000)
        val prey = arrayListOf(P2(0.0, x)); val pred = arrayListOf(P2(0.0, y))
        val k = if (y > 0) x / y else 1.0
        for (t in 1..n) { val nx = x * (1 + a - b * y); val ny = y * (1 - d + c * x); x = max(0.0, nx); y = max(0.0, ny); prey += P2(t.toDouble(), x); pred += P2(t.toDouble(), y * k) }
        Outcome(
            listOf(
                res(say("Après $n étapes : ${show(x)} proies, ${show(y)} prédateurs", "After $n steps: ${show(x)} prey, ${show(y)} predators")),
                res(say("Équilibre (d/c ; a/b) = (${show(d / c)} ; ${show(a / b)})", "Equilibrium (d/c, a/b) = (${show(d / c)} ; ${show(a / b)})")),
                stp(say("Les prédateurs sont tracés × ${brief(k)} pour tenir sur la même échelle ; MEN 2012 : 1000 proies, 50 prédateurs, a = 0,05, b = 0,002, c = 0,00004, d = 0,04", "Predators drawn × ${brief(k)} to share the scale; MEN 2012: 1000 prey, 50 predators, a = 0.05, b = 0.002, c = 0.00004, d = 0.04"))
            ),
            figure = Figure(listOf(Shape2.Polyline(prey, say("proies", "prey")), Shape2.Polyline(pred, say("prédateurs", "predators"), 1)), staged = true)
        )
    },
    Rich(
        "sieve", Subject.EXPERT, title("Crible d'Ératosthène", "Sieve of Eratosthenes"), "on raye les multiples de 2, 3, 5, 7…",
        listOf(num("N", "Jusqu'à N (≤ 10⁷)", "Up to N (≤ 10⁷)", whole = true, slider = 10.0..400.0, step = 10.0))
    ) { v ->
        val n = v.int("N")
        require(n in 2..10_000_000)
        val composite = BooleanArray(n + 1)
        var p = 2
        while (p.toLong() * p <= n) { if (!composite[p]) { var m = p * p; while (m <= n) { composite[m] = true; m += p } }; p++ }
        val primes = (2..n).filter { !composite[it] }
        val out = mutableListOf(res(say("${primes.size} nombres premiers jusqu'à $n", "${primes.size} primes up to $n")))
        out += stp(primes.take(40).joinToString(" ") + if (primes.size > 40) " … ${primes.last()}" else "")
        out += stp(say("On s'arrête à √N = ${show(sqrt(n.toDouble()))} : un nombre composé a un facteur ≤ √N ; N/ln N = ${show(n / kotlin.math.ln(n.toDouble()))}", "Stop at √N = ${show(sqrt(n.toDouble()))}: a composite number has a factor ≤ √N; N/ln N = ${show(n / kotlin.math.ln(n.toDouble()))}"))
        // The grid of the numbers, ten a row: the primes lit (up to 400).
        val shown = min(n, 400)
        val lit = (2..shown).filter { !composite[it] }.map { P2(((it - 1) % 10).toDouble(), -((it - 1) / 10).toDouble()) }
        val out2 = (1..shown).filter { it == 1 || composite[it] }.map { P2(((it - 1) % 10).toDouble(), -((it - 1) / 10).toDouble()) }
        val rows = (shown + 9) / 10
        Outcome(out, figure = Figure(listOf(Shape2.Dots(out2, tone = 3), Shape2.Dots(lit)), plain = true, square = true, window = Box2(-1.0, 10.0, -rows.toDouble(), 1.0)))
    },
    Rich(
        "pythagorean", Subject.EXPERT, title("Triplets pythagoriciens", "Pythagorean triples"), "a = m² − n², b = 2mn, c = m² + n²",
        listOf(num("c", "Hypoténuse ≤ c (≤ 10⁵)", "Hypotenuse ≤ c (≤ 10⁵)", whole = true, slider = 5.0..500.0, step = 5.0))
    ) { v ->
        val limit = v.long("c")
        require(limit in 5..100_000)
        // Euclid's formula: m > n, coprime, one even: every primitive triple once.
        val triples = ArrayList<Triple<Long, Long, Long>>()
        var m = 2L
        while (m * m + 1 <= limit) {
            for (n in 1 until m) if ((m - n) % 2 == 1L && BigInteger.valueOf(m).gcd(BigInteger.valueOf(n)) == BigInteger.ONE) {
                val c = m * m + n * n
                if (c <= limit) { val a = m * m - n * n; val b = 2 * m * n; triples += Triple(min(a, b), max(a, b), c) }
            }
            m++
        }
        triples.sortBy { it.third }
        val pts = triples.flatMap { (a, b, _) -> listOf(P2(a.toDouble(), b.toDouble()), P2(b.toDouble(), a.toDouble())) }
        Outcome(
            listOf(
                res(say("${triples.size} triplets primitifs avec c ≤ $limit", "${triples.size} primitive triples with c ≤ $limit")),
                stp(triples.take(12).joinToString("  ") { (a, b, c) -> "($a, $b, $c)" }),
                stp(say("m > n premiers entre eux, de parités différentes ; les autres triplets en sont les multiples", "m > n coprime, of different parity; the other triples are their multiples"))
            ),
            figure = Figure(listOf(Shape2.Dots(pts)), square = true)
        )
    },
    Rich(
        "pell", Subject.EXPERT, title("Équation de Pell-Fermat", "Pell's equation"), "x² − d·y² = 1",
        listOf(num("d", "d (non carré, ≤ 10⁶)", "d (not a square, ≤ 10⁶)", whole = true), num("k", "Solutions (≤ 8)", "Solutions (≤ 8)", whole = true))
    ) { v ->
        val d = v.long("d"); val count = v.int("k")
        require(d in 2..1_000_000 && count in 1..8)
        val r = sqrt(d.toDouble()).toLong().let { s -> (s - 1..s + 1).last { it * it <= d } }
        require(r * r != d) { "square" }
        // The continued fraction of √d gives the convergents p/q; the fundamental solution is the first with p² − dq² = 1.
        var m = 0L; var den = 1L; var a = r
        var p0 = BigInteger.ONE; var q0 = BigInteger.ZERO; var p1 = BigInteger.valueOf(a); var q1 = BigInteger.ONE
        val dd = BigInteger.valueOf(d)
        var minusOne: Pair<BigInteger, BigInteger>? = null
        var steps = 0
        while (p1 * p1 - dd * q1 * q1 != BigInteger.ONE && steps < 10_000) {
            if (minusOne == null && p1 * p1 - dd * q1 * q1 == BigInteger.ONE.negate()) minusOne = p1 to q1
            m = den * a - m; den = (d - m * m) / den; a = (r + m) / den
            val p2 = BigInteger.valueOf(a) * p1 + p0; val q2 = BigInteger.valueOf(a) * q1 + q0
            p0 = p1; q0 = q1; p1 = p2; q1 = q2; steps++
        }
        val x1 = p1; val y1 = q1
        val out = mutableListOf(res(say("Solution fondamentale : x = $x1, y = $y1", "Fundamental solution: x = $x1, y = $y1")))
        var x = x1; var y = y1
        val list = arrayListOf(x1 to y1)
        repeat(count - 1) { val nx = x1 * x + dd * y1 * y; val ny = x1 * y + y1 * x; x = nx; y = ny; list += x to y }
        list.drop(1).forEach { (a1, b1) -> out += stp("x = $a1, y = $b1") }
        minusOne?.let { out += stp(say("x² − ${d}y² = −1 a aussi la solution x = ${it.first}, y = ${it.second}", "x² − ${d}y² = −1 also has x = ${it.first}, y = ${it.second}")) }
        out += stp(say("Par la fraction continue de √$d ; les suivantes : (x + y√d)ⁿ", "From the continued fraction of √$d; the next ones: (x + y√d)ⁿ"))
        Outcome(out)
    },
    Rich(
        "affine-cipher", Subject.EXPERT, title("Chiffrement affine", "Affine cipher"), "y ≡ ax + b [26]",
        listOf(words("M", "Message", "Message"), num("a", "a (premier avec 26)", "a (coprime with 26)", whole = true), num("b", "b", "b", whole = true))
    ) { v ->
        val text = v.text("M").uppercase(); val a = v.long("a").mod(26L).toInt(); val b = v.long("b").mod(26L).toInt()
        require(text.any { it in 'A'..'Z' })
        if (BigInteger.valueOf(a.toLong()).gcd(BigInteger.valueOf(26)) != BigInteger.ONE) return@Rich lines(res(say("a = $a n'est pas premier avec 26 : deux lettres auraient le même code", "a = $a is not coprime with 26: two letters would share a code")))
        val inv = (1..25).first { (a * it) % 26 == 1 }
        fun map(s: String, f: (Int) -> Int) = s.map { c -> if (c in 'A'..'Z') 'A' + f(c - 'A') else c }.joinToString("")
        lines(
            res(say("Chiffré : ", "Encrypted: ") + map(text) { (a * it + b) % 26 }),
            res(say("Déchiffré (si le message était chiffré) : ", "Decrypted (if the message was encrypted): ") + map(text) { ((inv * (it - b)) % 26 + 26) % 26 }),
            stp(say("Déchiffrer : x ≡ a⁻¹(y − b) [26], a⁻¹ = $inv", "To decrypt: x ≡ a⁻¹(y − b) [26], a⁻¹ = $inv"))
        )
    },
    Rich(
        "vigenere", Subject.EXPERT, title("Chiffrement de Vigenère", "Vigenère cipher"), "yᵢ ≡ xᵢ + kᵢ [26]",
        listOf(words("M", "Message", "Message"), words("K", "Clé", "Key"))
    ) { v ->
        val text = v.text("M").uppercase(); val key = v.text("K").uppercase().filter { it in 'A'..'Z' }
        require(text.any { it in 'A'..'Z' } && key.isNotEmpty())
        fun shift(sign: Int): String {
            var i = 0
            return text.map { c -> if (c in 'A'..'Z') { val k = key[i % key.length] - 'A'; i++; 'A' + ((c - 'A' + sign * k) % 26 + 26) % 26 } else c }.joinToString("")
        }
        lines(
            res(say("Chiffré : ", "Encrypted: ") + shift(1)),
            res(say("Déchiffré (si le message était chiffré) : ", "Decrypted (if the message was encrypted): ") + shift(-1)),
            stp(say("Chaque lettre décalée de la lettre de la clé sous elle ; la clé se répète", "Each letter shifted by the key's letter under it; the key repeats"))
        )
    },
    Rich(
        "hill-cipher", Subject.EXPERT, title("Chiffrement de Hill", "Hill cipher"), "Y ≡ A·X [26]",
        listOf(words("M", "Message", "Message"), list("A", "A : a, b, c, d", "A: a, b, c, d"))
    ) { v ->
        val text = v.text("M").uppercase().filter { it in 'A'..'Z' }
        val m = v.list("A").map { Math.round(it).toInt() }
        require(text.isNotEmpty() && m.size == 4)
        val det = ((m[0] * m[3] - m[1] * m[2]) % 26 + 26) % 26
        if (BigInteger.valueOf(det.toLong()).gcd(BigInteger.valueOf(26)) != BigInteger.ONE) return@Rich lines(res(say("det A = $det n'est pas premier avec 26 : A n'est pas inversible modulo 26", "det A = $det is not coprime with 26: A cannot be inverted modulo 26")))
        val padded = if (text.length % 2 == 1) text + "X" else text
        fun apply(k: List<Int>) = padded.chunked(2).joinToString("") { pair ->
            val x = pair[0] - 'A'; val y = pair[1] - 'A'
            "${'A' + ((k[0] * x + k[1] * y) % 26 + 26) % 26}${'A' + ((k[2] * x + k[3] * y) % 26 + 26) % 26}"
        }
        val inv = (1..25).first { (det * it) % 26 == 1 }
        val back = listOf(m[3], -m[1], -m[2], m[0]).map { ((it * inv) % 26 + 26) % 26 }
        lines(
            res(say("Chiffré : ", "Encrypted: ") + apply(m)),
            res(say("Déchiffré (si le message était chiffré) : ", "Decrypted (if the message was encrypted): ") + apply(back)),
            stp(say("Lettres deux par deux ; det A = $det, det⁻¹ = $inv [26] ; A⁻¹ ≡ (${back.joinToString(", ")}) [26]", "Letters two by two; det A = $det, det⁻¹ = $inv [26]; A⁻¹ ≡ (${back.joinToString(", ")}) [26]"))
        )
    }
)
