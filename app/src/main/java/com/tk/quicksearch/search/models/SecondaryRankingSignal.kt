package com.tk.quicksearch.search.models

enum class SecondaryRankingSignal {
    RECENCY,
    MOST_OPENED,
    NONE,
    ;

    companion object {
        val DEFAULT = RECENCY

        fun fromStorage(value: String?): SecondaryRankingSignal =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
