package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllAppsAlphabetSeekTest {
    @Test
    fun `latin names map to uppercase letters`() {
        assertEquals('A', appSeekLetter("Airbnb"))
        assertEquals('Z', appSeekLetter("zoom"))
    }

    @Test
    fun `digits and symbols map to hash`() {
        assertEquals('#', appSeekLetter("3D Builder"))
        assertEquals('#', appSeekLetter("  10"))
        assertEquals('#', appSeekLetter(""))
    }

    @Test
    fun `first index keeps earliest app for each letter`() {
        val indexes =
                firstIndexBySeekLetter(
                        listOf("Airbnb", "AllTrails", "Chrome", "Clock", "YouTube"),
                )

        assertEquals(0, indexes['A'])
        assertEquals(2, indexes['C'])
        assertEquals(4, indexes['Y'])
        assertNull(indexes['B'])
    }

    @Test
    fun `missing letter jumps forward then backward`() {
        val indexes = firstIndexBySeekLetter(listOf("Airbnb", "YouTube"))

        assertEquals(0, indexForSeekLetter('A', indexes))
        assertEquals(1, indexForSeekLetter('B', indexes))
        assertEquals(1, indexForSeekLetter('Y', indexes))
        assertEquals(1, indexForSeekLetter('Z', indexes))
        assertEquals(0, indexForSeekLetter('#', indexes))
    }

    @Test
    fun `track position maps to letter slots`() {
        val letters = listOf('#', 'A', 'B')

        assertEquals('#', letterAtTrackPosition(y = 0f, trackHeightPx = 90f, letters = letters))
        assertEquals('A', letterAtTrackPosition(y = 45f, trackHeightPx = 90f, letters = letters))
        assertEquals('B', letterAtTrackPosition(y = 89f, trackHeightPx = 90f, letters = letters))
    }

    @Test
    fun `popup tracks the scrollbar thumb center`() {
        val metrics =
                LazyGridScrollbarMetrics(
                        totalItems = 80,
                        visibleItemCount = 12,
                        firstVisibleIndex = 0,
                        scrollFraction = 0.5f,
                        thumbSizeFraction = 0.2f,
                )

        assertEquals(250f, letterPopupThumbCenterY(500f, metrics, minThumbPx = 40f), 0.01f)
    }
}
