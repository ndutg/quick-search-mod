package com.tk.quicksearch.search.core

import com.tk.quicksearch.search.models.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchViewModelInitialStateFactoryTest {
    @Test
    fun recentLaunchOrderRefreshesStartupSuggestionsWithoutMovingPinnedApps() {
        val pinned = app("com.example.pinned")
        val older = app("com.example.older")
        val launched = app("com.example.launched")

        val reordered =
            SearchViewModelInitialStateFactory.applyRecentLaunchOrderToStartupSuggestions(
                apps = listOf(pinned, older, launched),
                pinnedAppKeys = setOf(pinned.launchCountKey()),
                recentLaunchKeys = listOf(launched.launchCountKey(), older.launchCountKey()),
            )

        assertEquals(listOf(pinned, launched, older), reordered)
    }

    @Test
    fun appsMissingFromRecentLaunchesKeepTheirSnapshotOrder() {
        val first = app("com.example.first")
        val second = app("com.example.second")

        val reordered =
            SearchViewModelInitialStateFactory.applyRecentLaunchOrderToStartupSuggestions(
                apps = listOf(first, second),
                pinnedAppKeys = emptySet(),
                recentLaunchKeys = listOf("com.example.not-in-snapshot"),
            )

        assertEquals(listOf(first, second), reordered)
    }

    private fun app(packageName: String) =
        AppInfo(
            appName = packageName,
            packageName = packageName,
            lastUsedTime = 0L,
            totalTimeInForeground = 0L,
            firstInstallTime = 0L,
            isSystemApp = false,
        )
}
