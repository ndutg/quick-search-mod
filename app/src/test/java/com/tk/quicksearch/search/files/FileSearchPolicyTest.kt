package com.tk.quicksearch.search.files

import com.tk.quicksearch.search.utils.SearchQueryContext
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchPolicyTest {
    @Test
    fun multiWordQueryCanBeCoveredByNickname() {
        val covered =
            FileSearchPolicy.areAllQueryTokensCovered(
                query = SearchQueryContext.fromRawQuery("teja passport"),
                displayName = "teja_notes.txt",
                nickname = "passport docs",
                fuzzyMinScore = 72,
                fuzzyMaxEditDistance = 2,
            )

        assertTrue(covered)
    }
}
