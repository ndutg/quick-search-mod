package com.tk.quicksearch.search.searchScreen.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingDotInputTest {
    @Test
    fun detectsKeyboardSpaceAfterLeadingDot() {
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = ".", newText = ". m"))
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = "", newText = ". "))
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = ".", newText = ". "))
        assertTrue(hasAutoSpaceAfterLeadingDot(previousText = ".m", newText = ". mk"))
    }

    @Test
    fun removesInsertedSpaceAndShiftsCursor() {
        val result =
            TextFieldValue(text = ". mk", selection = TextRange(4))
                .withoutAutoSpaceAfterLeadingDot(previousText = ".m")
        assertEquals(".mk", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun keepsSpacesTheUserTyped() {
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = ".mkv", newText = ".mkv "))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = "a.", newText = "a. b"))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = ". ", newText = ". m"))
        assertFalse(hasAutoSpaceAfterLeadingDot(previousText = "", newText = "a. b"))
    }
}
