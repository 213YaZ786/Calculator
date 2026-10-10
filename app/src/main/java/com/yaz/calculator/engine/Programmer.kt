package com.yaz.calculator.engine

/** The bases a programmer reads a value in. */
enum class Base(val radix: Int, val label: String) { HEX(16, "HEX"), DEC(10, "DEC"), OCT(8, "OCT"), BIN(2, "BIN") }

/**
 * A programmer's expression, as Windows Calculator's Programmer mode keeps
 * it: 64-bit integers in two's complement (QWORD), each number typed in the
 * chosen base and shown again in any other, the operators of C with their
 * precedence: NOT, then × ÷ % (remainder), + −, ≪ ≫, AND, XOR, OR.
 */
class ProgExpr(val tokens: List<Tok> = emptyList()) {

    sealed interface Tok {
        /** A number as typed: its bits, unsigned while typed. */
        data class Num(val bits: Long) : Tok
        data class Op(val op: String) : Tok
        data object Open : Tok
        data object Close : Tok
    }

    /** A digit in [base]; null when the number would pass 64 bits. */
    fun digit(d: Int, base: Base): ProgExpr? {
        if (d >= base.radix) return null
        val last = tokens.lastOrNull()
        if (last is Tok.Num) {
            val u = last.bits.toULong()
            val max = ULong.MAX_VALUE
            if (u > (max - d.toULong()) / base.radix.toULong()) return null
            return ProgExpr(tokens.dropLast(1) + Tok.Num((u * base.radix.toULong() + d.toULong()).toLong()))
        }
        if (last == Tok.Close) return ProgExpr(tokens + Tok.Op("×") + Tok.Num(d.toLong()))
        return ProgExpr(tokens + Tok.Num(d.toLong()))
    }

    /** A binary operator; it replaces one just typed, and waits for a value before it at the start (except NOT). */
    fun operator(op: String): ProgExpr? {
        val last = tokens.lastOrNull()
        if (op == NOT) {
            if (last is Tok.Num || last == Tok.Close) return ProgExpr(tokens + Tok.Op("×") + Tok.Op(NOT))
            return ProgExpr(tokens + Tok.Op(NOT))
        }
        return when {
            // A minus where a value starts is its sign.
            (last == null || last == Tok.Open) && op == "−" -> ProgExpr(tokens + Tok.Op(op))
            last == null || last == Tok.Open -> null
            last is Tok.Op -> if (last.op == NOT) null else ProgExpr(tokens.dropLast(1) + Tok.Op(op))
            else -> ProgExpr(tokens + Tok.Op(op))
        }
    }

    /** "( )": opens, or closes when a parenthesis is open and a value ends the line. */
    fun parenthesis(): ProgExpr {
        val open = tokens.count { it == Tok.Open } - tokens.count { it == Tok.Close }
        val last = tokens.lastOrNull()
        val valueEnds = last is Tok.Num || last == Tok.Close
        return when {
            open > 0 && valueEnds -> ProgExpr(tokens + Tok.Close)
            valueEnds -> ProgExpr(tokens + Tok.Op("×") + Tok.Open)
            else -> ProgExpr(tokens + Tok.Open)
        }
    }

    /** The last digit, or the last sign, goes. */
    fun backspace(base: Base): ProgExpr? {
        val last = tokens.lastOrNull() ?: return null
        if (last is Tok.Num) {
            val u = last.bits.toULong() / base.radix.toULong()
            val digits = java.lang.Long.toUnsignedString(last.bits, base.radix).length
            return ProgExpr(if (digits <= 1) tokens.dropLast(1) else tokens.dropLast(1) + Tok.Num(u.toLong()))
        }
        return ProgExpr(tokens.dropLast(1))
    }

