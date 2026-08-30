package com.tk.quicksearch.search.utils

import com.tk.quicksearch.search.searchHistory.RecentSearchEntry
import com.tk.quicksearch.search.models.SecondaryRankingSignal
import java.util.Locale
import java.util.IdentityHashMap

object RecentResultRankingUtils {
    data class RecencyIndex(
        val contactScores: Map<Long, Int> = emptyMap(),
        val fileScores: Map<String, Int> = emptyMap(),
        val settingScores: Map<String, Int> = emptyMap(),
        val appShortcutScores: Map<String, Int> = emptyMap(),
        val appSettingScores: Map<String, Int> = emptyMap(),
        val noteScores: Map<Long, Int> = emptyMap(),
        val contactOpenCounts: Map<Long, Int> = emptyMap(),
        val fileOpenCounts: Map<String, Int> = emptyMap(),
        val settingOpenCounts: Map<String, Int> = emptyMap(),
        val appShortcutOpenCounts: Map<String, Int> = emptyMap(),
        val appSettingOpenCounts: Map<String, Int> = emptyMap(),
        val noteOpenCounts: Map<Long, Int> = emptyMap(),
        val calendarLastOpenedTimes: Map<Long, Long> = emptyMap(),
        val calendarOpenCounts: Map<Long, Int> = emptyMap(),
    )

    fun buildRecencyIndex(
        entries: List<RecentSearchEntry>,
        openCounts: Map<String, Int> = emptyMap(),
        lastOpenedTimes: Map<String, Long> = emptyMap(),
    ): RecencyIndex {

        val contactScores = LinkedHashMap<Long, Int>()
        val fileScores = LinkedHashMap<String, Int>()
        val settingScores = LinkedHashMap<String, Int>()
        val appShortcutScores = LinkedHashMap<String, Int>()
        val appSettingScores = LinkedHashMap<String, Int>()
        val noteScores = LinkedHashMap<Long, Int>()

        val maxScore = entries.size
        entries.forEachIndexed { index, entry ->
            val recencyScore = maxScore - index
            when (entry) {
                is RecentSearchEntry.Contact -> contactScores.putIfAbsent(entry.contactId, recencyScore)
                is RecentSearchEntry.File -> fileScores.putIfAbsent(entry.uri, recencyScore)
                is RecentSearchEntry.Setting -> settingScores.putIfAbsent(entry.id, recencyScore)
                is RecentSearchEntry.AppShortcut ->
                    appShortcutScores.putIfAbsent(entry.shortcutKey, recencyScore)
                is RecentSearchEntry.AppSetting -> appSettingScores.putIfAbsent(entry.id, recencyScore)
                is RecentSearchEntry.Note -> noteScores.putIfAbsent(entry.noteId, recencyScore)
                is RecentSearchEntry.Query -> Unit
            }
        }

        return RecencyIndex(
            contactScores = contactScores,
            fileScores = fileScores,
            settingScores = settingScores,
            appShortcutScores = appShortcutScores,
            appSettingScores = appSettingScores,
            noteScores = noteScores,
            contactOpenCounts = countsByLongKey(openCounts, "contact:"),
            fileOpenCounts = countsByStringKey(openCounts, "file:"),
            settingOpenCounts = countsByStringKey(openCounts, "setting:"),
            appShortcutOpenCounts = countsByStringKey(openCounts, "app_shortcut:"),
            appSettingOpenCounts = countsByStringKey(openCounts, "app_setting:"),
            noteOpenCounts = countsByLongKey(openCounts, "note:"),
            calendarLastOpenedTimes = longValuesByLongKey(lastOpenedTimes, "calendar:"),
            calendarOpenCounts = countsByLongKey(openCounts, "calendar:"),
        )
    }

    private fun countsByStringKey(
        openCounts: Map<String, Int>,
        prefix: String,
    ): Map<String, Int> =
        openCounts.mapNotNull { (key, count) ->
            key.takeIf { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.let { it to count }
        }.toMap()

    private fun countsByLongKey(
        openCounts: Map<String, Int>,
        prefix: String,
    ): Map<Long, Int> =
        countsByStringKey(openCounts, prefix).mapNotNull { (key, count) ->
            key.toLongOrNull()?.let { it to count }
        }.toMap()

    private fun longValuesByLongKey(
        values: Map<String, Long>,
        prefix: String,
    ): Map<Long, Long> =
        values.mapNotNull { (key, value) ->
            key.takeIf { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.toLongOrNull()
                ?.let { it to value }
        }.toMap()

    fun <T, K> matchThenRecencyThenAlphabeticalComparator(
        recencyScores: Map<K, Int>,
        openCounts: Map<K, Int> = emptyMap(),
        secondaryRankingSignal: SecondaryRankingSignal = SecondaryRankingSignal.DEFAULT,
        keySelector: (T) -> K,
        labelSelector: (T) -> String,
    ): Comparator<Pair<T, Int>> {
        val alphabeticalKeys = IdentityHashMap<T, String>()
        var comparator = compareBy<Pair<T, Int>> { it.second }
        comparator =
            when (secondaryRankingSignal) {
                SecondaryRankingSignal.RECENCY ->
                    comparator.thenByDescending { recencyScores[keySelector(it.first)] ?: 0 }
                SecondaryRankingSignal.MOST_OPENED ->
                    comparator.thenByDescending { openCounts[keySelector(it.first)] ?: 0 }
                SecondaryRankingSignal.NONE -> comparator
            }
        return comparator.thenBy {
            alphabeticalKeys.getOrPut(it.first) {
                labelSelector(it.first).lowercase(Locale.getDefault())
            }
        }
    }
}
