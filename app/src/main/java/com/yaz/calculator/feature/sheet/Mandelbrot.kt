package com.yaz.calculator.feature.sheet

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaz.calculator.feature.advanced.Gold
import com.yaz.calculator.ui.component.DialogPane
import com.yaz.calculator.ui.component.FloatingAction
import com.yaz.calculator.ui.icon.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ln

/** zₙ₊₁ = zₙ² + c: the Mandelbrot set, pinch to zoom, drag to move, drawn again off the main thread. */
@Composable
fun MandelbrotDialog(onDismiss: () -> Unit) {
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogPane {
            Column(Modifier.fillMaxWidth(0.94f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("zₙ₊₁ = zₙ² + c", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FloatingAction(AppIcons.Close, "Close", onDismiss)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(height).padding(top = 8.dp).clip(RoundedCornerShape(20.dp))) {
                    // The view: centre and width in the complex plane.
                    var cx by remember { mutableStateOf(-0.6) }
                    var cy by remember { mutableStateOf(0.0) }
                    var span by remember { mutableStateOf(3.2) }
                    var picture by remember { mutableStateOf<Bitmap?>(null) }
                    val wPx = constraints.maxWidth / 3
                    val hPx = constraints.maxHeight / 3
                    LaunchedEffect(cx, cy, span, ink, accent) {
                        delay(60)
                        picture = withContext(Dispatchers.Default) { render(wPx, hPx, cx, cy, span, ink, accent) }
                    }
                    picture?.let {
                        Image(
                            it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Mandelbrot set" }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val perPx = span / size.width
                                        cx -= pan.x * perPx
                                        cy -= pan.y * perPx
                                        span = (span / zoom).coerceIn(1e-12, 6.0)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

/** Each pixel's escape time, smoothed, coloured from the gold outside to the theme's ink inside. */
private fun render(w: Int, h: Int, cx: Double, cy: Double, span: Double, ink: Int, accent: Int): Bitmap {
    val pixels = IntArray(w * h)
    val maxIter = (200 + 60 * ln(3.2 / span).coerceAtLeast(0.0)).toInt().coerceAtMost(3000)
    val gold = Gold.toArgb()
    for (py in 0 until h) {
        if (Thread.currentThread().isInterrupted) break
        val ci = cy + (py - h / 2.0) * span / w
        for (px in 0 until w) {
            val cr = cx + (px - w / 2.0) * span / w
            var zr = 0.0; var zi = 0.0; var n = 0
            while (n < maxIter && zr * zr + zi * zi <= 16) {
                val t = zr * zr - zi * zi + cr
                zi = 2 * zr * zi + ci
                zr = t
                n++
            }
            pixels[py * w + px] = if (n >= maxIter) ink else {
                val smooth = n + 1 - ln(ln(Math.sqrt(zr * zr + zi * zi))) / ln(2.0)
                val f = (smooth / 40.0 % 1.0).toFloat()
                // Bands of accent and gold, stronger near the set, fading far out.
                val near = Math.sqrt((smooth / 30.0).coerceIn(0.0, 1.0))
                blend(accent, gold, if (f < 0.5f) f * 2 else 2 - f * 2) and 0x00FFFFFF or ((0x18 + (0xE7 * near).toInt()) shl 24)
            }
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(x: Int, s: Int) = (x shr s) and 0xFF
    fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt()
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
