package com.yaz.calculator.feature.advanced

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

// A figure inside a page that scrolls, as a map inside a page: the page
// keeps one finger, the figure takes two (or a sideways turn for space).

/** Two fingers move and zoom; one finger is left to the page's scroll. */
suspend fun PointerInputScope.detectTwoFingers(onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom(); val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) onGesture(event.calculateCentroid(useCurrent = false), pan, zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * A scene: one finger moving sideways turns it, moving up or down is the
 * page's; two fingers pinch to zoom and slide up or down to tilt it.
 */
suspend fun PointerInputScope.detectSceneTurns(onTurn: (Float) -> Unit, onTilt: (Float) -> Unit, onZoom: (Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var ours: Boolean? = null
        var travel = Offset.Zero
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                onZoom(event.calculateZoom()); onTilt(event.calculatePan().y)
                event.changes.forEach { it.consume() }
                ours = true
            } else {
                val change = event.changes.first()
                val d = change.positionChange()
                if (ours == null) {
                    travel += d
                    if (travel.getDistance() > viewConfiguration.touchSlop) ours = abs(travel.x) > abs(travel.y)
                }
                if (ours == true) { onTurn(d.x); change.consume() }
                if (ours == false) break
            }
        } while (event.changes.any { it.pressed })
    }
}
