package com.tk.quicksearch.pinnedNotifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reposts the ongoing notification if a system UI permits dismissing it. */
class PinnedNotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PinnedNotifications.show(context)
    }
}
