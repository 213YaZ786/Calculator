package com.yaz.calculator.feature.calc

import android.icu.text.MessageFormat
import com.yaz.calculator.engine.Sym
import java.math.BigDecimal
import java.util.Locale

/**
 * A result in words, in the phone's language (Calzy), by ICU's spellout rules
 * (through MessageFormat's "spellout" argument: RuleBasedNumberFormat is not public on Android);
 * null when it has none: not a plain number, or so large that ICU writes digits.
 */
internal fun inWords(result: String, locale: Locale = Locale.getDefault()): String? {
    val value = result.removeSuffix("…").replace(Sym.MINUS, '-').toBigDecimalOrNull() ?: return null
    if (value.abs() >= WORDS_LIMIT) return null
    return runCatching { MessageFormat("{0,spellout}", locale).format(arrayOf(value)) }.getOrNull()
}

private val WORDS_LIMIT = BigDecimal("1E15")
