package com.tk.quicksearch.search.utils

object SearchTokenCoveragePolicy {
    fun areAllTokensCovered(
        query: SearchQueryContext,
        primaryText: String,
        supportingText: String?,
        fuzzyMinScore: Int,
        fuzzyMaxEditDistance: Int,
    ): Boolean {
        if (query.tokens.size <= 1) return true

        val normalizedPrimary = SearchTextNormalizer.normalizeForSearch(primaryText)
        val normalizedSupporting =
            supportingText
                ?.takeIf { it.isNotBlank() }
                ?.let(SearchTextNormalizer::normalizeForSearch)

        return query.tokens.all { token ->
            normalizedPrimary.contains(token) ||
                (!normalizedSupporting.isNullOrBlank() && normalizedSupporting.contains(token)) ||
                FuzzyMatcher.score(
                    query = token,
                    primaryTarget = normalizedPrimary,
                    secondaryTarget = normalizedSupporting,
                    maxEditDistance = fuzzyMaxEditDistance,
                ) >= fuzzyMinScore
        }
    }
}
