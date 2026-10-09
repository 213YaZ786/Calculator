package com.yaz.calculator.feature.calc

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaz.calculator.data.history.Entry
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.ui.component.EmptyZone
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.icon.AppIcons
import java.text.DateFormat
import java.util.Date

/**
 * Past calculations, the newest nearest the keys as on a paper roll, by day.
 * The expression of a line writes it again; its result is taken where the
 * cursor is.
 */
@Composable
fun HistoryList(vm: CalcViewModel, style: NumberStyle, padding: PaddingValues, onTaken: () -> Unit, modifier: Modifier = Modifier) {
    val data by vm.entries.collectAsState()
    val haptics = rememberHaptics()
    val entries = data.entries
    if (entries.isEmpty()) {
        Column(modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
            EmptyZone(title = "No calculations yet", message = "Each result appears here.", icon = AppIcons.History, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    // Newest first in the list, drawn from the bottom up.
    val rows = remember(entries) { rowsOf(entries) }
    LazyColumn(
        modifier.fillMaxSize(),
        state = rememberLazyListState(),
        reverseLayout = true,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.Bottom)
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is Row.Day -> Text(
                    row.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                )
                is Row.Line -> ZoneSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            style.show(vm.spelled(row.entry.expression)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClickLabel = "Write again") {
                                    feelIt(haptics, vm.takeExpression(row.entry))
                                    onTaken()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        Text(
                            "= " + style.show(row.entry.shown),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClickLabel = "Use this result") {
                                    feelIt(haptics, vm.takeResult(row.entry))
                                    onTaken()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Row {
    val key: String

    class Day(val label: String, override val key: String) : Row
    class Line(val entry: Entry, override val key: String) : Row
}

/** The lines newest first, a day's name after its last line (the list is drawn upwards). */
private fun rowsOf(entries: List<Entry>): List<Row> {
    val out = ArrayList<Row>()
    var day: String? = null
    var dayStart = 0L
    for ((i, e) in entries.withIndex().reversed()) {
        val label = dayLabel(e.at)
        if (day != null && label != day) out += Row.Day(day, "day-$dayStart")
        day = label
        dayStart = e.at
        out += Row.Line(e, "line-$i-${e.at}")
    }
    if (day != null) out += Row.Day(day, "day-$dayStart")
    return out
}

private fun dayLabel(at: Long): String = when {
    DateUtils.isToday(at) -> "Today"
    DateUtils.isToday(at + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
}
