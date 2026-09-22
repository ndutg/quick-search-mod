package com.tk.quicksearch.search.notificationHistory

import android.content.Context
import android.content.Intent
import com.tk.quicksearch.shared.util.sendFromUserTap

/** Opens a history entry the way tapping the original notification would. */
object NotificationHistoryLauncher {
    /**
     * Fires the notification's own tap action while it is still known, otherwise falls back to
     * launching the posting app. Returns false when neither is possible.
     */
    fun open(
        context: Context,
        entry: NotificationHistoryEntry,
    ): Boolean {
        val contentIntent = NotificationHistoryStore.contentIntentFor(entry)
        if (contentIntent != null && contentIntent.sendFromUserTap()) return true

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launchIntent) }.isSuccess
    }
}
