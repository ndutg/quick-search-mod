package com.tk.quicksearch.search.appSettings

import com.tk.quicksearch.search.utils.DefaultSearchMatcher
import com.tk.quicksearch.search.utils.SearchMatcher
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchTokenCoveragePolicy

object AppSettingsSearchPolicy {
    data class MatchResult(
        val hasMatch: Boolean,
        val titlePriority: Int,
        val fieldPriority: Int,
    )

    fun evaluateMatch(
        setting: AppSettingResult,
        query: SearchQueryContext,
        matcher: SearchMatcher = DefaultSearchMatcher,
    ): MatchResult {
        val titlePriority = matcher.match(setting.title, query)
        val fieldPriority =
            matcher.matchAny(
                query,
                setting.title,
                setting.keywords.joinToString(" "),
            )

        return MatchResult(
            hasMatch = matcher.isMatch(titlePriority) || matcher.isMatch(fieldPriority),
            titlePriority = titlePriority,
            fieldPriority = fieldPriority,
        )
    }

    fun rankingPriority(matchResult: MatchResult): Int =
        minOf(
            matchResult.titlePriority,
            matchResult.fieldPriority + 2,
        )

    fun areAllQueryTokensCovered(
        query: SearchQueryContext,
        title: String,
        description: String?,
        keywords: List<String>,
        fuzzyMinScore: Int,
        fuzzyMaxEditDistance: Int,
    ): Boolean {
        return SearchTokenCoveragePolicy.areAllTokensCovered(
            query = query,
            primaryText = title,
            supportingText = listOfNotNull(description, keywords.joinToString(" ")).joinToString(" "),
            fuzzyMinScore = fuzzyMinScore,
            fuzzyMaxEditDistance = fuzzyMaxEditDistance,
        )
    }
}
