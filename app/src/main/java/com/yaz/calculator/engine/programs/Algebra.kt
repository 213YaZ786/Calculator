package com.yaz.calculator.engine.programs

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import org.hipparchus.analysis.solvers.LaguerreSolver
import org.hipparchus.linear.Array2DRowRealMatrix
import org.hipparchus.linear.EigenDecompositionNonSymmetric
import org.hipparchus.linear.LUDecomposition

// Arithmetic and algebra beyond maths expertes (A-level Further Pure, IB HL,
// licence L1–L2), worked as a student writes them.

private fun bi(v: Long): BigInteger = BigInteger.valueOf(v)

/** The prime factors of [n] with their powers, by trial division (n below 10¹⁸). */
private fun factors(n: Long): List<Pair<Long, Int>> {
    var m = n
    val out = ArrayList<Pair<Long, Int>>()
    var p = 2L
    while (p * p <= m) {
        var e = 0
        while (m % p == 0L) { m /= p; e++ }
        if (e > 0) out += p to e
        p += if (p == 2L) 1 else 2
        require(p < 2_000_000_000L)
    }
    if (m > 1) out += m to 1
    return out
}

private fun complex(re: Double, im: Double): String = when {
    abs(im) < 1e-12 -> show(re)
    abs(re) < 1e-12 -> "${show(im)}i"
    im < 0 -> "${show(re)} − ${show(-im)}i"
    else -> "${show(re)} + ${show(im)}i"
}

/** A square matrix typed row by row, n² numbers. */
private fun square(values: List<Double>): Array<DoubleArray> {
    val n = sqrt(values.size.toDouble()).toInt()
    require(n in 1..6 && n * n == values.size)
    return Array(n) { i -> DoubleArray(n) { j -> values[i * n + j] } }
}

private fun row(r: DoubleArray) = "(" + r.joinToString(" ; ") { show(it) } + ")"

