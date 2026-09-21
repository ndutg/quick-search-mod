package com.tk.quicksearch.search.data.preferences

import android.content.Context

/** Stores the home card toggle and dismissal for only the currently surfaced alarm instance. */
class UpcomingAlarmPreferences(context: Context) : BasePreferences(context) {
    fun isShowUpcomingAlarmEnabled(): Boolean = getBooleanPref(KEY_SHOW_UPCOMING_ALARM, true)

    fun setShowUpcomingAlarmEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_UPCOMING_ALARM, enabled)

    fun isDismissed(triggerTime: Long): Boolean =
        prefs.getLong(KEY_DISMISSED_TRIGGER_TIME, NO_DISMISSED_ALARM) == triggerTime

    fun dismiss(triggerTime: Long) {
        prefs.edit().putLong(KEY_DISMISSED_TRIGGER_TIME, triggerTime).apply()
    }

    companion object {
        private const val KEY_SHOW_UPCOMING_ALARM = "home_show_upcoming_alarm"
        private const val KEY_DISMISSED_TRIGGER_TIME = "upcoming_alarm_dismissed_trigger_time"
        private const val NO_DISMISSED_ALARM = Long.MIN_VALUE
    }
}
