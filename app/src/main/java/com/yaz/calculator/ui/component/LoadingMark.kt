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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * key sinking whole, to its base, and springing back with a gleam.
 * The icon and the keys are its layers (Modules/icons/calculator: export.py mark),
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
    val base = ImageBitmap.imageResource(R.drawable.keys_mark_base)
    val baseShine = ImageBitmap.imageResource(R.drawable.keys_mark_base_shine)
    val baseSigns = ImageBitmap.imageResource(R.drawable.keys_mark_base_signs)
    val keys = listOf(
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key0), ImageBitmap.imageResource(R.drawable.keys_mark_key0_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key0_sign), 0.1562f, 0.1771f, 0.3238f, 0.3402f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key1), ImageBitmap.imageResource(R.drawable.keys_mark_key1_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key1_sign), 0.5026f, 0.1745f, 0.6708f, 0.3378f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key2), ImageBitmap.imageResource(R.drawable.keys_mark_key2_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key2_sign), 0.1536f, 0.5260f, 0.3221f, 0.6918f),
        Key(ImageBitmap.imageResource(R.drawable.keys_mark_key3), ImageBitmap.imageResource(R.drawable.keys_mark_key3_shine), ImageBitmap.imageResource(R.drawable.keys_mark_key3_sign), 0.5078f, 0.5312f, 0.6719f, 0.6906f)
    )
    val colors = MaterialTheme.colorScheme
    // The launcher icon's own tone (ic_launcher_disc), so the mark is the icon by day and by night.
    val tone = androidx.compose.ui.res.colorResource(android.R.color.system_accent1_500)
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
        // The icon as it is; a pressed key sinks whole, to its base, over its own
        // base in shade: the thin ring it uncovers follows the key's outline.
        image(base, origin, side, tint)
        image(baseShine, origin, side, null)
        image(baseSigns, origin, side, null)
        val unit = side / MARK_PX
        keys.forEachIndexed { i, key ->
            val press = if (running) press(clock - i * STEP_MILLIS) else Press.REST
            if (press.depth <= 0f) return@forEachIndexed
            val topLeft = origin + Offset(key.left * side, key.top * side)
            val width = key.body.width * unit
            image(key.body, topLeft, width, shade(tone, 0.30f * press.depth))
            val pivot = origin + Offset(key.cx * side, key.cy * side)
            withTransform({
                translate(top = side * 0.004f * press.depth)
                scale(press.scale, press.scale, pivot)
            }) {
                image(key.body, topLeft, width, shade(tone, 0.08f * press.depth))
                image(key.shine, topLeft, width, null)
                image(key.sign, topLeft, width, null)
                // The gleam: the key's own light, brighter for a moment.
                if (press.gleam > 0f) image(key.shine, topLeft, width, null, alpha = 0.8f * press.gleam)
            }
        }
    }
}

/** The accent multiplying the grey, darker by [amount]. */
private fun shade(tone: Color, amount: Float) =
    ColorFilter.tint(tone.copy(red = tone.red * (1f - amount), green = tone.green * (1f - amount), blue = tone.blue * (1f - amount)), BlendMode.Modulate)

private class Key(
    val body: ImageBitmap,
    val shine: ImageBitmap,
    val sign: ImageBitmap,
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
    val spring = if (u > 0.5f) 0.02f * sin(2f * PI.toFloat() * u) * (1f - u) else 0f
    val gleam = if (u > 0.55f) max(0f, sin(PI.toFloat() * (u - 0.55f) / 0.45f)) else 0f
    return Press(depth, 1f - 0.035f * depth + spring, gleam)
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

/** The icon's share of the mark. */
private const val PLATE = 0.94f
/** The side the mark's layers were exported at. */
private const val MARK_PX = 384f
private const val LOOP_MILLIS = 1800
private const val PRESS_MILLIS = 420f
private const val STEP_MILLIS = 230
