package com.yaz.calculator.data.settings

import android.content.Context
import com.yaz.calculator.core.common.writeTextAtomically
import com.yaz.calculator.core.update.UpdateMode
import com.yaz.calculator.engine.Fractions
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    /** What happens when a newer version is out, checked once when the app opens. */
    val updates: UpdateMode = UpdateMode.INSTALL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Zones and floating controls in liquid glass, over a soft light in the wallpaper's colours. */
    val glass: Boolean = true,
    /** Multiplier on every text style, one of the steps in ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    /** The first launch page was closed. */
    val welcomeSeen: Boolean = false,
    /** The app's picture in the recent apps screen stays blank. */
    val hideInRecents: Boolean = false,
    /** Angles in degrees, as most people learn them; radians otherwise. */
    val degrees: Boolean = true,
    /** Thousands grouped as the user's language groups them. */
    val grouping: Boolean = true,
    /** The exact fraction beside a result that is one (3/4 = 0.75) in the calculator, converter, sheets and programs. */
    val exactFractions: Boolean = true,
    /** Calculations kept in the history. */
    val keepHistory: Boolean = true,
    /** The scientific keys unfolded on a phone held upright. */
    val scientificOpen: Boolean = false,
    /** The last converter used, and its units, by category name. */
    val convertCategory: String = "LENGTH",
    val convertUnits: Map<String, List<String>> = emptyMap()
)

/**
 * Small preference file, plain JSON written atomically, like the other apps.
 * Nothing here is a secret: calculations and sheets are sealed elsewhere.
 */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load()).also { Fractions.enabled = it.value.exactFractions }
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        Fractions.enabled = updated.exactFractions
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }
}
