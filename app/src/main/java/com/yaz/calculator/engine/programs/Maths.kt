package com.yaz.calculator.engine.programs

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.hipparchus.distribution.continuous.NormalDistribution
import org.hipparchus.distribution.discrete.BinomialDistribution

private fun w(fr: String, en: String) = Words(fr, en)
private fun f(symbol: String, fr: String, en: String, unit: String = "", whole: Boolean = false) = Field(symbol, Words(fr, en), unit, whole)
private fun n(v: Double) = Solve.show(v)
private fun t(fr: String, en: String) = Words(fr, en).get()

/** A program that works out its answer step by step from every field filled. */
private class Method(
    override val id: String,
    override val subject: Subject,
    override val title: Words,
    override val formula: String,
    override val fields: List<Field>,
    private val work: (Map<String, Double>) -> List<Line>
) : Program {
    override fun run(values: Map<String, Double?>): Outcome {
        if (values.values.any { it == null } || values.size < fields.size) return Outcome(listOf(Line(ALL.get())))
        for (fd in fields) if (fd.whole && values[fd.symbol]!! != Math.rint(values[fd.symbol]!!)) return Outcome(listOf(Line(WHOLE.get().format(fd.symbol))))
        return runCatching { Outcome(work(values.mapValues { it.value!! })) }.getOrElse { Outcome(listOf(Line(IMPOSSIBLE.get()))) }
    }

    companion object {
        val ALL = Words("Remplissez toutes les valeurs.", "Fill every value.")
        val WHOLE = Words("%s doit être un entier.", "%s must be a whole number.")
        val IMPOSSIBLE = Words("Pas de résultat avec ces valeurs.", "No result with these values.")
    }
}

private fun big(v: Double): BigInteger = BigInteger.valueOf(Math.round(v))
private fun step(text: String) = Line(text)
private fun result(text: String) = Line(text, result = true)

/** √n written simply: 2√3 for √12, 5 for √25. */
private fun radical(n: BigInteger): String {
    var outside = BigInteger.ONE
    var inside = n
    var k = BigInteger.TWO
    while (k.multiply(k) <= inside && k < BigInteger.valueOf(100_000)) {
        val sq = k.multiply(k)
        while (inside.mod(sq).signum() == 0) { inside = inside.divide(sq); outside = outside.multiply(k) }
        k = k.add(BigInteger.ONE)
    }
    return when {
        inside == BigInteger.ONE -> "$outside"
        outside == BigInteger.ONE -> "√$inside"
        else -> "$outside√$inside"
    }
}

