package com.yaz.calculator.feature.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** The app's easter eggs, as the best apps hide theirs. */
object Eggs {

    /** When the keys last fell: Settings' version tapped seven times (Android's own egg). */
    var fellAt by mutableLongStateOf(0L)
        private set

    fun dropKeys() {
        fellAt = System.currentTimeMillis()
    }

    /** Whether the keys falling now is still news (a screen built later does not replay it). */
    fun falling(at: Long) = at > 0 && System.currentTimeMillis() - at < FALL_WINDOW_MS

    /** What shows in place of a result: 07734 upside down (the pocket calculators' hELLO), 213786 the app's ⵣ (OnePlus's 1+). */
    fun word(expression: String?): String? = when (expression) {
        "07734" -> "hELLO"
        "213786" -> "ⵣ"
        else -> null
    }

    /** π's day, 14 March: its key glows. */
    fun piDay(today: LocalDate = LocalDate.now()) = today.monthValue == 3 && today.dayOfMonth == 14

    private const val FALL_WINDOW_MS = 4_000L
}

/** The advanced mode's scientific eggs, each with its own animation over the screen. */
enum class ScienceEgg {
    /** φ = (1 + √5) / 2: a golden sparkle ripple and the golden spiral. */
    GOLDEN,
    /** c = 299 792 458 m/s: streaks of light. */
    LIGHT,
    /** Avogadro's 6.02214076 × 10²³: a mole of particles bursting. */
    AVOGADRO;

    companion object {
        fun of(v: Double): ScienceEgg? = when {
            Math.abs(v - 1.6180339887498949) < 1e-9 -> GOLDEN
            v == 299_792_458.0 -> LIGHT
            Math.abs(v / 6.02214076e23 - 1) < 1e-12 -> AVOGADRO
            else -> null
        }
    }
}

