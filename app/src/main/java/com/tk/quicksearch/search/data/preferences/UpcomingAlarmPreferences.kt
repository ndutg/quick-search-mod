package com.tk.quicksearch.search.data.preferences

import android.content.Context

/** Stores dismissal for only the currently surfaced alarm instance. */
class UpcomingAlarmPreferences(context: Context) : BasePreferences(context) {
    fun isDismissed(triggerTime: Long): Boolean =
        prefs.getLong(KEY_DISMISSED_TRIGGER_TIME, NO_DISMISSED_ALARM) == triggerTime

    fun dismiss(triggerTime: Long) {
        prefs.edit().putLong(KEY_DISMISSED_TRIGGER_TIME, triggerTime).apply()
    }

    companion object {
        private const val KEY_DISMISSED_TRIGGER_TIME = "upcoming_alarm_dismissed_trigger_time"
        private const val NO_DISMISSED_ALARM = Long.MIN_VALUE
    }
}
