package com.tk.quicksearch.shared.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build

/**
 * Sends another app's activity [PendingIntent] (a notification's tap action, a media session's
 * activity) in response to a tap in this app. Returns false if the intent was cancelled or refused.
 */
fun PendingIntent.sendFromUserTap(): Boolean =
    runCatching {
        // Android 14+ only lets a PendingIntent start an activity if the sender opts in. Android 17
        // honors the opt-in only while the sender is visible, which a tap in this app guarantees.
        val mode =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                else -> null
            }
        if (mode != null) {
            val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode)
            send(null, 0, null, null, null, null, options.toBundle())
        } else {
            send()
        }
    }.isSuccess
