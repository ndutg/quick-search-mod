package com.tk.quicksearch.search.data

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** A posted notification that App notifications can match. */
internal class AppNotification(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val contentIntent: PendingIntent?,
) {
    /** What keywords are matched against. */
    val searchText: String get() = listOfNotNull(title, text).joinToString("\n")
}

/**
 * Reads the notifications App notifications shows. Ongoing ones, media and the kinds other
 * At a Glance rows already cover (calls, alarms, progress, navigation) are skipped, as are
 * notifications hidden from At a Glance until the app posts them again.
 */
internal object AppNotifications {
    /** Post time of each notification hidden from At a Glance, by key. Guarded by itself. */
    private val dismissed = mutableMapOf<String, Long>()

    /**
     * The notifications [config] shows, newest first; nothing when it is off or has no apps or
     * keywords, and then no notification is read. [excludedKeys] are notifications another At a
     * Glance row already shows. Apps that can't match are skipped before their text is read.
     */
    fun parse(
        posted: List<StatusBarNotification>,
        excludedKeys: Set<String>,
        config: AppNotificationsConfig,
    ): List<AppNotification> {
        val postedKeys = posted.map { it.key }.toSet()
        synchronized(dismissed) { dismissed.keys.retainAll(postedKeys) }
        if (!config.isActive) return emptyList()
        val candidates =
            posted.mapNotNull { sbn ->
                sbn.takeIf {
                    it.key !in excludedKeys && config.filters.couldMatch(it.packageName) && isCandidate(it)
                }?.toAppNotification()
            }
        return select(candidates, excludedKeys, config)
    }

    /**
     * Of [notifications], those [config] shows, newest first, leaving out [excludedKeys] and the
     * ones hidden from At a Glance. Nothing when [config] is off or has no apps or keywords.
     */
    fun select(
        notifications: List<AppNotification>,
        excludedKeys: Set<String>,
        config: AppNotificationsConfig,
    ): List<AppNotification> {
        if (!config.isActive) return emptyList()
        val hidden = synchronized(dismissed) { dismissed.toMap() }
        return notifications
            .filter {
                it.key !in excludedKeys &&
                    hidden[it.key] != it.postTime &&
                    config.filters.matches(it.packageName, it.searchText)
            }.sortedByDescending { it.postTime }
    }

    /** Hides [notification] from At a Glance, leaving it posted; a newer post brings it back. */
    fun dismiss(notification: AppNotification) {
        synchronized(dismissed) { dismissed[notification.key] = notification.postTime }
    }

    fun clear() {
        synchronized(dismissed) { dismissed.clear() }
    }

    /** Whether [sbn] is a kind App notifications shows, before checking the user's apps and keywords. */
    fun isCandidate(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return false
        // Paused players are often neither ongoing nor in the transport category.
        if (notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return false
        return notification.category !in SKIPPED_CATEGORIES
    }

    private fun StatusBarNotification.toAppNotification(): AppNotification? {
        val extras = notification.extras ?: return null
        fun CharSequence?.clean() = this?.toString()?.trim()?.takeIf(String::isNotEmpty)
        val lastMessage =
            runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) }
                .getOrNull()
                ?.messages
                ?.lastOrNull()
        val title =
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).clean()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE).clean()
        val body =
            lastMessage?.let { message ->
                listOfNotNull(message.person?.name.clean(), message.text.clean()).joinToString(": ").ifEmpty { null }
            } ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT).clean()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT).clean()
        if (title == null && body == null) return null
        return AppNotification(
            key = key,
            packageName = packageName,
            title = title,
            text = body,
            postTime = postTime,
            contentIntent = notification.contentIntent,
        )
    }

    private val SKIPPED_CATEGORIES =
        setOf(
            Notification.CATEGORY_CALL,
            "missed_call",
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            "stopwatch",
            "navigation",
        )
}
