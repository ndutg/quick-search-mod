package com.tk.quicksearch.search.apps

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import com.tk.quicksearch.shared.util.hapticConfirm

/**
 * Gestures for a reorderable pinned grid tile: tap clicks, long press without moving opens the
 * options menu, and long press then drag reorders. Returns [Modifier] when dragging is disabled.
 */
@Composable
internal fun rememberPinnedGridDragModifier(
        key: Any,
        onClick: () -> Unit,
        onShowOptionsChange: (Boolean) -> Unit,
        onLocalDraggingChange: (Boolean) -> Unit,
        onPinnedDragStart: (() -> Unit)?,
        onPinnedDrag: ((Float, Float) -> Unit)?,
        onPinnedDragEnd: (() -> Unit)?,
): Modifier {
    val view = LocalView.current
    val currentClick by rememberUpdatedState(onClick)
    val currentShowOptionsChange by rememberUpdatedState(onShowOptionsChange)
    val currentLocalDraggingChange by rememberUpdatedState(onLocalDraggingChange)
    val currentPinnedDragStart by rememberUpdatedState(onPinnedDragStart)
    val currentPinnedDrag by rememberUpdatedState(onPinnedDrag)
    val currentPinnedDragEnd by rememberUpdatedState(onPinnedDragEnd)
    if (onPinnedDragStart == null || onPinnedDrag == null || onPinnedDragEnd == null) {
        return Modifier
    }
    return Modifier.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                if (currentEvent.changes.any { it.id == down.id && it.changedToUp() }) {
                    currentEvent.changes.forEach { it.consume() }
                    hapticConfirm(view)()
                    currentClick()
                }
                return@awaitEachGesture
            }
            var overSlop = Offset.Zero
            val drag =
                    awaitTouchSlopOrCancellation(longPress.id) { change, dragAmount ->
                        change.consume()
                        overSlop = dragAmount
                    }
            if (drag == null) {
                currentShowOptionsChange(true)
                currentEvent.changes.forEach { it.consume() }
                return@awaitEachGesture
            }

            currentShowOptionsChange(false)
            currentLocalDraggingChange(true)
            currentPinnedDragStart?.invoke()
            try {
                if (overSlop != Offset.Zero) {
                    currentPinnedDrag?.invoke(overSlop.x, overSlop.y)
                }
                drag(drag.id) { change ->
                    val dragAmount = change.positionChange()
                    change.consume()
                    currentPinnedDrag?.invoke(dragAmount.x, dragAmount.y)
                }
            } finally {
                currentLocalDraggingChange(false)
                currentPinnedDragEnd?.invoke()
            }
        }
    }
}