/** The programs of maths (spécialité, première and terminale). */
val maths: List<Program> = listOf(
    Method(
        "quadratic", Subject.MATHS, w("Second degré", "Quadratic equation"), "a·x² + b·x + c = 0",
        listOf(f("a", "a", "a"), f("b", "b", "b"), f("c", "c", "c"))
    ) { v ->
        val a = v["a"]!!; val b = v["b"]!!; val c = v["c"]!!
        require(a != 0.0)
        val d = b * b - 4 * a * c
        val alpha = -b / (2 * a)
        val beta = -d / (4 * a)
        val out = mutableListOf(step("Δ = b² − 4ac = ${n(d)}"))
        val wholeAll = listOf(a, b, c).all { it == Math.rint(it) && abs(it) < 1e9 }
        when {
            d > 0 -> {
                val r = sqrt(d)
                out += result("x₁ = (−b − √Δ) / 2a = ${n((-b - r) / (2 * a))}")
                out += result("x₂ = (−b + √Δ) / 2a = ${n((-b + r) / (2 * a))}")
                if (wholeAll) out += step("x = (${n(-b)} ± ${radical(big(d))}) / ${n(2 * a)}")
                out += step("${n(a)}(x − x₁)(x − x₂)")
            }
            d == 0.0 -> out += result("x₀ = −b / 2a = ${n(alpha)}")
            else -> {
                val im = sqrt(-d) / (2 * abs(a))
                out += step(t("Δ < 0 : deux racines complexes conjuguées", "Δ < 0: two complex conjugate roots"))
                out += result("z₁ = ${n(alpha)} − ${n(im)}i")
                out += result("z₂ = ${n(alpha)} + ${n(im)}i")
            }
        }
        out += step("α = −b / 2a = ${n(alpha)},  β = ${n(beta)}")
        out += step("f(x) = ${n(a)}(x − ${n(alpha)})² + ${n(beta)}")
        out += step(if (a > 0) t("Minimum ${n(beta)} en x = ${n(alpha)}", "Minimum ${n(beta)} at x = ${n(alpha)}") else t("Maximum ${n(beta)} en x = ${n(alpha)}", "Maximum ${n(beta)} at x = ${n(alpha)}"))
        out
    },
    Method(
        "arithmetic", Subject.MATHS, w("Suite arithmétique", "Arithmetic sequence"), "uₙ = u₀ + n·r",
        listOf(f("u₀", "Premier terme", "First term"), f("r", "Raison", "Common difference"), f("n", "Rang", "Index", whole = true))
    ) { v ->
        val u0 = v["u₀"]!!; val r = v["r"]!!; val k = v["n"]!!
        val un = u0 + k * r
        listOf(result("uₙ = ${n(un)}"), result("u₀ + … + uₙ = (n + 1)(u₀ + uₙ) / 2 = ${n((k + 1) * (u0 + un) / 2)}"))
    },
    Method(
        "geometric", Subject.MATHS, w("Suite géométrique", "Geometric sequence"), "uₙ = u₀·qⁿ",
        listOf(f("u₀", "Premier terme", "First term"), f("q", "Raison", "Common ratio"), f("n", "Rang", "Index", whole = true))
    ) { v ->
        val u0 = v["u₀"]!!; val q = v["q"]!!; val k = v["n"]!!
        val un = u0 * q.pow(k)
        val sum = if (q == 1.0) u0 * (k + 1) else u0 * (1 - q.pow(k + 1)) / (1 - q)
        listOf(
            result("uₙ = ${n(un)}"),
            result("u₀ + … + uₙ = u₀(1 − qⁿ⁺¹) / (1 − q) = ${n(sum)}"),
            step(when { abs(q) < 1 -> "|q| < 1 : uₙ → 0"; q > 1 -> "q > 1 : uₙ → ${if (u0 > 0) "+∞" else "−∞"}"; q == 1.0 -> t("q = 1 : constante", "q = 1: constant"); else -> t("q ≤ −1 : pas de limite", "q ≤ −1: no limit") })
        )
    },
    Method(
        "recurrence", Subject.MATHS, w("Suite arithmético-géométrique", "Recurrence uₙ₊₁ = a·uₙ + b"), "uₙ₊₁ = a·uₙ + b",
        listOf(f("u₀", "Premier terme", "First term"), f("a", "a", "a"), f("b", "b", "b"), f("n", "Rang", "Index", whole = true))
    ) { v ->
        val u0 = v["u₀"]!!; val a = v["a"]!!; val b = v["b"]!!; val k = v["n"]!!.toInt()
        require(k in 0..100_000)
        var u = u0
        val first = mutableListOf<String>()
        for (i in 0 until k) { if (i < 6) first += n(u); u = a * u + b }
        val out = mutableListOf(result("uₙ = ${n(u)}"), step("u₀, u₁, … : ${(first + if (k <= 6) listOf(n(u)) else listOf("…")).joinToString("  ")}"))
        if (a != 1.0) {
            val l = b / (1 - a)
            out += step(t("Point fixe ℓ = b / (1 − a) = ${n(l)} ; vₙ = uₙ − ℓ est géométrique de raison ${n(a)}", "Fixed point ℓ = b / (1 − a) = ${n(l)}; vₙ = uₙ − ℓ is geometric with ratio ${n(a)}"))
            out += step("uₙ = ${n(l)} + (${n(u0 - l)})·${n(a)}ⁿ")
            out += step(if (abs(a) < 1) "|a| < 1 : uₙ → ${n(l)}" else t("|a| ≥ 1 : pas de limite finie (sauf u₀ = ℓ)", "|a| ≥ 1: no finite limit (unless u₀ = ℓ)"))
        }
        out
    },
    Method(
        "confidence", Subject.MATHS, w("Intervalle de confiance (proportion)", "Confidence interval (proportion)"), "f ± 1,96·√(f(1 − f) / n)",
        listOf(f("f", "Fréquence observée", "Observed frequency"), f("n", "Taille de l'échantillon", "Sample size", whole = true))
    ) { v ->
        val fr = v["f"]!!; val size = v["n"]!!
        require(size > 0 && fr in 0.0..1.0)
        val e = 1.96 * sqrt(fr * (1 - fr) / size)
        listOf(result("[${n(fr - e)} ; ${n(fr + e)}]  (95 %)"), step("[f − 1/√n ; f + 1/√n] = [${n(fr - 1 / sqrt(size))} ; ${n(fr + 1 / sqrt(size))}]"))
    },
    Solver(
        "evolution", Subject.MATHS, w("Taux d'évolution", "Rate of change"), "V₁ = V₀·(1 + t)ⁿ",
        listOf(f("V₁", "Valeur finale", "Final value"), f("V₀", "Valeur initiale", "Initial value"), f("t", "Taux par période (0,05 pour 5 %)", "Rate per period (0.05 for 5%)"), f("n", "Nombre de périodes", "Periods")),
        positive = false
    ) { v -> v["V₁"]!! - v["V₀"]!! * (1 + v["t"]!!).pow(v["n"]!!) },
    Solver(
        "cosines", Subject.MATHS, w("Triangle : loi des cosinus", "Triangle: law of cosines"), "c² = a² + b² − 2ab·cos γ",
        listOf(f("c", "Côté c", "Side c"), f("a", "Côté a", "Side a"), f("b", "Côté b", "Side b"), f("γ", "Angle γ (degrés)", "Angle γ (degrees)", "°"))
    ) { v -> v["c"]!!.pow(2) - (v["a"]!!.pow(2) + v["b"]!!.pow(2) - 2 * v["a"]!! * v["b"]!! * cos(v["γ"]!! * PI / 180)) },
    Method(
        "vectors", Subject.MATHS, w("Vecteurs de l'espace", "Vectors in space"), "u·v = xx' + yy' + zz'",
        listOf(f("x", "x de u", "u's x"), f("y", "y de u", "u's y"), f("z", "z de u", "u's z"), f("x'", "x de v", "v's x"), f("y'", "y de v", "v's y"), f("z'", "z de v", "v's z"))
    ) { v ->
        val x = v["x"]!!; val y = v["y"]!!; val z = v["z"]!!; val x2 = v["x'"]!!; val y2 = v["y'"]!!; val z2 = v["z'"]!!
        val dot = x * x2 + y * y2 + z * z2
        val nu = sqrt(x * x + y * y + z * z); val nv = sqrt(x2 * x2 + y2 * y2 + z2 * z2)
        val cross = listOf(y * z2 - z * y2, z * x2 - x * z2, x * y2 - y * x2)
        listOf(
            result("u·v = ${n(dot)}"),
            result("‖u‖ = ${n(nu)}   ‖v‖ = ${n(nv)}"),
            result("(u, v) = ${n(Math.toDegrees(Math.acos((dot / (nu * nv)).coerceIn(-1.0, 1.0))))}°"),
            step(if (dot == 0.0) "u ⊥ v" else if (cross.all { it == 0.0 }) t("u et v colinéaires", "u and v collinear") else "u ∧ v = (${cross.joinToString(" ; ") { n(it) }})")
        )
    }
)

