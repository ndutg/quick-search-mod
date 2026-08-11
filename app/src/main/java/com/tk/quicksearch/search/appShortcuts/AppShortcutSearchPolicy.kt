package com.tk.quicksearch.search.appShortcuts

import com.tk.quicksearch.search.utils.DefaultSearchMatcher
import com.tk.quicksearch.search.utils.SearchMatcher
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchTokenCoveragePolicy

object AppShortcutSearchPolicy {
    fun matchPriority(
        displayName: String,
        appLabel: String,
        nickname: String?,
        query: SearchQueryContext,
        matcher: SearchMatcher = DefaultSearchMatcher,
    ): Int {
        val displayNamePriority = matcher.match(displayName, query, nickname)
        val appLabelPriority = matcher.match(appLabel, query)
        val combinedPriority =
            matcher.matchAny(
                query,
                "$displayName $appLabel",
                "$appLabel $displayName",
                *buildCombinedNicknameFields(nickname, appLabel),
            )
        return minOf(displayNamePriority, appLabelPriority, combinedPriority)
    }

    fun areAllQueryTokensCovered(
        query: SearchQueryContext,
        displayName: String,
        appLabel: String,
        nickname: String?,
        fuzzyMinScore: Int,
        fuzzyMaxEditDistance: Int,
    ): Boolean {
        return SearchTokenCoveragePolicy.areAllTokensCovered(
            query = query,
            primaryText = displayName,
            supportingText = listOfNotNull(appLabel, nickname).joinToString(" "),
            fuzzyMinScore = fuzzyMinScore,
            fuzzyMaxEditDistance = fuzzyMaxEditDistance,
        )
    }

    private fun buildCombinedNicknameFields(
        nickname: String?,
        appLabel: String,
    ): Array<String> {
        val normalizedNickname = nickname?.trim().orEmpty()
        if (normalizedNickname.isBlank()) return emptyArray()
        return arrayOf(
            "$normalizedNickname $appLabel",
            "$appLabel $normalizedNickname",
        )
    }
}
