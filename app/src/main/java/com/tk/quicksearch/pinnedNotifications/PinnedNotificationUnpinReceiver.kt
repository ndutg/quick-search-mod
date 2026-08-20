package com.tk.quicksearch.pinnedNotifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction

class PinnedNotificationUnpinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CustomWidgetButtonAction.fromJson(intent.getStringExtra(ExtraAction))?.let { action ->
            PinnedNotifications.remove(context, action)
        }
    }

    companion object {
        const val ExtraAction = "pinned_notification_action"
    }
}
