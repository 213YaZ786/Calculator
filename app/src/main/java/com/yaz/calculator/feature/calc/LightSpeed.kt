package com.yaz.calculator.feature.calc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yaz.calculator.ui.component.rememberHaptics
import kotlin.math.exp
import kotlin.random.Random

/**
 * c = 299 792 458: a jump to light speed over the whole screen, system bars
 * hidden. The stars come slowly from the middle, speed up and stretch into
 * streaks, the vibration rising with them (the launch of Android 17's egg);
 * holding the screen speeds them further. A white flash brings the result
 * back; a swipe or Back ends it at once.
 */
@Composable
fun LightSpeed(onEnd: () -> Unit) {
    val end by rememberUpdatedState(onEnd)
    Dialog(onDismissRequest = { end() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                w.setDimAmount(0f)
                WindowCompat.getInsetsController(w, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        val haptics = rememberHaptics()
        val accent = MaterialTheme.colorScheme.primary
        val palette = remember(accent) { listOf(Color.White, Color.White, Color(0xFFB8D4FF), Color(0xFF8FB8FF), accent) }
        // Each star: x, y in the plane, z its depth (1 far, 0 at the eye), its colour.
        val stars = remember { FloatArray(STARS * 4).also { a -> for (i in 0 until STARS) spawn(a, i, Random.nextFloat()) } }
        var frame by remember { mutableLongStateOf(0L) }
        var elapsed by remember { mutableFloatStateOf(0f) }
        var speed by remember { mutableFloatStateOf(0f) }
        var holding by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            var nextBuzz = 0f
            while (elapsed < DURATION) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                elapsed += dt
                // Slow drift, then an exponential rush to warp; a hold adds up to 80 %.
                val base = 0.12f + 3.2f / (1f + exp(-5f * (elapsed - 1.6f)))
                speed = base * (1f + 0.8f * holding)
                for (i in 0 until STARS) {
                    stars[i * 4 + 2] -= speed * dt
                    if (stars[i * 4 + 2] <= 0.02f) spawn(stars, i, 1f)
                }
                // The vibration rises with the speed: ticks closer together.
                if (elapsed >= nextBuzz && elapsed < DURATION - FLASH) {
                    haptics.tick()
                    nextBuzz = elapsed + (0.35f / speed).coerceIn(0.035f, 0.4f)
                }
                frame = now
            }
            haptics.done()
            end()
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Light speed" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        holding = 1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            // A swipe ends the jump.
                            if ((change.position - down.position).getDistance() > 48.dp.toPx()) { end(); break }
                            if (!change.pressed) break
                        }
                        holding = 0f
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            drawRect(Color.Black)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val scale = maxOf(size.width, size.height) * 0.5f
            // Streaks grow with the speed: the eye's position a moment earlier.
            val trail = (speed * 0.055f).coerceIn(0.002f, 0.2f)
            for (i in 0 until STARS) {
                val x = stars[i * 4]; val y = stars[i * 4 + 1]; val z = stars[i * 4 + 2]
                val head = Offset(cx + x / z * scale, cy + y / z * scale)
                if (head.x < -50 || head.x > size.width + 50 || head.y < -50 || head.y > size.height + 50) continue
                val zt = z + trail
                val tail = Offset(cx + x / zt * scale, cy + y / zt * scale)
                val near = (1f - z).coerceIn(0f, 1f)
                val color = palette[stars[i * 4 + 3].toInt() % palette.size].copy(alpha = (0.25f + 0.75f * near))
                drawLine(color, tail, head, (0.8f + 2.6f * near * near).dp.toPx(), StrokeCap.Round)
            }
            // The white flash at the end, the result behind it.
            val f = ((elapsed - (DURATION - FLASH)) / FLASH).coerceIn(0f, 1f)
            if (f > 0f) drawRect(Color.White.copy(alpha = if (f < 0.5f) f * 2 else 2 - f * 2))
        }
    }
}

private fun spawn(a: FloatArray, i: Int, z: Float) {
    a[i * 4] = Random.nextFloat() * 2f - 1f
    a[i * 4 + 1] = Random.nextFloat() * 2f - 1f
    a[i * 4 + 2] = z.coerceAtLeast(0.05f)
    a[i * 4 + 3] = Random.nextInt(5).toFloat()
}

private const val STARS = 900
private const val DURATION = 4.2f
private const val FLASH = 0.5f
