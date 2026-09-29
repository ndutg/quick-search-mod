package com.tk.quicksearch.appNotifications

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNotificationKeywordsTest {
    @Test
    fun addsTrimmedKeywordAtTheEnd() {
        assertEquals(listOf("otp", "delivered"), listOf("otp").plusKeyword("  delivered "))
    }

    @Test
    fun skipsBlankAndRepeatsInAnyCase() {
        val keywords = listOf("Delivered")
        assertEquals(keywords, keywords.plusKeyword("   "))
        assertEquals(keywords, keywords.plusKeyword("delivered"))
    }
}
