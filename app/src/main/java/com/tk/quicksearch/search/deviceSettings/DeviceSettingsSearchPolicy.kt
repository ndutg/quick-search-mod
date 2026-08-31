package com.tk.quicksearch.search.deviceSettings

import com.tk.quicksearch.search.utils.DefaultSearchMatcher
import com.tk.quicksearch.search.utils.SearchMatcher
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchTextCache
import com.tk.quicksearch.search.utils.SearchTokenCoveragePolicy

object DeviceSettingsSearchPolicy {
    data class MatchResult(
        val hasMatch: Boolean,
        val hasNicknameMatch: Boolean,
        val titleOrNicknamePriority: Int,
        val fieldPriority: Int,
    )

    fun evaluateMatch(
        setting: DeviceSetting,
        query: SearchQueryContext,
        matchingNicknameIds: Set<String>,
        nicknameCache: Map<String, String?>,
        matcher: SearchMatcher = DefaultSearchMatcher,
    ): MatchResult {
        val nickname = nicknameCache[setting.id]
        val titleOrNicknamePriority = matcher.match(setting.title, query, nickname)
        val hasNicknameMatch = matchingNicknameIds.contains(setting.id)

        val fieldPriority =
            matcher.matchAny(
                query,
                setting.title,
                setting.description.orEmpty(),
                setting.keywords.joinToString(" "),
            )

        return MatchResult(
            hasMatch =
                matcher.isMatch(titleOrNicknamePriority) ||
                    matcher.isMatch(fieldPriority) ||
                    hasNicknameMatch,
            hasNicknameMatch = hasNicknameMatch,
            titleOrNicknamePriority = titleOrNicknamePriority,
            fieldPriority = fieldPriority,
        )
    }

    fun rankingPriority(matchResult: MatchResult): Int {
        return minOf(
            matchResult.titleOrNicknamePriority,
            matchResult.fieldPriority + 2,
        )
    }

    fun areAllQueryTokensCovered(
        query: SearchQueryContext,
        title: String,
        description: String?,
        keywords: List<String>,
        nickname: String?,
        fuzzyMinScore: Int,
        fuzzyMaxEditDistance: Int,
        textCache: SearchTextCache? = null,
    ): Boolean {
        val supportingText = listOfNotNull(description, keywords.joinToString(" "), nickname).joinToString(" ")
        if (textCache != null) {
            return SearchTokenCoveragePolicy.areAllTokensCovered(
                query = query,
                primaryText = textCache.prepare(title),
                supportingText = supportingText.takeIf { it.isNotBlank() }?.let(textCache::prepare),
                fuzzyMinScore = fuzzyMinScore,
                fuzzyMaxEditDistance = fuzzyMaxEditDistance,
            )
        }
        return SearchTokenCoveragePolicy.areAllTokensCovered(
            query = query,
            primaryText = title,
            supportingText = supportingText,
            fuzzyMinScore = fuzzyMinScore,
            fuzzyMaxEditDistance = fuzzyMaxEditDistance,
        )
    }
}
