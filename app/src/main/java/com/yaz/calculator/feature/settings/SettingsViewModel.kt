package com.yaz.calculator.feature.settings

import androidx.lifecycle.ViewModel
import com.yaz.calculator.core.update.UpdateMode
import com.yaz.calculator.data.history.Ledger
import com.yaz.calculator.data.settings.Settings
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.data.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(val store: SettingsStore, private val ledger: Ledger) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

    fun setTheme(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }
    fun setPureBlack(on: Boolean) = store.update { it.copy(pureBlack = on) }
    fun setGlass(on: Boolean) = store.update { it.copy(glass = on) }
    fun setTextScale(scale: Float) = store.update { it.copy(textScale = scale) }
    fun setUpdates(mode: UpdateMode) = store.update { it.copy(updates = mode) }
    fun setHideInRecents(on: Boolean) = store.update { it.copy(hideInRecents = on) }
    fun setDegrees(on: Boolean) = store.update { it.copy(degrees = on) }
    fun setGrouping(on: Boolean) = store.update { it.copy(grouping = on) }
    // Source: setGrouping above (a saved on/off preference, developer.android.com/develop/ui/views/components/settings).
    fun setExactFractions(on: Boolean) = store.update { it.copy(exactFractions = on) }

    fun setKeepHistory(on: Boolean) {
        store.update { it.copy(keepHistory = on) }
        if (!on) clearHistory()
    }

    fun clearHistory() = ledger.update { it.copy(entries = emptyList()) }
}
