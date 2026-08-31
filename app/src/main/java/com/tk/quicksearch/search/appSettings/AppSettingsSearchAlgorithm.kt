package com.tk.quicksearch.search.appSettings

import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.fuzzy.FuzzySearchPerformanceLogger
import com.tk.quicksearch.search.fuzzy.FuzzySearchPolicyResolver
import com.tk.quicksearch.search.utils.FuzzyMatcher
import com.tk.quicksearch.search.utils.DefaultSearchMatcher
import com.tk.quicksearch.search.utils.RecentResultRankingUtils
import com.tk.quicksearch.search.utils.SearchMatcher
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchTextCache
import com.tk.quicksearch.search.models.SecondaryRankingSignal

private const val FUZZY_CANDIDATE_BUFFER_MULTIPLIER = 12

object AppSettingsSearchAlgorithm {
    fun search(
        fullList: List<AppSettingResult>,
        queryContext: SearchQueryContext,
        recentSettingScores: Map<String, Int> = emptyMap(),
        settingOpenCounts: Map<String, Int> = emptyMap(),
        secondaryRankingSignal: SecondaryRankingSignal = SecondaryRankingSignal.DEFAULT,
        resultLimit: Int = 25,
        enableFuzzyMatching: Boolean = false,
        isLowRamDevice: Boolean = false,
        matcher: SearchMatcher = DefaultSearchMatcher,
        textCache: SearchTextCache = SearchTextCache(),
    ): List<AppSettingResult> {
        if (fullList.isEmpty()) return emptyList()
        if (queryContext.normalizedQuery.isBlank()) return emptyList()

        val exactMatches =
            fullList
                .asSequence()
                .mapNotNull { setting ->
                    val matchResult =
                        AppSettingsSearchPolicy.evaluateMatch(
                            setting = setting,
                            query = queryContext,
                            matcher = matcher,
                        )
                    if (!matchResult.hasMatch) return@mapNotNull null
                    val priority = AppSettingsSearchPolicy.rankingPriority(matchResult)
                    setting to priority
                }.sortedWith(
                    RecentResultRankingUtils.matchThenRecencyThenAlphabeticalComparator(
                        recencyScores = recentSettingScores,
                        openCounts = settingOpenCounts,
                        secondaryRankingSignal = secondaryRankingSignal,
                        keySelector = { it.id },
                        labelSelector = { it.title },
                    ),
                ).take(resultLimit)
                .map { it.first }
                .toList()

        if (!enableFuzzyMatching) return exactMatches
        if (exactMatches.size >= resultLimit) return exactMatches

        val fuzzyPolicy =
            FuzzySearchPolicyResolver.effectivePolicy(
                section = SearchSection.APP_SETTINGS,
                query = queryContext.normalizedQuery,
                isLowRamDevice = isLowRamDevice,
            )
        if (!fuzzyPolicy.enabled) return exactMatches

        val remainingSlots = (resultLimit - exactMatches.size).coerceAtLeast(0)
        if (remainingSlots == 0) return exactMatches
        val exactMatchIds = exactMatches.map { it.id }.toSet()
        val fuzzyCandidateBudget = (remainingSlots * FUZZY_CANDIDATE_BUFFER_MULTIPLIER).coerceAtLeast(remainingSlots)
        val fuzzyCandidateCount =
            minOf(
                fullList.size,
                fuzzyPolicy.candidateLimit,
                fuzzyCandidateBudget,
            )
        if (fuzzyCandidateCount == 0) return exactMatches
        val fuzzyMatches =
            FuzzySearchPerformanceLogger.measure(
                section = SearchSection.APP_SETTINGS,
                query = queryContext.normalizedQuery,
                candidateCount = fuzzyCandidateCount,
            ) {
            fullList
                .asSequence()
                .take(fuzzyCandidateCount)
                .filterNot { exactMatchIds.contains(it.id) }
                .mapNotNull { setting ->
                    val normalizedTitle = textCache.prepare(setting.title).normalized
                    val supportingText =
                        buildString {
                            append(setting.description.orEmpty())
                            if (setting.keywords.isNotEmpty()) {
                                append(' ')
                                append(setting.keywords.joinToString(" "))
                            }
                        }
                    val normalizedSupportingText = textCache.prepare(supportingText).normalized
                    val fuzzyScore =
                        FuzzyMatcher.score(
                            query = queryContext.normalizedQuery,
                            primaryTarget = normalizedTitle,
                            secondaryTarget = normalizedSupportingText,
                            maxEditDistance = fuzzyPolicy.maximumEditDistance,
                        )
                    if (fuzzyScore < fuzzyPolicy.minimumScore) {
                        null
                    } else if (
                        !AppSettingsSearchPolicy.areAllQueryTokensCovered(
                            query = queryContext,
                            title = setting.title,
                            description = setting.description,
                            keywords = setting.keywords,
                            fuzzyMinScore = fuzzyPolicy.minimumScore,
                            fuzzyMaxEditDistance = fuzzyPolicy.maximumEditDistance,
                            textCache = textCache,
                        )
                    ) {
                        null
                    } else {
                        Triple(setting, fuzzyScore, setting.title.lowercase())
                    }
                }.sortedWith(
                    compareByDescending<Triple<AppSettingResult, Int, String>> { it.second }
                        .thenByDescending {
                            when (secondaryRankingSignal) {
                                SecondaryRankingSignal.RECENCY -> recentSettingScores[it.first.id] ?: 0
                                SecondaryRankingSignal.MOST_OPENED -> settingOpenCounts[it.first.id] ?: 0
                                SecondaryRankingSignal.NONE -> 0
                            }
                        }
                        .thenBy { it.third },
                ).map { it.first }
                .toList()
            }

        return exactMatches + fuzzyMatches.take(remainingSlots)
    }
}
