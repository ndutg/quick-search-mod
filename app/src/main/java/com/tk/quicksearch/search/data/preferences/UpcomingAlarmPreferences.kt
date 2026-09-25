package com.tk.quicksearch.search.data.preferences

import android.content.Context

/**
 * Stores the home card toggle, the dismissal for only the currently surfaced alarm instance, and
 * the apps whose alarms the user chose to hide.
 */
class UpcomingAlarmPreferences(context: Context) : BasePreferences(context) {
    fun isShowUpcomingAlarmEnabled(): Boolean = getBooleanPref(KEY_SHOW_UPCOMING_ALARM, true)

    fun setShowUpcomingAlarmEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_UPCOMING_ALARM, enabled)

    fun isDismissed(triggerTime: Long): Boolean =
        prefs.getLong(KEY_DISMISSED_TRIGGER_TIME, NO_DISMISSED_ALARM) == triggerTime

    fun dismiss(triggerTime: Long) {
        prefs.edit().putLong(KEY_DISMISSED_TRIGGER_TIME, triggerTime).apply()
    }

    fun isHiddenPackage(packageName: String): Boolean =
        packageName in getHiddenPackages()

    fun getHiddenPackages(): Set<String> = getStringSet(KEY_HIDDEN_PACKAGES)

    fun hidePackage(packageName: String) {
        updateStringSet(KEY_HIDDEN_PACKAGES) { it.add(packageName) }
    }

    fun unhidePackage(packageName: String): Set<String> =
        updateStringSet(KEY_HIDDEN_PACKAGES) { it.remove(packageName) }

    companion object {
        private const val KEY_SHOW_UPCOMING_ALARM = "home_show_upcoming_alarm"
        private const val KEY_DISMISSED_TRIGGER_TIME = "upcoming_alarm_dismissed_trigger_time"
        private const val KEY_HIDDEN_PACKAGES = "upcoming_alarm_hidden_packages"
        private const val NO_DISMISSED_ALARM = Long.MIN_VALUE
    }
}
