package com.tk.quicksearch.shared.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.os.Bundle

/**
 * Activity options that let a [PendingIntent] or IntentSender sent from a visible screen of this
 * app start an activity. Null below Android 14, where no opt-in is needed.
 */
fun pendingIntentSenderOptions(): Bundle? {
    // Android 14+ only lets a PendingIntent start an activity if the sender opts in. Android 17
    // honors the opt-in only while the sender is visible.
    val mode =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                @Suppress("DEPRECATION")
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            else -> return null
        }
    return ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
}

/**
 * Sends another app's activity [PendingIntent] (a notification's tap action, a media session's
 * activity) in response to a tap in this app. Returns false if the intent was cancelled or refused.
 */
fun PendingIntent.sendFromUserTap(): Boolean =
    runCatching {
        val options = pendingIntentSenderOptions()
        if (options != null) {
            send(null, 0, null, null, null, null, options)
        } else {
            send()
        }
    }.isSuccess
