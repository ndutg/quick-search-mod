package com.tk.quicksearch.search.data.preferences

import com.tk.quicksearch.search.searchHistory.RecentSearchEntry

/**
 * Stores a bounded list of recently opened non-query search results for ranking boosts.
 */
class RecentResultOpensPreferences(
    context: android.content.Context,
) : BasePreferences(context) {
    private val openCountsPrefs =
        context.applicationContext.getSharedPreferences(
            OPEN_COUNTS_PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
    private val lastOpenedPrefs =
        context.applicationContext.getSharedPreferences(
            LAST_OPENED_PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )

    fun getRecentResultOpens(): List<RecentSearchEntry> {
        val rawItems =
            PreferenceUtils.getStringListPref(
                sessionPrefs,
                BasePreferences.KEY_RECENT_RESULT_OPENS,
            )
        return rawItems
            .mapNotNull { RecentSearchEntry.fromRaw(it) }
            .filter(::isRankableEntry)
    }

    fun addRecentResultOpen(entry: RecentSearchEntry) {
        if (!isRankableEntry(entry)) return

        recordResultOpen(entry.stableKey)

        val currentItems = getRecentResultOpens().toMutableList()
        currentItems.removeAll { it.stableKey == entry.stableKey }
        currentItems.add(0, entry)

        val limited = currentItems.take(MAX_RECENT_RESULT_OPENS)
        PreferenceUtils.setStringListPref(
            sessionPrefs,
            BasePreferences.KEY_RECENT_RESULT_OPENS,
            limited.map { it.toJsonString() },
        )
    }

    fun deleteRecentResultOpen(entry: RecentSearchEntry) {
        val currentItems = getRecentResultOpens().toMutableList()
        currentItems.removeAll { it.stableKey == entry.stableKey }
        PreferenceUtils.setStringListPref(
            sessionPrefs,
            BasePreferences.KEY_RECENT_RESULT_OPENS,
            currentItems.map { it.toJsonString() },
        )
        openCountsPrefs.edit().remove(entry.stableKey).apply()
        lastOpenedPrefs.edit().remove(entry.stableKey).apply()
    }

    fun getRecentResultOpenCounts(): Map<String, Int> =
        openCountsPrefs.all.mapValues { (_, value) -> value as? Int ?: 0 }

    fun getRecentResultLastOpenedTimes(): Map<String, Long> =
        lastOpenedPrefs.all.mapValues { (_, value) -> value as? Long ?: 0L }

    fun recordCalendarEventOpen(eventId: Long) {
        recordResultOpen("calendar:$eventId")
    }

    fun clearRecentResultOpens() {
        PreferenceUtils.setStringListPref(
            sessionPrefs,
            BasePreferences.KEY_RECENT_RESULT_OPENS,
            emptyList<String>(),
        )
        openCountsPrefs.edit().clear().apply()
        lastOpenedPrefs.edit().clear().apply()
    }

    private fun recordResultOpen(stableKey: String) {
        val currentCount = openCountsPrefs.getInt(stableKey, 0)
        openCountsPrefs.edit().putInt(stableKey, currentCount + 1).apply()
        lastOpenedPrefs.edit().putLong(stableKey, System.currentTimeMillis()).apply()
    }

    private fun isRankableEntry(entry: RecentSearchEntry): Boolean =
        entry is RecentSearchEntry.Contact ||
            entry is RecentSearchEntry.File ||
            entry is RecentSearchEntry.Setting ||
            entry is RecentSearchEntry.AppSetting ||
            entry is RecentSearchEntry.Note ||
            entry is RecentSearchEntry.AppShortcut

    companion object {
        private const val MAX_RECENT_RESULT_OPENS = 100
        private const val OPEN_COUNTS_PREFS_NAME = "recent_result_open_counts"
        private const val LAST_OPENED_PREFS_NAME = "recent_result_last_opened"
    }
}