/** The programs of the maths expertes option: arithmetic, complex numbers, matrices and Markov chains. */
val expert: List<Program> = listOf(
    Method(
        "gcd", Subject.EXPERT, w("PGCD (algorithme d'Euclide)", "GCD (Euclid's algorithm)"), "a = b·q + r",
        listOf(f("a", "a", "a", whole = true), f("b", "b", "b", whole = true))
    ) { v ->
        var a = big(v["a"]!!).abs(); var b = big(v["b"]!!).abs()
        val a0 = a; val b0 = b
        val out = mutableListOf<Line>()
        while (b.signum() != 0) {
            val (q, r) = a.divideAndRemainder(b)
            out += step("$a = $b × $q + $r")
            a = b; b = r
        }
        out += result(t("PGCD", "GCD") + " = $a")
        if (a.signum() != 0) out += result(t("PPCM", "LCM") + " = ${a0.multiply(b0).divide(a)}")
        out += step(if (a == BigInteger.ONE) t("Premiers entre eux", "Coprime") else t("Non premiers entre eux", "Not coprime"))
        out
    },
    Method(
        "bezout", Subject.EXPERT, w("Bézout (Euclide étendu)", "Bézout (extended Euclid)"), "a·u + b·v = PGCD(a, b)",
        listOf(f("a", "a", "a", whole = true), f("b", "b", "b", whole = true))
    ) { v ->
        val a = big(v["a"]!!); val b = big(v["b"]!!)
        var (r0, r1) = a to b
        var (s0, s1) = BigInteger.ONE to BigInteger.ZERO
        var (t0, t1) = BigInteger.ZERO to BigInteger.ONE
        val out = mutableListOf<Line>()
        while (r1.signum() != 0) {
            val q = r0.divide(r1)
            out += step("$r0 = $r1 × $q + ${r0 - q * r1}")
            r0 = r1.also { r1 = r0 - q * r1 }
            s0 = s1.also { s1 = s0 - q * s1 }
            t0 = t1.also { t1 = t0 - q * t1 }
        }
        out += result("$a × ($s0) + $b × ($t0) = $r0")
        out += result("u = $s0,  v = $t0")
        out
    },
    Method(
        "factor", Subject.EXPERT, w("Décomposition en facteurs premiers", "Prime factorisation"), "n = p₁^a₁ × p₂^a₂ × …",
        listOf(f("n", "n", "n", whole = true))
    ) { v ->
        var m = big(v["n"]!!).abs()
        require(m.signum() > 0 && m.bitLength() <= 62)
        val n0 = m
        val parts = mutableListOf<Pair<BigInteger, Int>>()
        var p = BigInteger.TWO
        while (p.multiply(p) <= m) {
            var e = 0
            while (m.mod(p).signum() == 0) { m = m.divide(p); e++ }
            if (e > 0) parts += p to e
            p = if (p == BigInteger.TWO) BigInteger.valueOf(3) else p.add(BigInteger.TWO)
        }
        if (m > BigInteger.ONE) parts += m to 1
        val count = parts.fold(1) { acc, (_, e) -> acc * (e + 1) }
        val divisors = if (count <= 64) divisorsOf(parts).sorted().joinToString("  ") else "$count"
        listOf(
            result("$n0 = " + parts.joinToString(" × ") { (q, e) -> if (e == 1) "$q" else "$q${superscript(e)}" }),
            step(if (parts.size == 1 && parts[0].second == 1) t("$n0 est premier", "$n0 is prime") else t("$count diviseurs : $divisors", "$count divisors: $divisors"))
        )
    },
    Method(
        "prime", Subject.EXPERT, w("Primalité", "Primality"), "n premier ?",
        listOf(f("n", "n", "n", whole = true))
    ) { v ->
        val m = big(v["n"]!!)
        val prime = m.isProbablePrime(64)
        listOf(result(if (prime) t("$m est premier", "$m is prime") else t("$m n'est pas premier", "$m is not prime")), step(t("Nombre premier suivant : ", "Next prime: ") + m.nextProbablePrime()))
    },
    Method(
        "congruence", Subject.EXPERT, w("Congruence a·x ≡ b [n]", "Congruence a·x ≡ b (mod n)"), "a·x ≡ b [n]",
        listOf(f("a", "a", "a", whole = true), f("b", "b", "b", whole = true), f("n", "n", "n", whole = true))
    ) { v ->
        val a = big(v["a"]!!); val b = big(v["b"]!!); val m = big(v["n"]!!)
        require(m.signum() > 0)
        val g = a.gcd(m)
        if (b.mod(g).signum() != 0) return@Method listOf(result(t("Pas de solution : PGCD(a, n) = $g ne divise pas b", "No solution: GCD(a, n) = $g does not divide b")))
        val m2 = m.divide(g)
        val inv = a.divide(g).mod(m2).modInverse(m2)
        val x = b.divide(g).multiply(inv).mod(m2)
        listOf(
            step(t("PGCD(a, n) = $g ; inverse de ${a.divide(g).mod(m2)} modulo $m2 : $inv", "GCD(a, n) = $g; inverse of ${a.divide(g).mod(m2)} modulo $m2: $inv")),
            result("x ≡ $x [$m2]"),
            step(t("Solutions dans [0 ; ${m - BigInteger.ONE}] : ", "Solutions in [0, ${m - BigInteger.ONE}]: ") + (0 until g.toInt().coerceAtMost(20)).joinToString("  ") { (x + m2 * BigInteger.valueOf(it.toLong())).toString() })
        )
    },
    Method(
        "diophantine", Subject.EXPERT, w("Équation a·x + b·y = c", "Equation a·x + b·y = c"), "a·x + b·y = c",
        listOf(f("a", "a", "a", whole = true), f("b", "b", "b", whole = true), f("c", "c", "c", whole = true))
    ) { v ->
        val a = big(v["a"]!!); val b = big(v["b"]!!); val c = big(v["c"]!!)
        val g = a.gcd(b)
        require(g.signum() != 0)
        if (c.mod(g).signum() != 0) return@Method listOf(result(t("Pas de solution : PGCD(a, b) = $g ne divise pas c", "No solution: GCD(a, b) = $g does not divide c")))
        val (u, w2) = bezout(a, b)
        val k = c.divide(g)
        val x0 = u.multiply(k); val y0 = w2.multiply(k)
        listOf(
            step("Bézout : $a × ($u) + $b × ($w2) = $g"),
            result(t("Solution particulière : ", "Particular solution: ") + "x = $x0, y = $y0"),
            result("x = $x0 + ${b.divide(g)}k,  y = $y0 − ${a.divide(g)}k  (k ∈ ℤ)")
        )
    },
    Method(
        "modpow", Subject.EXPERT, w("Puissance modulo n (Fermat)", "Power modulo n (Fermat)"), "aᵏ mod n",
        listOf(f("a", "a", "a", whole = true), f("k", "k", "k", whole = true), f("n", "n", "n", whole = true))
    ) { v ->
        val a = big(v["a"]!!); val k = big(v["k"]!!); val m = big(v["n"]!!)
        require(m.signum() > 0 && k.signum() >= 0)
        val out = mutableListOf(result("$a^$k ≡ ${a.modPow(k, m)} [$m]"))
        if (m.isProbablePrime(64) && a.mod(m).signum() != 0) out += step(t("$m premier, $a non multiple : $a^${m - BigInteger.ONE} ≡ 1 [$m] (petit théorème de Fermat)", "$m prime, $a not a multiple: $a^${m - BigInteger.ONE} ≡ 1 (mod $m) (Fermat's little theorem)"))
        out
    },
    Method(
        "complex", Subject.EXPERT, w("Nombre complexe : formes", "Complex number: forms"), "z = a + ib = r·e^(iθ)",
        listOf(f("a", "Partie réelle", "Real part"), f("b", "Partie imaginaire", "Imaginary part"))
    ) { v ->
        val a = v["a"]!!; val b = v["b"]!!
        val r = hypot(a, b); val th = atan2(b, a)
        val m2 = a * a + b * b
        listOf(
            result("|z| = ${n(r)}"),
            result("arg z = ${n(th)} rad = ${n(Math.toDegrees(th))}°"),
            result("z = ${n(r)}·e^(i·${n(th)})"),
            step("z̄ = ${n(a)} − ${n(b)}i"),
            step(if (m2 == 0.0) t("0 n'a pas d'inverse", "0 has no inverse") else "1/z = ${n(a / m2)} + ${n(-b / m2)}i")
        )
    },
    Method(
        "moivre", Subject.EXPERT, w("Puissance et racines n-ièmes (Moivre)", "Powers and n-th roots (de Moivre)"), "zⁿ = rⁿ·e^(i·nθ)",
        listOf(f("a", "Partie réelle", "Real part"), f("b", "Partie imaginaire", "Imaginary part"), f("n", "n", "n", whole = true))
    ) { v ->
        val a = v["a"]!!; val b = v["b"]!!; val k = v["n"]!!.toInt()
        require(k in 1..64)
        val r = hypot(a, b); val th = atan2(b, a)
        val rn = r.pow(k)
        val out = mutableListOf(result("zⁿ = ${n(rn * cos(k * th))} + ${n(rn * sin(k * th))}i"))
        val root = r.pow(1.0 / k)
        out += step(t("Racines n-ièmes :", "n-th roots:"))
        for (j in 0 until k.coerceAtMost(12)) {
            val ang = (th + 2 * PI * j) / k
            out += step("z$j = ${n(root * cos(ang))} + ${n(root * sin(ang))}i")
        }
        out
    },
    Method(
        "unity", Subject.EXPERT, w("Racines n-ièmes de l'unité", "Roots of unity"), "e^(2ikπ/n), k = 0 … n − 1",
        listOf(f("n", "n", "n", whole = true))
    ) { v ->
        val k = v["n"]!!.toInt()
        require(k in 1..24)
        (0 until k).map { j -> step("e^(2i·$j·π/$k) = ${n(cos(2 * PI * j / k))} + ${n(sin(2 * PI * j / k))}i") } +
            result(if (k == 1) t("Somme = 1", "Sum = 1") else t("Somme = 0", "Sum = 0"))
    },
    Method(
        "matrix2", Subject.EXPERT, w("Matrice 2×2 : déterminant, inverse, puissance", "2×2 matrix: determinant, inverse, power"), "A = (a b ; c d)",
        listOf(f("a", "a", "a"), f("b", "b", "b"), f("c", "c", "c"), f("d", "d", "d"), f("n", "Puissance n", "Power n", whole = true))
    ) { v ->
        val m = arrayOf(doubleArrayOf(v["a"]!!, v["b"]!!), doubleArrayOf(v["c"]!!, v["d"]!!))
        matrixLines(m, v["n"]!!.toInt())
    },
    Method(
        "matrix3", Subject.EXPERT, w("Matrice 3×3 : déterminant, inverse, puissance", "3×3 matrix: determinant, inverse, power"), "A = (a b c ; d e f ; g h i)",
        listOf(f("a", "a", "a"), f("b", "b", "b"), f("c", "c", "c"), f("d", "d", "d"), f("e", "e", "e"), f("f", "f", "f"), f("g", "g", "g"), f("h", "h", "h"), f("i", "i", "i"), f("n", "Puissance n", "Power n", whole = true))
    ) { v ->
        val m = arrayOf(doubleArrayOf(v["a"]!!, v["b"]!!, v["c"]!!), doubleArrayOf(v["d"]!!, v["e"]!!, v["f"]!!), doubleArrayOf(v["g"]!!, v["h"]!!, v["i"]!!))
        matrixLines(m, v["n"]!!.toInt())
    },
    Method(
        "system2", Subject.EXPERT, w("Système 2×2", "2×2 linear system"), "a·x + b·y = e ;  c·x + d·y = f",
        listOf(f("a", "a", "a"), f("b", "b", "b"), f("e", "e", "e"), f("c", "c", "c"), f("d", "d", "d"), f("f", "f", "f"))
    ) { v ->
        val a = v["a"]!!; val b = v["b"]!!; val e = v["e"]!!; val c = v["c"]!!; val d = v["d"]!!; val ff = v["f"]!!
        val det = a * d - b * c
        if (det == 0.0) listOf(step("det = 0"), result(t("Pas de solution unique", "No single solution")))
        else listOf(step("det = ad − bc = ${n(det)}"), result("x = ${n((e * d - b * ff) / det)}"), result("y = ${n((a * ff - e * c) / det)}"))
    },
)

