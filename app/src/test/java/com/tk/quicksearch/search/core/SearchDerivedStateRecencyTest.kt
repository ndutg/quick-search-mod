package com.tk.quicksearch.search.core

import com.tk.quicksearch.search.models.AppInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDerivedStateRecencyTest {
    @Test
    fun newerLaunchForVisibleAppAppliesRefreshedOrder() {
        val first = app("com.example.first", lastUsedTime = 20L)
        val launched = app("com.example.launched", lastUsedTime = 10L)

        assertTrue(
            SearchDerivedStateDelegate.shouldApplyRefreshedSuggestionOrder(
                currentSuggestions = listOf(first, launched),
                refreshedSuggestions = listOf(launched.copy(lastUsedTime = 30L), first),
            ),
        )
    }

    @Test
    fun newlyVisibleRecentAppAppliesRefreshedOrder() {
        val current = app("com.example.current", lastUsedTime = 20L)
        val launched = app("com.example.launched", lastUsedTime = 30L)

        assertTrue(
            SearchDerivedStateDelegate.shouldApplyRefreshedSuggestionOrder(
                currentSuggestions = listOf(current),
                refreshedSuggestions = listOf(launched, current),
            ),
        )
    }

    @Test
    fun unchangedRecencyKeepsStableStartupOrder() {
        val first = app("com.example.first", lastUsedTime = 20L)
        val second = app("com.example.second", lastUsedTime = 10L)

        assertFalse(
            SearchDerivedStateDelegate.shouldApplyRefreshedSuggestionOrder(
                currentSuggestions = listOf(first, second),
                refreshedSuggestions = listOf(first, second),
            ),
        )
    }

    private fun app(
        packageName: String,
        lastUsedTime: Long,
    ) =
        AppInfo(
            appName = packageName,
            packageName = packageName,
            lastUsedTime = lastUsedTime,
            totalTimeInForeground = 0L,
            firstInstallTime = 0L,
            isSystemApp = false,
        )
}
