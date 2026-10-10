package com.yaz.calculator.engine.tibasic

import kotlin.math.PI
import kotlin.math.E

/**
 * A TI-Basic interpreter, written from TI's own programming guide
 * (TI-Prog82Guide): Prompt, Input, Disp, Output(, ClrHome, Pause, If,
 * Then, Else, End, For(, While, Repeat, Lbl, Goto, Menu(, Stop, Return,
 * DelVar, → into A–Z and θ, Ans. Numbers are doubles, as on the TI.
 *
 * It reaches nothing but [io]: no file, no network, no other program;
 * it stops after [maxSteps] statements, and when [io] says to stop.
 */
class Interpreter(source: String, private val io: Io, private val degrees: Boolean, private val maxSteps: Long = 1_000_000) {

    /** What a program may do outside itself: show lines and ask the user. */
    interface Io {
        fun show(line: String)
        fun clear()
        suspend fun ask(prompt: String): Double
        suspend fun choose(title: String, options: List<String>): Int
        suspend fun pause(line: String?)
        fun stopped(): Boolean
    }

    class Failure(message: String) : Exception(message)

    private val statements: List<String> = split(source)
    private val vars = HashMap<String, Double>()
    private var ans = 0.0

    /** Where each block's Else and End are, as the TI finds them. */
    private val ends = IntArray(statements.size) { -1 }
    private val elses = IntArray(statements.size) { -1 }
    private val opens = IntArray(statements.size) { -1 }

    init {
        val stack = ArrayDeque<Int>()
        statements.forEachIndexed { i, s ->
            when {
                s.startsWith("For(") || s.startsWith("While ") || s.startsWith("Repeat ") -> stack.addLast(i)
                s == "Then" -> stack.addLast(i)
                s == "Else" -> stack.lastOrNull()?.let { elses[it] = i }
                s == "End" -> {
                    val open = stack.removeLastOrNull() ?: throw Failure("End without a block (line ${i + 1})")
                    ends[open] = i
                    opens[i] = open
                }
            }
        }
        if (stack.isNotEmpty()) throw Failure("A block has no End")
    }

    suspend fun run() {
        var pc = 0
        var steps = 0L
        val labels = statements.withIndex().filter { it.value.startsWith("Lbl ") }.associate { it.value.removePrefix("Lbl ").trim() to it.index }
        while (pc < statements.size) {
            if (++steps > maxSteps) throw Failure("Stopped after $maxSteps steps")
            if (io.stopped()) return
            val s = statements[pc]
            var next = pc + 1
            when {
                s.isEmpty() || s.startsWith("Lbl ") -> Unit
                s == "Stop" || s == "Return" -> return
                s == "ClrHome" || s == "ClrDraw" -> io.clear()
                s == "Disp" -> io.show("")
                s.startsWith("Disp ") -> for (part in args(s.removePrefix("Disp "))) io.show(text(part))
                s.startsWith("Output(") -> args(inside(s, "Output(")).let { a -> io.show(text(a.getOrElse(2) { a.lastOrNull() ?: "" })) }
                s.startsWith("Pause") -> io.pause(s.removePrefix("Pause").trim().takeIf { it.isNotEmpty() }?.let(::text))
                s.startsWith("Prompt ") -> for (v in args(s.removePrefix("Prompt "))) set(v.trim(), io.ask("${v.trim()}=?"))
                s.startsWith("Input") -> {
                    val a = args(s.removePrefix("Input").trim())
                    when (a.size) {
                        0 -> io.pause(null)
                        1 -> set(a[0].trim(), io.ask("?"))
                        else -> set(a[1].trim(), io.ask(text(a[0])))
                    }
                }
                s.startsWith("DelVar ") -> vars.remove(name(s.removePrefix("DelVar ").trim()))
                s.startsWith("Goto ") -> next = labels[s.removePrefix("Goto ").trim()] ?: throw Failure("No Lbl ${s.removePrefix("Goto ").trim()}")
                s.startsWith("Menu(") -> {
                    val a = args(inside(s, "Menu("))
                    val options = (1 until a.size step 2).map { text(a[it]) }
                    val chosen = io.choose(text(a[0]), options)
                    val label = a.getOrNull(2 + 2 * chosen)?.trim() ?: throw Failure("Menu( without its labels")
                    next = labels[label] ?: throw Failure("No Lbl $label")
                }
                s.startsWith("If ") -> {
                    val yes = value(s.removePrefix("If ")) != 0.0
                    val then = statements.getOrNull(pc + 1) == "Then"
                    next = when {
                        then && yes -> pc + 2
                        then -> (elses[pc + 1].takeIf { it >= 0 }?.plus(1)) ?: (ends[pc + 1] + 1)
                        yes -> pc + 1
                        else -> pc + 2
                    }
                }
                s == "Then" -> Unit
                s == "Else" -> {
                    // Reached from the Then part: on past the End.
                    val then = (0 until pc).last { elses[it] == pc }
                    next = ends[then] + 1
                }
                s.startsWith("For(") -> {
                    val a = args(inside(s, "For("))
                    val v = a[0].trim()
                    val from = value(a[1]); val to = value(a[2]); val step = if (a.size > 3) value(a[3]) else 1.0
                    if (step == 0.0) throw Failure("For( with a step of 0")
                    set(v, from)
                    if (if (step > 0) from > to else from < to) next = ends[pc] + 1
                }
                s.startsWith("While ") -> if (value(s.removePrefix("While ")) == 0.0) next = ends[pc] + 1
                s.startsWith("Repeat ") -> Unit
                s == "End" -> {
                    val open = opens[pc]
                    val head = statements[open]
                    when {
                        head.startsWith("For(") -> {
                            val a = args(inside(head, "For("))
                            val v = a[0].trim()
                            val to = value(a[2]); val step = if (a.size > 3) value(a[3]) else 1.0
                            val now = get(v) + step
                            set(v, now)
                            if (if (step > 0) now <= to else now >= to) next = open + 1
                        }
                        head.startsWith("While ") -> next = open
                        head.startsWith("Repeat ") -> if (value(head.removePrefix("Repeat ")) == 0.0) next = open + 1
                    }
                }
                s.contains('→') -> {
                    val (e, target) = s.split('→', limit = 2)
                    val v = value(e)
                    set(target.trim(), v)
                    ans = v
                }
                else -> {
                    ans = value(s)
                }
            }
            pc = next
        }
    }