    /** The value, parentheses left open closing at the end and trailing operators waiting; null when nothing is worth it yet. */
    fun value(): Long? {
        var end = tokens.size
        while (end > 0 && tokens[end - 1].let { it is Tok.Op || it == Tok.Open }) end--
        if (end == 0) return null
        return Reader(tokens.subList(0, end)).read()
    }

    /** The expression as written in [base]: FF AND 3C. */
    fun written(base: Base): String = buildString {
        for (t in tokens) {
            when (t) {
                is Tok.Num -> { if (isNotEmpty() && !endsWith("(") && !endsWith(" ")) append(' '); append(show(t.bits, base, grouped = false)) }
                is Tok.Op -> { if (t.op == NOT) { if (isNotEmpty() && !endsWith("(")) append(' '); append(NOT).append(' ') } else append(' ').append(t.op).append(' ') }
                Tok.Open -> { if (isNotEmpty() && !endsWith(" ")) append(' '); append('(') }
                Tok.Close -> append(')')
            }
        }
    }.replace("  ", " ").trim()

    private class Reader(val t: List<Tok>) {
        var i = 0
        fun read(): Long {
            val v = or()
            if (i != t.size) throw CalcException(CalcError.SYNTAX)
            return v
        }
        private fun binary(next: () -> Long, ops: Set<String>, apply: (String, Long, Long) -> Long): Long {
            var v = next()
            while (i < t.size) {
                val op = (t[i] as? Tok.Op)?.op?.takeIf { it in ops } ?: break
                i++
                v = apply(op, v, next())
            }
            return v
        }
        fun or(): Long = binary(::xor, setOf(OR)) { _, a, b -> a or b }
        fun xor(): Long = binary(::and, setOf(XOR)) { _, a, b -> a xor b }
        fun and(): Long = binary(::shift, setOf(AND)) { _, a, b -> a and b }
        fun shift(): Long = binary(::sum, setOf(SHL, SHR)) { op, a, b ->
            val n = b.coerceIn(0, 64).toInt()
            if (n >= 64) (if (op == SHR && a < 0) -1 else 0) else if (op == SHL) a shl n else a shr n
        }
        fun sum(): Long = binary(::product, setOf("+", "−")) { op, a, b -> if (op == "+") a + b else a - b }
        fun product(): Long = binary(::unary, setOf("×", "÷", "%")) { op, a, b ->
            when (op) {
                "×" -> a * b
                else -> {
                    if (b == 0L) throw CalcException(CalcError.DIVIDE_BY_ZERO)
                    if (op == "÷") a / b else a % b
                }
            }
        }
        fun unary(): Long {
            val tok = t.getOrNull(i) ?: throw CalcException(CalcError.SYNTAX)
            if (tok is Tok.Op && tok.op == NOT) { i++; return unary().inv() }
            if (tok is Tok.Op && tok.op == "−") { i++; return -unary() }
            return atom()
        }
        fun atom(): Long {
            return when (val tok = t.getOrNull(i++) ?: throw CalcException(CalcError.SYNTAX)) {
                is Tok.Num -> tok.bits
                Tok.Open -> {
                    val v = or()
                    if (i < t.size && t[i] == Tok.Close) i++
                    v
                }
                else -> throw CalcException(CalcError.SYNTAX)
            }
        }
    }

    companion object {
        const val AND = "AND"
        const val OR = "OR"
        const val XOR = "XOR"
        const val NOT = "NOT"
        const val SHL = "≪"
        const val SHR = "≫"

        /** [bits] in [base]: decimal signed, the others as the 64 bits are; binary in groups of four when [grouped]. */
        fun show(bits: Long, base: Base, grouped: Boolean = true): String {
            if (base == Base.DEC) return bits.toString().replace('-', Sym.MINUS)
            val digits = java.lang.Long.toUnsignedString(bits, base.radix).uppercase()
            if (!grouped || base != Base.BIN) return digits
            val padded = digits.padStart((digits.length + 3) / 4 * 4, '0')
            return padded.chunked(4).joinToString(" ")
        }
    }
}
