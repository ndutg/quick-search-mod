package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.utils.DefaultSearchMatcher
import com.tk.quicksearch.search.utils.SearchMatcher
import com.tk.quicksearch.search.utils.SearchQueryContext

object AppSearchPolicy {
    // Keep in sync with SearchRankingUtils priority semantics:
    // 1 = startsWith, 2 = wordStartsWith, 3 = contains, 4 = no match.
    private const val INITIALS_MATCH_PRIORITY = 3
    private const val ABBREVIATION_MATCH_PRIORITY = 3

    fun matchPriority(
        appName: String,
        searchAliases: List<String> = emptyList(),
        nickname: String?,
        query: SearchQueryContext,
        initials: List<String> = emptyList(),
        abbreviationTargets: List<String> = emptyList(),
        matcher: SearchMatcher = DefaultSearchMatcher,
    ): Int {
        val basePriority = matcher.match(primaryText = appName, query = query, nickname = nickname)
        val bestTextPriority =
            searchAliases.fold(basePriority) { priority, alias ->
                minOf(priority, matcher.match(primaryText = alias, query = query))
            }
        if (matcher.isMatch(bestTextPriority)) return bestTextPriority

        if (initials.isNotEmpty() && matcher.isMatch(matcher.matchAny(query, *initials.toTypedArray()))) {
            return INITIALS_MATCH_PRIORITY
        }
        if (AppNameAbbreviation.matches(query, abbreviationTargets, nickname)) {
            return ABBREVIATION_MATCH_PRIORITY
        }
        return bestTextPriority
    }

    fun hasMatch(
        priority: Int,
        matcher: SearchMatcher = DefaultSearchMatcher,
    ): Boolean = matcher.isMatch(priority)

    fun areAllQueryTokensCovered(
        query: SearchQueryContext,
        preparedTokens: Map<String, PreparedAppFuzzyQuery>,
        appName: String,
        searchAliases: List<String> = emptyList(),
        nickname: String?,
        initials: List<String>,
        fuzzySearchStrategy: FuzzyAppSearchStrategy,
    ): Boolean {
        if (query.tokens.size <= 1) return true
        return query.tokens.all { token ->
            fuzzySearchStrategy.isTokenCoveredByApp(
                preparedToken = preparedTokens.getValue(token),
                appName = appName,
                searchAliases = searchAliases,
                nickname = nickname,
                initials = initials,
            )
        }
    }
}
