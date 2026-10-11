package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.utils.NicknameUtils
import com.tk.quicksearch.search.utils.SearchQueryContext
import com.tk.quicksearch.search.utils.SearchTextNormalizer

/**
 * Matches app names and nicknames abbreviated by keeping their start and end and skipping the
 * middle, e.g. "oban" -> "Obsidian" or "myes" -> nickname "MyNotes".
 */
object AppNameAbbreviation {
    private const val MIN_QUERY_LENGTH = 4
    private const val MIN_EDGE_LENGTH = 2

    // Skipping a single character is a typo, which stays with fuzzy search and its settings.
    private const val MIN_SKIPPED_LENGTH = 2

    /** Compact (separator-free, normalized) app name and aliases, precomputed per app. */
    fun targetsFor(app: AppInfo): List<String> =
        sequenceOf(app.appName)
            .plus(app.searchAliases.asSequence())
            .map(::compact)
            .filter { it.length >= MIN_QUERY_LENGTH + MIN_SKIPPED_LENGTH }
            .distinct()
            .toList()

    fun matches(
        query: SearchQueryContext,
        targets: List<String>,
        nickname: String?,
    ): Boolean {
        if (query.tokens.size != 1) return false
        val compactQuery = query.compactQuery
        if (compactQuery.length < MIN_QUERY_LENGTH) return false
        if (targets.any { matchesTarget(compactQuery, it) }) return true
        return NicknameUtils.split(nickname).any { matchesTarget(compactQuery, compact(it)) }
    }

    private fun matchesTarget(
        compactQuery: String,
        target: String,
    ): Boolean {
        val queryLength = compactQuery.length
        if (target.length < queryLength + MIN_SKIPPED_LENGTH) return false
        for (prefixLength in MIN_EDGE_LENGTH..queryLength - MIN_EDGE_LENGTH) {
            val suffixLength = queryLength - prefixLength
            if (
                target.regionMatches(0, compactQuery, 0, prefixLength) &&
                target.regionMatches(target.length - suffixLength, compactQuery, prefixLength, suffixLength)
            ) {
                return true
            }
        }
        return false
    }

    private fun compact(text: String): String =
        SearchTextNormalizer.compactForSearch(SearchTextNormalizer.normalizeForSearch(text))
}
