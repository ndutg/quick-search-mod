package com.tk.quicksearch.shared.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build

/**
 * Sends another app's activity [PendingIntent] (a notification's tap action, a media session's
 * activity) in response to a tap in this app. Returns false if the intent was cancelled or refused.
 */
@Suppress("DEPRECATION")
fun PendingIntent.sendFromUserTap(): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ only lets a PendingIntent start an activity if the sender opts in.
            val options =
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
            send(null, 0, null, null, null, null, options.toBundle())
        } else {
            send()
        }
    }.isSuccess
