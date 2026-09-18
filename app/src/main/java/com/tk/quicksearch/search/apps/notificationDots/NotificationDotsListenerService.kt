package com.tk.quicksearch.search.apps.notificationDots

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tk.quicksearch.search.notificationHistory.NotificationHistoryAccess
import com.tk.quicksearch.search.notificationHistory.NotificationHistoryStore

class NotificationDotsListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = runCatching { activeNotifications }.getOrNull()
        NotificationDotsStore.updateFromNotifications(active)
        NotificationHistoryAccess.set(true)
        NotificationHistoryStore.seed(this, active)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationDotsStore.clear()
        NotificationHistoryAccess.set(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        NotificationDotsStore.updateFromNotifications(runCatching { activeNotifications }.getOrNull())
        NotificationHistoryStore.record(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotificationDotsStore.updateFromNotifications(runCatching { activeNotifications }.getOrNull())
    }
}
