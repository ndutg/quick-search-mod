package com.tk.quicksearch.tools.setAlarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.tk.quicksearch.tools.dateCalculator.DateCalculatorUtils
import java.time.LocalTime

/**
 * Detects a standalone time in the search query and launches the system alarm intent for it.
 *
 * Detection reuses [DateCalculatorUtils.parseTimeString] so the tool recognizes exactly the same
 * time expressions the date calculator does ("5am", "3:45", "14:30", "5:30 pm").
 */
object SetAlarmHandler {

    /** Returns the time expressed by [query], or null when the query is not a standalone time. */
    fun detectAlarmTime(query: String): LocalTime? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return DateCalculatorUtils.parseTimeString(trimmed)
    }

    /**
     * Opens the system clock app prefilled with [time]. The clock app's own UI is shown so the user
     * confirms before the alarm is created.
     *
     * @return false when no installed app handles [AlarmClock.ACTION_SET_ALARM].
     */
    fun launchSetAlarm(context: Context, time: LocalTime): Boolean {
        val intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
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
