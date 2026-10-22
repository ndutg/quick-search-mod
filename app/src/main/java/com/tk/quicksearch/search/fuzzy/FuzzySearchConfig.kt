package com.tk.quicksearch.search.fuzzy

import com.tk.quicksearch.search.utils.SearchRankingUtils

/**
 * Configuration for fuzzy search behavior.
 * Centralized configuration to avoid scattering across multiple files.
 */
data class FuzzySearchConfig(
    val matchThreshold: Int = 75,
    val minQueryLength: Int = 3,
    val priority: Int = SearchRankingUtils.PRIORITY_FUZZY_MATCH,
) {
    companion object {
        /**
         * Default configuration for app search.
         */
        val DEFAULT_APP_CONFIG =
            FuzzySearchConfig(
                matchThreshold = 75,
                minQueryLength = 3,
                priority = SearchRankingUtils.PRIORITY_FUZZY_MATCH,
            )

        /**
         * Creates a config from individual parameters.
         */
        fun create(
            matchThreshold: Int = DEFAULT_APP_CONFIG.matchThreshold,
            minQueryLength: Int = DEFAULT_APP_CONFIG.minQueryLength,
            priority: Int = DEFAULT_APP_CONFIG.priority,
        ) = FuzzySearchConfig(matchThreshold, minQueryLength, priority)
    }
}