val algebra: List<Program> = listOf(
    Rich(
        "crt", Subject.EXPERT, title("Restes chinois", "Chinese remainder theorem"), "x ≡ aᵢ [nᵢ]",
        listOf(list("a", "Restes a₁, a₂, …", "Remainders a₁, a₂, …"), list("n", "Modules n₁, n₂, …", "Moduli n₁, n₂, …"))
    ) { v ->
        val a = v.list("a").map { Math.round(it) }; val n = v.list("n").map { Math.round(it) }
        require(a.size == n.size && a.isNotEmpty() && n.all { it > 1 })
        var x = BigInteger.ZERO; var m = BigInteger.ONE
        val out = mutableListOf<Line>()
        for (i in a.indices) {
            val ni = bi(n[i]); val ai = bi(a[i]).mod(ni)
            val g = m.gcd(ni)
            require(x.subtract(ai).mod(g).signum() == 0)
            // x + m·k ≡ aᵢ [nᵢ]: k = (aᵢ − x)/g · (m/g)⁻¹ mod nᵢ/g.
            val step = ni.divide(g)
            val k = ai.subtract(x).divide(g).multiply(m.divide(g).modInverse(step)).mod(step)
            x = x.add(m.multiply(k))
            m = m.divide(g).multiply(ni)
            x = x.mod(m)
            out += stp("x ≡ $x [$m]")
        }
        out.add(0, res("x ≡ $x [$m]"))
        out.add(1, res(say("Plus petite solution positive : $x", "Smallest positive solution: $x")))
        Outcome(out)
    },
    Rich(
        "euler-phi", Subject.EXPERT, title("Indicatrice d'Euler et diviseurs", "Euler's totient and divisors"), "φ(n) = n·Π(1 − 1/p)",
        listOf(num("n", "n", "n", whole = true))
    ) { v ->
        val n = v.long("n")
        require(n in 1..999_999_999_999L)
        val f = factors(n)
        var phi = n
        f.forEach { (p, _) -> phi = phi / p * (p - 1) }
        val count = f.fold(1L) { c, (_, e) -> c * (e + 1) }
        val sum = f.fold(BigInteger.ONE) { s, (p, e) -> s.multiply(bi(p).pow(e + 1).subtract(BigInteger.ONE).divide(bi(p - 1))) }
        val divisors = if (count <= 64) {
            var d = listOf(1L)
            f.forEach { (p, e) -> d = d.flatMap { x -> (0..e).map { k -> x * p.toDouble().pow(k).toLong() } } }
            d.sorted().joinToString("  ")
        } else null
        Outcome(listOfNotNull(
            res("φ($n) = $phi"),
            res(say("$count diviseurs, de somme σ = $sum", "$count divisors, summing to σ = $sum")),
            divisors?.let { stp(it) },
            stp("$n = " + f.joinToString(" × ") { (p, e) -> if (e == 1) "$p" else "$p^$e" })
        ))
    },
    Rich(
        "mod-inverse", Subject.EXPERT, title("Inverse modulo n", "Inverse modulo n"), "a·u ≡ 1 [n]",
        listOf(num("a", "a", "a", whole = true), num("n", "n", "n", whole = true))
    ) { v ->
        val a = bi(v.long("a")); val n = bi(v.long("n"))
        require(n > BigInteger.ONE)
        val g = a.gcd(n)
        if (g != BigInteger.ONE) return@Rich lines(res(say("PGCD(a, n) = $g ≠ 1 : pas d'inverse", "gcd(a, n) = $g ≠ 1: no inverse")))
        val u = a.modInverse(n)
        lines(res("${a}⁻¹ ≡ $u [$n]"), stp("$a × $u = ${a.multiply(u)} = ${a.multiply(u).divide(n)} × $n + 1"))
    },
    Rich(
        "rsa", Subject.EXPERT, title("Chiffrement RSA", "RSA encryption"), "c ≡ mᵉ [n], m ≡ cᵈ [n]",
        listOf(num("p", "Premier p", "Prime p", whole = true), num("q", "Premier q", "Prime q", whole = true), num("e", "Exposant e", "Exponent e", whole = true), num("m", "Message m < n", "Message m < n", whole = true))
    ) { v ->
        val p = bi(v.long("p")); val q = bi(v.long("q")); val e = bi(v.long("e")); val m = bi(v.long("m"))
        require(p.isProbablePrime(40) && q.isProbablePrime(40) && p != q)
        val n = p.multiply(q)
        val phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE))
        if (e.gcd(phi) != BigInteger.ONE) return@Rich lines(res(say("e doit être premier avec φ(n) = $phi", "e must be coprime with φ(n) = $phi")))
        require(m.signum() >= 0 && m < n)
        val d = e.modInverse(phi)
        val c = m.modPow(e, n)
        lines(
            res("n = pq = $n   φ(n) = $phi"),
            res(say("Clé privée d = e⁻¹ mod φ(n) = $d", "Private key d = e⁻¹ mod φ(n) = $d")),
            res(say("Chiffré c = mᵉ mod n = $c", "Ciphertext c = mᵉ mod n = $c")),
            stp(say("Déchiffré cᵈ mod n = ${c.modPow(d, n)}", "Decrypted cᵈ mod n = ${c.modPow(d, n)}"))
        )
    },
    Rich(
        "fermat-wilson", Subject.EXPERT, title("Fermat et Wilson", "Fermat and Wilson"), "aᵖ⁻¹ ≡ 1 [p], (p − 1)! ≡ −1 [p]",
        listOf(num("p", "p", "p", whole = true))
    ) { v ->
        val p = v.long("p")
        require(p in 2..10_000_000L)
        val bp = bi(p)
        var fact = BigInteger.ONE
        for (k in 2 until p) fact = fact.multiply(bi(k)).mod(bp)
        val wilson = fact == bp.subtract(BigInteger.ONE)
        val fermat = listOf(2L, 3L, 5L, 7L).filter { it % p != 0L }.map { a -> a to bi(a).modPow(bp.subtract(BigInteger.ONE), bp) }
        lines(
            res(if (wilson) say("(p − 1)! ≡ −1 [p] : $p est premier (Wilson)", "(p − 1)! ≡ −1 (mod p): $p is prime (Wilson)") else say("(p − 1)! ≡ $fact [p] : $p n'est pas premier", "(p − 1)! ≡ $fact (mod p): $p is not prime")),
            stp(fermat.joinToString("   ") { (a, r) -> "$a^(p−1) ≡ $r" })
        )
    },
    Rich(
        "cubic", Subject.EXPERT, title("Équation du troisième degré", "Cubic equation"), "ax³ + bx² + cx + d = 0",
        listOf(num("a", "a", "a"), num("b", "b", "b"), num("c", "c", "c"), num("d", "d", "d"))
    ) { v ->
        val a = v["a"]; val b = v["b"]; val c = v["c"]; val d = v["d"]
        require(a != 0.0)
        val roots = LaguerreSolver(1e-14, 1e-14).solveAllComplex(doubleArrayOf(d, c, b, a), 0.0)
        val out = mutableListOf<Line>()
        roots.sortedBy { it.real }.forEachIndexed { i, z -> out += res("x${"₁₂₃"[i]} = ${complex(z.real, z.imaginary)}") }
        out += stp(say("Viète : x₁ + x₂ + x₃ = −b/a = ${show(-b / a)},  x₁x₂ + x₁x₃ + x₂x₃ = c/a = ${show(c / a)},  x₁x₂x₃ = −d/a = ${show(-d / a)}", "Vieta: x₁ + x₂ + x₃ = −b/a = ${show(-b / a)},  x₁x₂ + x₁x₃ + x₂x₃ = c/a = ${show(c / a)},  x₁x₂x₃ = −d/a = ${show(-d / a)}"))
        val disc = 18 * a * b * c * d - 4 * b.pow(3) * d + b * b * c * c - 4 * a * c.pow(3) - 27 * a * a * d * d
        out += stp("Δ = ${show(disc)} : " + when { disc > 0 -> say("trois racines réelles distinctes", "three distinct real roots"); disc == 0.0 -> say("une racine multiple", "a repeated root"); else -> say("une racine réelle, deux complexes conjuguées", "one real root, two complex conjugates") })
        Outcome(out)
    },
    Rich(
        "gauss", Subject.EXPERT, title("Système n × n (Gauss)", "n × n system (Gauss)"), "AX = B",
        listOf(list("A", "A, ligne après ligne (n² nombres)", "A, row by row (n² numbers)"), list("B", "B (n nombres)", "B (n numbers)"))
    ) { v ->
        val m = square(v.list("A")); val b = v.list("B")
        val n = m.size
        require(b.size == n)
        // Elimination with partial pivoting, each pivot written down.
        val a = Array(n) { i -> m[i].copyOf() + b[i] }
        val out = mutableListOf<Line>()
        for (col in 0 until n) {
            val p = (col until n).maxBy { abs(a[it][col]) }
            if (abs(a[p][col]) < 1e-12) return@Rich lines(res(say("Système non inversible (det A = 0)", "Singular system (det A = 0)")))
            if (p != col) { val tmp = a[p]; a[p] = a[col]; a[col] = tmp; out += stp("L${col + 1} ↔ L${p + 1}") }
            for (r in col + 1 until n) {
                val k = a[r][col] / a[col][col]
                if (k == 0.0) continue
                for (j in col..n) a[r][j] -= k * a[col][j]
                out += stp("L${r + 1} ← L${r + 1} − ${show(k)}·L${col + 1}")
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) x[i] = (a[i][n] - (i + 1 until n).sumOf { a[i][it] * x[it] }) / a[i][i]
        val det = LUDecomposition(Array2DRowRealMatrix(m)).determinant
        val top = mutableListOf(res("X = ${row(x)}"), res("det A = ${show(det)}"))
        if (n <= 4) {
            val inv = LUDecomposition(Array2DRowRealMatrix(m)).solver.inverse
            top += stp("A⁻¹ = " + (0 until n).joinToString("  ") { row(inv.getRow(it)) })
        }
        Outcome(top + out.take(12))
    },
    Rich(
        "eigen", Subject.EXPERT, title("Valeurs et vecteurs propres", "Eigenvalues and eigenvectors"), "A·v = λ·v",
        listOf(list("A", "A, ligne après ligne (4 ou 9 nombres)", "A, row by row (4 or 9 numbers)"))
    ) { v ->
        val m = square(v.list("A"))
        require(m.size in 2..3)
        val n = m.size
        val e = EigenDecompositionNonSymmetric(Array2DRowRealMatrix(m))
        val out = mutableListOf<Line>()
        // The characteristic polynomial: λ² − tr·λ + det, or λ³ − tr·λ² + s₂·λ − det.
        val tr = (0 until n).sumOf { m[it][it] }
        val det = LUDecomposition(Array2DRowRealMatrix(m)).determinant
        if (n == 2) out += stp("χ(λ) = λ² − ${show(tr)}λ + ${show(det)}")
        else {
            val s2 = m[0][0] * m[1][1] - m[0][1] * m[1][0] + m[0][0] * m[2][2] - m[0][2] * m[2][0] + m[1][1] * m[2][2] - m[1][2] * m[2][1]
            out += stp("χ(λ) = λ³ − ${show(tr)}λ² + ${show(s2)}λ − ${show(det)}")
        }
        e.eigenvalues.forEachIndexed { i, z ->
            out += res("λ${"₁₂₃"[i]} = ${complex(z.real, z.imaginary)}")
            if (abs(z.imaginary) < 1e-12) {
                val vec = e.getEigenvector(i)
                val re = DoubleArray(n) { vec.getEntry(it).real }
                val big = re.maxBy { abs(it) }
                if (big != 0.0) out += stp("v${"₁₂₃"[i]} = ${row(DoubleArray(n) { re[it] / big })}")
            }
        }
        Outcome(out.sortedByDescending { it.result })
    },
    Rich(
        "linear-recurrence", Subject.EXPERT, title("Récurrence linéaire d'ordre 2", "Second-order linear recurrence"), "uₙ₊₂ = a·uₙ₊₁ + b·uₙ",
        listOf(num("a", "a", "a"), num("b", "b", "b"), num("u₀", "u₀", "u₀"), num("u₁", "u₁", "u₁"), num("n", "Rang", "Index", whole = true))
    ) { v ->
        val a = v["a"]; val b = v["b"]; val k = v.int("n")
        require(k in 0..100_000)
        var u0 = v["u₀"]; var u1 = v["u₁"]
        repeat(k) { val u2 = a * u1 + b * u0; u0 = u1; u1 = u2 }
        val d = a * a + 4 * b
        val out = mutableListOf(res("uₙ = ${show(u0)}"), stp(say("Équation caractéristique r² = ${show(a)}r + ${show(b)}, Δ = ${show(d)}", "Characteristic equation r² = ${show(a)}r + ${show(b)}, Δ = ${show(d)}")))
        out += stp(when {
            d > 0 -> "r₁ = ${show((a - sqrt(d)) / 2)}, r₂ = ${show((a + sqrt(d)) / 2)} : uₙ = λr₁ⁿ + μr₂ⁿ"
            d == 0.0 -> "r = ${show(a / 2)} : uₙ = (λ + μn)rⁿ"
            else -> "r = ${complex(a / 2, sqrt(-d) / 2)} : uₙ = ρⁿ(λcos nθ + μsin nθ), ρ = ${show(sqrt(-b))}"
        })
        Outcome(out)
    }
)
