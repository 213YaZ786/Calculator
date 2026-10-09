package com.yaz.calculator.data.history

import android.content.Context
import com.yaz.calculator.core.common.writeBytesAtomically
import com.yaz.calculator.core.security.Vault
import com.yaz.calculator.engine.Sym
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A result kept for its exact value: the expression that gave it and the
 * angle unit it was worked out in. In an expression it stands as one
 * character of Unicode's private area, so √2 taken back from the history
 * is still √2, not 1.4142135.
 */
@Serializable
data class Stored(val source: String, val degrees: Boolean)

/** One line of the history. [result] is the result as kept (digits, or a stored result's character). */
@Serializable
data class Entry(val expression: String, val result: String, val shown: String, val degrees: Boolean, val at: Long)

@Serializable
data class LedgerData(
    val entries: List<Entry> = emptyList(),
    val stored: Map<Int, Stored> = emptyMap(),
    val memory: String? = null,
    /** The expression on screen when the app was left. */
    val expression: String = ""
)

/**
 * The calculator's memory of the user: history, memory, stored results and
 * the expression left on screen. One file sealed by the Keystore (Vault),
 * written a moment after each change, off the main thread.
 */
class Ledger(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "ledger.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val _data = MutableStateFlow(LedgerData())
    val data: StateFlow<LedgerData> = _data.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var pending: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val read = runCatching {
                if (!file.exists()) return@runCatching null
                Vault.open(file.readBytes(), LABEL)?.let { json.decodeFromString<LedgerData>(it.decodeToString()) }
            }.getOrNull()
            if (read != null) _data.value = read
            _loaded.value = true
        }
    }

    fun update(change: (LedgerData) -> LedgerData) {
        _data.value = change(_data.value)
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(400)
            write(_data.value)
        }
    }

    private fun write(data: LedgerData) {
        runCatching { file.writeBytesAtomically(Vault.seal(json.encodeToString(compact(data)).encodeToByteArray(), LABEL)) }
    }

    /** A new character for [stored], the lowest one free. */
    fun store(stored: Stored): Char {
        val used = _data.value.stored.keys
        val free = (FIRST..LAST).firstOrNull { it !in used } ?: run {
            // Full: keep only what is still in use, then take a free one.
            update { compact(it) }
            (FIRST..LAST).first { it !in _data.value.stored.keys }
        }
        update { it.copy(stored = it.stored + (free to stored)) }
        return free.toChar()
    }

    companion object {
        private const val LABEL = "ledger"
        private const val FIRST = 0xE000
        private const val LAST = 0xF8FF
        /** Past this many lines, the oldest leave the history. */
        const val MAX_ENTRIES = 500

        /** Only the stored results still reached from the history, the memory or the screen. */
        fun compact(data: LedgerData): LedgerData {
            val reached = HashSet<Int>()
            fun walk(text: String) {
                for (c in text) if (Sym.isRef(c) && reached.add(c.code)) data.stored[c.code]?.let { walk(it.source) }
            }
            data.entries.forEach { walk(it.expression); walk(it.result) }
            data.memory?.let(::walk)
            walk(data.expression)
            return data.copy(stored = data.stored.filterKeys { it in reached })
        }
    }
}
