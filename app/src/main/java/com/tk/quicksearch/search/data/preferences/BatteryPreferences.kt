package com.tk.quicksearch.search.data.preferences

import android.content.Context

/** Stores the home At a Glance toggle for the low battery row. */
class BatteryPreferences(context: Context) : BasePreferences(context) {
    fun isShowLowBatteryEnabled(): Boolean = getBooleanPref(KEY_SHOW_LOW_BATTERY, true)

    fun setShowLowBatteryEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_LOW_BATTERY, enabled)

    companion object {
        /** The home row appears at or below this charge level while the phone is unplugged. */
        const val LOW_BATTERY_THRESHOLD_PERCENT = 15

        private const val KEY_SHOW_LOW_BATTERY = "home_show_low_battery"
    }
}