    // ---------- values ----------

    private fun name(v: String): String {
        val n = v.trim()
        if (!VARIABLE.matches(n)) throw Failure("$n is no variable (A to Z, θ)")
        return n
    }

    private fun set(v: String, x: Double) {
        vars[name(v)] = x
    }

    private fun get(v: String) = vars[name(v)] ?: 0.0

    /** A string in quotes as it is, else the expression's value written as the TI does. */
    private fun text(part: String): String {
        val p = part.trim()
        if (p.startsWith("\"")) return p.removePrefix("\"").removeSuffix("\"")
        return write(value(p))
    }

    fun value(expression: String): Double = Expr(expression.trim(), this::lookup, degrees).parse()

    private fun lookup(n: String): Double? = when (n) {
        "Ans" -> ans
        "π", "pi" -> PI
        "e" -> E
        else -> if (VARIABLE.matches(n)) vars[n] ?: 0.0 else null
    }

    companion object {
        private val VARIABLE = Regex("[A-Zθ]")

        /** Statements: lines, and the TI's ':' between statements outside quotes. */
        fun split(source: String): List<String> {
            val out = ArrayList<String>()
            for (raw in source.replace("->", "→").replace("!=", "≠").replace("<=", "≤").replace(">=", "≥").lines()) {
                val b = StringBuilder()
                var quoted = false
                for (c in raw) {
                    if (c == '"') quoted = !quoted
                    if (c == ':' && !quoted) { out += b.toString().trim().removePrefix(":").trim(); b.clear() } else b.append(c)
                }
                out += b.toString().trim()
            }
            // ":Disp 1" starts with the TI's colon: no empty statement for it.
            return out.map { it.trim() }.filter { it.isNotEmpty() }
        }

        /** The arguments of a command, split at the commas outside quotes and parentheses. */
        fun args(s: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            var quoted = false
            val b = StringBuilder()
            for (c in s) {
                when {
                    c == '"' -> { quoted = !quoted; b.append(c) }
                    quoted -> b.append(c)
                    c == '(' -> { depth++; b.append(c) }
                    c == ')' -> { depth--; b.append(c) }
                    c == ',' && depth == 0 -> { out += b.toString(); b.clear() }
                    else -> b.append(c)
                }
            }
            if (b.isNotBlank()) out += b.toString()
            return out
        }

        private fun inside(s: String, head: String) = s.removePrefix(head).let { if (it.endsWith(")")) it.dropLast(1) else it }

        /** A number as the TI shows it: up to 10 significant digits, E notation when large or small. */
        fun write(v: Double): String {
            if (v.isNaN()) return "ERR"
            if (v == Math.rint(v) && Math.abs(v) < 1e10) return v.toLong().toString().replace('-', '−')
            val a = Math.abs(v)
            if (a >= 1e10 || a < 1e-3) return String.format(java.util.Locale.ROOT, "%.9E", v).replace(Regex("\\.?0+E"), "E").replace("E+", "E").replace('-', '−')
            return java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString().replace('-', '−')
        }
    }
}
