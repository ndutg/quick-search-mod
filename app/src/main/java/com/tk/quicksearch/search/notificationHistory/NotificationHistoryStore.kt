package com.tk.quicksearch.search.notificationHistory

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** A single notification as it was posted, captured for the Notification History screen. */
data class NotificationHistoryEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
) {
    /** Identifies this exact posting, used to find its live tap action. */
    internal fun contentIntentKey(): String = "$key|$postTime"

    /** Collapses repeated updates of the same notification into one history row. */
    internal fun dedupeKey(): String = "$key|$title|$text"
}

/**
 * Bounded, persisted log of the notifications seen by the shared notification listener service.
 *
 * History can only accumulate while notification listener access is granted; the platform exposes
 * no way to backfill notifications posted before that.
 */
object NotificationHistoryStore {
    private const val PREFS_NAME = "notification_history"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
    private const val MAX_ENTRIES = 500

    private val entriesState = MutableStateFlow<List<NotificationHistoryEntry>>(emptyList())
    private val hiddenPackagesState = MutableStateFlow<Set<String>>(emptySet())

    // PendingIntents can't be persisted, so tap actions only survive while this process is alive.
    private val contentIntents = ConcurrentHashMap<String, PendingIntent>()

    @Volatile
    private var prefs: SharedPreferences? = null

    val entries: StateFlow<List<NotificationHistoryEntry>> = entriesState.asStateFlow()

    /** Apps whose notifications are hidden from history and no longer recorded. */
    val hiddenPackages: StateFlow<Set<String>> = hiddenPackagesState.asStateFlow()

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val loaded =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = loaded
        val hidden = loaded.getStringSet(KEY_HIDDEN_PACKAGES, null).orEmpty().toSet()
        val decoded = decode(loaded.getString(KEY_ENTRIES, null))
        val visible = decoded.filterNot { it.packageName in hidden }
        hiddenPackagesState.value = hidden
        entriesState.value = visible
        // Purge anything stored for hidden apps (e.g. captured before hiding deleted history).
        if (visible.size != decoded.size) {
            loaded.edit().putString(KEY_ENTRIES, encode(visible)).apply()
        }
    }

    fun setPackageHidden(
        context: Context,
        packageName: String,
        hidden: Boolean,
    ) {
        ensureLoaded(context)
        synchronized(this) {
            val updated =
                if (hidden) {
                    hiddenPackagesState.value + packageName
                } else {
                    hiddenPackagesState.value - packageName
                }
            if (updated == hiddenPackagesState.value) return
            hiddenPackagesState.value = updated
            val editor = prefs?.edit()?.putStringSet(KEY_HIDDEN_PACKAGES, updated)
            if (hidden) {
                // Hiding an app discards what was already captured from it, not just future posts.
                val remaining = entriesState.value.filterNot { it.packageName == packageName }
                entriesState.value = remaining
                contentIntents.keys.retainAll(remaining.mapTo(HashSet()) { it.contentIntentKey() })
                editor?.putString(KEY_ENTRIES, encode(remaining))
            }
            editor?.apply()
        }
    }

    fun contentIntentFor(entry: NotificationHistoryEntry): PendingIntent? =
        contentIntents[entry.contentIntentKey()]

    /** Records a single newly posted notification. */
    fun record(
        context: Context,
        notification: StatusBarNotification?,
    ) {
        val entry = notification?.toHistoryEntry() ?: return
        ensureLoaded(context)
        if (entry.packageName in hiddenPackagesState.value) return
        rememberContentIntent(entry, notification)
        append(listOf(entry))
    }

    /**
     * Seeds the log from the currently active notifications so a freshly granted screen is not
     * blank on first open.
     */
    fun seed(
        context: Context,
        notifications: Array<StatusBarNotification>?,
    ) {
        ensureLoaded(context)
        val hidden = hiddenPackagesState.value
        val seeded =
            notifications.orEmpty().mapNotNull { notification ->
                notification.toHistoryEntry()
                    ?.takeUnless { it.packageName in hidden }
                    ?.also { rememberContentIntent(it, notification) }
            }
        if (seeded.isEmpty()) return
        append(seeded)
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        synchronized(this) {
            entriesState.value = emptyList()
            prefs?.edit()?.remove(KEY_ENTRIES)?.apply()
        }
    }

    @Synchronized
    private fun append(additions: List<NotificationHistoryEntry>) {
        val hidden = hiddenPackagesState.value
        val byDedupeKey = linkedMapOf<String, NotificationHistoryEntry>()
        entriesState.value.forEach { byDedupeKey[it.dedupeKey()] = it }
        additions.filterNot { it.packageName in hidden }.forEach { addition ->
            val existing = byDedupeKey[addition.dedupeKey()]
            byDedupeKey[addition.dedupeKey()] =
                if (existing == null || addition.postTime > existing.postTime) addition else existing
        }

        val merged =
            byDedupeKey.values
                .sortedByDescending { it.postTime }
                .take(MAX_ENTRIES)
        if (merged == entriesState.value) return

        entriesState.value = merged
        val retainedKeys = merged.mapTo(HashSet()) { it.contentIntentKey() }
        contentIntents.keys.retainAll(retainedKeys)
        prefs?.edit()?.putString(KEY_ENTRIES, encode(merged))?.apply()
    }

    private fun rememberContentIntent(
        entry: NotificationHistoryEntry,
        notification: StatusBarNotification,
    ) {
        val intent = runCatching { notification.notification?.contentIntent }.getOrNull() ?: return
        contentIntents[entry.contentIntentKey()] = intent
    }

    private fun encode(entries: List<NotificationHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(FIELD_KEY, entry.key)
                    .put(FIELD_PACKAGE, entry.packageName)
                    .put(FIELD_TITLE, entry.title)
                    .put(FIELD_TEXT, entry.text)
                    .put(FIELD_POST_TIME, entry.postTime),
            )
        }
        return array.toString()
    }

    private fun decode(stored: String?): List<NotificationHistoryEntry> {
        if (stored.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val packageName = json.optString(FIELD_PACKAGE)
                if (packageName.isBlank()) continue
                add(
                    NotificationHistoryEntry(
                        key = json.optString(FIELD_KEY),
                        packageName = packageName,
                        title = json.optString(FIELD_TITLE),
                        text = json.optString(FIELD_TEXT),
                        postTime = json.optLong(FIELD_POST_TIME),
                    ),
                )
            }
        }
    }

    private const val FIELD_KEY = "key"
    private const val FIELD_PACKAGE = "package"
    private const val FIELD_TITLE = "title"
    private const val FIELD_TEXT = "text"
    private const val FIELD_POST_TIME = "postTime"
}

private fun StatusBarNotification.toHistoryEntry(): NotificationHistoryEntry? {
    val posted = runCatching { notification }.getOrNull() ?: return null
    if (posted.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

    val extras = runCatching { posted.extras }.getOrNull()
    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    // Prefer the expanded content (big text / inbox lines) so history shows the full notification.
    val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
    val textLines =
        extras
            ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { line -> line?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            ?.joinToString("\n")
    val plainText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
    val text =
        listOf(bigText, textLines, plainText)
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()
    if (title.isBlank() && text.isBlank()) return null

    return NotificationHistoryEntry(
        key = runCatching { key }.getOrNull().orEmpty(),
        packageName = packageName.orEmpty(),
        title = title,
        text = text,
        postTime = postTime,
    )
}
