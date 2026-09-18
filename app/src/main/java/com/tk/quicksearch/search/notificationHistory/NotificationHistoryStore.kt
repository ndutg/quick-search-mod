package com.tk.quicksearch.search.notificationHistory

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors

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
}

/**
 * Bounded, persisted log of the notifications seen by the shared notification listener service.
 *
 * Rows live in Room so each posting is a small insert on a background thread instead of rewriting
 * the whole log. History can only accumulate while notification listener access is granted; the
 * platform exposes no way to backfill notifications posted before that.
 */
object NotificationHistoryStore {
    private const val PREFS_NAME = "notification_history"
    private const val KEY_LEGACY_ENTRIES = "entries"
    private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
    private const val MAX_ENTRIES = 500

    // Hidden until the user opts in; System UI mostly posts transient status notifications.
    // Always listed in the app filter so it can be enabled before it has any history.
    val DEFAULT_HIDDEN_PACKAGES = setOf("com.android.systemui")

    private val hiddenPackagesState = MutableStateFlow<Set<String>>(emptySet())

    // A single thread keeps writes in posting order without blocking the listener's main thread.
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    // PendingIntents can't be persisted, so tap actions only survive while this process is alive.
    // Bounded like the log itself; the oldest postings drop out first.
    private val contentIntents =
        object : LinkedHashMap<String, Pair<String, PendingIntent>>() {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<String, PendingIntent>>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var dao: NotificationHistoryDao? = null

    /** Apps whose notifications are hidden from history and no longer recorded. */
    val hiddenPackages: StateFlow<Set<String>> = hiddenPackagesState.asStateFlow()

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        val loaded = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dao = NotificationHistoryDatabase.get(appContext).notificationHistoryDao()
        val storedHidden = loaded.getStringSet(KEY_HIDDEN_PACKAGES, null)?.toSet()
        hiddenPackagesState.value = storedHidden ?: DEFAULT_HIDDEN_PACKAGES
        if (storedHidden == null) {
            // Persist the defaults once so re-enabling a default app sticks.
            loaded.edit().putStringSet(KEY_HIDDEN_PACKAGES, DEFAULT_HIDDEN_PACKAGES).apply()
        }
        // History used to be one JSON blob in preferences; it now lives in Room.
        if (loaded.contains(KEY_LEGACY_ENTRIES)) loaded.edit().remove(KEY_LEGACY_ENTRIES).apply()
        prefs = loaded
    }

    /** Newest-first history, re-queried only while collected and only when the log changes. */
    fun entries(context: Context): Flow<List<NotificationHistoryEntry>> {
        ensureLoaded(context)
        return requireDao()
            .observeRecent(MAX_ENTRIES)
            .map { rows -> rows.map(NotificationHistoryEntity::toModel) }
            .distinctUntilChanged()
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
            prefs?.edit()?.putStringSet(KEY_HIDDEN_PACKAGES, updated)?.apply()
        }
        if (hidden) {
            // Hiding an app discards what was already captured from it, not just future posts.
            synchronized(contentIntents) {
                contentIntents.values.removeAll { (owner, _) -> owner == packageName }
            }
            onDatabaseThread { deleteByPackage(packageName) }
        }
    }

    fun contentIntentFor(entry: NotificationHistoryEntry): PendingIntent? =
        synchronized(contentIntents) { contentIntents[entry.contentIntentKey()]?.second }

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

    /** Deletes a single history row. */
    fun remove(
        context: Context,
        entry: NotificationHistoryEntry,
    ) {
        ensureLoaded(context)
        synchronized(contentIntents) { contentIntents.remove(entry.contentIntentKey()) }
        onDatabaseThread { delete(entry.key, entry.title, entry.text) }
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        synchronized(contentIntents) { contentIntents.clear() }
        onDatabaseThread { deleteAll() }
    }

    private fun append(additions: List<NotificationHistoryEntry>) {
        val rows = additions.map(NotificationHistoryEntry::toEntity)
        onDatabaseThread { record(rows, MAX_ENTRIES) }
    }

    private fun rememberContentIntent(
        entry: NotificationHistoryEntry,
        notification: StatusBarNotification,
    ) {
        val intent = runCatching { notification.notification?.contentIntent }.getOrNull() ?: return
        synchronized(contentIntents) {
            contentIntents[entry.contentIntentKey()] = entry.packageName to intent
        }
    }

    private fun requireDao(): NotificationHistoryDao =
        checkNotNull(dao) { "NotificationHistoryStore.ensureLoaded must be called first" }

    private fun onDatabaseThread(block: NotificationHistoryDao.() -> Unit) {
        val target = requireDao()
        databaseExecutor.execute {
            runCatching { target.block() }
                .onFailure { Log.w(TAG, "Notification history write failed", it) }
        }
    }

    private const val TAG = "NotificationHistory"
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
