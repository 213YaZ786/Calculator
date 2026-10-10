package com.yaz.calculator.engine

import java.math.BigInteger

/** Why an expression has no value. */
enum class CalcError { SYNTAX, DIVIDE_BY_ZERO, DOMAIN, TOO_LARGE, TIMEOUT }

class CalcException(val error: CalcError) : Exception(error.name)

/**
 * The characters of an expression as the app keeps it. The screen shows
 * them in the user's language (decimal comma, grouping), the app keeps
 * them in this one form.
 */
object Sym {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val POWER = '^'
    const val SQRT = '√'
    const val PI = 'π'
    const val E = 'e'
    const val FACT = '!'
    const val PERCENT = '%'
    const val SQUARE = '²'
    const val POINT = '.'
    const val EXP = 'E'
    const val OPEN = '('
    const val CLOSE = ')'

    /** Functions, each with its opening parenthesis; the longest first, so sin⁻¹( is not read as sin. */
    val FUNCTIONS = listOf("sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "ln(", "log(", "exp(")

    val BINARY = setOf(PLUS, MINUS, TIMES, DIVIDE, POWER)

    /** A stored result standing for its exact value: one character of Unicode's private area. */
    fun isRef(c: Char): Boolean = c in ''..''

    /** The function that starts at [index] of [text], if one does. */
    fun functionAt(text: CharSequence, index: Int): String? =
        FUNCTIONS.firstOrNull { f -> index + f.length <= text.length && (f.indices).all { text[index + it] == f[it] } }
}

internal sealed interface Token

/** A value: a number typed ([literal]), a stored result, a variable, π or e. */
internal class Num(val value: UnifiedReal, val literal: Boolean) : Token

internal class Op(val c: Char) : Token

/** A function, its parenthesis opened. */
internal class Fn(val name: String) : Token

/**
 * Reads and computes an expression exactly, with the meaning ExactCalculator
 * (AOSP, LineageOS) gives it: implicit multiplication (2π, 3(4)), 100+10% is
 * 110, parentheses left open close at the end, trailing operators wait.
 * [ref] gives the exact value of a stored result, [variable] a sheet's names.
 */
class Calc(
    private val ref: (Char) -> UnifiedReal? = { null },
    private val variable: (String) -> UnifiedReal? = { null }
) {

    /** The value a name stands for, as the caller defines it. */
    fun variableOf(name: String): UnifiedReal? = variable(name)

    /** The exact value of [text], or a CalcException. Slow cases are cut by the caller's thread. */
    fun evaluate(text: CharSequence, degrees: Boolean): UnifiedReal {
        val tokens = tokens(text)
        val end = significantEnd(tokens)
        if (end == 0) throw CalcException(CalcError.SYNTAX)
        return Parser(tokens, end, degrees).parse()
    }

    /** Whether [text] is worth a result under it: more than a number on its own. */
    fun interesting(text: CharSequence): Boolean {
        val tokens = runCatching { tokens(text) }.getOrNull() ?: return false
        val end = significantEnd(tokens)
        var first = 0
        if (end > 0 && (tokens[0] as? Op)?.c == Sym.MINUS) first = 1
        for (i in first until end) {
            val t = tokens[i]
            if (t is Op || t is Fn || t is Num && !t.literal) return true
        }
        return false
    }

    /** Where the expression ends once the operators still waiting for their right side are left out. */
    private fun significantEnd(tokens: List<Token>): Int {
        var end = tokens.size
        while (end > 0) {
            val last = tokens[end - 1] as? Op ?: break
            if (last.c !in Sym.BINARY && last.c != Sym.SQRT) break
            end--
        }
        return end
    }

    internal fun tokens(text: CharSequence): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == Sym.POINT -> {
                    val start = i
                    while (i < text.length && (text[i].isDigit() || text[i] == Sym.POINT)) i++
                    // A result written with its power of ten: 1.5E−12.
                    if (i < text.length && text[i] == Sym.EXP) {
                        var j = i + 1
                        if (j < text.length && (text[j] == Sym.MINUS || text[j] == Sym.PLUS)) j++
                        if (j < text.length && text[j].isDigit()) {
                            while (j < text.length && text[j].isDigit()) j++
                            i = j
                        }
                    }
                    out += Num(literal(text.substring(start, i)), literal = true)
                }
                Sym.isRef(c) -> {
                    out += Num(ref(c) ?: throw CalcException(CalcError.SYNTAX), literal = true)
                    i++
                }
                c == Sym.PI -> { out += Num(UnifiedReal.PI, literal = false); i++ }
                else -> {
                    val function = Sym.functionAt(text, i)
                    if (function != null) {
                        out += Fn(function)
                        i += function.length
                    } else if (c.isLetter() || c == '_') {
                        val start = i
                        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                        val name = text.substring(start, i)
                        val value = variable(name)
                        out += when {
                            value != null -> Num(value, literal = true)
                            name == "e" -> Num(UnifiedReal.E, literal = false)
                            else -> throw CalcException(CalcError.SYNTAX)
                        }
                    } else if (c in OPERATORS) {
                        out += Op(c)
                        i++
                    } else {
                        throw CalcException(CalcError.SYNTAX)
                    }
                }
            }
        }
        return out
    }

    private class Parser(private val t: List<Token>, private val end: Int, private val degrees: Boolean) {
        private var pos = 0

        fun parse(): UnifiedReal {
            val value = expr()
            if (pos != end) throw CalcException(CalcError.SYNTAX)
            return value
        }

        private fun isOp(at: Int, c: Char) = at < end && (t[at] as? Op)?.c == c

        private fun unary(): UnifiedReal {
            if (pos >= end) throw CalcException(CalcError.SYNTAX)
            return when (val token = t[pos]) {
                is Num -> { pos++; token.value }
                is Fn -> {
                    pos++
                    val arg = expr()
                    if (isOp(pos, Sym.CLOSE)) pos++
                    apply(token.name, arg)
                }
                is Op -> when (token.c) {
                    Sym.SQRT -> {
                        pos++
                        if (isOp(pos, Sym.MINUS)) {
                            pos++
                            unary().negate().sqrt()
                        } else {
                            unary().sqrt()
                        }
                    }
                    Sym.OPEN -> {
                        pos++
                        val inner = expr()
                        if (isOp(pos, Sym.CLOSE)) pos++
                        inner
                    }
                    else -> throw CalcException(CalcError.SYNTAX)
                }
            }
        }

        private fun suffix(): UnifiedReal {
            var value = unary()
            while (true) {
                value = when {
                    isOp(pos, Sym.FACT) -> factorial(value)
                    isOp(pos, Sym.SQUARE) -> value.multiply(value)
                    isOp(pos, Sym.PERCENT) -> value.multiply(HUNDREDTH)
                    else -> return value
                }
                pos++
            }
        }

        private fun factor(): UnifiedReal {
            var value = suffix()
            if (isOp(pos, Sym.POWER)) {
                pos++
                value = value.pow(signedFactor())
            }
            return value
        }

        private fun signedFactor(): UnifiedReal {
            val negative = isOp(pos, Sym.MINUS)
            if (negative) pos++
            val value = factor()
            return if (negative) value.negate() else value
        }

        /** What may follow a value with an implicit multiplication: 2π, 3(4), 2√2. */
        private fun canStartFactor(): Boolean {
            if (pos >= end) return false
            val op = t[pos] as? Op ?: return true
            return op.c !in Sym.BINARY && op.c != Sym.FACT && op.c != Sym.CLOSE &&
                op.c != Sym.PERCENT && op.c != Sym.SQUARE
        }

        private fun term(): UnifiedReal {
            var value = signedFactor()
            while (true) {
                val times = isOp(pos, Sym.TIMES)
                val divide = isOp(pos, Sym.DIVIDE)
                if (times || divide) pos++ else if (!canStartFactor()) return value
                val right = signedFactor()
                value = if (divide) value.divide(right) else value.multiply(right)
            }
        }

        /**
         * 200+10% is 220 and 200−10% is 180, as people mean it: a number
         * with % right after + or −, followed by nothing, + − or ).
         * Anything else keeps % as a hundredth.
         */
        private fun isPercent(at: Int): Boolean {
            if (at + 1 >= end || !isOp(at + 1, Sym.PERCENT)) return false
            val number = t[at] as? Num ?: return false
            if (!number.literal) return false
            if (at + 2 == end) return true
            val next = t[at + 2] as? Op ?: return false
            return next.c == Sym.PLUS || next.c == Sym.MINUS || next.c == Sym.CLOSE
        }

        private fun expr(): UnifiedReal {
            var value = term()
            while (true) {
                val plus = isOp(pos, Sym.PLUS)
                if (!plus && !isOp(pos, Sym.MINUS)) return value
                pos++
                if (isPercent(pos)) {
                    val n = (t[pos] as Num).value.multiply(HUNDREDTH)
                    value = value.multiply(UnifiedReal.ONE.add(if (plus) n else n.negate()))
                    pos += 2
                } else {
                    val right = term()
                    value = if (plus) value.add(right) else value.subtract(right)
                }
            }
        }

        private fun radians(x: UnifiedReal) = if (degrees) x.multiply(UnifiedReal.PI).divide(HALF_TURN) else x
        private fun fromRadians(x: UnifiedReal) = if (degrees) x.multiply(HALF_TURN).divide(UnifiedReal.PI) else x

        private fun apply(name: String, x: UnifiedReal): UnifiedReal = when (name) {
            "sin(" -> radians(x).sin()
            "cos(" -> radians(x).cos()
            "tan(" -> {
                val a = radians(x)
                // tan 90° has no value: say so, not a division by zero the user never typed.
                try {
                    a.sin().divide(a.cos())
                } catch (_: ArithmeticException) {
                    throw CalcException(CalcError.DOMAIN)
                }
            }
            "sin⁻¹(" -> fromRadians(x.asin())
            "cos⁻¹(" -> fromRadians(x.acos())
            "tan⁻¹(" -> fromRadians(x.atan())
            "ln(" -> x.ln()
            "log(" -> x.ln().divide(UnifiedReal.TEN.ln())
            "exp(" -> x.exp()
            else -> throw CalcException(CalcError.SYNTAX)
        }

        /** n! up to a size that still shows: past it, the digits alone would take minutes. */
        private fun factorial(x: UnifiedReal): UnifiedReal {
            val whole = x.bigIntegerValue() ?: x.crValue().get_appr(0)
            if (whole > MAX_FACTORIAL) throw CalcException(CalcError.TOO_LARGE)
            return x.fact()
        }
    }

    companion object {
        private val OPERATORS = setOf(
            Sym.PLUS, Sym.MINUS, Sym.TIMES, Sym.DIVIDE, Sym.POWER, Sym.SQRT,
            Sym.FACT, Sym.PERCENT, Sym.SQUARE, Sym.OPEN, Sym.CLOSE
        )
        private val HUNDREDTH: UnifiedReal = UnifiedReal(100).inverse()
        private val HALF_TURN = UnifiedReal(180)
        private val MAX_FACTORIAL = BigInteger.valueOf(5000)
        /** Powers of ten written in a number, beyond which nothing useful shows. */
        private const val MAX_EXPONENT = 100_000

        /** A number as written, exactly: 0.1 is one tenth, not the nearest binary fraction. */
        internal fun literal(text: String): UnifiedReal {
            val mantissa = text.substringBefore(Sym.EXP)
            val exponent = if (text.contains(Sym.EXP)) {
                text.substringAfter(Sym.EXP).replace(Sym.MINUS, '-').toIntOrNull()
                    ?.takeIf { it in -MAX_EXPONENT..MAX_EXPONENT } ?: throw CalcException(CalcError.TOO_LARGE)
            } else {
                0
            }
            if (mantissa.count { it == Sym.POINT } > 1) throw CalcException(CalcError.SYNTAX)
            val digits = mantissa.replace(Sym.POINT.toString(), "")
            if (digits.isEmpty()) throw CalcException(CalcError.SYNTAX)
            val scale = mantissa.length - mantissa.indexOf(Sym.POINT).let { if (it < 0) mantissa.length else it + 1 }
            var numerator = BigInteger(digits)
            var denominator = BigInteger.ONE
            val shift = exponent - scale
            if (shift >= 0) numerator = numerator.multiply(BigInteger.TEN.pow(shift)) else denominator = BigInteger.TEN.pow(-shift)
            return UnifiedReal(BoundedRational(numerator, denominator))
        }
    }
}
