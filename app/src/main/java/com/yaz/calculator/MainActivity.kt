package com.yaz.calculator

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.yaz.calculator.core.Shortcuts
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.navigation.Asked
import com.yaz.calculator.navigation.CalculatorApp
import com.yaz.calculator.ui.theme.AppSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** The calculator, the converter and the sheets. */
class MainActivity : ComponentActivity() {

    private val settings: SettingsStore by inject()

    /** What a shortcut on the app's icon asked for. An intent only opens a place, it never acts. */
    private val asked = mutableStateOf<Asked?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) asked.value = askedBy(intent)
        setContent { AppSurface { CalculatorApp(asked) } }
        Shortcuts.publish(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                settings.settings.map { it.hideInRecents }.distinctUntilChanged().collect { setRecentsScreenshotEnabled(!it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        asked.value = askedBy(intent)
    }

    private fun askedBy(intent: Intent?): Asked? = when (intent?.action) {
        Shortcuts.ACTION_CONVERT -> Asked.Convert
        Shortcuts.ACTION_SHEET -> Asked.NewSheet
        else -> null
    }
}
