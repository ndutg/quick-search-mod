package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyGridVerticalScrollbarTest {
    @Test
    fun `track top jumps to first item`() {
        val metrics =
                LazyGridScrollbarMetrics(
                        totalItems = 80,
                        visibleItemCount = 12,
                        firstVisibleIndex = 0,
                        scrollFraction = 0f,
                        thumbSizeFraction = 0.15f,
                )

        assertEquals(0, targetIndexForTrackPosition(y = 0f, trackHeightPx = 400f, metrics = metrics))
    }

    @Test
    fun `track bottom jumps to last scrollable index`() {
        val metrics =
                LazyGridScrollbarMetrics(
                        totalItems = 80,
                        visibleItemCount = 12,
                        firstVisibleIndex = 0,
                        scrollFraction = 0f,
                        thumbSizeFraction = 0.15f,
                )

        assertEquals(
                68,
                targetIndexForTrackPosition(y = 400f, trackHeightPx = 400f, metrics = metrics),
        )
    }

    @Test
    fun `mid track maps proportionally`() {
        val metrics =
                LazyGridScrollbarMetrics(
                        totalItems = 21,
                        visibleItemCount = 1,
                        firstVisibleIndex = 0,
                        scrollFraction = 0f,
                        thumbSizeFraction = 0.1f,
                )

        assertEquals(
                10,
                targetIndexForTrackPosition(y = 200f, trackHeightPx = 400f, metrics = metrics),
        )
    }
}
