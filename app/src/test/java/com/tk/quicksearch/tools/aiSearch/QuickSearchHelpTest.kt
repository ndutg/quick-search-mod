package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickSearchHelpTest {
    @Test
    fun detectsAliasAtStartOrEnd() {
        assertEquals("how do I pin apps", QuickSearchHelp.questionOrNull("@help how do I pin apps"))
        assertEquals("how do I pin apps", QuickSearchHelp.questionOrNull("  how do I pin apps  @HELP "))
        assertEquals("", QuickSearchHelp.questionOrNull("@help"))
    }

    @Test
    fun ignoresAliasInsideQueryOrWordsStartingWithIt() {
        assertNull(QuickSearchHelp.questionOrNull("how @help do I pin apps"))
        assertNull(QuickSearchHelp.questionOrNull("@helper setup"))
        assertNull(QuickSearchHelp.questionOrNull("email me@help"))
    }

    @Test
    fun aliasRangeCoversOnlyTheAliasToken() {
        assertEquals(2 until 7, QuickSearchHelp.aliasRange("  @help pin apps"))
        assertEquals(9 until 14, QuickSearchHelp.aliasRange("pin apps @Help "))
        assertEquals(0 until 5, QuickSearchHelp.aliasRange("@help"))
        assertNull(QuickSearchHelp.aliasRange("@hel"))
        assertNull(QuickSearchHelp.aliasRange("pin @help apps"))
    }

    @Test
    fun stripAliasKeepsQueriesWithoutAlias() {
        assertEquals("pin apps", QuickSearchHelp.stripAlias("pin apps @help"))
        assertEquals("pin apps", QuickSearchHelp.stripAlias(" pin apps "))
    }
}
