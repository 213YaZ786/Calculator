package com.yaz.calculator.feature.main

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yaz.calculator.CalculatorTile
import com.yaz.calculator.R
import com.yaz.calculator.core.update.Updates
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.ui.component.BoldButton
import com.yaz.calculator.ui.component.LoadingMark
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import org.koin.compose.koinInject

/**
 * The one page shown at the first launch: the few choices worth making,
 * the best one already set. Each zone turns into a tick once done.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val haptics = rememberHaptics()
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val canInstall = remember(checks) { Updates.canInstall(context) }
    val notifying = remember(checks) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    var tileAdded by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(40.dp))
        LoadingMark(size = 140.dp)
        Spacer(Modifier.height(20.dp))
        Text("Welcome to Calculator", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        WelcomeZone(AppIcons.Angle, "Angles", done = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Degrees", false to "Radians").forEach { (deg, label) ->
                    ZoneSurface(
                        shape = CircleShape,
                        accent = settings.degrees == deg,
                        onClick = { haptics.tick(); store.update { it.copy(degrees = deg) } }
                    ) {
                        Text(label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(16.dp))
            WelcomeZone(AppIcons.Calculate, "Calculator in Quick Settings", done = tileAdded) {
                if (!tileAdded) BoldButton(onClick = {
                    val bar = context.getSystemService(StatusBarManager::class.java)
                    bar.requestAddTileService(
                        ComponentName(context, CalculatorTile::class.java),
                        context.getString(R.string.app_name),
                        Icon.createWithResource(context, R.drawable.ic_tile),
                        ContextCompat.getMainExecutor(context)
                    ) { result ->
                        tileAdded = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    }
                }) { Text("Add the tile") }
            }
        }
        Spacer(Modifier.height(16.dp))
        WelcomeZone(AppIcons.Update, "Automatic updates", done = canInstall && notifying) {
            if (!notifying) BoldButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            else if (!canInstall) BoldButton(onClick = { Updates.allowInstalls(context) }) { Text("Allow updates") }
        }

        Spacer(Modifier.height(32.dp))
        BoldButton(filled = true, onClick = onStart) { Text("Start") }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** One thing to settle, a tick once done. */
@Composable
private fun WelcomeZone(icon: ImageVector, title: String, done: Boolean, content: @Composable () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) AppIcons.CheckCircle else icon, contentDescription = if (done) "Done" else null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            content()
        }
    }
}
