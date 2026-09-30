package com.tk.quicksearch.app

import android.app.Activity

/** No-op for F-Droid builds (Google Play in-app updates are unavailable). */
object UpdateHelper {
    fun checkForUpdates(
        activity: Activity,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) = Unit

    fun checkForUpdatesIfDue(
        activity: Activity,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) = Unit

    fun startUpdate(activity: Activity) = Unit

    fun completeUpdate(activity: Activity) = Unit
}
