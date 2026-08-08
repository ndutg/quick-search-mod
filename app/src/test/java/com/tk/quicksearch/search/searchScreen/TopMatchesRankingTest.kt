package com.tk.quicksearch.search.searchScreen

import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.SecondaryRankingSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class TopMatchesRankingTest {
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
