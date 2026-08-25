package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.fuzzy.FuzzySearchConfig
import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.SecondaryRankingSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchAlgorithmTest {

    @Test
    fun secondaryRankingSignalChangesOrderWithinSameMatchTier() {
        val alpha = app("Alpha Search", "alpha", launchCount = 2, lastUsedTime = 300L)
        val beta = app("Beta Search", "beta", launchCount = 8, lastUsedTime = 100L)
        val source = listOf(beta, alpha)

        fun ranked(signal: SecondaryRankingSignal) =
            AppSearchAlgorithm.findMatches(
                query = "search",
                source = source,
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = signal,
            )

        assertEquals(listOf(alpha, beta), ranked(SecondaryRankingSignal.RECENCY))
        assertEquals(listOf(beta, alpha), ranked(SecondaryRankingSignal.MOST_OPENED))
        assertEquals(listOf(alpha, beta), ranked(SecondaryRankingSignal.NONE))
    }

    @Test
    fun secondaryRankingSignalDoesNotOverridePrimaryMatchQuality() {
        val exactMatch = app("Search", "exact", launchCount = 1, lastUsedTime = 1L)
        val weakerMatch = app("Alpha Search", "weaker", launchCount = 1_000, lastUsedTime = 1_000L)

        SecondaryRankingSignal.entries.forEach { signal ->
            val matches =
                AppSearchAlgorithm.findMatches(
                    query = "search",
                    source = listOf(weakerMatch, exactMatch),
                    limit = 10,
                    fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                    appNicknames = emptyMap(),
                    secondaryRankingSignal = signal,
                )

            assertEquals("Unexpected order for $signal", listOf(exactMatch, weakerMatch), matches)
        }
    }

    @Test
    fun deterministicMatchesRankBeforeFuzzyOnlyMatches() {
        val exactMatch = app("Settings", "settings", launchCount = 1)
        val fuzzyOnlyMatch = app("Settlings", "settlings", launchCount = 100)

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "settings",
                source = listOf(fuzzyOnlyMatch, exactMatch),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.RECENCY,
            )

        assertEquals(listOf(exactMatch, fuzzyOnlyMatch), matches)
    }

    @Test
    fun shortTypoQueriesDoNotReturnNoisyFuzzyMatches() {
        val matches =
            AppSearchAlgorithm.findMatches(
                query = "zz",
                source = listOf(app("Gmail", "gmail")),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun typoQueriesCanFindAppsWhenDeterministicMatchingMisses() {
        val settings = app("Settings", "settings")

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "setings",
                source = listOf(settings),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertEquals(listOf(settings), matches)
    }

    @Test
    fun localizedAppLabelMatchesItsCachedEnglishAlias() {
        val whatsapp = app("واتساب", "whatsapp", searchAliases = listOf("WhatsApp"))

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "whatsapp",
                source = listOf(whatsapp),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertEquals(listOf(whatsapp), matches)
    }

    @Test
    fun disabledFuzzySearchDoesNotReturnTypoOnlyMatches() {
        val settings = app("Settings", "settings")

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "setings",
                source = listOf(settings),
                limit = 10,
                fuzzySearchStrategy =
                    FuzzyAppSearchStrategy(
                        config = FuzzySearchConfig.DEFAULT_APP_CONFIG,
                        isFuzzySearchEnabled = { false },
                    ),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun lowRamDevicesDoNotReturnTypoOnlyMatches() {
        val settings = app("Settings", "settings")

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "setings",
                source = listOf(settings),
                limit = 10,
                fuzzySearchStrategy =
                    FuzzyAppSearchStrategy(
                        config = FuzzySearchConfig.DEFAULT_APP_CONFIG,
                        isLowRamDevice = true,
                    ),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun githubTypoQueriesReturnGithubResult() {
        val github = app("GitHub", "github")

        val deletedCharMatches =
            AppSearchAlgorithm.findMatches(
                query = "Githb",
                source = listOf(github),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )
        val substitutedCharMatches =
            AppSearchAlgorithm.findMatches(
                query = "Githbb",
                source = listOf(github),
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.NONE,
            )

        assertEquals(listOf(github), deletedCharMatches)
        assertEquals(listOf(github), substitutedCharMatches)
    }

    @Test
    fun punctuationSeparatedAppNameMatchesCompactPrefixQuery() {
        val fdroid = app("F-Droid", "fdroid")

        listOf("fdr", "fdro", "fdroi").forEach { query ->
            val matches =
                AppSearchAlgorithm.findMatches(
                    query = query,
                    source = listOf(fdroid),
                    limit = 10,
                    fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                    appNicknames = emptyMap(),
                    secondaryRankingSignal = SecondaryRankingSignal.NONE,
                )

            assertEquals("Expected F-Droid to match $query", listOf(fdroid), matches)
        }
    }

    @Test
    fun typoEligibleCandidatesAreNotStarvedByUnrelatedApps() {
        val github = app("GitHub", "github")
        val unrelatedApps =
            (1..1_300).map { index ->
                app("Camera$index", "camera$index", launchCount = 100 + index)
            }

        val matches =
            AppSearchAlgorithm.findMatches(
                query = "githb",
                source = unrelatedApps + github,
                limit = 10,
                fuzzySearchStrategy = FuzzyAppSearchStrategy(FuzzySearchConfig.DEFAULT_APP_CONFIG),
                appNicknames = emptyMap(),
                secondaryRankingSignal = SecondaryRankingSignal.RECENCY,
            )

        assertTrue(matches.contains(github))
    }

    private fun app(
        appName: String,
        packageSuffix: String,
        searchAliases: List<String> = emptyList(),
        launchCount: Int = 0,
        lastUsedTime: Long = 0L,
    ): AppInfo =
        AppInfo(
            appName = appName,
            packageName = "com.example.$packageSuffix",
            lastUsedTime = lastUsedTime,
            totalTimeInForeground = 0L,
            launchCount = launchCount,
            firstInstallTime = 0L,
            isSystemApp = false,
            searchAliases = searchAliases,
        )
}
