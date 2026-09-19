package com.tk.quicksearch.tools.setAlarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Locale

/**
 * Detects a standalone duration in the search query ("10min", "10 min", "10 minutes", "10h",
 * "10 hrs", "10 hours") and starts a timer for it through the system clock app.
 *
 * Bare "m" is deliberately not a minute alias: the unit converter already reads "10 m" as meters.
 */
object StartTimerHandler {

    private const val MAX_TIMER_SECONDS = 24 * 60 * 60

    private val durationPattern =
        Regex("""^(\d{1,4})\s*(h|hr|hrs|hour|hours|min|mins|minute|minutes)$""")

    /** Returns the timer length in seconds, or null when [query] is not a standalone duration. */
    fun detectTimerSeconds(query: String): Int? {
        val match = durationPattern.matchEntire(query.trim().lowercase(Locale.US)) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        if (amount <= 0) return null
        val seconds =
            if (match.groupValues[2].startsWith("h")) {
                amount.toLong() * 3600L
            } else {
                amount.toLong() * 60L
            }
        if (seconds > MAX_TIMER_SECONDS) return null
        return seconds.toInt()
    }

    /**
     * Starts a timer of [seconds] in the system clock app without showing its UI, so the pill acts
     * immediately. Callers confirm the start to the user themselves.
     *
     * @return false when no installed app handles [AlarmClock.ACTION_SET_TIMER].
     */
    fun launchStartTimer(context: Context, seconds: Int): Boolean {
        val intent =
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
