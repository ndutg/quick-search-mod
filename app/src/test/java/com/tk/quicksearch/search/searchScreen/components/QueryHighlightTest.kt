package com.tk.quicksearch.search.searchScreen.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryHighlightTest {
    @Test
    fun `highlights a case insensitive phrase`() {
        assertEquals(listOf(5..9), queryHighlightRanges("Open Notes now", "notes"))
    }

    @Test
    fun `maps normalized diacritics back to original text`() {
        assertEquals(listOf(0..3), queryHighlightRanges("Café Notes", "cafe"))
    }

    @Test
    fun `highlights separately matched query tokens`() {
        assertEquals(
            listOf(0..4, 6..11),
            queryHighlightRanges("Quick Search", "search quick"),
        )
    }

    @Test
    fun `highlights word initials for acronym matches`() {
        assertEquals(listOf(0..0, 6..6), queryHighlightRanges("Quick Search", "qs"))
    }

    @Test
    fun `does not highlight a blank query`() {
        assertTrue(queryHighlightRanges("Quick Search", "  ").isEmpty())
    }
}
