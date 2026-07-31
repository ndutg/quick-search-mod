package com.tk.quicksearch.search.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactInitialsTest {
    @Test
    fun emojiOnlyNamePartIsSkipped() {
        assertEquals("ES", contactInitials("E 💎 Smith"))
    }

    @Test
    fun emojiBeforeAWordIsIgnored() {
        assertEquals("ES", contactInitials("E 💎Smith"))
    }

    @Test
    fun firstTwoRegularNamePartsArePreserved() {
        assertEquals("BT", contactInitials("Bala Teja Karlapudi"))
    }

    @Test
    fun emojiOnlyNameHasNoInitials() {
        assertEquals("", contactInitials("💎 😀"))
    }
}
