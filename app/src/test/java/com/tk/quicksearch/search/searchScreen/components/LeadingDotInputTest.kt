package com.tk.quicksearch.search.searchScreen.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingDotInputTest {
    @Test
    fun detectsKeyboardSpaceAfterLeadingDot() {
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = ".", newText = ". m"))
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = "", newText = ". "))
    }

    @Test
    fun keepsSpacesTheUserTyped() {
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = ".", newText = ". "))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = ".mkv", newText = ".mkv "))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = "a.", newText = "a. b"))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = ". ", newText = ". m"))
    }
}
