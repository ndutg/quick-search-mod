package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyGridVerticalScrollbarTest {
    private val metrics =
            LazyGridScrollbarMetrics(
                    totalItems = 80,
                    scrollFraction = 0f,
                    thumbSizeFraction = 0.15f,
                    columns = 4,
                    rowHeightPx = 100f,
                    scrollRangePx = 1500f,
            )

    @Test
    fun `fraction zero maps to first item`() {
        assertEquals(0 to 0, scrollPositionForFraction(0f, metrics))
    }

    @Test
    fun `fraction maps to row start and pixel offset`() {
        // 0.5 * 1500 = 750px -> row 7 (item 28) plus 50px into that row.
        assertEquals(28 to 50, scrollPositionForFraction(0.5f, metrics))
    }

    @Test
    fun `fraction one maps to end of scroll range`() {
        assertEquals(60 to 0, scrollPositionForFraction(1f, metrics))
    }

    @Test
    fun `thumb top maps to clamped fraction`() {
        assertEquals(0f, thumbFractionForTop(-20f, trackHeightPx = 400f, thumbHeightPx = 100f), 0.001f)
        assertEquals(0.5f, thumbFractionForTop(150f, trackHeightPx = 400f, thumbHeightPx = 100f), 0.001f)
        assertEquals(1f, thumbFractionForTop(500f, trackHeightPx = 400f, thumbHeightPx = 100f), 0.001f)
    }
}
