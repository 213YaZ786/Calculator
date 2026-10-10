package com.yaz.calculator.data.advanced

import android.content.Context
import com.yaz.calculator.core.common.writeBytesAtomically
import com.yaz.calculator.core.security.Vault
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

/** One of the Y= functions: its expression in x (the app's form) and whether it is drawn. */
@Serializable
data class Function(val expression: String = "", val on: Boolean = true)

/** A TI-Basic program the user wrote. */
@Serializable
data class UserProgram(val name: String, val source: String)

/** The graph's window, as the TI's WINDOW screen. */
@Serializable
data class Window(val xMin: Double = -10.0, val xMax: Double = 10.0, val yMin: Double = -10.0, val yMax: Double = 10.0)

@Serializable
data class AdvancedData(
    val functions: List<Function> = List(FUNCTIONS) { Function() },
    val window: Window = Window(),
    val tableStart: Double = 0.0,
    val tableStep: Double = 1.0,
    /** STAT's lists L1 and L2, as typed (the app's form). */
    val l1: List<String> = emptyList(),
    val l2: List<String> = emptyList(),
    /** The user's own TI-Basic programs. */
    val userPrograms: List<UserProgram> = emptyList()
) {
    companion object {
        /** Y1 to Y6, as many as the TI-82's function screen. */
        const val FUNCTIONS = 6
    }
}

/** The advanced mode's functions, window and table, in one file sealed by the Keystore (Vault). */
class AdvancedStore(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "advanced.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val _data = MutableStateFlow(AdvancedData())
    val data: StateFlow<AdvancedData> = _data.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var pending: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val read = runCatching {
                if (!file.exists()) return@runCatching null
                Vault.open(file.readBytes(), LABEL)?.let { json.decodeFromString<AdvancedData>(it.decodeToString()) }
            }.getOrNull()
            if (read != null) _data.value = read.copy(functions = (read.functions + List(AdvancedData.FUNCTIONS) { Function() }).take(AdvancedData.FUNCTIONS))
            _loaded.value = true
        }
    }

    fun update(transform: (AdvancedData) -> AdvancedData) {
        val next = transform(_data.value)
        if (next == _data.value) return
        _data.value = next
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(500)
            runCatching { file.writeBytesAtomically(Vault.seal(json.encodeToString(_data.value).encodeToByteArray(), LABEL)) }
        }
    }

    private companion object {
        const val LABEL = "advanced"
    }
}
