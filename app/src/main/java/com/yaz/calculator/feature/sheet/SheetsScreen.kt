package com.yaz.calculator.feature.sheet

import com.yaz.calculator.ui.component.sink
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.yaz.calculator.ui.component.PillItem
import com.yaz.calculator.ui.component.PillMenu
import com.yaz.calculator.ui.component.PillMotion
import com.yaz.calculator.ui.component.SwipeAction
import com.yaz.calculator.ui.component.SwipeRow
import com.yaz.calculator.ui.component.rememberPillMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaz.calculator.data.sheets.SheetStore
import com.yaz.calculator.navigation.LocalReadableInset
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import org.koin.compose.koinInject

/**
 * The sheets: a new one first, then the others, the last changed first,
 * each with its first lines. Cards wrap into columns on a wide screen.
 */
@Composable
fun SheetsScreen(padding: PaddingValues, grid: androidx.compose.foundation.lazy.grid.LazyGridState, onOpen: (String) -> Unit) {
    val store: SheetStore = koinInject()
    val sheets by store.sheets.collectAsState()
    val haptics = rememberHaptics()
    val inset = LocalReadableInset.current
    var deleted by remember { mutableStateOf<Pair<com.yaz.calculator.data.sheets.Sheet, Int>?>(null) }
    var renaming by remember { mutableStateOf<com.yaz.calculator.data.sheets.Sheet?>(null) }
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp + inset,
            end = 16.dp + inset,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ZoneSurface(
                shape = RoundedCornerShape(28.dp),
                accent = true,
                onClick = {
                    haptics.firm()
                    onOpen(store.create().id)
                },
                modifier = Modifier.fillMaxWidth().sink(0.97f)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(AppIcons.NoteAdd, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text("New sheet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        items(sheets, key = { it.id }) { sheet ->
            val menu = rememberPillMenu()
            // A swipe either way deletes, with Undo; held, Rename, Duplicate, Delete.
            fun delete() {
                val at = sheets.indexOfFirst { it.id == sheet.id }
                store.delete(sheet.id)
                haptics.done()
                deleted = sheet to at
            }
            val gone = SwipeAction(AppIcons.Delete, "Delete", danger = true) { delete() }
            SwipeRow(start = gone, end = gone, modifier = Modifier.fillMaxWidth().animateItem().then(menu.tracker)) {
            ZoneSurface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().sink(0.97f).clip(RoundedCornerShape(24.dp)).combinedClickable(
                    onClickLabel = "Open",
                    onLongClickLabel = "More",
                    onClick = {
                        haptics.tick()
                        onOpen(sheet.id)
                    },
                    onLongClick = menu::open
                )
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        sheet.title.ifEmpty { "Sheet" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val rest = sheet.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).take(3).joinToString("\n")
                    if (rest.isNotEmpty()) {
                        Text(
                            rest,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        DateUtils.getRelativeTimeSpanString(sheet.updated).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            PillMenu(
                menu,
                listOf(
                    PillItem(AppIcons.Edit, "Rename", PillMotion.BOUNCE) { renaming = sheet },
                    PillItem(AppIcons.Copy, "Duplicate", PillMotion.BOUNCE) { haptics.done(); store.duplicate(sheet.id, "copy") },
                    PillItem(AppIcons.Delete, "Delete", PillMotion.WIGGLE, danger = true) { delete() }
                )
            )
            }
        }
    }
    // Deleted: Undo for a few seconds, floating at the foot.
    val last = deleted
    LaunchedEffect(last) { if (last != null) { kotlinx.coroutines.delay(6_000); deleted = null } }
    AnimatedVisibility(
        visible = last != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        ZoneSurface(shape = CircleShape, accent = true, onClick = {
            haptics.tick()
            deleted?.let { (sheet, at) -> store.restore(sheet, at) }
            deleted = null
        }, modifier = Modifier.height(48.dp).sink(0.92f)) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Sheet deleted, undo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    renaming?.let { sheet -> RenameDialog(sheet.title, onDismiss = { renaming = null }) { name -> renaming = null; haptics.done(); store.rename(sheet.id, name) } }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val state = androidx.compose.foundation.text.input.rememberTextFieldState(current)
    com.yaz.calculator.ui.component.ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                state = state,
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { com.yaz.calculator.ui.component.BoldButton(filled = true, onClick = { state.text.toString().takeIf { it.isNotBlank() }?.let(onDone) }) { Text("Rename") } },
        dismissButton = { com.yaz.calculator.ui.component.QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
