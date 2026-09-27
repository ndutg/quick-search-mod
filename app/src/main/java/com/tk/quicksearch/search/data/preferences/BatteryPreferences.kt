package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.os.SystemClock

/**
 * Stores the home At a Glance toggles for the low battery and charging rows, plus the charging
 * row's dismissal, which lasts until the phone is unplugged.
 */
class BatteryPreferences(context: Context) : BasePreferences(context) {
    fun isShowLowBatteryEnabled(): Boolean = getBooleanPref(KEY_SHOW_LOW_BATTERY, true)

    fun setShowLowBatteryEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_LOW_BATTERY, enabled)

    fun isShowChargingEnabled(): Boolean = getBooleanPref(KEY_SHOW_CHARGING, true)

    fun setShowChargingEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_CHARGING, enabled)

    /**
     * The charge level when the charging row was dismissed, or null when it is not dismissed. A
     * reboot ends the dismissal, since the phone may have been unplugged while it was off.
     */
    fun getChargingDismissedPercent(): Int? {
        if (!prefs.contains(KEY_CHARGING_DISMISSED_PERCENT)) return null
        if (SystemClock.elapsedRealtime() < prefs.getLong(KEY_CHARGING_DISMISSED_AT, 0L)) {
            clearChargingDismissal()
            return null
        }
        return prefs.getInt(KEY_CHARGING_DISMISSED_PERCENT, 0)
    }

    fun dismissCharging(percent: Int) {
        prefs.edit()
            .putInt(KEY_CHARGING_DISMISSED_PERCENT, percent)
            .putLong(KEY_CHARGING_DISMISSED_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    fun clearChargingDismissal() {
        if (!prefs.contains(KEY_CHARGING_DISMISSED_PERCENT)) return
        prefs.edit().remove(KEY_CHARGING_DISMISSED_PERCENT).remove(KEY_CHARGING_DISMISSED_AT).apply()
    }

    companion object {
        /** The home row appears at or below this charge level while the phone is unplugged. */
        const val LOW_BATTERY_THRESHOLD_PERCENT = 15

        private const val KEY_SHOW_LOW_BATTERY = "home_show_low_battery"
        private const val KEY_SHOW_CHARGING = "home_show_charging"
        private const val KEY_CHARGING_DISMISSED_PERCENT = "home_charging_dismissed_percent"
        private const val KEY_CHARGING_DISMISSED_AT = "home_charging_dismissed_at"
    }
}
