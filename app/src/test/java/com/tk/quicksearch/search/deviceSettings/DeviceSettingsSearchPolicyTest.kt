package com.tk.quicksearch.search.deviceSettings

import com.tk.quicksearch.search.utils.SearchQueryContext
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSettingsSearchPolicyTest {
    @Test
    fun multiWordQueryCanBeCoveredByDescriptionOrKeywords() {
        val covered =
            DeviceSettingsSearchPolicy.areAllQueryTokensCovered(
                query = SearchQueryContext.fromRawQuery("teja passport"),
                title = "Teja settings",
                description = "passport controls",
                keywords = listOf("android", "device"),
                nickname = null,
                fuzzyMinScore = 78,
                fuzzyMaxEditDistance = 2,
            )

        assertTrue(covered)
    }
}
