package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.managers.IconPackDrawableInfo
import com.tk.quicksearch.search.managers.filterIconPackDrawables
import org.junit.Assert.assertEquals
import org.junit.Test

class IconPackDrawableSearchTest {
    @Test
    fun `filters by installed app label when drawable name does not match`() {
        val bundledNotes = IconPackDrawableInfo("blue_tiles", setOf("com.bundled.notes"))
        val unrelated = IconPackDrawableInfo("bundled_logo", setOf("com.example.unrelated"))

        val results =
            filterIconPackDrawables(
                iconDrawables = listOf(unrelated, bundledNotes),
                query = "bundled",
                installedAppLabels = mapOf("com.bundled.notes" to "Bundled Notes"),
            )

        assertEquals(listOf(bundledNotes, unrelated), results)
    }

    @Test
    fun `filters by target package when the mapped app is not installed`() {
        val mappedIcon = IconPackDrawableInfo("blue_tiles", setOf("com.bundled.notes"))

        val results =
            filterIconPackDrawables(
                iconDrawables = listOf(mappedIcon),
                query = "bundled",
                installedAppLabels = emptyMap(),
            )

        assertEquals(listOf(mappedIcon), results)
    }
}
