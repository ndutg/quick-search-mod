package com.tk.quicksearch.search.notificationHistory

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

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
        if (contentIntent != null && sendContentIntent(contentIntent)) return true

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launchIntent) }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun sendContentIntent(contentIntent: PendingIntent): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ only lets a PendingIntent start an activity if the sender opts in.
                val options =
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                contentIntent.send(null, 0, null, null, null, null, options.toBundle())
            } else {
                contentIntent.send()
            }
        }.isSuccess
}
