package com.tk.quicksearch.search.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesTextUtilsTest {
    @Test
    fun `centers preview around matching line with two lines of context`() {
        val content = (1..7).joinToString("\n") { line -> "line $line" }

        assertEquals(
            "line 2\nline 3\nline 4\nline 5\nline 6",
            NotesTextUtils.matchCenteredPreview(content, "line 4"),
        )
    }

    @Test
    fun `uses normalized matching for preview`() {
        assertEquals(
            "before\nCafé plans\nafter",
            NotesTextUtils.matchCenteredPreview("before\nCafé plans\nafter", "cafe"),
        )
    }

    @Test
    fun `returns null when note body does not match`() {
        assertNull(NotesTextUtils.matchCenteredPreview("note body", "missing"))
    }
}