private fun bezout(a: BigInteger, b: BigInteger): Pair<BigInteger, BigInteger> {
    var (r0, r1) = a to b
    var (s0, s1) = BigInteger.ONE to BigInteger.ZERO
    var (t0, t1) = BigInteger.ZERO to BigInteger.ONE
    while (r1.signum() != 0) {
        val q = r0.divide(r1)
        r0 = r1.also { r1 = r0 - q * r1 }
        s0 = s1.also { s1 = s0 - q * s1 }
        t0 = t1.also { t1 = t0 - q * t1 }
    }
    return if (r0.signum() < 0) s0.negate() to t0.negate() else s0 to t0
}

private fun divisorsOf(parts: List<Pair<BigInteger, Int>>): List<BigInteger> {
    var out = listOf(BigInteger.ONE)
    for ((p, e) in parts) out = out.flatMap { d -> (0..e).map { d.multiply(p.pow(it)) } }
    return out
}

private fun superscript(e: Int) = e.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

private fun matrixLines(m: Array<DoubleArray>, power: Int): List<Line> {
    require(power in 0..1000)
    val a = org.hipparchus.linear.MatrixUtils.createRealMatrix(m)
    val det = org.hipparchus.linear.LUDecomposition(a).determinant
    fun show(x: org.hipparchus.linear.RealMatrix) = (0 until x.rowDimension).joinToString("   ") { r -> "(" + x.getRow(r).joinToString("  ") { n(clean(it)) } + ")" }
    val out = mutableListOf(result("det A = ${n(clean(det))}"))
    out += if (abs(det) < 1e-12) step(t("A n'est pas inversible", "A is not invertible")) else result("A⁻¹ = " + show(org.hipparchus.linear.MatrixUtils.inverse(a)))
    out += result("Aⁿ = " + show(a.power(power)))
    return out
}

/** Rounding noise of a double removed (1e-16 is 0, 2.9999999999999996 is 3). */
private fun clean(x: Double): Double {
    val r = Math.rint(x)
    return if (abs(x - r) < 1e-9 * maxOf(1.0, abs(x))) r else x
}

/** Every program, by subject. */
val programs: List<Program> by lazy { maths + sequences + symbolic + formal + analysis + integrals + integralsMore + approximations + laws + probability + statistics + geometry + vectors + expert + graphs + algebra + physics + chemistry }
