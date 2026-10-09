package com.yaz.calculator.feature.sheet

import com.yaz.calculator.ui.component.sink
import android.text.format.DateUtils
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
fun SheetsScreen(padding: PaddingValues, onOpen: (String) -> Unit) {
    val store: SheetStore = koinInject()
    val sheets by store.sheets.collectAsState()
    val haptics = rememberHaptics()
    val inset = LocalReadableInset.current
    LazyVerticalGrid(
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
            ZoneSurface(
                shape = RoundedCornerShape(24.dp),
                onClick = {
                    haptics.tick()
                    onOpen(sheet.id)
                },
                modifier = Modifier.fillMaxWidth().sink(0.97f)
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
        }
    }
}
