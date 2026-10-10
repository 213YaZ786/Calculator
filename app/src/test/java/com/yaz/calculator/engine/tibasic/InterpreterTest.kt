package com.yaz.calculator.engine.tibasic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpreterTest {

    private class Console(val answers: ArrayDeque<Double> = ArrayDeque(), val choices: ArrayDeque<Int> = ArrayDeque()) : Interpreter.Io {
        val lines = ArrayList<String>()
        override fun show(line: String) { lines += line }
        override fun clear() = lines.clear()
        override suspend fun ask(prompt: String) = answers.removeFirst()
        override suspend fun choose(title: String, options: List<String>) = choices.removeFirst()
        override suspend fun pause(line: String?) { line?.let { lines += it } }
        override fun stopped() = false
    }

    private fun run(source: String, vararg answers: Double, choices: List<Int> = emptyList()): List<String> {
        val io = Console(ArrayDeque(answers.toList()), ArrayDeque(choices))
        runBlocking { Interpreter(source, io, degrees = false).run() }
        return io.lines
    }

    @Test
    fun quadraticAsStudentsWriteIt() {
        val program = """
            :Prompt A,B,C
            :B²-4AC→D
            :Disp "DELTA",D
            :If D<0
            :Then
            :Disp "PAS DE RACINE"
            :Else
            :Disp (-B-√(D))/(2A)
            :Disp (-B+√(D))/(2A)
            :End
        """.trimIndent()
        assertEquals(listOf("DELTA", "16", "−3", "1"), run(program, 1.0, 2.0, -3.0))
        assertEquals(listOf("DELTA", "−16", "PAS DE RACINE"), run(program, 1.0, 2.0, 5.0))
    }

    @Test
    fun loops() {
        assertEquals(listOf("55"), run("0→S:For(I,1,10):S+I→S:End:Disp S"))
        assertEquals(listOf("64"), run("1→N:While N<50:2N→N:End:Disp N"))
        assertEquals(listOf("5"), run("0→N:Repeat N≥5:N+1→N:End:Disp N"))
        assertEquals(listOf("10", "8", "6"), run("For(I,10,6,-2):Disp I:End"))
    }

    @Test
    fun labelsMenusAndInput() {
        val menu = "Menu(\"CHOIX\",\"UN\",A,\"DEUX\",B)\nLbl A\nDisp 1\nStop\nLbl B\nDisp 2"
        assertEquals(listOf("2"), run(menu, choices = listOf(1)))
        assertEquals(listOf("49"), run("Input \"N=\",N\nDisp N²", 7.0))
        assertTrue(run("1→A:If A=1 and 2>1:Disp \"OK\"").contains("OK"))
    }

    @Test
    fun gravitationProgram() {
        val program = "Prompt M,N,D\n6.67E-11*M*N/D²→F\nDisp \"F=\",F"
        val out = run(program, 5.972e24, 7.348e22, 3.844e8)
        assertEquals("F=", out[0])
        assertTrue(out[1], out[1].startsWith("1.98083"))
    }

    @Test(expected = Interpreter.Failure::class)
    fun endlessLoopsStop() {
        runBlocking { Interpreter("Lbl A\nGoto A", Console(), degrees = false, maxSteps = 10_000).run() }
    }
}
