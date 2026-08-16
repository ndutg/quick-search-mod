package com.tk.quicksearch.search.searchScreen

import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.SecondaryRankingSignal
import com.tk.quicksearch.search.other.OtherSearchItemId
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchRankingUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TopMatchesRankingTest {
    @Test
    fun otherResultPriorityTracksQueryRelevance() {
        val exactPriority =
            otherSearchItemMatchPriority(OtherSearchItemId.SCREEN_TIME, "screen time")
        val partialPriority =
            otherSearchItemMatchPriority(OtherSearchItemId.SCREEN_TIME, "time")

        org.junit.Assert.assertTrue(exactPriority < partialPriority)
    }

    @Test
    fun selectedSignalControlsTopMatchSecondaryScore() {
        assertEquals(300L, topMatchSecondaryScore(SecondaryRankingSignal.RECENCY, 300L, 2L))
        assertEquals(2L, topMatchSecondaryScore(SecondaryRankingSignal.MOST_OPENED, 300L, 2L))
        assertEquals(0L, topMatchSecondaryScore(SecondaryRankingSignal.NONE, 300L, 2L))
    }

    @Test
    fun secondaryScoreOrdersTopMatchesWithinSameMatchTier() {
        val lowerScore = topMatch("lower", priority = 1, secondaryScore = 2L, index = 0)
        val higherScore = topMatch("higher", priority = 1, secondaryScore = 9L, index = 1)

        assertEquals(listOf(higherScore, lowerScore), rankTopMatches(listOf(lowerScore, higherScore), 10))
    }

    @Test
    fun primaryMatchQualityAlwaysOutranksSecondarySignal() {
        val betterMatch = topMatch("better", priority = 0, secondaryScore = 0L, index = 0)
        val weakerMatch = topMatch("weaker", priority = 1, secondaryScore = Long.MAX_VALUE, index = 1)

        assertEquals(listOf(betterMatch, weakerMatch), rankTopMatches(listOf(weakerMatch, betterMatch), 10))
    }

    @Test
    fun nonePreservesSectionAndSourceOrderingForTies() {
        val laterSection = topMatch("later-section", priority = 1, secondaryScore = 0L, sectionOrder = 2, index = 0)
        val secondInSection = topMatch("second", priority = 1, secondaryScore = 0L, sectionOrder = 1, index = 1)
        val firstInSection = topMatch("first", priority = 1, secondaryScore = 0L, sectionOrder = 1, index = 0)

        assertEquals(
            listOf(firstInSection, secondInSection, laterSection),
            rankTopMatches(listOf(laterSection, secondInSection, firstInSection), 10),
        )
    }

    @Test
    fun topMatchesDeferUpdatesUntilBothLocalSearchBatchesAreSettled() {
        assertEquals(
            true,
            shouldDeferTopMatchesForLocalSearch(
                query = "Gm",
                isAppSearchInProgress = true,
                isSecondarySearchInProgress = false,
            ),
        )
        assertEquals(
            true,
            shouldDeferTopMatchesForLocalSearch(
                query = "Gm",
                isAppSearchInProgress = false,
                isSecondarySearchInProgress = true,
            ),
        )
        assertEquals(
            false,
            shouldDeferTopMatchesForLocalSearch(
                query = "Gm",
                isAppSearchInProgress = false,
                isSecondarySearchInProgress = false,
            ),
        )
    }

    @Test
    fun topMatchesKeepLastSettledResultsUntilTheNewSearchFinishes() {
        val oldMatches = listOf(topMatch("Teams", priority = 0, secondaryScore = 0L, index = 0))
        val partialMatches = listOf(topMatch("Telegram", priority = 0, secondaryScore = 0L, index = 0))
        val newMatches = listOf(topMatch("Tesla", priority = 0, secondaryScore = 0L, index = 0))
        val buffer = SettledSearchResultsBuffer(emptyList<TopMatchItem>())

        assertEquals(oldMatches, buffer.displayedValue("T", oldMatches, isSearchRefreshing = false))
        assertEquals(oldMatches, buffer.displayedValue("Te", partialMatches, isSearchRefreshing = true))
        assertEquals(newMatches, buffer.displayedValue("Te", newMatches, isSearchRefreshing = false))
    }

    @Test
    fun firstSearchDoesNotReuseHomeOrClearedResults() {
        val buffer = SettledSearchResultsBuffer(emptyList<String>())

        assertEquals(emptyList<String>(), buffer.displayedValue("", listOf("Home"), false))
        assertEquals(emptyList<String>(), buffer.displayedValue("T", listOf("Partial"), true))
        assertEquals(listOf("Teams"), buffer.displayedValue("T", listOf("Teams"), false))
    }

    @Test
    fun regularResultsKeepTheWholePreviousSnapshotUntilAllSearchesFinish() {
        val oldResults = listOf("app:Teams", "contact:Teja")
        val appOnlyPartialResults = listOf("app:Telegram")
        val newResults = listOf("app:Telegram", "file:Template")
        val buffer = SettledSearchResultsBuffer(emptyList<String>())

        assertEquals(oldResults, buffer.displayedValue("T", oldResults, false))
        assertEquals(oldResults, buffer.displayedValue("Te", appOnlyPartialResults, true))
        assertEquals(newResults, buffer.displayedValue("Te", newResults, false))
    }

    @Test
    fun refreshingTopMatchesKeepCurrentMatchesAndDropStaleOnes() {
        val currentMatch = topMatch("Gmail", priority = 0, secondaryScore = 0L, index = 0)
        val staleMatch = topMatch("Old shortcut", priority = 4, secondaryScore = 0L, index = 1)

        assertEquals(
            listOf(currentMatch),
            filterTopMatchesForActiveQuery(
                matches = listOf(currentMatch, staleMatch),
                filterStaleCandidates = true,
            ),
        )
        assertEquals(
            listOf(currentMatch, staleMatch),
            filterTopMatchesForActiveQuery(
                matches = listOf(currentMatch, staleMatch),
                filterStaleCandidates = false,
            ),
        )
    }

    @Test
    fun appInitialsRemainAValidTopMatchWhileResultsRefresh() {
        val googleMaps =
            AppInfo(
                appName = "Google Maps",
                packageName = "com.google.android.apps.maps",
                lastUsedTime = 0L,
                totalTimeInForeground = 0L,
                launchCount = 0,
                firstInstallTime = 0L,
                isSystemApp = false,
            )

        val priority =
            appTopMatchPriority(
                app = googleMaps,
                nickname = null,
                query = SearchQueryContext.fromRawQuery("Gm"),
            )

        assertFalse(SearchRankingUtils.isOtherMatch(priority))
    }

    private fun topMatch(
        name: String,
        priority: Int,
        secondaryScore: Long,
        sectionOrder: Int = 0,
        index: Int,
    ): TopMatchItem.App =
        TopMatchItem.App(
            app = AppInfo(
                appName = name,
                packageName = "com.example.$name",
                lastUsedTime = 0L,
                totalTimeInForeground = 0L,
                launchCount = 0,
                firstInstallTime = 0L,
                isSystemApp = false,
            ),
            priority = priority,
            sectionOrder = sectionOrder,
            secondaryScore = secondaryScore,
            index = index,
        )
}
