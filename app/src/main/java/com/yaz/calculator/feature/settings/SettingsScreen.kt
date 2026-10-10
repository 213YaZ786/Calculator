package com.yaz.calculator.feature.settings


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaz.calculator.BuildConfig
import com.yaz.calculator.core.update.UpdateMode
import com.yaz.calculator.core.update.Updates
import com.yaz.calculator.data.settings.ThemeMode
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.navigation.LocalReadableInset
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.component.FloatingFrame
import com.yaz.calculator.ui.component.FloatingTop
import com.yaz.calculator.ui.component.QuietButton
import com.yaz.calculator.ui.component.ZoneAlertDialog
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import com.yaz.calculator.ui.theme.TEXT_SCALES
import com.yaz.calculator.ui.theme.textScaleLabel
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

private enum class OpenDialog { NONE, THEME, TEXT_SIZE, UPDATES, ANGLE, CLEAR }

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = rememberHaptics()
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop("Settings", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))

            Section("Calculator") {
                SettingRow(
                    title = "Angles",
                    summary = if (settings.degrees) "Degrees" else "Radians",
                    onClick = { dialog = OpenDialog.ANGLE }
                )
                val sample = NumberStyle.of(Locale.getDefault(), true).show("1234567.89")
                SwitchRow(
                    title = "Digit grouping",
                    summary = sample,
                    checked = settings.grouping,
                    onChange = viewModel::setGrouping
                )
                SwitchRow(
                    title = "Keep history",
                    summary = null,
                    checked = settings.keepHistory,
                    onChange = viewModel::setKeepHistory
                )
                SettingRow(title = "Clear history", summary = null, onClick = { dialog = OpenDialog.CLEAR })
            }

            Section("Privacy") {
                SwitchRow(
                    title = "Hide in recent apps",
                    summary = "The app's preview stays blank in recent apps.",
                    checked = settings.hideInRecents,
                    onChange = viewModel::setHideInRecents
                )
            }

            Section("Appearance") {
                SettingRow(title = "Theme", summary = themeLabel(settings.themeMode), onClick = { dialog = OpenDialog.THEME })
                SwitchRow(title = "Pure black", summary = "Black background in dark mode.", checked = settings.pureBlack, onChange = viewModel::setPureBlack)
                SwitchRow(title = "Glass effects", summary = "Keys and panes in liquid glass.", checked = settings.glass, onChange = viewModel::setGlass)
                SettingRow(
                    title = "Text size",
                    summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                    onClick = { dialog = OpenDialog.TEXT_SIZE }
                )
            }

            Section("About") {
                SettingRow(title = "Updates", summary = updatesLabel(settings.updates), onClick = { dialog = OpenDialog.UPDATES })
                // Seven taps, as on Android's own version: the keys fall.
                var taps by remember { mutableStateOf(0 to 0L) }
                SettingRow(title = "Calculator ${BuildConfig.VERSION_NAME}", summary = null, onClick = {
                    val now = System.currentTimeMillis()
                    val count = if (now - taps.second < 1_500) taps.first + 1 else 1
                    taps = count to now
                    if (count >= 7) {
                        taps = 0 to 0L
                        haptics.done()
                        com.yaz.calculator.feature.calc.Eggs.dropKeys()
                        onBack()
                    }
                })
                SettingRow(
                    title = "Exact arithmetic",
                    summary = "crcalc and ExactCalculator, Android Open Source Project and LineageOS, Apache License 2.0",
                    onClick = { uriHandler.openUri("https://github.com/LineageOS/android_packages_apps_ExactCalculator") }
                )
                SettingRow(
                    title = "Computer algebra",
                    summary = "Giac by Bernard Parisse, Université Grenoble Alpes, GPL-3.0, with GMP and MPFR, LGPL-3.0",
                    onClick = { uriHandler.openUri("https://github.com/geogebra/giac") }
                )
                SettingRow(
                    title = "Exchange rates",
                    summary = "exchange-api by Fawaz Ahmed, public domain",
                    onClick = { uriHandler.openUri("https://github.com/fawazahmed0/exchange-api") }
                )
                SettingRow(
                    title = "Source code",
                    summary = "github.com/213YaZ786/Calculator, GPL-3.0",
                    onClick = { uriHandler.openUri("https://github.com/213YaZ786/Calculator") }
                )
            }

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    when (dialog) {
        OpenDialog.THEME -> ChoiceDialog("Theme", ThemeMode.entries.map { it to themeLabel(it) }, settings.themeMode, viewModel::setTheme) { dialog = OpenDialog.NONE }
        OpenDialog.TEXT_SIZE -> ChoiceDialog("Text size", TEXT_SCALES.map { it to textScaleLabel(it) }, settings.textScale, viewModel::setTextScale) { dialog = OpenDialog.NONE }
        OpenDialog.ANGLE -> ChoiceDialog("Angles", listOf(true to "Degrees", false to "Radians"), settings.degrees, viewModel::setDegrees) { dialog = OpenDialog.NONE }
        OpenDialog.UPDATES -> ChoiceDialog(
            "Updates",
            UpdateMode.entries.map { it to updatesLabel(it) },
            settings.updates,
            { mode ->
                viewModel.setUpdates(mode)
                if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
            }
        ) { dialog = OpenDialog.NONE }
        OpenDialog.CLEAR -> ZoneAlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Clear the history?") },
            confirmButton = {
                QuietButton(onClick = {
                    viewModel.clearHistory()
                    haptics.done()
                    dialog = OpenDialog.NONE
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { QuietButton(onClick = { dialog = OpenDialog.NONE }) { Text("Cancel") } }
        )
        OpenDialog.NONE -> Unit
    }
}

/** A titled group of rows on one rounded zone. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp)
    )
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(title: String, summary: String?, onClick: (() -> Unit)?, quiet: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val haptics = rememberHaptics()
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        supportingContent = summary?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable { if (!quiet) haptics.tick(); onClick() } else Modifier
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val change = { on: Boolean -> haptics.toggle(on); onChange(on) }
    SettingRow(title, summary, onClick = { change(!checked) }, quiet = true, trailing = { Switch(checked = checked, onCheckedChange = change) })
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install automatically"
}

