package com.yaz.calculator.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import com.yaz.calculator.ui.component.sink
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yaz.calculator.BuildConfig
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.data.sheets.SheetStore
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.feature.calc.CalcScreen
import com.yaz.calculator.feature.calc.CalcViewModel
import com.yaz.calculator.feature.convert.ConvertScreen
import com.yaz.calculator.feature.convert.ConvertViewModel
import com.yaz.calculator.feature.main.WelcomeScreen
import com.yaz.calculator.feature.settings.SettingsScreen
import com.yaz.calculator.feature.sheet.SheetScreen
import com.yaz.calculator.feature.sheet.SheetsScreen
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.component.FloatingFrame
import com.yaz.calculator.ui.component.FloatingPane
import com.yaz.calculator.ui.component.FloatingTop
import com.yaz.calculator.ui.component.UpdatePrompt
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.glass.LocalGlass
import com.yaz.calculator.ui.icon.AppIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** The app's three places, side by side under one switch. */
enum class Mode(val label: String, val icon: ImageVector) {
    CALCULATOR("Calculator", AppIcons.Calculate),
    CONVERT("Convert", AppIcons.Convert),
    SHEETS("Sheets", AppIcons.Sheet)
}

/** What a shortcut on the app's icon asked for. */
sealed interface Asked {
    data object Convert : Asked
    data object NewSheet : Asked
}

object Routes {
    const val WELCOME = "welcome"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val SHEET = "sheet/{id}"
    fun sheet(id: String) = "sheet/$id"
}

@Composable
fun CalculatorApp(asked: MutableState<Asked?>) {
    val nav = rememberNavController()
    val store: SettingsStore = koinInject()
    val sheets: SheetStore = koinInject()
    val settings by store.settings.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val style = remember(locale, settings.grouping) { NumberStyle.of(locale, settings.grouping) }
    var mode by rememberSaveable { mutableStateOf(Mode.CALCULATOR) }

    LaunchedEffect(asked.value) {
        when (asked.value) {
            Asked.Convert -> mode = Mode.CONVERT
            Asked.NewSheet -> {
                mode = Mode.SHEETS
                nav.navigate(Routes.sheet(sheets.create().id))
            }
            null -> Unit
        }
        asked.value = null
    }

    NavHost(nav, startDestination = if (settings.welcomeSeen) Routes.MAIN else Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = {
                store.update { it.copy(welcomeSeen = true) }
                nav.navigate(Routes.MAIN) { popUpTo(Routes.WELCOME) { inclusive = true } }
            })
        }
        composable(Routes.MAIN) {
            MainScreen(nav, style, mode) { mode = it }
            UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SHEET) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SheetScreen(id, style, onBack = { nav.popBackStack() })
        }
    }
    PlainBack(nav)
}

@Composable
private fun MainScreen(nav: NavHostController, style: NumberStyle, mode: Mode, onMode: (Mode) -> Unit) {
    val calc: CalcViewModel = koinViewModel()
    val convert: ConvertViewModel = koinViewModel()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(historyOpen) { historyOpen = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A tablet lying down; a phone on its side keeps its height for the keys.
        val sideHistory = maxWidth > maxHeight * 1.1f && maxWidth >= 840.dp && maxHeight >= 480.dp && !calc.advanced
        FloatingFrame(
            bottom = 0.dp,
            top = {
                FloatingTop(
                    title = null,
                    leading = {
                        if (mode == Mode.CALCULATOR && !sideHistory) {
                            FloatingAction(if (historyOpen) AppIcons.Close else AppIcons.History, if (historyOpen) "Close the history" else "History", onClick = {
                                historyOpen = !historyOpen
                            })
                        }
                    },
                    center = { ModeSwitch(mode) { historyOpen = false; onMode(it) } },
                    trailing = { FloatingAction(AppIcons.Settings, "Settings", onClick = { nav.navigate(Routes.SETTINGS) }) }
                )
            }
        ) { padding ->
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "mode"
            ) { m ->
                when (m) {
                    Mode.CALCULATOR -> CalcScreen(calc, style, padding, historyOpen) { historyOpen = it }
                    Mode.CONVERT -> ConvertScreen(convert, style, padding)
                    Mode.SHEETS -> SheetsScreen(padding) { nav.navigate(Routes.sheet(it)) }
                }
            }
        }
    }
}

/**
 * The switch between the three places: a pill of glass, the place chosen
 * in a drop of the accent; icons only.
 */
@Composable
private fun ModeSwitch(mode: Mode, onMode: (Mode) -> Unit) {
    val haptics = rememberHaptics()
    val accent = LocalGlass.current?.accentTint ?: MaterialTheme.colorScheme.secondaryContainer
    // The chosen place's drop of accent slides from one to the other.
    val at by androidx.compose.animation.core.animateFloatAsState(
        Mode.entries.indexOf(mode).toFloat(),
        androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "tab"
    )
    FloatingPane(shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val width = maxWidth / Mode.entries.size
            Box(
                Modifier
                    .offset(x = width * at)
                    .width(width)
                    .height(TAB_HEIGHT)
                    .background(accent, CircleShape)
            )
            Row(Modifier.fillMaxWidth()) {
                Mode.entries.forEach { m ->
                    val on = m == mode
                    Column(
                        Modifier
                            .weight(1f)
                            .height(TAB_HEIGHT)
                            .sink(0.92f)
                            .clip(CircleShape)
                            .clickable(role = Role.Tab) {
                                if (!on) {
                                    haptics.firm()
                                    onMode(m)
                                }
                            }
                            .semantics { contentDescription = m.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icons alone, large enough to read at a glance; the name stays for TalkBack.
                        val grow by androidx.compose.animation.core.animateFloatAsState(if (on) 1.12f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "grow")
                        Icon(
                            m.icon,
                            contentDescription = null,
                            tint = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = grow; scaleY = grow }
                        )
                    }
                }
            }
        }
    }
}

/** The tabs' height: the place switch is reached at a glance and a thumb. */
private val TAB_HEIGHT = 52.dp
