package com.yaz.calculator.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yaz.calculator.ui.icon.AppIcons

/**
 * Calculator's loading mark, for now the calculate glyph breathing inside
 * an arc that turns, until the app has its own icon to set in motion.
 * [progress] from 0 to 1 draws the arc as far as a task has gone; while
 * [running] it turns on its own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) {
    val transition = rememberInfiniteTransition(label = "mark")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "turn")
    val breath by transition.animateFloat(0.86f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "breath")
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.07f
            val sweep = if (running) 100f else 360f * progress.coerceIn(0f, 1f)
            drawArc(
                color = primary,
                startAngle = if (running) turn else -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                size = androidx.compose.ui.geometry.Size(this.size.width - 2 * stroke, this.size.height - 2 * stroke)
            )
        }
        Icon(
            AppIcons.Calculate,
            contentDescription = null,
            tint = primary,
            modifier = Modifier
                .size(size * 0.5f)
                .graphicsLayer {
                    val s = if (running) breath else 1f
                    scaleX = s
                    scaleY = s
                }
        )
    }
}
