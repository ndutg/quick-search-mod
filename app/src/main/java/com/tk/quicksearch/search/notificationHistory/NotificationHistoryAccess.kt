package com.tk.quicksearch.search.notificationHistory

import android.content.Context
import com.tk.quicksearch.search.apps.notificationDots.NotificationDotsPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether notification listener access is currently granted.
 *
 * Reading notifications needs listener access, which is granted from a system settings screen
 * rather than a runtime permission dialog, so the state is refreshed on resume and whenever the
 * listener service connects or disconnects.
 */
object NotificationHistoryAccess {
    private val grantedState = MutableStateFlow<Boolean?>(null)

    /** `null` until the first check completes, so callers can avoid showing a wrong state. */
    val granted: StateFlow<Boolean?> = grantedState.asStateFlow()

    fun refresh(context: Context): Boolean {
        val isGranted = NotificationDotsPermission.hasNotificationListenerAccess(context)
        grantedState.value = isGranted
        return isGranted
    }

    fun set(isGranted: Boolean) {
        grantedState.value = isGranted
    }

    fun openSettings(context: Context) {
        NotificationDotsPermission.openNotificationListenerSettings(context)
    }
}
