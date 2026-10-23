package com.tk.quicksearch.search.searchScreen.searchRoute

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val TWO_FINGER_SWIPE_THRESHOLD = 64.dp

/**
 * Detects a vertical swipe made with two fingers. While two fingers are down, their events are
 * consumed in the initial pass so the results list doesn't scroll and fire single-finger gestures.
 */
@Composable
internal fun Modifier.twoFingerVerticalSwipe(
    enabled: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    return pointerInput(Unit) {
        val thresholdPx = TWO_FINGER_SWIPE_THRESHOLD.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startX = 0f
            var startY = 0f
            var isTwoFinger = false
            var handled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val fingers = pressed.take(2)
                    val centerX = fingers.sumOf { it.position.x.toDouble() }.toFloat() / 2f
                    val centerY = fingers.sumOf { it.position.y.toDouble() }.toFloat() / 2f
                    if (!isTwoFinger) {
                        isTwoFinger = true
                        startX = centerX
                        startY = centerY
                    }
                    val dy = centerY - startY
                    if (!handled && abs(dy) >= thresholdPx && abs(dy) > abs(centerX - startX)) {
                        handled = true
                        if (dy < 0) currentOnSwipeUp() else currentOnSwipeDown()
                    }
                }
                if (isTwoFinger) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
