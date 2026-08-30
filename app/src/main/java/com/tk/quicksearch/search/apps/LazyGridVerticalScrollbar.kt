package com.tk.quicksearch.search.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LazyGridScrollbarTouchWidth = DesignTokens.SpacingLarge
private val ThumbWidth = DesignTokens.SpacingXSmall
private val ThumbEndInset = 2.dp
private val MinThumbHeight = DesignTokens.Spacing48

@Composable
internal fun LazyGridVerticalScrollbar(
        state: LazyGridState,
        modifier: Modifier = Modifier,
        onDraggingChange: (Boolean) -> Unit = {},
) {
    val metrics by remember {
        derivedStateOf { scrollbarMetrics(state) }
    }
    if (metrics == null) return

    val currentMetrics = rememberUpdatedState(metrics)
    val currentOnDraggingChange = rememberUpdatedState(onDraggingChange)
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    BoxWithConstraints(
            modifier =
                    modifier
                            .fillMaxHeight()
                            .width(LazyGridScrollbarTouchWidth)
                            .padding(end = ThumbEndInset)
                            .pointerInput(state) {
                                detectTapGestures { offset ->
                                    val snapshot = currentMetrics.value ?: return@detectTapGestures
                                    currentOnDraggingChange.value(true)
                                    coroutineScope.launch {
                                        try {
                                            state.scrollToItem(
                                                    targetIndexForTrackPosition(
                                                            y = offset.y,
                                                            trackHeightPx = size.height.toFloat(),
                                                            metrics = snapshot,
                                                    ),
                                            )
                                        } finally {
                                            currentOnDraggingChange.value(false)
                                        }
                                    }
                                }
                            }
                            .pointerInput(state) {
                                detectVerticalDragGestures(
                                        onDragStart = { currentOnDraggingChange.value(true) },
                                        onDragEnd = { currentOnDraggingChange.value(false) },
                                        onDragCancel = { currentOnDraggingChange.value(false) },
                                ) { change, _ ->
                                    change.consume()
                                    val snapshot = currentMetrics.value ?: return@detectVerticalDragGestures
                                    coroutineScope.launch {
                                        state.scrollToItem(
                                                targetIndexForTrackPosition(
                                                        y = change.position.y,
                                                        trackHeightPx = size.height.toFloat(),
                                                        metrics = snapshot,
                                                ),
                                        )
                                    }
                                }
                            },
            contentAlignment = Alignment.TopEnd,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        if (trackHeightPx <= 0f) return@BoxWithConstraints
        val resolved = metrics ?: return@BoxWithConstraints
        val minThumbPx = with(density) { MinThumbHeight.toPx() }
        val thumbHeightPx =
                (trackHeightPx * resolved.thumbSizeFraction).coerceIn(minThumbPx, trackHeightPx)
        val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = maxOffsetPx * resolved.scrollFraction

        Box(
                modifier =
                        Modifier
                                .fillMaxHeight()
                                .width(ThumbWidth)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(trackColor),
        )
        Box(
                modifier =
                        Modifier
                                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                                .width(ThumbWidth)
                                .height(with(density) { thumbHeightPx.toDp() })
                                .clip(RoundedCornerShape(percent = 50))
                                .background(thumbColor),
        )
    }
}

internal data class LazyGridScrollbarMetrics(
        val totalItems: Int,
        val visibleItemCount: Int,
        val firstVisibleIndex: Int,
        val scrollFraction: Float,
        val thumbSizeFraction: Float,
)

internal fun scrollbarMetrics(state: LazyGridState): LazyGridScrollbarMetrics? {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return null

    val visibleItemCount = visibleItems.size
    val canScroll = state.canScrollForward || state.canScrollBackward
    val contentOverflows = visibleItemCount < totalItems
    if (!canScroll && !contentOverflows) return null

    val scrollableRange = (totalItems - visibleItemCount).coerceAtLeast(1)
    val firstVisibleIndex = state.firstVisibleItemIndex.coerceIn(0, totalItems - 1)
    val scrollFraction = (firstVisibleIndex.toFloat() / scrollableRange.toFloat()).coerceIn(0f, 1f)
    val thumbSizeFraction = (visibleItemCount.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
    return LazyGridScrollbarMetrics(
            totalItems = totalItems,
            visibleItemCount = visibleItemCount,
            firstVisibleIndex = firstVisibleIndex,
            scrollFraction = scrollFraction,
            thumbSizeFraction = thumbSizeFraction,
    )
}

internal fun targetIndexForTrackPosition(
        y: Float,
        trackHeightPx: Float,
        metrics: LazyGridScrollbarMetrics,
): Int {
    if (trackHeightPx <= 0f || metrics.totalItems <= 0) return 0
    val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
    val maxIndex = (metrics.totalItems - metrics.visibleItemCount).coerceAtLeast(0)
    return (fraction * maxIndex).roundToInt().coerceIn(0, metrics.totalItems - 1)
}
