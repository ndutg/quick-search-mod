package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.fuzzy.FuzzySearchPerformanceLogger
import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.SecondaryRankingSignal
import com.tk.quicksearch.search.utils.SearchQueryContext
import java.util.Locale

object AppSearchAlgorithm {
    fun findMatches(
        query: String,
        source: List<AppInfo>,
        limit: Int,
        fuzzySearchStrategy: FuzzyAppSearchStrategy,
        appNicknames: Map<String, String>,
        secondaryRankingSignal: SecondaryRankingSignal,
    ): List<AppInfo> {
        if (query.isBlank()) return emptyList()
        return findMatches(
            queryContext = SearchQueryContext.fromRawQuery(query),
            source = source,
            limit = limit,
            fuzzySearchStrategy = fuzzySearchStrategy,
            appNicknames = appNicknames,
            secondaryRankingSignal = secondaryRankingSignal,
        )
    }

    fun findMatches(
        queryContext: SearchQueryContext,
        source: List<AppInfo>,
        limit: Int,
        fuzzySearchStrategy: FuzzyAppSearchStrategy,
        appNicknames: Map<String, String>,
        secondaryRankingSignal: SecondaryRankingSignal,
    ): List<AppInfo> {
        if (queryContext.normalizedQuery.isBlank()) return emptyList()

        val preparedFuzzyQuery =
            fuzzySearchStrategy.prepareQuery(queryContext.normalizedQuery)
        val canUseFuzzySearch = preparedFuzzyQuery.policy.enabled
        val fuzzyCandidateLimit =
            if (canUseFuzzySearch) {
                preparedFuzzyQuery.policy.candidateLimit
            } else {
                0
            }
        var fuzzyCandidatesScored = 0

        val searchBlock = {
            source
                .asSequence()
                .mapNotNull { app ->
                    calculateAppMatch(
                        app = app,
                        queryContext = queryContext,
                        fuzzySearchStrategy = fuzzySearchStrategy,
                        preparedFuzzyQuery = preparedFuzzyQuery,
                        appNicknames = appNicknames,
                        canScoreFuzzyCandidate = {
                            if (fuzzyCandidatesScored >= fuzzyCandidateLimit) {
                                false
                            } else {
                                fuzzyCandidatesScored += 1
                                true
                            }
                        },
                    )
                }.sortedWith(createAppComparator(secondaryRankingSignal))
                .map { it.app }
                .take(limit)
                .toList()
        }

        if (!canUseFuzzySearch) return searchBlock()

        return FuzzySearchPerformanceLogger.measure(
            section = SearchSection.APPS,
            query = queryContext.normalizedQuery,
            candidateCount = minOf(source.size, fuzzyCandidateLimit),
            block = searchBlock,
        )
    }

    private data class AppMatch(
        val app: AppInfo,
        val priority: Int,
        val fuzzyScore: Int,
        val isFuzzy: Boolean,
    )

    private fun calculateAppMatch(
        app: AppInfo,
        queryContext: SearchQueryContext,
        fuzzySearchStrategy: FuzzyAppSearchStrategy,
        preparedFuzzyQuery: PreparedAppFuzzyQuery,
        appNicknames: Map<String, String>,
        canScoreFuzzyCandidate: () -> Boolean,
    ): AppMatch? {
        val nickname = appNicknames[app.packageName]
        val initials = AppSearchInitials.initialsFor(app)
        val priority = AppSearchPolicy.matchPriority(app.appName, nickname, queryContext, initials)
        if (AppSearchPolicy.hasMatch(priority)) {
            if (
                !AppSearchPolicy.areAllQueryTokensCovered(
                    queryContext,
                    app.appName,
                    nickname,
                    initials,
                    fuzzySearchStrategy,
                )
            ) {
                return null
            }
            return AppMatch(app, priority, 0, false)
        }

        if (
            !fuzzySearchStrategy.isTypoEligibleCandidate(
                preparedQuery = preparedFuzzyQuery,
                appName = app.appName,
                nickname = nickname,
                initials = initials,
            )
        ) {
            return null
        }

        if (!canScoreFuzzyCandidate()) return null

        val match =
            fuzzySearchStrategy.scoreEligibleCandidate(
                preparedQuery = preparedFuzzyQuery,
                app = app,
                nickname = appNicknames[app.packageName],
                initials = initials,
            )

        return match?.let {
            if (
                !AppSearchPolicy.areAllQueryTokensCovered(
                    queryContext,
                    app.appName,
                    nickname,
                    initials,
                    fuzzySearchStrategy,
                )
            ) {
                return null
            }
            AppMatch(app, it.priority, it.score, true)
        }
    }

    private fun createAppComparator(secondaryRankingSignal: SecondaryRankingSignal): Comparator<AppMatch> {
        return Comparator { first, second ->
            if (first.isFuzzy != second.isFuzzy) {
                return@Comparator if (first.isFuzzy) 1 else -1
            }

            if (!first.isFuzzy) {
                val priorityCompare = first.priority.compareTo(second.priority)
                if (priorityCompare != 0) {
                    return@Comparator priorityCompare
                }
                return@Comparator compareBySecondarySignalOrName(first.app, second.app, secondaryRankingSignal)
            }

            val fuzzyCompare = second.fuzzyScore.compareTo(first.fuzzyScore)
            if (fuzzyCompare != 0) {
                return@Comparator fuzzyCompare
            }
            compareBySecondarySignalOrName(first.app, second.app, secondaryRankingSignal)
        }
    }

    private fun compareBySecondarySignalOrName(
        first: AppInfo,
        second: AppInfo,
        secondaryRankingSignal: SecondaryRankingSignal,
    ): Int {
        val secondaryCompare =
            when (secondaryRankingSignal) {
                SecondaryRankingSignal.RECENCY -> second.lastUsedTime.compareTo(first.lastUsedTime)
                SecondaryRankingSignal.MOST_OPENED -> second.launchCount.compareTo(first.launchCount)
                SecondaryRankingSignal.NONE -> 0
            }
        return secondaryCompare.takeIf { it != 0 }
            ?: first.appName
                .lowercase(Locale.getDefault())
                .compareTo(second.appName.lowercase(Locale.getDefault()))
    }
}
