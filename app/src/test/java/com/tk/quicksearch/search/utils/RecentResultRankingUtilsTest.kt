package com.tk.quicksearch.search.utils

import com.tk.quicksearch.search.models.SecondaryRankingSignal
import com.tk.quicksearch.search.searchHistory.RecentSearchEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentResultRankingUtilsTest {
    @Test
    fun buildsIndependentRecencyAndOpenCountScores() {
        val index =
            RecentResultRankingUtils.buildRecencyIndex(
                entries = listOf(RecentSearchEntry.Setting("recent"), RecentSearchEntry.Setting("frequent")),
                openCounts =
                    mapOf(
                        "setting:recent" to 2,
                        "setting:frequent" to 9,
                        "calendar:42" to 4,
                    ),
                lastOpenedTimes = mapOf("calendar:42" to 1234L),
            )

        assertEquals(2, index.settingScores["recent"])
        assertEquals(1, index.settingScores["frequent"])
        assertEquals(2, index.settingOpenCounts["recent"])
        assertEquals(9, index.settingOpenCounts["frequent"])
        assertEquals(4, index.calendarOpenCounts[42L])
        assertEquals(1234L, index.calendarLastOpenedTimes[42L])
    }

    @Test
    fun comparatorUsesSelectedSecondarySignal() {
        val items = listOf(Item("Beta", "frequent"), Item("Alpha", "recent"))
        val recency = mapOf("recent" to 2, "frequent" to 1)
        val opens = mapOf("recent" to 2, "frequent" to 9)

        fun ranked(signal: SecondaryRankingSignal) =
            items
                .map { it to 1 }
                .sortedWith(
                    RecentResultRankingUtils.matchThenRecencyThenAlphabeticalComparator(
                        recencyScores = recency,
                        openCounts = opens,
                        secondaryRankingSignal = signal,
                        keySelector = { it.key },
                        labelSelector = { it.label },
                    ),
                ).map { it.first.key }

        assertEquals(listOf("recent", "frequent"), ranked(SecondaryRankingSignal.RECENCY))
        assertEquals(listOf("frequent", "recent"), ranked(SecondaryRankingSignal.MOST_OPENED))
        assertEquals(listOf("recent", "frequent"), ranked(SecondaryRankingSignal.NONE))
    }

    private data class Item(
        val label: String,
        val key: String,
    )
}
