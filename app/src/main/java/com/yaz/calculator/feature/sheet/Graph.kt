package com.yaz.calculator.feature.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaz.calculator.engine.Curve
import com.yaz.calculator.data.advanced.Window
import com.yaz.calculator.feature.advanced.Plotter
import com.yaz.calculator.feature.advanced.curveColors
import androidx.compose.foundation.layout.fillMaxSize
import com.yaz.calculator.engine.NumberStyle
import com.yaz.calculator.ui.component.DialogPane
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.icon.AppIcons
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A sheet's curve: the line as written and its function. */
class Plotted(val legend: String, val curve: Curve)

/**
 * The sheet's curves on one graph (Apple Math Notes, Desmos): pinch to zoom,
 * drag to move, a touch reads every curve at that x.
 */
@Composable
fun GraphDialog(curves: List<Plotted>, style: NumberStyle, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors.primary) { curveColors(colors.primary) }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogPane {
            Column(Modifier.fillMaxWidth(0.94f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        curves.forEachIndexed { i, c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(10.dp)) { drawCircle(palette[i % palette.size]) }
                                Spacer(Modifier.size(6.dp))
                                Text(c.legend, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                            }
                        }
                    }
                    FloatingAction(AppIcons.Close, "Close", onDismiss)
                }
                Spacer(Modifier.height(8.dp))
                // The advanced mode's plotter: square units, −10 to 10 across.
                BoxWithConstraints(Modifier.fillMaxWidth().height(height).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))) {
                    val half = 10.0 * (maxHeight / maxWidth)
                    var window by remember { mutableStateOf(Window(-10.0, 10.0, -half, half)) }
                    var traced by remember { mutableStateOf<Double?>(null) }
                    Plotter(
                        curves.mapIndexed { i, c -> c.curve to palette[i % palette.size] },
                        window, { window = it }, style, Modifier.fillMaxSize(),
                        traced = traced,
                        onTouch = { x -> traced = x }
                    )
                }
            }
        }
    }
}
