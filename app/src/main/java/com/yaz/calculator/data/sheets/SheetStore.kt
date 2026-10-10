package com.yaz.calculator.data.sheets

import android.content.Context
import com.yaz.calculator.core.common.writeBytesAtomically
import com.yaz.calculator.core.security.Vault
import java.io.File
import java.util.UUID
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

@Serializable
data class Sheet(val id: String, val text: String, val updated: Long) {
    /** The sheet's name: its first line with words. */
    val title: String get() = text.lineSequence().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

/** The sheets, newest first, in one file sealed by the Keystore (Vault). */
class SheetStore(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "sheets.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val _sheets = MutableStateFlow<List<Sheet>>(emptyList())
    val sheets: StateFlow<List<Sheet>> = _sheets.asStateFlow()
    private var pending: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val read = runCatching {
                if (!file.exists()) return@runCatching null
                Vault.open(file.readBytes(), LABEL)?.let { json.decodeFromString<List<Sheet>>(it.decodeToString()) }
            }.getOrNull()
            if (read != null) _sheets.value = read
        }
    }

    fun create(): Sheet {
        val sheet = Sheet(UUID.randomUUID().toString(), "", System.currentTimeMillis())
        change { listOf(sheet) + it }
        return sheet
    }

    fun write(id: String, text: String) = change { list ->
        val sheet = list.firstOrNull { it.id == id } ?: return@change list
        if (sheet.text == text) return@change list
        listOf(sheet.copy(text = text, updated = System.currentTimeMillis())) + list.filter { it.id != id }
    }

    fun delete(id: String) = change { list -> list.filter { it.id != id } }

    /** A sheet deleted by a swipe, put back where it was. */
    fun restore(sheet: Sheet, at: Int) = change { list -> if (list.any { it.id == sheet.id }) list else list.toMutableList().apply { add(at.coerceIn(0, size), sheet) } }

    /** A copy, first in the list, its name marked as a copy. */
    fun duplicate(id: String, copyWord: String) = change { list ->
        val sheet = list.firstOrNull { it.id == id } ?: return@change list
        val lines = sheet.text.lines().toMutableList()
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first >= 0) lines[first] = lines[first].trimEnd() + " ($copyWord)"
        listOf(Sheet(UUID.randomUUID().toString(), lines.joinToString("\n"), System.currentTimeMillis())) + list
    }

    /** The name is the first line: a heading put first, or the first line written over. */
    fun rename(id: String, name: String) = change { list ->
        val sheet = list.firstOrNull { it.id == id } ?: return@change list
        val lines = sheet.text.lines().toMutableList()
        val first = lines.indexOfFirst { it.isNotBlank() }
        val heading = "# " + name.trim()
        if (first >= 0 && lines[first].trimStart().startsWith("#")) lines[first] = heading else lines.add(0, heading)
        list.map { if (it.id == id) it.copy(text = lines.joinToString("\n"), updated = System.currentTimeMillis()) else it }
    }

    /** A sheet left empty goes when it is closed. */
    fun dropIfEmpty(id: String) = change { list -> list.filter { it.id != id || it.text.isNotBlank() } }

    private fun change(transform: (List<Sheet>) -> List<Sheet>) {
        val next = transform(_sheets.value)
        if (next === _sheets.value) return
        _sheets.value = next
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(500)
            runCatching { file.writeBytesAtomically(Vault.seal(json.encodeToString(_sheets.value).encodeToByteArray(), LABEL)) }
        }
    }

    private companion object {
        const val LABEL = "sheets"
    }
}
