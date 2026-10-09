package com.yaz.calculator.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yaz.calculator.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Calculator's loading mark, the launcher icon in motion: its four keys
 * pressed one after the other, + − × ÷, as a calculation typed, each
 * sinking into the plate and springing back with a gleam. The plate and the
 * keys are the icon's layers (Modules/icons/calculator: export.py, mark.py):
 * a grey multiplied by the theme's accent, its white light, the signs in
 * ivory. [progress] is not drawn: the keys keep typing while [running], and
 * rest otherwise.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    @Suppress("UNUSED_PARAMETER") progress: Float = 0f
) {
    val plate = ImageBitmap.imageResource(R.drawable.keys_mark_plate)
    val plateShine = ImageBitmap.imageResource(R.drawable.keys_mark_plate_shine)
    val keys = listOf(
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key0), ImageBitmap.imageResource(R.drawable.keys_mark_key0_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key0_sign), ImageBitmap.imageResource(R.drawable.keys_mark_key0_light), 0.1484f, 0.0833f, 0.3431f, 0.2762f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key1), ImageBitmap.imageResource(R.drawable.keys_mark_key1_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key1_sign), ImageBitmap.imageResource(R.drawable.keys_mark_key1_light), 0.5651f, 0.0833f, 0.7599f, 0.2773f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key2), ImageBitmap.imageResource(R.drawable.keys_mark_key2_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key2_sign), ImageBitmap.imageResource(R.drawable.keys_mark_key2_light), 0.1484f, 0.4974f, 0.3432f, 0.6925f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key3), ImageBitmap.imageResource(R.drawable.keys_mark_key3_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key3_sign), ImageBitmap.imageResource(R.drawable.keys_mark_key3_light), 0.5677f, 0.5000f, 0.7608f, 0.6929f)
    )
    val colors = MaterialTheme.colorScheme
    val night = colors.background.luminance() < 0.5f
    // The plate keeps the icon's deep tone; by night primary is a pale one.
    val tone = if (night) colors.inversePrimary else colors.primary
    val tint = remember(tone) { ColorFilter.tint(tone, BlendMode.Modulate) }
    val transition = rememberInfiniteTransition(label = "typing")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = LOOP_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )

    Canvas(modifier.size(size)) {
        val side = this.size.minDimension * PLATE
        val origin = Offset((this.size.width - side) / 2f, (this.size.height - side) / 2f)
        image(plate, origin, side, tint)
        image(plateShine, origin, side, null)
        keys.forEachIndexed { i, key ->
            val press = if (running) press(clock - i * STEP_MILLIS) else Press.REST
            val keyTint = if (press.depth > 0f) ColorFilter.tint(tone.copy(red = tone.red * (1f - 0.1f * press.depth), green = tone.green * (1f - 0.1f * press.depth), blue = tone.blue * (1f - 0.1f * press.depth)), BlendMode.Modulate) else tint
            val unit = side / MARK_PX
            val topLeft = origin + Offset(key.left * side, key.top * side)
            val pivot = origin + Offset(key.cx * side, key.cy * side)
            withTransform({
                translate(top = side * 0.01f * press.depth)
                scale(press.scale, press.scale, pivot)
            }) {
                image(key.body, topLeft, key.body.width * unit, keyTint)
                image(key.shine, topLeft, key.body.width * unit, null)
                image(key.sign, topLeft, key.body.width * unit, null)
                if (press.gleam > 0f) image(key.light, topLeft, key.body.width * unit, null, alpha = 0.45f * press.gleam)
            }
        }
    }
}

private class Key(
    val body: ImageBitmap,
    val shine: ImageBitmap,
    val sign: ImageBitmap,
    val light: ImageBitmap,
    /** The key's box and centre in the mark, in fractions of its side. */
    val left: Float,
    val top: Float,
    val cx: Float,
    val cy: Float
)

private class Press(val depth: Float, val scale: Float, val gleam: Float) {
    companion object {
        val REST = Press(0f, 1f, 0f)
    }
}

/** A key [t] ms into its turn: down fast, back with a little spring, a gleam at the end. */
private fun press(t: Float): Press {
    val local = ((t % LOOP_MILLIS) + LOOP_MILLIS) % LOOP_MILLIS
    if (local >= PRESS_MILLIS) return Press.REST
    val u = local / PRESS_MILLIS
    val depth = sin(PI.toFloat() * u)
    val spring = if (u > 0.5f) 0.06f * sin(2f * PI.toFloat() * u) * (1f - u) else 0f
    val gleam = if (u > 0.55f) max(0f, sin(PI.toFloat() * (u - 0.55f) / 0.45f)) else 0f
    return Press(depth, 1f - 0.08f * depth + spring, gleam)
}

/** [image], its height in proportion, at [at], [width] wide. */
private fun DrawScope.image(image: ImageBitmap, at: Offset, width: Float, filter: ColorFilter?, alpha: Float = 1f) {
    val w = width.roundToInt().coerceAtLeast(1)
    val h = (width * image.height / image.width).roundToInt().coerceAtLeast(1)
    drawImage(
        image,
        dstOffset = IntOffset(at.x.roundToInt(), at.y.roundToInt()),
        dstSize = IntSize(w, h),
        alpha = alpha,
        colorFilter = filter,
        filterQuality = FilterQuality.High
    )
}

/** The plate's share of the mark, leaving room for the keys' spring. */
private const val PLATE = 0.92f
/** The side the mark's layers were exported at. */
private const val MARK_PX = 384f
private const val LOOP_MILLIS = 1800
private const val PRESS_MILLIS = 420f
private const val STEP_MILLIS = 230
