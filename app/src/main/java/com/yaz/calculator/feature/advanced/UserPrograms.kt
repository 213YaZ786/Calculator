package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.engine.programs.Words
import com.yaz.calculator.feature.calc.feelIt
import com.yaz.calculator.feature.calc.showIn
import com.yaz.calculator.ui.component.QuietButton
import com.yaz.calculator.ui.component.ZoneAlertDialog
import com.yaz.calculator.ui.component.ZoneSurface
import com.yaz.calculator.ui.component.rememberHaptics
import com.yaz.calculator.ui.component.sink
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.awaitCancellation

/** "My programs": the user's own TI-Basic, a card each, and one to write a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyPrograms(adv: AdvancedViewModel) {
    val haptics = rememberHaptics()
    val data by adv.data.collectAsState()
    Text(MINE.get(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoneSurface(shape = RoundedCornerShape(18.dp), accent = true, onClick = { haptics.tick(); adv.edit(-1) }, modifier = Modifier.sink(0.95f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(NEW.get(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        data.userPrograms.forEachIndexed { i, p ->
            ZoneSurface(shape = RoundedCornerShape(18.dp), onClick = { haptics.tick(); adv.runUser(i) }, modifier = Modifier.widthIn(min = 120.dp, max = 240.dp).sink(0.95f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${p.source.lines().count { it.isNotBlank() }} " + LINES.get(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The editor: a name, the code typed with the phone's keyboard, a bar of the TI's commands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramEditor(adv: AdvancedViewModel) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Close, contentDescription = CANCEL.get(), modifier = Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tick(); adv.cancelEdit() }.padding(8.dp), tint = colors.onSurfaceVariant)
            BasicTextField(
                state = adv.editorName,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = NAME.get() },
                decorator = { inner -> if (adv.editorName.text.isEmpty()) Text(NAME.get(), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant); inner() }
            )
            QuietButton(onClick = { feelIt(haptics, adv.saveEdit()) }) { Text(SAVE.get()) }
        }
        // The TI's commands, typed where the cursor is.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            TOKENS.forEach { token ->
                Text(
                    token.trim(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(CircleShape).sink(0.9f)
                        .border(1.dp, colors.outlineVariant, CircleShape)
                        .clickable { haptics.tick(); insert(adv, token) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (adv.editorName.text.isNotEmpty()) runCatching { focus.requestFocus() } }
        BasicTextField(
            state = adv.editorSource,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = colors.onSurface),
            cursorBrush = SolidColor(Gold),
            modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus).semantics { contentDescription = CODE.get() },
            decorator = { inner -> if (adv.editorSource.text.isEmpty()) Text(EXAMPLE, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant.copy(alpha = 0.6f)); inner() }
        )
    }
}

/** Inserts [token] where the cursor is; a command starts its own line. */
private fun insert(adv: AdvancedViewModel, token: String) {
    val state = adv.editorSource
    val at = state.selection.min
    val before = state.text.substring(0, at)
    val piece = if (token.endsWith(" ") && before.isNotEmpty() && !before.endsWith("\n") && token.first().isUpperCase()) "\n$token" else token
    state.edit {
        replace(at, state.selection.max, piece)
        selection = androidx.compose.ui.text.TextRange(at + piece.length)
    }
}

/** A program running: its console, the value it asks for typed with the keys, its menu as buttons. */
@Composable
fun ProgramRun(adv: AdvancedViewModel, style: NumberStyle) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val data by adv.data.collectAsState()
    val index = adv.running ?: return
    val program = data.userPrograms.getOrNull(index) ?: return
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ArrowBack, contentDescription = PROGRAMS.get(), modifier = Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tick(); adv.closeRun() }.padding(8.dp))
            Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (adv.finished) QuietButton(onClick = { haptics.tick(); adv.runUser(index) }) { Text(AGAIN.get()) }
            else QuietButton(onClick = { haptics.firm(); adv.stop() }) { Text(STOP.get()) }
            QuietButton(onClick = { haptics.tick(); adv.edit(index) }) { Text(EDIT.get()) }
            Icon(AppIcons.Delete, contentDescription = DELETE.get(), tint = colors.error, modifier = Modifier.size(40.dp).clip(CircleShape).clickable { deleting = true }.padding(8.dp))
        }
        val list = rememberLazyListState()
        LaunchedEffect(adv.console.size) { if (adv.console.isNotEmpty()) list.animateScrollToItem(adv.console.lastIndex) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), state = list) {
            items(adv.console) { line ->
                Text(style.show(line), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 17.sp, color = if (line.startsWith("ERR")) colors.error else colors.onSurface))
            }
            if (adv.finished) item { Text(DONE.get(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        adv.asking?.let { prompt ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(prompt, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = colors.primary, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(8.dp))
                val focus = remember { FocusRequester() }
                LaunchedEffect(prompt) { runCatching { focus.requestFocus() } }
                val output = remember(style) { OutputTransformation { showIn(this, style) { "" } } }
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    BasicTextField(
                        state = adv.answer,
                        outputTransformation = output,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = colors.onSurface),
                        cursorBrush = SolidColor(Gold),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = prompt }
                    )
                }
            }
        }
        if (adv.paused) Text(CONTINUE.get(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        adv.choosing?.let { (title, options) ->
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            options.forEachIndexed { i, o -> QuietButton(onClick = { haptics.tick(); adv.pick(i) }, modifier = Modifier.fillMaxWidth()) { Text("${i + 1}: $o") } }
        }
    }
    if (deleting) ZoneAlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(DELETE_ASK.get().format(program.name)) },
        confirmButton = { QuietButton(onClick = { deleting = false; feelIt(haptics, adv.deleteProgram(index)) }) { Text(DELETE.get(), color = colors.error) } },
        dismissButton = { QuietButton(onClick = { deleting = false }) { Text(CANCEL.get()) } }
    )
}

/** The commands of TI's guide that a lycée program uses most. */
private val TOKENS = listOf(
    "→", ":", "\"", "=", "≠", "<", ">", "≤", "≥", "²", "√(", " and ", " or ", "not(",
    "Prompt ", "Input ", "Disp ", "If ", "Then", "Else", "End", "For(", "While ", "Repeat ", "Lbl ", "Goto ", "Menu(", "Pause", "Stop", "ClrHome"
)

private const val EXAMPLE = "Prompt M,N,D\n6.67E-11*M*N/D²→F\nDisp \"F=\",F"

private val MINE = Words("Mes programmes", "My programs")
private val NEW = Words("Nouveau programme", "New program")
private val LINES = Words("lignes", "lines")
private val NAME = Words("Nom du programme", "Program name")
private val CODE = Words("Code TI-Basic", "TI-Basic code")
private val SAVE = Words("Enregistrer", "Save")
private val CANCEL = Words("Annuler", "Cancel")
private val PROGRAMS = Words("Programmes", "Programs")
private val AGAIN = Words("Relancer", "Run again")
private val STOP = Words("Arrêter", "Stop")
private val EDIT = Words("Modifier", "Edit")
private val DELETE = Words("Supprimer", "Delete")
private val DELETE_ASK = Words("Supprimer %s ?", "Delete %s?")
private val DONE = Words("Terminé", "Done")
private val CONTINUE = Words("= pour continuer", "= to go on")
